package com.kingzcheung.xime.rime

import android.util.Log

data class RimeCandidate(
    val text: String,
    val comment: String
)

class RimeEngine {

    companion object {
        private const val TAG = "RimeEngine"
        private var instance: RimeEngine? = null
        private var deploymentCallback: ((Boolean, String) -> Unit)? = null

        init {
            System.loadLibrary("rime_jni")
            Log.d(TAG, "Native library loaded")
        }

        fun getInstance(): RimeEngine {
            return instance ?: synchronized(this) {
                instance ?: RimeEngine().also { instance = it }
            }
        }

        fun isInitialized(): Boolean = instance?.isInitialized ?: false

        fun setDeploymentCallback(callback: (isDeploying: Boolean, message: String) -> Unit) {
            deploymentCallback = callback
        }
    }

    private var isInitialized = false
    private val initLock = Any()

    private fun notifyDeploymentStatus(isDeploying: Boolean, message: String) {
        deploymentCallback?.invoke(isDeploying, message)
    }

    fun initialize(userDataDir: String, sharedDataDir: String) {
        if (!isInitialized) {
            synchronized(initLock) {
                if (!isInitialized) {
                    try {
                        Log.d(TAG, "Initializing Rime: userDataDir=$userDataDir, sharedDataDir=$sharedDataDir")

                        notifyDeploymentStatus(true, "正在加载输入法引擎...")
                        nativeInitialize(userDataDir, sharedDataDir)
                        isInitialized = true

                        // 参考 trime: startup 只初始化引擎，不创建 session
                        // session 在第一次使用时延迟创建（ensureSession）
                        // 部署在后台异步运行，不阻塞

                        notifyDeploymentStatus(false, "")
                        Log.d(TAG, "Rime engine initialized (session deferred)")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error during Rime initialization", e)
                        notifyDeploymentStatus(false, "初始化失败")
                    }
                }
            }
        }
    }

    fun ensureSession(timeoutMs: Long = 60000L): Boolean {
        if (!isInitialized) return false
        if (nativeHasSession() && getAvailableSchemas().isNotEmpty()) return true

        var waited = 0L
        while (waited < timeoutMs) {
            if (!nativeHasSession()) {
                nativeCreateSession()
            }
            if (getAvailableSchemas().isNotEmpty()) {
                if (waited > 100) {
                    Log.d(TAG, "ensureSession: schemas ready after ${waited}ms")
                }
                return true
            }
            try {
                Thread.sleep(100)
            } catch (_: InterruptedException) {
                return false
            }
            waited += 100
            if (waited % 5000 == 0L) {
                Log.d(TAG, "ensureSession: waiting for schemas... (${waited/1000}s)")
            }
        }
        Log.w(TAG, "ensureSession: schemas not available after ${timeoutMs}ms, deployment may still be running")
        return false
    }

    fun isMaintaining(): Boolean {
        return nativeIsMaintaining()
    }

    fun getCurrentSchema(): String {
        if (!nativeHasSession()) return ""
        return nativeGetCurrentSchema() ?: ""
    }

    fun processKey(keycode: Int, mask: Int): Boolean {
        if (!isInitialized) return false
        if (!nativeHasSession() && !nativeCreateSession()) return false
        return nativeProcessKey(keycode, mask)
    }

    fun getCandidates(): Array<String> {
        if (!nativeHasSession()) return emptyArray()
        return nativeGetCandidates() ?: emptyArray()
    }

    fun getCandidatesWithComments(): Array<RimeCandidate> {
        if (!nativeHasSession()) return emptyArray()
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
        if (!nativeHasSession()) return false
        return nativeSelectCandidate(index)
    }

    fun pageDown(): Boolean {
        if (!nativeHasSession()) return false
        return nativePageDown()
    }

    fun pageUp(): Boolean {
        if (!nativeHasSession()) return false
        return nativePageUp()
    }

    fun hasNextPage(): Boolean {
        if (!nativeHasSession()) return false
        return nativeHasNextPage()
    }

    fun hasPrevPage(): Boolean {
        if (!nativeHasSession()) return false
        return nativeHasPrevPage()
    }

    fun commit(): String {
        return nativeCommit() ?: ""
    }

    fun clearComposition() {
        nativeClearComposition()
    }

    fun toggleAsciiMode(): Boolean {
        if (!nativeHasSession()) return false
        return nativeToggleAsciiMode()
    }

    fun isAsciiMode(): Boolean {
        if (!nativeHasSession()) return false
        return nativeIsAsciiMode()
    }

    fun switchSchema(schemaId: String): Boolean {
        if (!nativeHasSession()) return false
        return nativeSwitchSchema(schemaId)
    }

    fun startMaintenance(full: Boolean): Boolean {
        if (!isInitialized) return false
        return nativeStartMaintenance(full)
    }

    fun deploy(): Boolean {
        if (!isInitialized) return false
        return nativeDeploy()
    }

    fun lookupText(text: String): String {
        if (!isInitialized || text.isEmpty() || !nativeHasSession()) return ""
        return nativeLookupText(text) ?: ""
    }

    fun getAvailableSchemas(): Array<String> {
        if (!nativeHasSession()) return emptyArray()
        return nativeGetAvailableSchemas() ?: emptyArray()
    }

    fun destroy() {
        if (isInitialized) {
            Log.d(TAG, "Destroying Rime engine")
            nativeDestroy()
            isInitialized = false
        }
    }

    // Native 方法声明
    private external fun nativeInitialize(userDataDir: String, sharedDataDir: String)
    private external fun nativeCreateSession(): Boolean
    private external fun nativeHasSession(): Boolean
    private external fun nativeIsMaintaining(): Boolean
    private external fun nativeGetCurrentSchema(): String?
    private external fun nativeProcessKey(keycode: Int, mask: Int): Boolean
    private external fun nativeGetCandidates(): Array<String>?
    private external fun nativeGetCandidatesWithComments(): Array<Array<String>>?
    private external fun nativeGetInput(): String?
    private external fun nativeSelectCandidate(index: Int): Boolean
    private external fun nativePageDown(): Boolean
    private external fun nativePageUp(): Boolean
    private external fun nativeHasNextPage(): Boolean
    private external fun nativeHasPrevPage(): Boolean
    private external fun nativeCommit(): String?
    private external fun nativeClearComposition()
    private external fun nativeToggleAsciiMode(): Boolean
    private external fun nativeIsAsciiMode(): Boolean
    private external fun nativeSwitchSchema(schemaId: String): Boolean
    private external fun nativeStartMaintenance(full: Boolean): Boolean
    private external fun nativeDeploy(): Boolean
    private external fun nativeLookupText(text: String): String
    private external fun nativeGetAvailableSchemas(): Array<String>?
    private external fun nativeDestroy()
}
