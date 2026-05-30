package com.kingzcheung.xime.settings

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.util.zip.ZipInputStream

data class SchemaMeta(
    val schemaId: String,
    val name: String,
    val version: String = "",
    val author: String = "",
    val description: String = ""
)

object SchemaManager {
    private const val TAG = "SchemaManager"
    private const val CUSTOM_YAML = "default.custom.yaml"

    private fun getSharedDir(context: Context): File =
        File(context.filesDir, "rime/shared")

    private fun getUserDir(context: Context): File =
        File(context.filesDir, "rime/user")

    private fun getBuildDir(context: Context): File =
        File(getUserDir(context), "build")

    private fun getCustomYamlFile(context: Context): File =
        File(getUserDir(context), CUSTOM_YAML)

    fun isSchemaCompiled(context: Context, schemaId: String): Boolean {
        return File(getBuildDir(context), "$schemaId.prism.bin").exists()
    }

    fun getReferencedDictName(context: Context, schemaId: String): String? {
        val schemaFile = File(getSharedDir(context), "$schemaId.schema.yaml")
        if (!schemaFile.exists()) return null
        return try {
            val content = schemaFile.readText()
            // matches "  dictionary: wubi86" or "translator/dictionary: wubi86" or inline {dictionary:wubi86}
            val regex = Regex("""dictionary\s*:\s*['\"]?(\w[\w-]*)['\"]?""")
            regex.find(content)?.groupValues?.getOrNull(1)
        } catch (e: Exception) { null }
    }

    fun hasDictFile(context: Context, schemaId: String): Boolean {
        val dictName = getReferencedDictName(context, schemaId) ?: schemaId
        val f = File(getSharedDir(context), "$dictName.dict.yaml")
        return f.exists()
    }

    fun schemaNeedsDict(context: Context, schemaId: String): Boolean {
        val dictName = getReferencedDictName(context, schemaId) ?: schemaId
        return !File(getSharedDir(context), "$dictName.dict.yaml").exists()
    }

    fun getSchemaIssues(context: Context, schemaId: String): List<String> {
        val issues = mutableListOf<String>()
        val schemaFile = File(getSharedDir(context), "$schemaId.schema.yaml")
        if (!schemaFile.exists()) {
            issues.add("缺少 .schema.yaml 文件")
            return issues
        }
        val dictName = getReferencedDictName(context, schemaId) ?: schemaId
        if (!File(getSharedDir(context), "$dictName.dict.yaml").exists()) {
            issues.add("缺少 $dictName.dict.yaml 词典文件，无法编译")
        }
        return issues
    }

    fun discoverSchemas(context: Context): List<SchemaMeta> {
        val sharedDir = getSharedDir(context)
        if (!sharedDir.exists()) return emptyList()

        val schemas = mutableListOf<SchemaMeta>()
        val schemaFiles = sharedDir.listFiles { f -> f.name.endsWith(".schema.yaml") }
            ?: return emptyList()

        for (file in schemaFiles) {
            val meta = parseSchemaYaml(file)
            if (meta != null) {
                schemas.add(meta)
            }
        }

        schemas.sortBy { it.name }
        return schemas
    }

    private fun parseSchemaYaml(file: File): SchemaMeta? {
        try {
            val lines = file.readLines()
            var schemaId = ""
            var name = ""
            var version = ""
            var author = ""
            var description = ""
            var inSchemaBlock = false
            var inAuthorBlock = false
            var inDescription = false
            val descriptionLines = mutableListOf<String>()

            for (line in lines) {
                val trimmed = line.trimStart()

                if (trimmed == "schema:") {
                    inSchemaBlock = true
                    inAuthorBlock = false
                    inDescription = false
                    continue
                }

                if (!inSchemaBlock && !trimmed.startsWith("schema:")) continue

                if (trimmed.startsWith("schema_id:")) {
                    schemaId = trimmed.removePrefix("schema_id:").trim().removeSurrounding("\"")
                } else if (trimmed.startsWith("name:")) {
                    name = trimmed.removePrefix("name:").trim().removeSurrounding("\"")
                } else if (trimmed.startsWith("version:")) {
                    version = trimmed.removePrefix("version:").trim().removeSurrounding("\"")
                } else if (trimmed.startsWith("author:")) {
                    inAuthorBlock = true
                    inDescription = false
                    val rest = trimmed.removePrefix("author:").trim()
                    if (rest.isNotEmpty()) {
                        author = rest.removeSurrounding("\"").removePrefix("- ").trim()
                    }
                } else if (trimmed.startsWith("description:")) {
                    inAuthorBlock = false
                    inDescription = true
                    val rest = trimmed.removePrefix("description:").trim()
                    if (rest.isNotEmpty() && rest != "|") {
                        description = rest.removeSurrounding("\"")
                    }
                } else if (inAuthorBlock && trimmed.startsWith("- ")) {
                    if (author.isEmpty()) {
                        author = trimmed.removePrefix("- ").trim().removeSurrounding("\"")
                    }
                } else if (inDescription && trimmed.isNotEmpty()) {
                    descriptionLines.add(trimmed)
                } else {
                    if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                        inAuthorBlock = false
                        inDescription = false
                    }
                }
            }

            if (schemaId.isNotEmpty()) {
                if (descriptionLines.isNotEmpty()) {
                    description = descriptionLines.joinToString(" ").trim()
                }
                return SchemaMeta(
                    schemaId = schemaId,
                    name = name.ifEmpty { schemaId },
                    version = version,
                    author = author,
                    description = description
                )
            }
            return null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse schema file: ${file.name}", e)
            return null
        }
    }

    fun getEnabledSchemas(context: Context): List<String> {
        val customFile = getCustomYamlFile(context)
        if (!customFile.exists()) {
            val defaultBuiltIn = listOf("wubi86", "wubi86_pinyin", "pinyin_simp")
            setEnabledSchemas(context, defaultBuiltIn)
            return defaultBuiltIn
        }

        try {
            val content = customFile.readText()
            val schemas = mutableListOf<String>()
            var inSchemaList = false
            for (line in content.lines()) {
                val trimmed = line.trim()
                if (trimmed == "schema_list:") {
                    inSchemaList = true
                    continue
                }
                if (inSchemaList) {
                    if (trimmed.startsWith("- schema:")) {
                        val id = trimmed.removePrefix("- schema:").trim()
                        if (id.isNotEmpty()) schemas.add(id)
                    } else if (!trimmed.startsWith("- ")) {
                        inSchemaList = false
                    }
                }
            }
            if (schemas.isNotEmpty()) return schemas
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read custom.yaml", e)
        }

        return listOf("wubi86", "wubi86_pinyin", "pinyin_simp")
    }

    fun setEnabledSchemas(context: Context, schemaIds: List<String>) {
        val sb = StringBuilder()
        sb.appendLine("patch:")
        sb.appendLine("  schema_list:")
        for (id in schemaIds) {
            sb.appendLine("    - schema: $id")
        }
        try {
            getCustomYamlFile(context).writeText(sb.toString())
            Log.d(TAG, "Updated custom.yaml with schemas: $schemaIds")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write custom.yaml", e)
        }
    }

    fun toggleSchema(context: Context, schemaId: String) {
        val enabled = getEnabledSchemas(context).toMutableList()
        if (schemaId in enabled) {
            enabled.remove(schemaId)
        } else {
            enabled.add(schemaId)
        }
        setEnabledSchemas(context, enabled)
    }

    fun isSchemaEnabled(context: Context, schemaId: String): Boolean {
        return schemaId in getEnabledSchemas(context)
    }

    fun deleteSchemaFiles(context: Context, schemaId: String) {
        val sharedDir = getSharedDir(context)
        for (ext in listOf(".schema.yaml", ".dict.yaml")) {
            val f = File(sharedDir, "$schemaId$ext")
            if (f.exists()) f.delete()
        }
        val enabled = getEnabledSchemas(context).toMutableList()
        enabled.remove(schemaId)
        setEnabledSchemas(context, enabled)
        Log.i(TAG, "Deleted schema files for: $schemaId")
    }

    suspend fun importSchemaFile(context: Context, uri: Uri): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val sharedDir = getSharedDir(context)
                if (!sharedDir.exists()) sharedDir.mkdirs()

                val displayName = getFileName(context, uri) ?: return@withContext false

                if (displayName.endsWith(".zip", ignoreCase = true)) {
                    return@withContext importZip(context, uri, sharedDir)
                }

                val targetFile = File(sharedDir, displayName)
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(targetFile).use { output ->
                        input.copyTo(output)
                    }
                } ?: return@withContext false

                Log.i(TAG, "Imported: $displayName")

                true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to import schema file", e)
                false
            }
        }
    }

    private fun importZip(context: Context, uri: Uri, targetDir: File): Boolean {
        try {
            val importedSchemas = mutableSetOf<String>()

            context.contentResolver.openInputStream(uri)?.use { input ->
                ZipInputStream(input.buffered()).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        val name = entry.name
                        if (!entry.isDirectory && (name.endsWith(".yaml") || name.endsWith(".txt") || name.endsWith(".bin"))) {
                            val file = File(targetDir, name.substringAfterLast('/'))
                            file.parentFile?.mkdirs()
                            FileOutputStream(file).use { output ->
                                zis.copyTo(output)
                            }
                            Log.d(TAG, "Extracted: $name -> ${file.name}")

                            when {
                                name.endsWith(".schema.yaml") ->
                                    importedSchemas.add(name.substringAfterLast('/').removeSuffix(".schema.yaml"))
                                name.endsWith(".dict.yaml") ->
                                    importedSchemas.add(name.substringAfterLast('/').removeSuffix(".dict.yaml"))
                            }
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
            } ?: return false

            if (importedSchemas.isNotEmpty()) {
                Log.i(TAG, "Imported zip with schemas: $importedSchemas")
            }

            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to import zip", e)
            return false
        }
    }

    private fun getFileName(context: Context, uri: Uri): String? {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0) {
                    return it.getString(nameIndex)
                }
            }
        }
        return uri.lastPathSegment
    }
}
