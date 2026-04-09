package com.kingzcheung.kime.plugin.api

data class ExtensionInput(
    val text: String? = null,
    val audioData: ByteArray? = null,
    val audioSampleRate: Int = 16000,
    val topK: Int = 5,
    val context: Map<String, Any> = emptyMap()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ExtensionInput) return false
        
        if (text != other.text) return false
        if (audioData != null) {
            if (other.audioData == null) return false
            if (!audioData.contentEquals(other.audioData)) return false
        } else if (other.audioData != null) return false
        if (audioSampleRate != other.audioSampleRate) return false
        if (topK != other.topK) return false
        if (context != other.context) return false
        
        return true
    }
    
    override fun hashCode(): Int {
        var result = text?.hashCode() ?: 0
        result = 31 * result + (audioData?.contentHashCode() ?: 0)
        result = 31 * result + audioSampleRate
        result = 31 * result + topK
        result = 31 * result + context.hashCode()
        return result
    }
}