package com.kingzcheung.kime.plugin.api

import android.content.Context

data class EmojiItem(
    val id: String,
    val displayText: String,
    val insertText: String,
    val imageUrl: String? = null,
    val category: String? = null
)

interface EmojiPlugin : PluginMetadata {
    suspend fun getEmojis(category: String?, searchText: String?, topK: Int = 100): List<EmojiItem>
    
    suspend fun getCategories(): List<String>
}