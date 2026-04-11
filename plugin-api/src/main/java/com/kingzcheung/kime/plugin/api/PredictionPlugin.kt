package com.kingzcheung.kime.plugin.api

import android.content.Context

data class PredictionCandidate(
    val text: String,
    val score: Float = 1.0f
)

interface PredictionPlugin : PluginMetadata {
    suspend fun predict(inputText: String, topK: Int = 5): List<PredictionCandidate>
    
    fun learn(text: String) {}
    
    suspend fun saveLearnedData() {}
}