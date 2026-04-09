package com.kingzcheung.kime.association

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class NgramFusionEngine(private val context: Context) {
    companion object {
        private const val TAG = "NgramFusionEngine"
        private const val DEFAULT_LAMBDA = 0.7f
    }
    
    private val userNgramCache = UserNgramCache(context)
    private var lambda = DEFAULT_LAMBDA
    private var isInitialized = false
    
    suspend fun initialize(): Boolean {
        if (isInitialized) return true
        
        val result = userNgramCache.initialize()
        isInitialized = result
        return result
    }
    
    fun recordUserInput(text: String) {
        userNgramCache.recordInput(text)
    }
    
    fun fuseCandidates(
        modelCandidates: List<AssociationCandidate>,
        context: String
    ): List<AssociationCandidate> {
        if (!isInitialized) {
            return modelCandidates
        }
        
        val userCandidates = userNgramCache.getUserCandidates(context, 10)
        
        val allCandidates = mutableMapOf<String, Float>()
        
        modelCandidates.forEach { candidate ->
            val modelScore = normalizeModelScore(candidate.score)
            allCandidates[candidate.text] = modelScore
        }
        
        userCandidates.forEach { (word, userScore) ->
            val existingScore = allCandidates[word]
            if (existingScore != null) {
                val effectiveLambda = 0.3f
                allCandidates[word] = effectiveLambda * existingScore + (1 - effectiveLambda) * userScore
            } else {
                allCandidates[word] = userScore
            }
        }
        
        if (userCandidates.isEmpty() && modelCandidates.isNotEmpty()) {
            return modelCandidates.map { candidate ->
                val modelScore = normalizeModelScore(candidate.score)
                val userScore = userNgramCache.getContextualScore(context, candidate.text)
                
                val effectiveLambda = if (userScore > 0) 0.3f else lambda
                val fusedScore = effectiveLambda * modelScore + (1 - effectiveLambda) * userScore
                
                AssociationCandidate(candidate.text, fusedScore)
            }.sortedByDescending { it.score }
        }
        
        return allCandidates.map { (word, score) ->
            AssociationCandidate(word, score)
        }.sortedByDescending { it.score }
    }
    
    private fun normalizeModelScore(score: Float): Float {
        return when {
            score > 0 -> (score / 100f).coerceIn(0f, 1f)
            score < 0 -> (kotlin.math.exp((score / 10f).toDouble()).toFloat()).coerceIn(0f, 1f)
            else -> 0.5f
        }
    }
    
    suspend fun saveCache() {
        userNgramCache.save()
    }
    
    fun clearCache() {
        userNgramCache.clear()
    }
    
    fun getCacheSize(): Int = userNgramCache.getCacheSize()
    
    fun isInitialized(): Boolean = isInitialized
}