package com.kingzcheung.kime.clipboard

import android.content.ClipData
import android.content.ClipboardManager as AndroidClipboardManager
import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ClipboardItem(
    val id: Long = System.currentTimeMillis(),
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isPinned: Boolean = false
)

class ClipboardManager private constructor(private val context: Context) {
    
    companion object {
        private const val TAG = "ClipboardManager"
        private const val MAX_ITEMS = 50
        private const val PREFS_NAME = "clipboard_prefs"
        private const val KEY_CLIPBOARD_ITEMS = "clipboard_items"
        
        @Volatile
        private var instance: ClipboardManager? = null
        
        fun getInstance(context: Context): ClipboardManager {
            return instance ?: synchronized(this) {
                instance ?: ClipboardManager(context.applicationContext).also { instance = it }
            }
        }
    }
    
    private val androidClipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as AndroidClipboardManager
    
    private val _clipboardItems = MutableStateFlow<List<ClipboardItem>>(emptyList())
    val clipboardItems: StateFlow<List<ClipboardItem>> = _clipboardItems.asStateFlow()
    
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    init {
        loadItems()
        startListening()
    }
    
    private fun loadItems() {
        val itemsJson = prefs.getString(KEY_CLIPBOARD_ITEMS, null)
        if (itemsJson != null) {
            try {
                val items = deserializeItems(itemsJson)
                _clipboardItems.value = items
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load clipboard items", e)
            }
        }
    }
    
    private fun saveItems() {
        val itemsJson = serializeItems(_clipboardItems.value)
        prefs.edit().putString(KEY_CLIPBOARD_ITEMS, itemsJson).apply()
    }
    
    private fun serializeItems(items: List<ClipboardItem>): String {
        return items.joinToString(separator = "|||") { item ->
            "${item.id}:::${item.text.escape()}:::${item.timestamp}:::${item.isPinned}"
        }
    }
    
    private fun deserializeItems(json: String): List<ClipboardItem> {
        if (json.isEmpty()) return emptyList()
        return json.split("|||").mapNotNull { itemStr ->
            val parts = itemStr.split(":::")
            if (parts.size == 4) {
                try {
                    ClipboardItem(
                        id = parts[0].toLong(),
                        text = parts[1].unescape(),
                        timestamp = parts[2].toLong(),
                        isPinned = parts[3].toBoolean()
                    )
                } catch (e: Exception) {
                    null
                }
            } else null
        }
    }
    
    private fun String.escape(): String {
        return this.replace("|||", "〈PIPE〉").replace(":::", "〈COLON〉")
    }
    
    private fun String.unescape(): String {
        return this.replace("〈PIPE〉", "|||").replace("〈COLON〉", ":::")
    }
    
    private fun startListening() {
        androidClipboardManager.addPrimaryClipChangedListener {
            val clipData = androidClipboardManager.primaryClip
            if (clipData != null && clipData.itemCount > 0) {
                val text = clipData.getItemAt(0).text?.toString()
                if (!text.isNullOrEmpty()) {
                    addItem(text)
                }
            }
        }
    }
    
    fun addItem(text: String) {
        if (text.isBlank()) return
        
        val currentItems = _clipboardItems.value.toMutableList()
        
        val existingIndex = currentItems.indexOfFirst { it.text == text }
        if (existingIndex >= 0) {
            val existing = currentItems[existingIndex]
            currentItems[existingIndex] = existing.copy(timestamp = System.currentTimeMillis())
            currentItems.moveToTop(existingIndex)
        } else {
            val newItem = ClipboardItem(text = text)
            currentItems.add(0, newItem)
            
            val unpinnedCount = currentItems.count { !it.isPinned }
            if (unpinnedCount > MAX_ITEMS) {
                val toRemove = currentItems
                    .filter { !it.isPinned }
                    .sortedBy { it.timestamp }
                    .take(unpinnedCount - MAX_ITEMS)
                currentItems.removeAll(toRemove.toSet())
            }
        }
        
        _clipboardItems.value = currentItems
        saveItems()
    }
    
    private fun <T> MutableList<T>.moveToTop(index: Int) {
        if (index > 0) {
            val item = removeAt(index)
            add(0, item)
        }
    }
    
    fun removeItem(id: Long) {
        val currentItems = _clipboardItems.value.toMutableList()
        currentItems.removeAll { it.id == id }
        _clipboardItems.value = currentItems
        saveItems()
    }
    
    fun togglePin(id: Long) {
        val currentItems = _clipboardItems.value.toMutableList()
        val index = currentItems.indexOfFirst { it.id == id }
        if (index >= 0) {
            val item = currentItems[index]
            currentItems[index] = item.copy(isPinned = !item.isPinned)
            _clipboardItems.value = currentItems.sortedWith(
                compareByDescending<ClipboardItem> { it.isPinned }
                    .thenByDescending { it.timestamp }
            )
            saveItems()
        }
    }
    
    fun clearAll() {
        val pinnedItems = _clipboardItems.value.filter { it.isPinned }
        _clipboardItems.value = pinnedItems
        saveItems()
    }
    
    fun copyToSystemClipboard(text: String) {
        val clip = ClipData.newPlainText("kime_clipboard", text)
        androidClipboardManager.setPrimaryClip(clip)
    }
    
    fun getCurrentClipboardText(): String? {
        val clipData = androidClipboardManager.primaryClip
        return if (clipData != null && clipData.itemCount > 0) {
            clipData.getItemAt(0).text?.toString()
        } else null
    }
}