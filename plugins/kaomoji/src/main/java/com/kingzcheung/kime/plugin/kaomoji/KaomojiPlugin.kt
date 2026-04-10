package com.kingzcheung.kime.plugin.kaomoji

import android.content.Context
import android.content.Intent
import android.util.Log
import com.kingzcheung.kime.plugin.api.*

class KaomojiPlugin : KimeExtension {
    
    override val id: String = "kaomoji_plugin"
    override val name: String = "颜文字表情包"
    override val description: String = "提供390+个日式颜文字表情"
    override val type: ExtensionType = ExtensionType.EMOJI
    override val version: String = "1.0.0"
    
    private var kaomojiList: List<EmojiItem> = emptyList()
    
    companion object {
        private const val TAG = "KaomojiPlugin"
    }
    
    override fun initialize(context: Context): Boolean {
        try {
            Log.d(TAG, "Initializing Kaomoji plugin")
            
            kaomojiList = KaomojiData.kaomojis.mapIndexed { index, kaomoji ->
                EmojiItem(
                    id = "kaomoji_$index",
                    displayText = kaomoji,
                    insertText = kaomoji,
                    imageUrl = null,
                    category = "颜文字"
                )
            }
            
            Log.d(TAG, "Loaded ${kaomojiList.size} kaomojis")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Initialization failed", e)
            return false
        }
    }
    
    override suspend fun process(input: ExtensionInput): ExtensionResult {
        val searchText = input.text ?: ""
        
        val filtered = if (searchText.isEmpty()) {
            kaomojiList
        } else {
            kaomojiList.filter { kaomoji ->
                kaomoji.displayText.contains(searchText)
            }
        }
        
        val result = filtered.take(input.topK)
        
        return ExtensionResult.Emojis(result)
    }
    
    override fun release() {
        kaomojiList = emptyList()
    }
    
    override fun hasSettings(): Boolean = true
    
    override fun createSettingsIntent(context: Context): Intent {
        val intent = Intent()
        intent.setClassName(
            "com.kingzcheung.kime.plugin.kaomoji",
            "com.kingzcheung.kime.plugin.kaomoji.PluginSettingsActivity"
        )
        return intent
    }
}