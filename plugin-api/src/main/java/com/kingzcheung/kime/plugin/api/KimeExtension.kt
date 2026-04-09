package com.kingzcheung.kime.plugin.api

import android.content.Context

interface KimeExtension {
    val id: String
    val name: String
    val type: ExtensionType
    val version: String
    
    fun initialize(context: Context): Boolean
    
    suspend fun process(input: ExtensionInput): ExtensionResult
    
    fun release()
    
    fun learn(text: String) {}
    
    suspend fun saveLearnedData() {}
}