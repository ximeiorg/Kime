package com.kingzcheung.kime.association

import android.content.Context
import android.util.Log
import com.kingzcheung.kime.settings.SettingsPreferences

class NgramFusionEngine(private val context: Context) {
    companion object {
        private const val TAG = "NgramFusionEngine"
        private const val DEFAULT_LAMBDA = 0.7f
    }
    
    private val userNgramCache = UserNgramCache(context)
    private var lambda = SettingsPreferences.getAssociationLambda(context)
    private var isInitialized = false
    
    suspend fun initialize(): Boolean {
        if (isInitialized) return true
        
        val result = userNgramCache.initialize()
        isInitialized = result
        Log.d(TAG, "Fusion engine initialized: $result")
        return result
    }
    
    fun setLambda(value: Float) {
        lambda = value.coerceIn(0f, 1f)
        SettingsPreferences.setAssociationLambda(context, lambda)
        Log.d(TAG, "Lambda set to $lambda")
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
        
        Log.d(TAG, "Fusing candidates for context: '$context', model candidates: ${modelCandidates.map { it.text }}")
        
        // 先获取用户缓存的候选词
        val userCandidates = userNgramCache.getUserCandidates(context, 10)
        Log.d(TAG, "User candidates: $userCandidates")
        
        // 合并候选词：模型候选词 + 用户候选词
        val allCandidates = mutableMapOf<String, Float>()
        
        // 添加模型候选词
        modelCandidates.forEach { candidate ->
            val modelScore = normalizeModelScore(candidate.score)
            allCandidates[candidate.text] = modelScore
        }
        
        // 添加或更新用户候选词
        userCandidates.forEach { (word, userScore) ->
            val existingScore = allCandidates[word]
            if (existingScore != null) {
                // 如果已存在，融合分数
                val effectiveLambda = 0.3f
                allCandidates[word] = effectiveLambda * existingScore + (1 - effectiveLambda) * userScore
                Log.d(TAG, "Fused '$word': modelScore=$existingScore, userScore=$userScore, final=${allCandidates[word]}")
            } else {
                // 如果不存在，直接使用用户分数（权重更高）
                allCandidates[word] = userScore
                Log.d(TAG, "Added user candidate '$word' with score=$userScore")
            }
        }
        
        // 如果没有用户候选词，使用原始的融合逻辑
        if (userCandidates.isEmpty() && modelCandidates.isNotEmpty()) {
            return modelCandidates.map { candidate ->
                val modelScore = normalizeModelScore(candidate.score)
                val userScore = userNgramCache.getContextualScore(context, candidate.text)
                
                val effectiveLambda = if (userScore > 0) 0.3f else lambda
                val fusedScore = effectiveLambda * modelScore + (1 - effectiveLambda) * userScore
                
                if (userScore > 0) {
                    Log.d(TAG, "Candidate '${candidate.text}': modelScore=$modelScore, userScore=$userScore, fused=$fusedScore")
                }
                
                AssociationCandidate(candidate.text, fusedScore)
            }.sortedByDescending { it.score }
        }
        
        return allCandidates.map { (word, score) ->
            AssociationCandidate(word, score)
        }.sortedByDescending { it.score }
    }
    
    private fun normalizeModelScore(score: Float): Float {
        // logits 可能是很大的正数或负数，归一化到 0-1 范围
        return when {
            score > 0 -> (score / 100f).coerceIn(0f, 1f)
            score < 0 -> (Math.exp((score / 10f).toDouble()).toFloat()).coerceIn(0f, 1f)
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