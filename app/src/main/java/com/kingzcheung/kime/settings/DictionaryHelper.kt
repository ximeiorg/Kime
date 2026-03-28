package com.kingzcheung.kime.settings

import android.content.Context
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader

data class DictEntry(
    val word: String,
    val code: String
)

object DictionaryHelper {
    private const val TAG = "DictionaryHelper"
    
    private val schemaToDictMap = mapOf(
        "wubi86" to "wubi86.dict.yaml",
        "wubi86_pinyin" to "wubi86.dict.yaml",
        "pinyin_simp" to "pinyin_simp.dict.yaml"
    )
    
    fun getDictFileForSchema(schemaId: String): String? {
        return schemaToDictMap[schemaId]
    }
    
    fun loadDictionary(context: Context, schemaId: String): List<DictEntry> {
        val dictFile = getDictFileForSchema(schemaId) ?: return emptyList()
        val entries = mutableListOf<DictEntry>()
        
        try {
            val inputStream = context.assets.open("rime/$dictFile")
            val reader = BufferedReader(InputStreamReader(inputStream, "UTF-8"))
            
            var inDataSection = false
            var line: String? = reader.readLine()
            
            while (line != null) {
                val trimmed = line.trim()
                
                if (trimmed == "...") {
                    inDataSection = true
                    line = reader.readLine()
                    continue
                }
                
                if (inDataSection && trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                    val parts = trimmed.split("\t")
                    if (parts.size >= 2) {
                        entries.add(DictEntry(parts[0], parts[1]))
                    }
                }
                
                line = reader.readLine()
            }
            
            reader.close()
            inputStream.close()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load dictionary: $dictFile", e)
        }
        
        return entries
    }
    
    fun searchDictionary(entries: List<DictEntry>, query: String): List<DictEntry> {
        if (query.isEmpty()) return entries.take(100)
        return entries.filter { 
            it.word.contains(query) || it.code.contains(query) 
        }.take(100)
    }
}