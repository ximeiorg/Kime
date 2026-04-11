package com.kingzcheung.kime.plugin.kaomoji

import android.content.Context
import android.content.Intent
import android.util.Log
import com.kingzcheung.kime.plugin.api.EmojiItem
import com.kingzcheung.kime.plugin.api.EmojiPlugin
import com.kingzcheung.kime.plugin.api.PluginType

class KaomojiPlugin : EmojiPlugin {
    
    override val id = "kaomoji_plugin"
    override val name = "颜文字表情包"
    override val description = "提供精选日式颜文字表情"
    override val version = "1.0.0"
    override val type = PluginType.EMOJI
    
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
    
    override suspend fun getEmojis(category: String?, searchText: String?, topK: Int): List<EmojiItem> {
        val filtered = if (searchText.isNullOrEmpty()) {
            kaomojiList
        } else {
            kaomojiList.filter { it.displayText.contains(searchText) }
        }
        
        return filtered.take(topK)
    }
    
    override suspend fun getCategories(): List<String> {
        return listOf("颜文字")
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