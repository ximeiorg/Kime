package com.kingzcheung.kime.association

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.util.zip.ZipInputStream

object ModelDownloadManager {
    private const val TAG = "ModelDownloadManager"
    private const val MODEL_SUBDIR = "association_model"
    
    private const val DEFAULT_MODEL_URL = "https://modelscope.cn/models/bikeand/predictive-text-small/resolve/master"
    
    fun getModelFiles(baseUrl: String): List<Pair<String, String>> {
        return listOf(
            "model.onnx" to "$baseUrl/model_int8_dynamic.onnx",
            "vocab.json" to "$baseUrl/vocab.json"
        )
    }
    
    fun getDefaultModelFiles(): List<Pair<String, String>> = getModelFiles(DEFAULT_MODEL_URL)
    
    data class DownloadProgress(
        val fileIndex: Int,
        val totalFiles: Int,
        val fileName: String,
        val progress: Int,
        val downloadedBytes: Long,
        val totalBytes: Long
    )
    
    suspend fun downloadModel(
        context: Context,
        baseUrl: String,
        onProgress: (DownloadProgress) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val targetDir = File(context.filesDir, MODEL_SUBDIR)
            if (!targetDir.exists()) {
                targetDir.mkdirs()
            }
            
            val modelFiles = getModelFiles(baseUrl)
            modelFiles.forEachIndexed { index, (fileName, url) ->
                Log.i(TAG, "Downloading $fileName from $url")
                onProgress(DownloadProgress(
                    fileIndex = index,
                    totalFiles = modelFiles.size,
                    fileName = fileName,
                    progress = 0,
                    downloadedBytes = 0,
                    totalBytes = 0
                ))
                
                val targetFile = File(targetDir, fileName)
                val success = downloadFile(url, targetFile) { downloaded, total ->
                    val progress = if (total > 0) ((downloaded * 100) / total).toInt() else 0
                    onProgress(DownloadProgress(
                        fileIndex = index,
                        totalFiles = modelFiles.size,
                        fileName = fileName,
                        progress = progress,
                        downloadedBytes = downloaded,
                        totalBytes = total
                    ))
                }
                
                if (!success) {
                    Log.e(TAG, "Failed to download $fileName")
                    return@withContext false
                }
                
                Log.i(TAG, "Downloaded $fileName successfully")
            }
            
            Log.i(TAG, "All model files downloaded successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download model", e)
            false
        }
    }
    
    private fun downloadFile(
        url: String,
        targetFile: File,
        onProgress: (Long, Long) -> Unit
    ): Boolean {
        return try {
            val connection = URL(url).openConnection()
            connection.connectTimeout = 30000
            connection.readTimeout = 60000
            connection.setRequestProperty("User-Agent", "Kime-IME/1.0")
            connection.connect()
            
            val totalBytes = connection.contentLengthLong
            var downloadedBytes = 0L
            
            connection.getInputStream().use { input ->
                FileOutputStream(targetFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead
                        onProgress(downloadedBytes, totalBytes)
                    }
                }
            }
            
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download file: $url", e)
            if (targetFile.exists()) {
                targetFile.delete()
            }
            false
        }
    }
    
    fun isModelDownloaded(context: Context): Boolean {
        val modelDir = File(context.filesDir, MODEL_SUBDIR)
        if (!modelDir.exists()) return false
        
        val vocabFile = File(modelDir, "vocab.json")
        if (!vocabFile.exists()) return false
        
        val modelFile = File(modelDir, "model.onnx")
        return modelFile.exists()
    }
    
    fun getModelSize(context: Context): Long {
        val modelDir = File(context.filesDir, MODEL_SUBDIR)
        if (!modelDir.exists()) return 0
        
        return modelDir.walkTopDown()
            .filter { it.isFile }
            .map { it.length() }
            .sum()
    }
    
    fun deleteModel(context: Context): Boolean {
        val modelDir = File(context.filesDir, MODEL_SUBDIR)
        if (!modelDir.exists()) return true
        
        return try {
            modelDir.deleteRecursively()
            Log.i(TAG, "Model deleted successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete model", e)
            false
        }
    }
    
    fun formatSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            bytes < 1024 * 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
            else -> String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0))
        }
    }
}