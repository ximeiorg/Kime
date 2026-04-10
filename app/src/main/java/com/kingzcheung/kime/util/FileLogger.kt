package com.kingzcheung.kime.util

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

object FileLogger {
    private var logFile: File? = null
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
    
    fun init(context: Context) {
        if (logFile == null) {
            logFile = File(context.filesDir, "plugin_debug.log")
            logFile?.delete() // 清除旧日志
        }
    }
    
    fun isInitialized(): Boolean = logFile != null
    
    fun e(tag: String, message: String) {
        val file = logFile ?: return
        val timestamp = dateFormat.format(Date())
        val logLine = "$timestamp E/$tag: $message\n"
        file.appendText(logLine)
    }
    
    fun d(tag: String, message: String) {
        val file = logFile ?: return
        val timestamp = dateFormat.format(Date())
        val logLine = "$timestamp D/$tag: $message\n"
        file.appendText(logLine)
    }
    
    fun getLogs(): String {
        return if (logFile?.exists() == true) {
            logFile!!.readText()
        } else {
            "No logs found"
        }
    }
    
    fun clear() {
        logFile?.delete()
    }
}