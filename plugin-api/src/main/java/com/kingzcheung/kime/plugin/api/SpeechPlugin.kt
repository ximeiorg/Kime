package com.kingzcheung.kime.plugin.api

import android.content.Context

data class AudioConfig(
    val sampleRate: Int = 16000,
    val encoding: AudioEncoding = AudioEncoding.PCM16,
    val channels: Int = 1
)

enum class AudioEncoding {
    PCM16,
    OPUS,
    SPEEX
}

data class SpeechResult(
    val text: String,
    val isFinal: Boolean,
    val confidence: Float = 1.0f,
    val durationMs: Long = 0
)

enum class RecognitionState {
    IDLE,
    LISTENING,
    PROCESSING,
    ERROR
}

interface SpeechPlugin : PluginMetadata {
    val supportsRealtime: Boolean
    val requiresNetwork: Boolean
    
    fun startRecognition(
        config: AudioConfig,
        onResult: (SpeechResult) -> Unit
    ): Boolean
    
    fun sendAudioChunk(data: ByteArray)
    
    fun stopRecognition()
    
    fun cancelRecognition()
    
    suspend fun recognizeOnce(data: ByteArray, config: AudioConfig): String?
    
    fun getState(): RecognitionState
}