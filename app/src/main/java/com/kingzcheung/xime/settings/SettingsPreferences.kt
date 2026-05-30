package com.kingzcheung.xime.settings

import android.content.Context
import android.content.SharedPreferences
import com.kingzcheung.xime.plugin.core.runtime.PluginManager

object SettingsPreferences {
    private const val PREFS_NAME = "kime_settings"
    private const val KEY_CURRENT_SCHEMA = "current_schema"
    private const val KEY_DEPLOYMENT_DONE = "deployment_done"
    private const val KEY_DARK_MODE = "dark_mode"
    
    private const val KEY_SOUND_ENABLED = "sound_enabled"
    private const val KEY_SOUND_VOLUME = "sound_volume"
    private const val KEY_VIBRATION_ENABLED = "vibration_enabled"
    private const val KEY_VIBRATION_INTENSITY = "vibration_intensity"
    private const val KEY_KEYBOARD_THEME = "keyboard_theme"
    private const val KEY_SHOW_BOTTOM_BUTTONS = "show_bottom_buttons"
    
    private const val KEY_SMART_PREDICTION_ENABLED = "smart_prediction_enabled"
    private const val KEY_PREDICTION_MODEL_REPO = "prediction_model_repo"
    
    private const val KEY_STT_ENABLED = "stt_enabled"
    private const val KEY_STT_PROVIDER = "stt_provider"
    private const val KEY_FUNASR_API_KEY = "funasr_api_key"
    private const val KEY_STT_USE_LOCAL = "stt_use_local"
    private const val KEY_STT_KEEP_MODEL_IN_RAM = "stt_keep_model_in_ram"
    
    private const val KEY_PUNCTUATION_MODEL_ENABLED = "punctuation_model_enabled"
    
    private const val KEY_SWIPE_DOWN_SHOW_ROOTS = "swipe_down_show_roots"
    
    private const val KEY_KEYBOARD_HEIGHT_DP = "keyboard_height_dp"
    private const val KEY_KEYBOARD_HEIGHT_DP_LANDSCAPE = "keyboard_height_dp_landscape"
    private const val DEFAULT_KEYBOARD_HEIGHT_DP = 290
    
    private const val KEY_KEYBOARD_BOTTOM_PADDING_DP = "keyboard_bottom_padding_dp"
    private const val DEFAULT_KEYBOARD_BOTTOM_PADDING_DP = 0

    private const val KEY_WEBDAV_URL = "webdav_url"
    private const val KEY_WEBDAV_USERNAME = "webdav_username"
    private const val KEY_WEBDAV_PASSWORD = "webdav_password"
    private const val KEY_WEBDAV_PATH = "webdav_path"
    
    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    fun getPrefsPublic(context: Context): SharedPreferences {
        return getPrefs(context)
    }
    
    fun getCurrentSchema(context: Context): String {
        return getPrefs(context).getString(KEY_CURRENT_SCHEMA, "wubi86") ?: "wubi86"
    }
    
    fun setCurrentSchema(context: Context, schemaId: String) {
        getPrefs(context).edit().putString(KEY_CURRENT_SCHEMA, schemaId).apply()
    }
    
    fun isDeploymentDone(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_DEPLOYMENT_DONE, false)
    }
    
    fun setDeploymentDone(context: Context, done: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_DEPLOYMENT_DONE, done).apply()
    }
    
    fun getDarkMode(context: Context): Int {
        return getPrefs(context).getInt(KEY_DARK_MODE, 0)
    }
    
    fun setDarkMode(context: Context, mode: Int) {
        getPrefs(context).edit().putInt(KEY_DARK_MODE, mode).apply()
    }
    
    fun isSoundEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_SOUND_ENABLED, true)
    }
    
    fun setSoundEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_SOUND_ENABLED, enabled).apply()
    }
    
    fun getSoundVolume(context: Context): Int {
        return getPrefs(context).getInt(KEY_SOUND_VOLUME, 50)
    }
    
    fun setSoundVolume(context: Context, volume: Int) {
        getPrefs(context).edit().putInt(KEY_SOUND_VOLUME, volume).apply()
    }
    
    fun isVibrationEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_VIBRATION_ENABLED, true)
    }
    
    fun setVibrationEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_VIBRATION_ENABLED, enabled).apply()
    }
    
    fun getVibrationIntensity(context: Context): Int {
        return getPrefs(context).getInt(KEY_VIBRATION_INTENSITY, 50)
    }
    
    fun setVibrationIntensity(context: Context, intensity: Int) {
        getPrefs(context).edit().putInt(KEY_VIBRATION_INTENSITY, intensity).apply()
    }
    
    fun getKeyboardTheme(context: Context): String {
        return getPrefs(context).getString(KEY_KEYBOARD_THEME, "lavender_purple") ?: "lavender_purple"
    }
    
    fun setKeyboardTheme(context: Context, themeId: String) {
        getPrefs(context).edit().putString(KEY_KEYBOARD_THEME, themeId).apply()
    }
    
    fun showBottomButtons(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_SHOW_BOTTOM_BUTTONS, false)
    }
    
    fun setShowBottomButtons(context: Context, show: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_SHOW_BOTTOM_BUTTONS, show).apply()
    }
    
    fun isPluginEnabled(context: Context, pluginId: String): Boolean {
        val prefs = getPrefs(context)
        val key = "plugin_enabled_$pluginId"
        
        if (prefs.contains(key)) {
            return prefs.getBoolean(key, false)
        }
        
        val pluginInfo = PluginManager.getAllInstallPlugins().find { it.id == pluginId }
        return pluginInfo?.enabled ?: true
    }
    
    fun setPluginEnabled(context: Context, pluginId: String, enabled: Boolean) {
        getPrefs(context).edit().putBoolean("plugin_enabled_$pluginId", enabled).apply()
    }
    
    fun isSmartPredictionEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_SMART_PREDICTION_ENABLED, false)
    }
    
    fun setSmartPredictionEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_SMART_PREDICTION_ENABLED, enabled).apply()
    }
    
    fun getPredictionModelRepo(context: Context): String {
        return getPrefs(context).getString(KEY_PREDICTION_MODEL_REPO, "https://www.modelscope.cn/models/bikeand/predictive-text-small") 
            ?: "https://www.modelscope.cn/models/bikeand/predictive-text-small"
    }
    
    fun setPredictionModelRepo(context: Context, repo: String) {
        getPrefs(context).edit().putString(KEY_PREDICTION_MODEL_REPO, repo).apply()
    }
    
    fun isSttEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_STT_ENABLED, true)
    }
    
    fun setSttEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_STT_ENABLED, enabled).apply()
    }
    
    fun getSttProvider(context: Context): String {
        return getPrefs(context).getString(KEY_STT_PROVIDER, "funasr") ?: "funasr"
    }
    
    fun setSttProvider(context: Context, provider: String) {
        getPrefs(context).edit().putString(KEY_STT_PROVIDER, provider).apply()
    }
    
    fun getFunAsrApiKey(context: Context): String {
        return getPrefs(context).getString(KEY_FUNASR_API_KEY, "") ?: ""
    }
    
    fun setFunAsrApiKey(context: Context, apiKey: String) {
        getPrefs(context).edit().putString(KEY_FUNASR_API_KEY, apiKey).apply()
    }
    
    fun isSttUseLocal(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_STT_USE_LOCAL, false)
    }
    
    fun setSttUseLocal(context: Context, useLocal: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_STT_USE_LOCAL, useLocal).apply()
    }
    
    fun isSttKeepModelInRam(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_STT_KEEP_MODEL_IN_RAM, true)
    }
    
    fun setSttKeepModelInRam(context: Context, keep: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_STT_KEEP_MODEL_IN_RAM, keep).apply()
    }
    
    fun isPunctuationModelEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_PUNCTUATION_MODEL_ENABLED, false)
    }
    
    fun setPunctuationModelEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_PUNCTUATION_MODEL_ENABLED, enabled).apply()
    }
    
    fun isSwipeDownShowRootsEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_SWIPE_DOWN_SHOW_ROOTS, false)
    }
    
    fun setSwipeDownShowRootsEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_SWIPE_DOWN_SHOW_ROOTS, enabled).apply()
    }
    
    fun getKeyboardHeightDp(context: Context): Int {
        return getPrefs(context).getInt(KEY_KEYBOARD_HEIGHT_DP, DEFAULT_KEYBOARD_HEIGHT_DP)
    }

    fun getKeyboardHeightDp(context: Context, isLandscape: Boolean): Int {
        val key = if (isLandscape) KEY_KEYBOARD_HEIGHT_DP_LANDSCAPE else KEY_KEYBOARD_HEIGHT_DP
        val alt = if (isLandscape) KEY_KEYBOARD_HEIGHT_DP else KEY_KEYBOARD_HEIGHT_DP_LANDSCAPE
        // 先读本方向的值，如果没有则用另一个方向的，最后用默认值
        return getPrefs(context).getInt(key, getPrefs(context).getInt(alt, DEFAULT_KEYBOARD_HEIGHT_DP))
    }

    fun setKeyboardHeightDp(context: Context, heightDp: Int) {
        getPrefs(context).edit().putInt(KEY_KEYBOARD_HEIGHT_DP, heightDp).apply()
    }

    fun setKeyboardHeightDp(context: Context, heightDp: Int, isLandscape: Boolean) {
        val key = if (isLandscape) KEY_KEYBOARD_HEIGHT_DP_LANDSCAPE else KEY_KEYBOARD_HEIGHT_DP
        getPrefs(context).edit().putInt(key, heightDp).apply()
    }
    
    fun getDefaultKeyboardHeightDp(): Int = DEFAULT_KEYBOARD_HEIGHT_DP

    fun getOrientationDefaultKeyboardHeightDp(context: Context, isLandscape: Boolean): Int {
        val key = if (isLandscape) KEY_KEYBOARD_HEIGHT_DP_LANDSCAPE else KEY_KEYBOARD_HEIGHT_DP
        return getPrefs(context).getInt(key, DEFAULT_KEYBOARD_HEIGHT_DP)
    }
    
    fun getKeyboardBottomPaddingDp(context: Context): Int {
        return getPrefs(context).getInt(KEY_KEYBOARD_BOTTOM_PADDING_DP, DEFAULT_KEYBOARD_BOTTOM_PADDING_DP)
    }
    
    fun setKeyboardBottomPaddingDp(context: Context, paddingDp: Int) {
        getPrefs(context).edit().putInt(KEY_KEYBOARD_BOTTOM_PADDING_DP, paddingDp).apply()
    }
    
    fun getDefaultKeyboardBottomPaddingDp(): Int = DEFAULT_KEYBOARD_BOTTOM_PADDING_DP

    fun getWebDavUrl(context: Context): String {
        return getPrefs(context).getString(KEY_WEBDAV_URL, "") ?: ""
    }

    fun setWebDavUrl(context: Context, url: String) {
        getPrefs(context).edit().putString(KEY_WEBDAV_URL, url).apply()
    }

    fun getWebDavUsername(context: Context): String {
        return getPrefs(context).getString(KEY_WEBDAV_USERNAME, "") ?: ""
    }

    fun setWebDavUsername(context: Context, username: String) {
        getPrefs(context).edit().putString(KEY_WEBDAV_USERNAME, username).apply()
    }

    fun getWebDavPassword(context: Context): String {
        return getPrefs(context).getString(KEY_WEBDAV_PASSWORD, "") ?: ""
    }

    fun setWebDavPassword(context: Context, password: String) {
        getPrefs(context).edit().putString(KEY_WEBDAV_PASSWORD, password).apply()
    }

    fun getWebDavPath(context: Context): String {
        return getPrefs(context).getString(KEY_WEBDAV_PATH, "xime") ?: "xime"
    }

    fun setWebDavPath(context: Context, path: String) {
        getPrefs(context).edit().putString(KEY_WEBDAV_PATH, path).apply()
    }
}