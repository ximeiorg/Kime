package com.kingzcheung.xime.rime

import android.content.Context
import android.util.Log
import com.kingzcheung.xime.settings.SchemaConfigHelper
import com.kingzcheung.xime.settings.SchemaManager
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

object RimeConfigHelper {
    private const val TAG = "RimeConfigHelper"
    private const val ASSETS_RIME_DIR = "rime"
    
    suspend fun initializeRimeDataAsync(context: Context): Pair<String, String> {
        val sharedDataDir = File(context.filesDir, "rime/shared")
        val userDataDir = File(context.filesDir, "rime/user")
        
        Log.d(TAG, "initializeRimeData: sharedDataDir=${sharedDataDir.absolutePath}")
        Log.d(TAG, "initializeRimeData: userDataDir=${userDataDir.absolutePath}")
        
        if (!sharedDataDir.exists()) {
            sharedDataDir.mkdirs()
        }
        if (!userDataDir.exists()) {
            userDataDir.mkdirs()
        }
        
        copyAssetsToRimeDir(context, sharedDataDir)
        stripLuaTranslatorFromSchemas(sharedDataDir)
        
        Log.d(TAG, "Checking for missing schema files...")
        try {
            withTimeout(60_000L) {
                val downloaded = SchemaConfigHelper.downloadMissingSchemas(context)
                if (downloaded.isNotEmpty()) {
                    Log.i(TAG, "Downloaded schemas: $downloaded")
                }
            }
        } catch (e: TimeoutCancellationException) {
            Log.w(TAG, "Schema download timed out, continuing with existing files")
        }
        
        checkAndCleanBuildDir(sharedDataDir, userDataDir)
        listFilesRecursively(sharedDataDir, TAG)
        
        return Pair(userDataDir.absolutePath, sharedDataDir.absolutePath)
    }
    
    fun initializeRimeData(context: Context): Pair<String, String> {
        val sharedDataDir = File(context.filesDir, "rime/shared")
        val userDataDir = File(context.filesDir, "rime/user")
        
        Log.d(TAG, "initializeRimeData: sharedDataDir=${sharedDataDir.absolutePath}")
        Log.d(TAG, "initializeRimeData: userDataDir=${userDataDir.absolutePath}")
        
        if (!sharedDataDir.exists()) {
            sharedDataDir.mkdirs()
        }
        if (!userDataDir.exists()) {
            userDataDir.mkdirs()
        }
        
        copyAssetsToRimeDir(context, sharedDataDir)
        checkAndCleanBuildDir(sharedDataDir, userDataDir)
        listFilesRecursively(sharedDataDir, TAG)
        
        return Pair(userDataDir.absolutePath, sharedDataDir.absolutePath)
    }
    
    fun isDeploymentComplete(context: Context): Boolean {
        val buildDir = File(File(context.filesDir, "rime/user"), "build")
        if (!buildDir.exists()) return false

        val enabledSchemas = SchemaManager.getEnabledSchemas(context)
        if (enabledSchemas.isEmpty()) return false

        for (schemaId in enabledSchemas) {
            if (!File(buildDir, "$schemaId.prism.bin").exists()) return false
        }
        return true
    }

    private fun checkAndCleanBuildDir(sharedDataDir: File, userDataDir: File) {
        val buildDir = File(userDataDir, "build")
        val defaultYaml = File(sharedDataDir, "default.yaml")
        
        if (!defaultYaml.exists() || !buildDir.exists()) {
            Log.d(TAG, "default.yaml or build directory not found, skipping check")
            return
        }
        
        try {
            val content = defaultYaml.readText()
            val schemaListRegex = Regex("""schema:\s*(\S+)""")
            val schemas = schemaListRegex.findAll(content).map { it.groupValues[1] }.toList()
            Log.d(TAG, "Schemas in default.yaml: $schemas")
            
            for (schema in schemas) {
                val schemaFile = File(sharedDataDir, "$schema.schema.yaml")
                val prismFile = File(buildDir, "$schema.prism.bin")
                
                if (schemaFile.exists()) {
                    if (prismFile.exists()) {
                        Log.d(TAG, "Schema $schema already deployed")
                    } else {
                        Log.d(TAG, "Schema $schema needs deployment (missing prism.bin)")
                    }
                } else {
                    Log.d(TAG, "Schema $schema schema file not found, skipping")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse default.yaml", e)
        }
    }
    
    private fun copyAssetsToRimeDir(context: Context, targetDir: File): Boolean {
        try {
            return copyAssetsRecursively(context, ASSETS_RIME_DIR, targetDir)
        } catch (e: IOException) {
            Log.e(TAG, "Failed to copy assets", e)
            return false
        }
    }
    
    private fun copyAssetsRecursively(context: Context, assetPath: String, targetDir: File): Boolean {
        val files = context.assets.list(assetPath)
        
        if (files.isNullOrEmpty()) {
            Log.d(TAG, "No files found in assets/$assetPath")
            return false
        }
        
        var copiedAny = false
        
        for (fileName in files) {
            val fullAssetPath = "$assetPath/$fileName"
            val targetFile = File(targetDir, fileName)
            
            try {
                val subFiles = context.assets.list(fullAssetPath)
                if (!subFiles.isNullOrEmpty()) {
                    if (!targetFile.exists()) {
                        targetFile.mkdirs()
                    }
                    Log.d(TAG, "Processing subdirectory: $fullAssetPath")
                    if (copyAssetsRecursively(context, fullAssetPath, targetFile)) {
                        copiedAny = true
                    }
                } else if (fileName.endsWith(".yaml")) {
                    copyAssetFile(context, fullAssetPath, targetFile)
                    copiedAny = true
                }
            } catch (e: IOException) {
                Log.e(TAG, "Failed to process: $fullAssetPath", e)
            }
        }
        
        return copiedAny
    }
    
    private fun copyAssetFile(context: Context, assetPath: String, targetFile: File) {
        try {
            if (targetFile.exists() && targetFile.name != "default.yaml") {
                return
            }

            targetFile.parentFile?.mkdirs()
            context.assets.open(assetPath).use { input ->
                val text = input.bufferedReader().readText()
                val cleaned = if (targetFile.name.endsWith(".schema.yaml")) {
                    text.lines().filter { !it.trimStart().startsWith("- lua_translator") }.joinToString("\n")
                } else text
                FileOutputStream(targetFile).use { output ->
                    output.write(cleaned.toByteArray())
                }
            }
            Log.d(TAG, "Copied: $assetPath -> ${targetFile.absolutePath}")
        } catch (e: IOException) {
            Log.e(TAG, "Failed to copy: $assetPath", e)
        }
    }

    private fun stripLuaTranslatorFromSchemas(sharedDataDir: File) {
        val schemaFiles = sharedDataDir.listFiles { f -> f.name.endsWith(".schema.yaml") } ?: return
        for (file in schemaFiles) {
            try {
                val lines = file.readLines()
                val filtered = lines.filter { !it.trimStart().startsWith("- lua_translator") }
                if (filtered.size != lines.size) {
                    file.writeText(filtered.joinToString("\n"))
                    Log.d(TAG, "Stripped lua_translator from ${file.name}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to process ${file.name}", e)
            }
        }
    }

    private fun listFilesRecursively(dir: File, tag: String, prefix: String = "") {
        val files = dir.listFiles()
        if (files == null) {
            Log.e(tag, "$prefix${dir.name} is empty or not a directory!")
            return
        }
        Log.d(tag, "$prefix${dir.name}/ (${files.size} items)")
        for (file in files) {
            if (file.isDirectory) {
                listFilesRecursively(file, tag, "$prefix  ")
            } else {
                Log.d(tag, "$prefix  ${file.name} (${file.length()} bytes)")
            }
        }
    }
}