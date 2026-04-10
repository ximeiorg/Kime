package com.kingzcheung.kime.plugin.prediction

import android.content.Context
import android.content.Intent
import android.util.Log
import com.kingzcheung.kime.association.AssociationManager
import com.kingzcheung.kime.plugin.api.ExtensionInput
import com.kingzcheung.kime.plugin.api.ExtensionResult
import com.kingzcheung.kime.plugin.api.ExtensionType
import com.kingzcheung.kime.plugin.api.KimeExtension
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.io.File

class OnnxPredictionPlugin : KimeExtension {
    
    override val id = "plugin_prediction_onnx"
    override val name = "ONNX 联想词插件"
    override val description = "基于 ONNX Runtime 的智能联想词预测，支持用户输入学习"
    override val type = ExtensionType.PREDICTION
    override val version = "1.0.0"
    
    private var isInitialized = false
    
    companion object {
        private const val TAG = "OnnxPredictionPlugin"
    }
    
    override fun initialize(context: Context): Boolean {
        if (isInitialized) {
            Log.d(TAG, "Already initialized")
            return true
        }
        
        Log.d(TAG, "Initializing ONNX prediction plugin...")
        Log.e(TAG, "Plugin context package: ${context.packageName}")
        
        // 关键：使用主应用的filesDir来存储模型（通过路径硬编码）
        val mainAppFilesDir = File("/data/data/com.kingzcheung.kime/files")
        if (!mainAppFilesDir.exists()) {
            mainAppFilesDir.mkdirs()
        }
        
        return try {
            val success = runBlocking(Dispatchers.IO) {
                AssociationManager.initialize(context, mainAppFilesDir)
            }
            
            isInitialized = success
            Log.d(TAG, "ONNX plugin initialized: $success")
            isInitialized
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize", e)
            false
        }
    }
    
    override suspend fun process(input: ExtensionInput): ExtensionResult {
        if (!isInitialized) {
            return ExtensionResult.Error("Plugin not initialized")
        }
        
        val text = input.text
        if (text.isNullOrEmpty()) {
            return ExtensionResult.Text(emptyList())
        }
        
        return try {
            val candidates = AssociationManager.predict(text, input.topK)
            
            if (candidates.isEmpty()) {
                return ExtensionResult.Text(emptyList())
            }
            
            val texts = candidates.map { it.text }
            Log.d(TAG, "Predicted ${texts.size} candidates: $texts")
            ExtensionResult.Text(texts)
        } catch (e: Exception) {
            Log.e(TAG, "Prediction failed", e)
            ExtensionResult.Error("Prediction failed: ${e.message}", e)
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
        // 关键：使用插件的包名创建 Intent
        val intent = Intent()
        intent.setClassName(
            "com.kingzcheung.kime.plugin.prediction",
            "com.kingzcheung.kime.plugin.prediction.PluginSettingsActivity"
        )
        return intent
    }
}