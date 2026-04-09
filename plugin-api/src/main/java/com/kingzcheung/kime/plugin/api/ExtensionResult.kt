package com.kingzcheung.kime.plugin.api

sealed class ExtensionResult {
    data class Text(val candidates: List<String>) : ExtensionResult()
    
    data class Emojis(val items: List<EmojiItem>) : ExtensionResult()
    
    data class Error(val message: String, val exception: Exception? = null) : ExtensionResult()
}

data class EmojiItem(
    val id: String,
    val displayText: String,
    val insertText: String,
    val imageUrl: String? = null,
    val category: String? = null
)