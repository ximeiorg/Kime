package com.kingzcheung.kime.association

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object AssociationManager {
    private const val TAG = "AssociationManager"
    
    @Volatile
    private var isInitialized = false
    private val mutex = Mutex()
    
    private lateinit var fusionEngine: NgramFusionEngine
    
    suspend fun initialize(context: Context): Boolean = withContext(Dispatchers.IO) {
        if (isInitialized) {
            Log.d(TAG, "AssociationManager already initialized")
            return@withContext true
        }
        
        mutex.withLock {
            if (isInitialized) {
                return@withContext true
            }
            
            try {
                fusionEngine = NgramFusionEngine(context)
                
                val modelInit = OnnxAssociationEngine.initialize(context)
                val cacheInit = fusionEngine.initialize()
                
                isInitialized = modelInit
                
                Log.d(TAG, "AssociationManager initialized: model=$modelInit, cache=$cacheInit")
                isInitialized
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize AssociationManager", e)
                false
            }
        }
    }
    
    suspend fun predict(context: String, topK: Int = 5): List<AssociationCandidate> = withContext(Dispatchers.Default) {
        if (!isInitialized) {
            Log.w(TAG, "AssociationManager not initialized")
            return@withContext emptyList()
        }
        
        try {
            val modelCandidates = OnnxAssociationEngine.predict(context, topK * 2)
            
            if (modelCandidates.isEmpty()) {
                return@withContext emptyList()
            }
            
            val fusedCandidates = fusionEngine.fuseCandidates(modelCandidates, context)
            
            fusedCandidates.take(topK)
            
        } catch (e: Exception) {
            Log.e(TAG, "Prediction failed", e)
            emptyList()
        }
    }
    
    fun recordInput(text: String) {
        if (!isInitialized) return
        fusionEngine.recordUserInput(text)
    }
    
    suspend fun saveUserData() {
        if (!isInitialized) return
        fusionEngine.saveCache()
    }
    
    fun setFusionLambda(lambda: Float) {
        if (!isInitialized) return
        fusionEngine.setLambda(lambda)
    }
    
    fun clearUserCache() {
        if (!isInitialized) return
        fusionEngine.clearCache()
    }
    
    fun getCacheSize(): Int {
        return if (isInitialized) fusionEngine.getCacheSize() else 0
    }
    
    fun isInitialized(): Boolean = isInitialized
    
    fun release() {
        if (isInitialized) {
            OnnxAssociationEngine.release()
            isInitialized = false
            Log.d(TAG, "AssociationManager released")
        }
    }
}