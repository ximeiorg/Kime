package com.kingzcheung.kime.plugin

import android.content.Context
import android.util.Log
import com.kingzcheung.kime.plugin.api.EmojiPlugin
import com.kingzcheung.kime.plugin.api.PluginMetadata
import com.kingzcheung.kime.plugin.api.PluginType
import com.kingzcheung.kime.plugin.api.PredictionPlugin
import com.kingzcheung.kime.plugin.api.SpeechPlugin
import com.kingzcheung.kime.settings.SettingsPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ExtensionManager {
    private const val TAG = "ExtensionManager"
    
    private val predictionPlugins = mutableListOf<PredictionPlugin>()
    private val emojiPlugins = mutableListOf<EmojiPlugin>()
    private val speechPlugins = mutableListOf<SpeechPlugin>()
    private var isInitialized = false
    
    fun initialize(context: Context): Boolean {
        if (isInitialized) {
            Log.d(TAG, "ExtensionManager already initialized")
            return true
        }
        
        Log.d(TAG, "Initializing ExtensionManager...")
        
        try {
            val plugins = ExtensionLoader.loadPlugins(context)
            
            plugins.forEach { plugin ->
                when (plugin) {
                    is PredictionPlugin -> {
                        predictionPlugins.add(plugin)
                        Log.d(TAG, "Loaded prediction plugin: ${plugin.name}")
                    }
                    is EmojiPlugin -> {
                        emojiPlugins.add(plugin)
                        Log.d(TAG, "Loaded emoji plugin: ${plugin.name}")
                    }
                    is SpeechPlugin -> {
                        speechPlugins.add(plugin)
                        Log.d(TAG, "Loaded speech plugin: ${plugin.name}")
                    }
                    else -> {
                        Log.w(TAG, "Unknown plugin type: ${plugin.name}")
                    }
                }
            }
            
            isInitialized = true
            Log.d(TAG, "ExtensionManager initialized: ${predictionPlugins.size} prediction, ${emojiPlugins.size} emoji, ${speechPlugins.size} speech")
            
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize ExtensionManager", e)
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
        val plugins = ExtensionLoader.loadPlugins(context)
        
        val currentPredictionIds = plugins.filterIsInstance<PredictionPlugin>().map { it.id }.toSet()
        val currentEmojiIds = plugins.filterIsInstance<EmojiPlugin>().map { it.id }.toSet()
        val currentSpeechIds = plugins.filterIsInstance<SpeechPlugin>().map { it.id }.toSet()
        
        predictionPlugins.filter { it.id !in currentPredictionIds }.forEach { plugin ->
            plugin.release()
            predictionPlugins.remove(plugin)
            Log.d(TAG, "Removed prediction plugin: ${plugin.id}")
        }
        
        emojiPlugins.filter { it.id !in currentEmojiIds }.forEach { plugin ->
            plugin.release()
            emojiPlugins.remove(plugin)
            Log.d(TAG, "Removed emoji plugin: ${plugin.id}")
        }
        
        speechPlugins.filter { it.id !in currentSpeechIds }.forEach { plugin ->
            plugin.release()
            speechPlugins.remove(plugin)
            Log.d(TAG, "Removed speech plugin: ${plugin.id}")
        }
        
        plugins.filterIsInstance<PredictionPlugin>().filter { it.id !in predictionPlugins.map { p -> p.id } }.forEach {
            predictionPlugins.add(it)
            Log.d(TAG, "Added new prediction plugin: ${it.id}")
        }
        
        plugins.filterIsInstance<EmojiPlugin>().filter { it.id !in emojiPlugins.map { p -> p.id } }.forEach {
            emojiPlugins.add(it)
            Log.d(TAG, "Added new emoji plugin: ${it.id}")
        }
        
        plugins.filterIsInstance<SpeechPlugin>().filter { it.id !in speechPlugins.map { p -> p.id } }.forEach {
            speechPlugins.add(it)
            Log.d(TAG, "Added new speech plugin: ${it.id}")
        }
        
        return true
    }
    
    fun forceReload(context: Context): Boolean {
        Log.d(TAG, "Force reloading plugins...")
        ExtensionLoader.clearAllCachedPlugins()
        release()
        return initialize(context)
    }
    
    fun getPredictionPlugins(): List<PredictionPlugin> = predictionPlugins.toList()
    
    fun getEmojiPlugins(): List<EmojiPlugin> = emojiPlugins.toList()
    
    fun getSpeechPlugins(): List<SpeechPlugin> = speechPlugins.toList()
    
    fun getEnabledPredictionPlugins(context: Context): List<PredictionPlugin> {
        return predictionPlugins.filter { SettingsPreferences.isPluginEnabled(context, it.id) }
    }
    
    fun getEnabledEmojiPlugins(context: Context): List<EmojiPlugin> {
        return emojiPlugins.filter { SettingsPreferences.isPluginEnabled(context, it.id) }
    }
    
    fun getEnabledSpeechPlugins(context: Context): List<SpeechPlugin> {
        return speechPlugins.filter { SettingsPreferences.isPluginEnabled(context, it.id) }
    }
    
    fun getPluginById(id: String): PluginMetadata? {
        return predictionPlugins.find { it.id == id }
            ?: emojiPlugins.find { it.id == id }
            ?: speechPlugins.find { it.id == id }
    }
    
    suspend fun predict(
        context: Context,
        inputText: String,
        topK: Int = 5
    ): List<String> = withContext(Dispatchers.Default) {
        val enabledPlugins = getEnabledPredictionPlugins(context)
        
        if (enabledPlugins.isEmpty()) {
            Log.d(TAG, "No enabled prediction plugins")
            return@withContext emptyList()
        }
        
        val results = mutableListOf<String>()
        
        enabledPlugins.forEach { plugin ->
            try {
                val candidates = plugin.predict(inputText, topK)
                results.addAll(candidates.map { it.text })
            } catch (e: Exception) {
                Log.e(TAG, "Prediction failed for ${plugin.id}", e)
            }
        }
        
        results.distinct().take(topK)
    }
    
    suspend fun getEmojis(
        context: Context,
        category: String? = null,
        searchText: String? = null,
        topK: Int = 100
    ): List<com.kingzcheung.kime.plugin.api.EmojiItem> = withContext(Dispatchers.Default) {
        val enabledPlugins = getEnabledEmojiPlugins(context)
        
        if (enabledPlugins.isEmpty()) {
            Log.d(TAG, "No enabled emoji plugins")
            return@withContext emptyList()
        }
        
        val results = mutableListOf<com.kingzcheung.kime.plugin.api.EmojiItem>()
        
        enabledPlugins.forEach { plugin ->
            try {
                val emojis = plugin.getEmojis(category, searchText, topK)
                results.addAll(emojis)
            } catch (e: Exception) {
                Log.e(TAG, "Get emojis failed for ${plugin.id}", e)
            }
        }
        
        results.take(topK)
    }
    
    fun release() {
        Log.d(TAG, "Releasing all plugins...")
        
        predictionPlugins.forEach { plugin ->
            try {
                plugin.release()
                Log.d(TAG, "Released prediction plugin: ${plugin.id}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to release ${plugin.id}", e)
            }
        }
        predictionPlugins.clear()
        
        emojiPlugins.forEach { plugin ->
            try {
                plugin.release()
                Log.d(TAG, "Released emoji plugin: ${plugin.id}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to release ${plugin.id}", e)
            }
        }
        emojiPlugins.clear()
        
        speechPlugins.forEach { plugin ->
            try {
                plugin.release()
                Log.d(TAG, "Released speech plugin: ${plugin.id}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to release ${plugin.id}", e)
            }
        }
        speechPlugins.clear()
        
        isInitialized = false
        Log.d(TAG, "ExtensionManager released")
    }
    
    fun isInitialized(): Boolean = isInitialized
    
    fun hasPredictionPlugins(context: Context): Boolean {
        return getEnabledPredictionPlugins(context).isNotEmpty()
    }
    
    fun hasEmojiPlugins(context: Context): Boolean {
        return getEnabledEmojiPlugins(context).isNotEmpty()
    }
    
    fun hasSpeechPlugins(context: Context): Boolean {
        return getEnabledSpeechPlugins(context).isNotEmpty()
    }
    
    fun getAllPlugins(): List<PluginMetadata> {
        return predictionPlugins + emojiPlugins + speechPlugins
    }
}