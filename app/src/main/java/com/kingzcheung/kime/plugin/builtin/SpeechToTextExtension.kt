package com.kingzcheung.kime.plugin.builtin

import android.content.Context
import android.util.Log
import com.kingzcheung.kime.plugin.api.AudioConfig
import com.kingzcheung.kime.plugin.api.RecognitionState
import com.kingzcheung.kime.plugin.api.SpeechPlugin
import com.kingzcheung.kime.plugin.api.SpeechResult
import com.kingzcheung.kime.plugin.api.PluginType

class SpeechToTextExtension : SpeechPlugin {
    
    override val id = "builtin_speech_to_text"
    override val name = "语音转文字（示例）"
    override val description = "内置语音转文字插件（占位符，未实现）"
    override val version = "1.0.0"
    override val type = PluginType.SPEECH
    override val supportsRealtime = false
    override val requiresNetwork = false
    
    private var isInitialized = false
    private var state = RecognitionState.IDLE
    
    companion object {
        private const val TAG = "SpeechToTextExtension"
    }
    
    override fun initialize(context: Context): Boolean {
        if (isInitialized) {
            return true
        }
        
        Log.d(TAG, "Speech to text extension initialized (placeholder)")
        isInitialized = true
        return true
    }
    
    override fun startRecognition(config: AudioConfig, onResult: (SpeechResult) -> Unit): Boolean {
        Log.d(TAG, "startRecognition called (placeholder)")
        state = RecognitionState.ERROR
        onResult(SpeechResult("语音识别未实现，这是一个占位符插件", isFinal = true))
        return false
    }
    
    override fun sendAudioChunk(data: ByteArray) {
        Log.d(TAG, "sendAudioChunk called: ${data.size} bytes (placeholder)")
    }
    
    override fun stopRecognition() {
        Log.d(TAG, "stopRecognition called (placeholder)")
        state = RecognitionState.IDLE
    }
    
    override fun cancelRecognition() {
        Log.d(TAG, "cancelRecognition called (placeholder)")
        state = RecognitionState.IDLE
    }
    
    override suspend fun recognizeOnce(data: ByteArray, config: AudioConfig): String? {
        Log.d(TAG, "recognizeOnce called: ${data.size} bytes (placeholder)")
        return null
    }
    
    override fun getState(): RecognitionState = state
    
    override fun release() {
        isInitialized = false
        state = RecognitionState.IDLE
        Log.d(TAG, "Speech to text extension released")
    }
}