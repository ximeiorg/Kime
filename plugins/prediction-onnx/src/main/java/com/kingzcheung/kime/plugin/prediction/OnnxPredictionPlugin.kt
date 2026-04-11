package com.kingzcheung.kime.plugin.prediction

import android.content.Context
import android.content.Intent
import android.util.Log
import com.kingzcheung.kime.association.AssociationManager
import com.kingzcheung.kime.plugin.api.PluginType
import com.kingzcheung.kime.plugin.api.PredictionCandidate
import com.kingzcheung.kime.plugin.api.PredictionPlugin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.io.File

class OnnxPredictionPlugin : PredictionPlugin {
    
    override val id = "plugin_prediction_onnx"
    override val name = "ONNX 联想词插件"
    override val description = "基于 ONNX Runtime 的智能联想词预测，支持用户输入学习"
    override val version = "1.0.0"
    override val type = PluginType.PREDICTION
    
    private var isInitialized = false
    
    companion object {
        private const val TAG = "OnnxPredictionPlugin"
    }
    
    override fun initialize(context: Context): Boolean {
        return initialize(context, null)
    }
    
    override fun initialize(context: Context, apkPath: String?): Boolean {
        if (isInitialized) {
            Log.d(TAG, "Already initialized")
            return true
        }
        
        Log.d(TAG, "Initializing ONNX prediction plugin...")
        Log.d(TAG, "Plugin context: ${context.packageName}, APK: $apkPath")
        
        val mainAppFilesDir = File("/data/data/com.kingzcheung.kime/files")
        if (!mainAppFilesDir.exists()) {
            mainAppFilesDir.mkdirs()
        }
        
        return try {
            val success = runBlocking(Dispatchers.IO) {
                AssociationManager.initialize(context, mainAppFilesDir, apkPath)
            }
            
            isInitialized = success
            Log.d(TAG, "ONNX plugin initialized: $success")
            isInitialized
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize", e)
            false
        }
    }
    
    override suspend fun predict(inputText: String, topK: Int): List<PredictionCandidate> {
        if (!isInitialized) {
            Log.e(TAG, "Plugin not initialized")
            return emptyList()
        }
        
        if (inputText.isEmpty()) {
            return emptyList()
        }
        
        return try {
            val candidates = AssociationManager.predict(inputText, topK)
            candidates.map { candidate ->
                PredictionCandidate(candidate.text, candidate.score)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Prediction failed", e)
            emptyList()
        }
    }
    
    override fun learn(text: String) {
        if (isInitialized) {
            AssociationManager.recordInput(text)
        }
    }
    
    override suspend fun saveLearnedData() {
        if (isInitialized) {
            AssociationManager.saveUserData()
        }
    }
    
    override fun release() {
        if (isInitialized) {
            AssociationManager.release()
            isInitialized = false
            Log.d(TAG, "ONNX plugin released")
        }
    }
    
    override fun hasSettings(): Boolean = true
    
    override fun createSettingsIntent(context: Context): Intent {
        val intent = Intent()
        intent.setClassName(
            "com.kingzcheung.kime.plugin.prediction",
            "com.kingzcheung.kime.plugin.prediction.PluginSettingsActivity"
        )
        return intent
    }
}