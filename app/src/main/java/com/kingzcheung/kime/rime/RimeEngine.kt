package com.kingzcheung.kime.rime

import android.util.Log

data class RimeCandidate(
    val text: String,
    val comment: String
)

class RimeEngine {
    
    companion object {
        private const val TAG = "RimeEngine"
        
        init {
            // 加载 native 库
            System.loadLibrary("rime_jni")
            Log.d(TAG, "Native library loaded")
        }
    }
    
    private var isInitialized = false
    
    /**
     * 初始化 Rime 引擎
     * @param userDataDir 用户数据目录（存放用户配置和词库）
     * @param sharedDataDir 共享数据目录（存放系统词库）
     */
    fun initialize(userDataDir: String, sharedDataDir: String) {
        if (!isInitialized) {
            Log.d(TAG, "Initializing Rime: userDataDir=$userDataDir, sharedDataDir=$sharedDataDir")
            
            nativeInitialize(userDataDir, sharedDataDir)
            isInitialized = true
            
            Log.d(TAG, "Rime initialized, checking deployment status...")
            
            val isMaintaining = isMaintaining()
            Log.d(TAG, "Is maintaining: $isMaintaining")
            
            if (isMaintaining) {
                Log.d(TAG, "Waiting for deployment to complete...")
                var waitCount = 0
                while (isMaintaining() && waitCount < 300) {
                    Thread.sleep(100)
                    waitCount++
                    if (waitCount % 10 == 0) {
                        Log.d(TAG, "Still waiting for deployment... (${waitCount / 10} seconds)")
                    }
                }
                if (isMaintaining()) {
                    Log.e(TAG, "Deployment timeout after 30 seconds!")
                } else {
                    Log.d(TAG, "Deployment completed")
                }
            }
            
            val currentSchema = getCurrentSchema()
            Log.d(TAG, "Current schema after init: $currentSchema")
        }
    }
    
    /**
     * 检查是否正在维护（部署）
     */
    fun isMaintaining(): Boolean {
        return nativeIsMaintaining()
    }
    
    /**
     * 获取当前方案
     */
    fun getCurrentSchema(): String {
        return nativeGetCurrentSchema() ?: ""
    }
    
    /**
     * 处理按键输入
     * @param keycode 按键码（ASCII 码）
     * @param mask 修饰键掩码
     * @return 是否成功处理
     */
    fun processKey(keycode: Int, mask: Int): Boolean {
        if (!isInitialized) {
            return false
        }
        if (!nativeIsMaintaining()) {
            return nativeProcessKey(keycode, mask)
        }
        return false
    }
    
    /**
     * 获取候选词列表
     * @return 候选词数组
     */
    fun getCandidates(): Array<String> {
        return nativeGetCandidates() ?: emptyArray()
    }
    
    /**
     * 获取候选词列表（包含编码注释）
     * @return 候选词列表，包含文本和编码注释
     */
    fun getCandidatesWithComments(): Array<RimeCandidate> {
        val rawCandidates = nativeGetCandidatesWithComments() ?: emptyArray()
        return rawCandidates.map { pair ->
            RimeCandidate(
                text = pair.getOrElse(0) { "" },
                comment = pair.getOrElse(1) { "" }
            )
        }.toTypedArray()
    }
    
    fun getInput(): String {
        return nativeGetInput() ?: ""
    }
    
    fun selectCandidate(index: Int): Boolean {
        return nativeSelectCandidate(index)
    }
    
    fun commit(): String {
        return nativeCommit() ?: ""
    }
    
    fun clearComposition() {
        nativeClearComposition()
    }
    
    /**
     * 切换中英文模式（ascii_mode）
     * @return 是否成功切换
     */
    fun toggleAsciiMode(): Boolean {
        if (!isInitialized) {
            return false
        }
        return nativeToggleAsciiMode()
    }
    
    fun isAsciiMode(): Boolean {
        if (!isInitialized) return false
        return nativeIsAsciiMode()
    }
    
    fun switchSchema(schemaId: String): Boolean {
        if (!isInitialized) {
            return false
        }
        return nativeSwitchSchema(schemaId)
    }
    
    fun deploy(): Boolean {
        if (!isInitialized) {
            return false
        }
        return nativeDeploy()
    }

    /**
     * 查询词汇编码
     * @param text 要查询的文本
     * @return 编码（如果是空字符串表示未找到）
     */
    fun lookupText(text: String): String {
        if (!isInitialized || text.isEmpty()) {
            return ""
        }
        return nativeLookupText(text) ?: ""
    }
    
    fun getAvailableSchemas(): Array<String> {
        return nativeGetAvailableSchemas() ?: emptyArray()
    }
    
    /**
     * 销毁引擎
     */
    fun destroy() {
        if (isInitialized) {
            Log.d(TAG, "Destroying Rime engine")
            nativeDestroy()
            isInitialized = false
        }
    }
    
    // Native 方法声明
    private external fun nativeInitialize(userDataDir: String, sharedDataDir: String)
    private external fun nativeIsMaintaining(): Boolean
    private external fun nativeGetCurrentSchema(): String?
    private external fun nativeProcessKey(keycode: Int, mask: Int): Boolean
    private external fun nativeGetCandidates(): Array<String>?
    private external fun nativeGetCandidatesWithComments(): Array<Array<String>>?
    private external fun nativeGetInput(): String?
    private external fun nativeSelectCandidate(index: Int): Boolean
    private external fun nativeCommit(): String?
    private external fun nativeClearComposition()
    private external fun nativeToggleAsciiMode(): Boolean
    private external fun nativeIsAsciiMode(): Boolean
    private external fun nativeSwitchSchema(schemaId: String): Boolean
    private external fun nativeDeploy(): Boolean
    private external fun nativeLookupText(text: String): String
    private external fun nativeGetAvailableSchemas(): Array<String>?
    private external fun nativeDestroy()
}