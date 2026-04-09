package com.kingzcheung.kime.plugin

import android.content.Context
import android.util.Log
import com.kingzcheung.kime.plugin.api.ExtensionType
import com.kingzcheung.kime.plugin.api.KimeExtension
import com.kingzcheung.kime.plugin.api.ExtensionInput
import com.kingzcheung.kime.plugin.api.ExtensionResult
import com.kingzcheung.kime.plugin.builtin.BuiltinExtensionFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ExtensionManager {
    private const val TAG = "ExtensionManager"
    
    private val extensions = mutableListOf<KimeExtension>()
    private var isInitialized = false
    
    fun initialize(context: Context): Boolean {
        if (isInitialized) {
            Log.d(TAG, "ExtensionManager already initialized")
            return true
        }
        
        Log.d(TAG, "Initializing ExtensionManager...")
        
        try {
            val builtinExtensions = loadBuiltinExtensions(context)
            Log.d(TAG, "Loaded ${builtinExtensions.size} builtin extensions")
            extensions.addAll(builtinExtensions)
            
            val systemExtensions = ExtensionLoader.loadInstalledExtensions(context)
            Log.d(TAG, "Loaded ${systemExtensions.size} system extensions")
            extensions.addAll(systemExtensions)
            
            val privateExtensions = ExtensionLoader.loadExtensionsFromPrivateDir(context)
            Log.d(TAG, "Loaded ${privateExtensions.size} private extensions")
            extensions.addAll(privateExtensions)
            
            isInitialized = true
            Log.d(TAG, "ExtensionManager initialized with ${extensions.size} extensions")
            
            extensions.forEach { ext ->
                Log.d(TAG, "  - ${ext.id}: ${ext.name} (${ext.type})")
            }
            
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize ExtensionManager", e)
            return false
        }
    }
    
    fun reload(context: Context): Boolean {
        Log.d(TAG, "Reloading extensions...")
        release()
        return initialize(context)
    }
    
    fun getExtensions(): List<KimeExtension> = extensions.toList()
    
    fun getExtensionsByType(type: ExtensionType): List<KimeExtension> {
        return extensions.filter { it.type == type }
    }
    
    fun getExtensionById(id: String): KimeExtension? {
        return extensions.find { it.id == id }
    }
    
    suspend fun process(
        type: ExtensionType,
        input: ExtensionInput
    ): List<ExtensionResult> = withContext(Dispatchers.Default) {
        val targetExtensions = getExtensionsByType(type)
        
        if (targetExtensions.isEmpty()) {
            Log.d(TAG, "No extensions found for type: $type")
            return@withContext emptyList()
        }
        
        targetExtensions.map { extension ->
            try {
                Log.d(TAG, "Processing with extension: ${extension.id}")
                extension.process(input)
            } catch (e: Exception) {
                Log.e(TAG, "Extension ${extension.id} processing failed", e)
                ExtensionResult.Error("Extension ${extension.id} failed: ${e.message}", e)
            }
        }
    }
    
    suspend fun processFirst(
        type: ExtensionType,
        input: ExtensionInput
    ): ExtensionResult? = withContext(Dispatchers.Default) {
        val results = process(type, input)
        results.firstOrNull { it !is ExtensionResult.Error }
    }
    
    fun addExtension(extension: KimeExtension) {
        if (extensions.none { it.id == extension.id }) {
            extensions.add(extension)
            Log.d(TAG, "Added extension: ${extension.id}")
        } else {
            Log.w(TAG, "Extension ${extension.id} already exists")
        }
    }
    
    fun removeExtension(id: String) {
        val extension = extensions.find { it.id == id }
        if (extension != null) {
            extension.release()
            extensions.remove(extension)
            Log.d(TAG, "Removed extension: $id")
        }
    }
    
    fun release() {
        Log.d(TAG, "Releasing all extensions...")
        extensions.forEach { extension ->
            try {
                extension.release()
                Log.d(TAG, "Released extension: ${extension.id}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to release extension ${extension.id}", e)
            }
        }
        extensions.clear()
        isInitialized = false
        Log.d(TAG, "ExtensionManager released")
    }
    
    fun isInitialized(): Boolean = isInitialized
    
    fun hasExtensionsOfType(type: ExtensionType): Boolean {
        return getExtensionsByType(type).isNotEmpty()
    }
    
    private fun loadBuiltinExtensions(context: Context): List<KimeExtension> {
        return try {
            val factory = BuiltinExtensionFactory()
            factory.createExtensions().map { extension ->
                try {
                    val initSuccess = extension.initialize(context)
                    if (initSuccess) {
                        Log.d(TAG, "Builtin extension ${extension.id} initialized")
                        extension
                    } else {
                        Log.w(TAG, "Builtin extension ${extension.id} initialization failed")
                        null
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to initialize builtin extension ${extension.id}", e)
                    null
                }
            }.filterNotNull()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load builtin extensions", e)
            emptyList()
        }
    }
}