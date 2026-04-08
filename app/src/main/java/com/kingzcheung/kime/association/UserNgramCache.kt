package com.kingzcheung.kime.association

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class UserNgramCache(private val context: Context) {
    companion object {
        private const val TAG = "UserNgramCache"
        private const val CACHE_FILE_NAME = "user_ngram_cache.json"
        private const val MAX_ENTRIES = 1000
        private const val MAX_HISTORY_SIZE = 100
    }
    
    private val bigramTrie = NgramTrie()
    private val trigramTrie = NgramTrie()
    private val recentInputs = mutableListOf<String>()
    
    private val cacheFile: File
        get() = File(context.filesDir, CACHE_FILE_NAME)
    
    suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        try {
            if (cacheFile.exists()) {
                val json = JSONObject(cacheFile.readText())
                loadFromJson(json)
                Log.d(TAG, "Loaded ${bigramTrie.size()} bigrams, ${trigramTrie.size()} trigrams")
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize cache", e)
            false
        }
    }
    
    fun recordInput(text: String) {
        if (text.isBlank()) return
        
        val tokens = tokenize(text)
        if (tokens.isEmpty()) return
        
        Log.d(TAG, "Recording input: '$text' -> tokens: $tokens")
        
        updateRecentInputs(tokens)
        
        tokens.windowed(2).forEach { window ->
            bigramTrie.insert(window)
        }
        
        tokens.windowed(3).forEach { window ->
            trigramTrie.insert(window)
        }
        
        Log.d(TAG, "After recording: ${bigramTrie.size()} bigrams, ${trigramTrie.size()} trigrams")
        
        trimIfNeeded()
    }
    
    private fun tokenize(text: String): List<String> {
        val punctuation = setOf('，', '。', '！', '？', '、', '；', '：', '"', '"', '\'', '\'', ' ', '\n', '\t')
        return text.filter { it.isLetterOrDigit() || it in punctuation }
            .map { it.toString() }
    }
    
    private fun updateRecentInputs(newTokens: List<String>) {
        recentInputs.addAll(newTokens)
        if (recentInputs.size > MAX_HISTORY_SIZE) {
            recentInputs.subList(0, recentInputs.size - MAX_HISTORY_SIZE).clear()
        }
    }
    
    private fun trimIfNeeded() {
        val totalSize = bigramTrie.size() + trigramTrie.size()
        if (totalSize > MAX_ENTRIES) {
            Log.d(TAG, "Cache size $totalSize exceeds limit, keeping recent entries")
        }
    }
    
    fun getBigramScore(word1: String, word2: String): Float {
        return bigramTrie.getFrequency(listOf(word1, word2))
    }
    
    fun getTrigramScore(word1: String, word2: String, word3: String): Float {
        return trigramTrie.getFrequency(listOf(word1, word2, word3))
    }
    
    fun getContextualScore(context: String, candidate: String): Float {
        val tokens = tokenize(context)
        if (tokens.isEmpty()) return 0f
        
        Log.d(TAG, "Getting score for context: '$context' -> tokens: $tokens, candidate: '$candidate'")
        
        // 使用最后一个 token 作为前缀来查询 bigram
        // 例如 context = "比", tokens = ["比"], 查询 ("比", candidate)
        val lastToken = tokens.last()
        val bigramScore = getBigramScore(lastToken, candidate)
        
        // 如果有多个 token，也尝试 trigram
        val trigramScore = if (tokens.size >= 2) {
            getTrigramScore(tokens[tokens.size - 2], lastToken, candidate)
        } else {
            0f
        }
        
        val score = maxOf(bigramScore, trigramScore)
        Log.d(TAG, "Bigram score for ('$lastToken', '$candidate'): $bigramScore, Trigram score: $trigramScore, Final: $score")
        
        return score
    }
    
    fun getUserCandidates(context: String, topK: Int = 5): List<Pair<String, Float>> {
        val tokens = tokenize(context)
        if (tokens.isEmpty()) return emptyList()
        
        val lastToken = tokens.last()
        val candidates = mutableListOf<Pair<String, Float>>()
        
        // 从 bigram 中查找以 lastToken 开头的所有候选词
        bigramTrie.getAllEntries().forEach { (ngram, count) ->
            if (ngram.size == 2 && ngram[0] == lastToken) {
                val score = getBigramScore(ngram[0], ngram[1])
                if (score > 0) {
                    candidates.add(ngram[1] to score)
                }
            }
        }
        
        // 从 trigram 中查找
        if (tokens.size >= 2) {
            trigramTrie.getAllEntries().forEach { (ngram, count) ->
                if (ngram.size == 3 && ngram[0] == tokens[tokens.size - 2] && ngram[1] == lastToken) {
                    val score = getTrigramScore(ngram[0], ngram[1], ngram[2])
                    if (score > 0) {
                        candidates.add(ngram[2] to score)
                    }
                }
            }
        }
        
        return candidates.sortedByDescending { it.second }.take(topK)
    }
    
    suspend fun save() = withContext(Dispatchers.IO) {
        try {
            val json = toJson()
            cacheFile.writeText(json.toString(2))
            Log.d(TAG, "Cache saved: ${bigramTrie.size()} bigrams, ${trigramTrie.size()} trigrams")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save cache", e)
        }
    }
    
    fun clear() {
        bigramTrie.clear()
        trigramTrie.clear()
        recentInputs.clear()
        if (cacheFile.exists()) {
            cacheFile.delete()
        }
        Log.d(TAG, "Cache cleared")
    }
    
    fun getCacheSize(): Int = bigramTrie.size() + trigramTrie.size()
    
    fun debugPrint() {
        Log.d(TAG, "=== Bigram Trie ===")
        bigramTrie.debugPrint()
        Log.d(TAG, "=== Trigram Trie ===")
        trigramTrie.debugPrint()
    }
    
    private fun toJson(): JSONObject {
        val json = JSONObject()
        
        val bigrams = JSONArray()
        bigramTrie.getAllEntries().forEach { (tokens, count) ->
            val entry = JSONObject()
            entry.put("tokens", JSONArray(tokens))
            entry.put("count", count)
            bigrams.put(entry)
        }
        json.put("bigrams", bigrams)
        
        val trigrams = JSONArray()
        trigramTrie.getAllEntries().forEach { (tokens, count) ->
            val entry = JSONObject()
            entry.put("tokens", JSONArray(tokens))
            entry.put("count", count)
            trigrams.put(entry)
        }
        json.put("trigrams", trigrams)
        
        json.put("recentInputs", JSONArray(recentInputs))
        
        return json
    }
    
    private fun loadFromJson(json: JSONObject) {
        bigramTrie.clear()
        trigramTrie.clear()
        recentInputs.clear()
        
        val bigrams = json.optJSONArray("bigrams") ?: JSONArray()
        for (i in 0 until bigrams.length()) {
            val entry = bigrams.getJSONObject(i)
            val tokens = entry.getJSONArray("tokens").let { arr ->
                (0 until arr.length()).map { arr.getString(it) }
            }
            val count = entry.getInt("count")
            repeat(count) { bigramTrie.insert(tokens) }
        }
        
        val trigrams = json.optJSONArray("trigrams") ?: JSONArray()
        for (i in 0 until trigrams.length()) {
            val entry = trigrams.getJSONObject(i)
            val tokens = entry.getJSONArray("tokens").let { arr ->
                (0 until arr.length()).map { arr.getString(it) }
            }
            val count = entry.getInt("count")
            repeat(count) { trigramTrie.insert(tokens) }
        }
        
        val recentArray = json.optJSONArray("recentInputs") ?: JSONArray()
        for (i in 0 until recentArray.length()) {
            recentInputs.add(recentArray.getString(i))
        }
    }
}