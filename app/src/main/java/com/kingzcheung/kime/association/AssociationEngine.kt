package com.kingzcheung.kime.association

import android.content.Context
import android.util.Log
import java.io.File

object AssociationEngine {
    
    private const val TAG = "AssociationEngine"
    
    init {
        try {
            System.loadLibrary("association_jni")
            Log.d(TAG, "Association JNI library loaded")
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to load association_jni library: ${e.message}")
        }
    }
    
    @Volatile
    private var isInitialized = false
    private val lock = Any()
    
    fun initialize(context: Context): Boolean {
        if (isInitialized) {
            Log.d(TAG, "Already initialized")
            return true
        }
        
        synchronized(lock) {
            if (isInitialized) {
                Log.d(TAG, "Already initialized (double-check)")
                return true
            }
            
            val modelDir = getModelDir(context)
            if (!File(modelDir).exists()) {
                Log.e(TAG, "Model directory not found: $modelDir")
                return false
            }
            
            Log.d(TAG, "Initializing with model dir: $modelDir")
            val result = nativeInitialize(modelDir)
            isInitialized = result
            Log.d(TAG, "Initialization result: $result")
            return result
        }
    }
    
    fun predict(inputText: String, topK: Int = 5): Array<AssociationCandidate> {
        if (!isInitialized) {
            Log.w(TAG, "Engine not initialized, returning empty candidates")
            return emptyArray()
        }
        
        if (inputText.isEmpty()) {
            return emptyArray()
        }
        
        return nativePredict(inputText, topK) ?: emptyArray()
    }
    
    fun isInitialized(): Boolean = isInitialized && nativeIsInitialized()
    
    fun release() {
        if (isInitialized) {
            nativeRelease()
            isInitialized = false
            Log.d(TAG, "Engine released")
        }
    }
    
    private fun getModelDir(context: Context): String {
        return File(context.filesDir, "association_model").absolutePath
    }
    
    private external fun nativeInitialize(modelDir: String): Boolean
    private external fun nativePredict(inputText: String, topK: Int): Array<AssociationCandidate>?
    private external fun nativeIsInitialized(): Boolean
    private external fun nativeRelease()
}