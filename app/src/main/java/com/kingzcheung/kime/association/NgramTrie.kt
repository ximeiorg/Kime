package com.kingzcheung.kime.association

import android.util.Log

data class TrieNode(
    val children: MutableMap<String, TrieNode> = mutableMapOf(),
    var count: Int = 0,
    var frequency: Float = 0f,
    var prefixCount: Int = 0
)

class NgramTrie {
    companion object {
        private const val TAG = "NgramTrie"
    }
    
    private val root = TrieNode()
    
    fun insert(ngram: List<String>) {
        if (ngram.isEmpty()) return
        
        var node = root
        // 更新根节点的 prefixCount
        node.prefixCount++
        
        // 遍历每个 token，更新路径上所有节点的 prefixCount
        for (token in ngram) {
            val nextNode = node.children.getOrPut(token) { TrieNode() }
            nextNode.prefixCount++
            node = nextNode
        }
        
        // 最后一个节点是实际的 ngram，增加 count
        node.count++
        updateFrequency(node)
        
        Log.d(TAG, "Inserted $ngram, final node prefixCount=${node.prefixCount}, count=${node.count}, frequency=${node.frequency}")
    }
    
    private fun updateFrequency(node: TrieNode) {
        if (node.prefixCount > 0) {
            node.frequency = node.count.toFloat() / node.prefixCount
        }
    }
    
    fun getFrequency(ngram: List<String>): Float {
        if (ngram.isEmpty()) return 0f
        
        // 对于 bigram ["比", "较"]，需要找到 "比" 节点，获取其子节点 "较"
        // 频率 = count("比", "较") / prefixCount("比")
        
        var node = root
        
        // 找到前缀节点
        for (token in ngram.dropLast(1)) {
            node = node.children[token] ?: return 0f
        }
        
        // prefixCount 是前缀节点的 prefixCount
        val prefixCount = node.prefixCount
        if (prefixCount == 0) return 0f
        
        // 获取最后一个 token 的节点
        val lastToken = ngram.last()
        val lastNode = node.children[lastToken] ?: return 0f
        
        // 频率 = 该 ngram 的 count / 前缀节点的 prefixCount
        val frequency = lastNode.count.toFloat() / prefixCount
        
        Log.d(TAG, "GetFrequency $ngram: prefixCount=$prefixCount, count=${lastNode.count}, frequency=$frequency")
        
        return frequency
    }
    
    fun getAllEntries(): List<Pair<List<String>, Int>> {
        val entries = mutableListOf<Pair<List<String>, Int>>()
        collectEntries(root, emptyList(), entries)
        return entries
    }
    
    private fun collectEntries(
        node: TrieNode,
        path: List<String>,
        entries: MutableList<Pair<List<String>, Int>>
    ) {
        if (node.count > 0) {
            entries.add(path to node.count)
        }
        node.children.forEach { (token, childNode) ->
            collectEntries(childNode, path + token, entries)
        }
    }
    
    fun clear() {
        root.children.clear()
        root.count = 0
        root.frequency = 0f
        root.prefixCount = 0
    }
    
    fun size(): Int {
        var count = 0
        fun countNodes(node: TrieNode) {
            count += node.count
            node.children.values.forEach { countNodes(it) }
        }
        countNodes(root)
        return count
    }
    
    fun debugPrint() {
        fun printNode(node: TrieNode, path: List<String>, depth: Int) {
            if (node.count > 0 || node.prefixCount > 0) {
                Log.d(TAG, "  ".repeat(depth) + "path=$path, prefixCount=${node.prefixCount}, count=${node.count}, freq=${node.frequency}")
            }
            node.children.forEach { (token, childNode) ->
                printNode(childNode, path + token, depth + 1)
            }
        }
        printNode(root, emptyList(), 0)
    }
}