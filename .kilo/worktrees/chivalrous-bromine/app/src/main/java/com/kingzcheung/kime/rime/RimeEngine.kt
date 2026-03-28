package com.kingzcheung.kime.rime

import android.util.Log

/**
 * Rime 输入引擎封装类
 * 通过 JNI 调用 librime 库
 */
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
            
            // 调用 native 初始化（内部会等待部署完成）
            nativeInitialize(userDataDir, sharedDataDir)
            isInitialized = true
            
            Log.d(TAG, "Rime initialized, checking deployment status...")
            
            // 检查是否正在维护（部署）
            val isMaintaining = isMaintaining()
            Log.d(TAG, "Is maintaining: $isMaintaining")
            
            // 等待部署完成（最多等待 30 秒）
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
            
            // 检查当前方案
            val currentSchema = getCurrentSchema()
            Log.d(TAG, "Current schema: $currentSchema")
            
            // 测试输入
            Log.d(TAG, "Testing key input...")
            val testResult = processKey('a'.code, 0)
            Log.d(TAG, "Test key 'a' result: $testResult, input: '${getInput()}'")
            clearComposition()
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
            Log.w(TAG, "Engine not initialized, cannot process key")
            return false
        }
        val result = nativeProcessKey(keycode, mask)
        Log.d(TAG, "processKey($keycode, $mask) = $result")
        return result
    }
    
    /**
     * 获取候选词列表
     * @return 候选词数组
     */
    fun getCandidates(): Array<String> {
        val candidates = nativeGetCandidates() ?: emptyArray()
        Log.d(TAG, "getCandidates: ${candidates.size} candidates")
        return candidates
    }
    
    /**
     * 获取当前输入的编码
     * @return 输入编码字符串
     */
    fun getInput(): String {
        val input = nativeGetInput() ?: ""
        Log.d(TAG, "getInput: '$input'")
        return input
    }
    
    /**
     * 选择候选词
     * @param index 候选词索引
     * @return 是否成功选择
     */
    fun selectCandidate(index: Int): Boolean {
        val result = nativeSelectCandidate(index)
        Log.d(TAG, "selectCandidate($index) = $result")
        return result
    }
    
    /**
     * 提交当前输入
     * @return 提交的文本
     */
    fun commit(): String {
        val text = nativeCommit() ?: ""
        Log.d(TAG, "commit: '$text'")
        return text
    }
    
    /**
     * 清除当前输入
     */
    fun clearComposition() {
        Log.d(TAG, "clearComposition")
        nativeClearComposition()
    }
    
    /**
     * 切换中英文模式（ascii_mode）
     * @return 是否成功切换
     */
    fun toggleAsciiMode(): Boolean {
        if (!isInitialized) {
            Log.w(TAG, "Engine not initialized, cannot toggle ascii mode")
            return false
        }
        val result = nativeToggleAsciiMode()
        Log.d(TAG, "toggleAsciiMode() = $result")
        return result
    }
    
    /**
     * 获取当前是否为英文模式
     * @return 是否为英文模式
     */
    fun isAsciiMode(): Boolean {
        if (!isInitialized) return false
        return nativeIsAsciiMode()
    }
    
    /**
     * 切换输入方案
     * @param schemaId 方案ID（如 "wubi86", "wubi86_pinyin"）
     * @return 是否成功切换
     */
    fun switchSchema(schemaId: String): Boolean {
        if (!isInitialized) {
            Log.w(TAG, "Engine not initialized, cannot switch schema")
            return false
        }
        val result = nativeSwitchSchema(schemaId)
        Log.d(TAG, "switchSchema($schemaId) = $result")
        return result
    }
    
    /**
     * 获取可用的方案列表
     * @return 方案ID数组
     */
    fun getAvailableSchemas(): Array<String> {
        val schemas = nativeGetAvailableSchemas() ?: emptyArray()
        Log.d(TAG, "getAvailableSchemas: ${schemas.size} schemas")
        return schemas
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
    private external fun nativeGetInput(): String?
    private external fun nativeSelectCandidate(index: Int): Boolean
    private external fun nativeCommit(): String?
    private external fun nativeClearComposition()
    private external fun nativeToggleAsciiMode(): Boolean
    private external fun nativeIsAsciiMode(): Boolean
    private external fun nativeSwitchSchema(schemaId: String): Boolean
    private external fun nativeGetAvailableSchemas(): Array<String>?
    private external fun nativeDestroy()
}