package com.kingzcheung.kime.association

import android.content.Context
import android.util.Log
import java.io.File
import java.io.IOException

object AssociationModelHelper {
    private const val TAG = "AssociationModelHelper"
    private const val MODEL_SUBDIR = "association_model"
    private const val ASSETS_MODEL_DIR = "association_model"
    
    fun copyModelFromAssets(context: Context): Boolean {
        val targetDir = File(context.filesDir, MODEL_SUBDIR)
        
        if (targetDir.exists()) {
            targetDir.deleteRecursively()
            Log.d(TAG, "Removed old model directory")
        }
        
        targetDir.mkdirs()
        
        val filesToCopy = listOf("model.onnx", "vocab.json")
        
        for (fileName in filesToCopy) {
            try {
                val assetPath = "$ASSETS_MODEL_DIR/$fileName"
                context.assets.open(assetPath).use { input ->
                    File(targetDir, fileName).outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                Log.d(TAG, "Copied $fileName from assets (${File(targetDir, fileName).length()} bytes)")
            } catch (e: IOException) {
                Log.e(TAG, "Failed to copy $fileName from assets: ${e.message}")
                return false
            }
        }
        
        Log.i(TAG, "Model files copied successfully to ${targetDir.absolutePath}")
        return true
    }
    
    fun getModelDir(context: Context): String {
        return File(context.filesDir, MODEL_SUBDIR).absolutePath
    }
    
    fun isModelReady(context: Context): Boolean {
        val modelDir = File(context.filesDir, MODEL_SUBDIR)
        if (!modelDir.exists()) return false
        
        val vocabFile = File(modelDir, "vocab.json")
        if (!vocabFile.exists()) return false
        
        val modelFile = File(modelDir, "model.onnx")
        return modelFile.exists()
    }
    
    fun cleanup(context: Context) {
        val modelDir = File(context.filesDir, MODEL_SUBDIR)
        if (modelDir.exists()) {
            modelDir.deleteRecursively()
            Log.d(TAG, "Model directory cleaned up")
        }
    }
}