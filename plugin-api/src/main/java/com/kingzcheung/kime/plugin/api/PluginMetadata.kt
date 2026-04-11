package com.kingzcheung.kime.plugin.api

import android.content.Context
import android.content.Intent

interface PluginMetadata {
    val id: String
    val name: String
    val description: String
    val version: String
    val type: PluginType
    
    fun initialize(context: Context): Boolean
    
    fun initialize(context: Context, apkPath: String?): Boolean {
        return initialize(context)
    }
    
    fun release()
    
    fun hasSettings(): Boolean = false
    
    fun createSettingsIntent(context: Context): Intent? = null
}