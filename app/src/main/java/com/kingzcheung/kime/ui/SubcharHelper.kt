package com.kingzcheung.kime.ui

import android.content.Context
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader

object SubcharHelper {
    private const val TAG = "SubcharHelper"
    private const val SUBCHAR_DIR = "subchar"
    
    private var availableSvgs: Set<String> = emptySet()
    
    fun init(context: Context) {
        try {
            val files = context.assets.list(SUBCHAR_DIR) ?: emptyArray()
            availableSvgs = files
                .filter { it.endsWith(".svg") }
                .map { it.removeSuffix(".svg") }
                .toSet()
            Log.d(TAG, "Available SVGs: $availableSvgs")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to list subchar directory: ${e.message}")
            availableSvgs = emptySet()
        }
    }
    
    fun hasSvg(char: String): Boolean {
        return availableSvgs.contains(char)
    }
    
    fun getSvgPath(char: String): String? {
        return if (hasSvg(char)) "$SUBCHAR_DIR/$char.svg" else null
    }
    
    fun parseSwipeDownText(text: String): List<CharInfo> {
        return text.map { char ->
            CharInfo(
                char = char.toString(),
                hasSvg = hasSvg(char.toString())
            )
        }
    }
    
    fun getCharsWithSvg(text: String): List<String> {
        return text.filter { hasSvg(it.toString()) }.map { it.toString() }
    }
    
    fun getCharsWithoutSvg(text: String): String {
        return text.filter { !hasSvg(it.toString()) }
    }
}

data class CharInfo(
    val char: String,
    val hasSvg: Boolean
)