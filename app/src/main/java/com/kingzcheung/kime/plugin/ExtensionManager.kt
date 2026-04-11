package com.kingzcheung.kime.plugin

import android.content.Context
import android.util.Log
import com.kingzcheung.kime.plugin.api.ExtensionType
import com.kingzcheung.kime.plugin.api.KimeExtension
import com.kingzcheung.kime.plugin.api.ExtensionInput
import com.kingzcheung.kime.plugin.api.ExtensionResult
import com.kingzcheung.kime.plugin.builtin.BuiltinExtensionFactory
import com.kingzcheung.kime.settings.SettingsPreferences
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
            
            android.util.Log.e(TAG, "About to load installed extensions...")
            val systemExtensions = ExtensionLoader.loadInstalledExtensions(context)
            Log.d(TAG, "Loaded ${systemExtensions.size} system extensions")
            android.util.Log.e(TAG, "Loaded ${systemExtensions.size} system extensions")
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
            android.util.Log.e(TAG, "Failed to initialize ExtensionManager: ${e.message}")
            return false
        }
    }
    
    fun reload(context: Context): Boolean {
        if (isInitialized) {
            Log.d(TAG, "ExtensionManager already initialized, scanning for new plugins...")
            return scanNewPlugins(context)
        }
        return initialize(context)
    }
    
    fun scanNewPlugins(context: Context): Boolean {
        val systemExtensions = ExtensionLoader.loadInstalledExtensions(context)
        val privateExtensions = ExtensionLoader.loadExtensionsFromPrivateDir(context)
        
        val currentPluginIds = (systemExtensions + privateExtensions).map { it.id }.toSet()
        val builtinIds = extensions.filter { it.id.startsWith("builtin_") }.map { it.id }.toSet()
        
        val removedExtensions = extensions.filter { 
            it.id !in currentPluginIds && it.id !in builtinIds 
        }
        
        if (removedExtensions.isNotEmpty()) {
            removedExtensions.forEach { ext ->
                try {
                    ext.release()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to release removed extension ${ext.id}", e)
                }
                extensions.remove(ext)
                Log.d(TAG, "Removed uninstalled plugin: ${ext.id}")
            }
        }
        
        val existingIds = extensions.map { it.id }.toSet()
        val newExtensions = (systemExtensions + privateExtensions).filter { it.id !in existingIds }
        
        if (newExtensions.isNotEmpty()) {
            extensions.addAll(newExtensions)
            Log.d(TAG, "Found ${newExtensions.size} new plugins")
            newExtensions.forEach { ext ->
                Log.d(TAG, "  - New: ${ext.id}: ${ext.name}")
            }
        }
        
        if (removedExtensions.isEmpty() && newExtensions.isEmpty()) {
            Log.d(TAG, "No plugin changes detected")
        }
        
        return true
    }
    
    fun forceReload(context: Context): Boolean {
        Log.d(TAG, "Force reloading extensions...")
        ExtensionLoader.clearAllCachedExtensions()
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
        input: ExtensionInput,
        context: Context? = null
    ): List<ExtensionResult> = withContext(Dispatchers.Default) {
        val targetExtensions = getExtensionsByType(type)
        
        if (targetExtensions.isEmpty()) {
            Log.d(TAG, "No extensions found for type: $type")
            return@withContext emptyList()
        }
        
        // 过滤出已启用的插件
        val enabledExtensions = if (context != null) {
            targetExtensions.filter { extension ->
                val enabled = SettingsPreferences.isPluginEnabled(context, extension.id)
                Log.d(TAG, "Extension ${extension.id} enabled: $enabled")
                enabled
            }
        } else {
            Log.w(TAG, "No context provided, skipping enabled check")
            targetExtensions
        }
        
        if (enabledExtensions.isEmpty()) {
            Log.d(TAG, "No enabled extensions for type: $type")
            return@withContext emptyList()
        }
        
        enabledExtensions.map { extension ->
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
        input: ExtensionInput,
        context: Context? = null
    ): ExtensionResult? = withContext(Dispatchers.Default) {
        val results = process(type, input, context)
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
                    val initSuccess = extension.initialize(context, null)
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