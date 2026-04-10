package com.kingzcheung.kime.plugin.api

import android.content.Context
import android.content.Intent

interface KimeExtension {
    val id: String
    val name: String
    val description: String
        get() = ""
    val type: ExtensionType
    val version: String
    
    fun initialize(context: Context): Boolean
    
    fun initialize(context: Context, apkPath: String?): Boolean {
        return initialize(context)
    }
    
    suspend fun process(input: ExtensionInput): ExtensionResult
    
    fun release()
    
    fun learn(text: String) {}
    
    suspend fun saveLearnedData() {}
    
    fun hasSettings(): Boolean = false
    
    fun createSettingsIntent(context: Context): Intent? = null
}