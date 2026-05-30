package com.kingzcheung.xime.settings

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.URL

object SchemaConfigHelper {
    private const val TAG = "SchemaConfigHelper"
    private const val RIME_ASSETS_DIR = "rime"

    private val schemaDownloadUrls = mapOf(
        "wubi86" to "https://s.ximei.me/rime-wubi",
        "wubi86_pinyin" to "https://s.ximei.me/rime-wubi",
        "pinyin_simp" to "https://s.ximei.me/rime-wubi"
    )

    fun parseSchemaListFromDefault(context: Context): List<String> {
        val schemas = mutableListOf<String>()
        try {
            val inputStream = context.assets.open("$RIME_ASSETS_DIR/default.yaml")
            val content = inputStream.bufferedReader().readText()
            inputStream.close()
            for (line in content.lines()) {
                val trimmed = line.trim()
                if (trimmed.startsWith("- schema:")) {
                    val schemaId = trimmed.removePrefix("- schema:").trim()
                    if (schemaId.isNotEmpty()) schemas.add(schemaId)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse default.yaml", e)
        }
        return schemas
    }

    fun checkSchemaFilesExist(context: Context, schemaId: String): Pair<Boolean, Boolean> {
        val sharedDir = File(context.filesDir, "rime/shared")
        val schemaFile = File(sharedDir, "$schemaId.schema.yaml")
        val dictFile = File(sharedDir, "$schemaId.dict.yaml")
        return Pair(schemaFile.exists(), dictFile.exists())
    }

    fun needsDownload(context: Context, schemaId: String): Boolean {
        val (schemaExists, dictExists) = checkSchemaFilesExist(context, schemaId)
        return !schemaExists
    }

    suspend fun downloadSchema(context: Context, schemaId: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val sharedDir = File(context.filesDir, "rime/shared")
                if (!sharedDir.exists()) sharedDir.mkdirs()

                val baseUrl = schemaDownloadUrls[schemaId]
                if (baseUrl == null) {
                    Log.w(TAG, "Schema $schemaId has no download URL configured")
                    return@withContext false
                }

                Log.i(TAG, "Downloading schema: $schemaId from $baseUrl")

                val schemaUrl = "$baseUrl/$schemaId.schema.yaml"
                downloadFile(schemaUrl, File(sharedDir, "$schemaId.schema.yaml"))

                val dictUrl = "$baseUrl/$schemaId.dict.yaml"
                downloadFile(dictUrl, File(sharedDir, "$schemaId.dict.yaml"))

                Log.i(TAG, "Schema $schemaId downloaded successfully")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to download schema $schemaId", e)
                false
            }
        }
    }

    private fun downloadFile(url: String, targetFile: File) {
        val connection = URL(url).openConnection()
        connection.connectTimeout = 30000
        connection.readTimeout = 60000
        connection.getInputStream().use { input ->
            FileOutputStream(targetFile).use { output ->
                input.copyTo(output)
            }
        }
    }

    suspend fun downloadMissingSchemas(context: Context): List<String> {
        val downloaded = mutableListOf<String>()
        val schemaListIds = parseSchemaListFromDefault(context)
        for (schemaId in schemaListIds) {
            if (needsDownload(context, schemaId)) {
                Log.d(TAG, "Schema $schemaId needs download")
                if (downloadSchema(context, schemaId)) {
                    downloaded.add(schemaId)
                }
            }
        }
        return downloaded
    }
}
