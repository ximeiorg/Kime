package com.kingzcheung.kime.plugin

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.kingzcheung.kime.plugin.api.EmojiPlugin
import com.kingzcheung.kime.plugin.api.PluginFactory
import com.kingzcheung.kime.plugin.api.PluginMetadata
import com.kingzcheung.kime.plugin.api.PluginType
import com.kingzcheung.kime.plugin.api.PredictionPlugin
import com.kingzcheung.kime.plugin.api.SpeechPlugin
import dalvik.system.PathClassLoader
import java.io.File

object ExtensionLoader {
    private const val TAG = "ExtensionLoader"
    private const val PLUGIN_ACTION = "com.kingzcheung.kime.plugin.EXTENSION"
    private const val PLUGIN_FACTORY_CLASS_META = "com.kingzcheung.kime.plugin.factory.class"
    
    private val loadedPlugins = mutableMapOf<String, PluginMetadata>()
    private val loadedApks = mutableMapOf<String, List<PluginMetadata>>()
    
    @Suppress("DEPRECATION")
    private val PACKAGE_FLAGS = PackageManager.GET_CONFIGURATIONS or
        PackageManager.GET_META_DATA or
        PackageManager.GET_SIGNATURES or
        (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.GET_SIGNING_CERTIFICATES else 0)
    
    private val addedNativeLibDirs = mutableSetOf<String>()
    
    fun addNativeLibraryDir(context: Context, nativeLibDir: String) {
        if (nativeLibDir.isBlank() || addedNativeLibDirs.contains(nativeLibDir)) {
            return
        }
        
        try {
            val classLoader = context.classLoader
            val baseDexClassLoader = classLoader.javaClass.superclass ?: classLoader.javaClass
            val pathListField = baseDexClassLoader.getDeclaredField("pathList")
            pathListField.isAccessible = true
            val pathList = pathListField.get(classLoader)
            
            val nativeLibDirsField = pathList.javaClass.getDeclaredField("nativeLibraryDirectories")
            nativeLibDirsField.isAccessible = true
            
            @Suppress("UNCHECKED_CAST")
            val libDirs = nativeLibDirsField.get(pathList) as MutableList<File>
            val newDir = File(nativeLibDir)
            if (!libDirs.contains(newDir)) {
                libDirs.add(newDir)
                addedNativeLibDirs.add(nativeLibDir)
                Log.d(TAG, "Added native library dir: $nativeLibDir")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add native library dir: $nativeLibDir", e)
        }
    }
    
    fun loadPredictionPlugins(context: Context): List<PredictionPlugin> {
        return loadPlugins(context).filterIsInstance<PredictionPlugin>()
    }
    
    fun loadEmojiPlugins(context: Context): List<EmojiPlugin> {
        return loadPlugins(context).filterIsInstance<EmojiPlugin>()
    }
    
    fun loadSpeechPlugins(context: Context): List<SpeechPlugin> {
        return loadPlugins(context).filterIsInstance<SpeechPlugin>()
    }
    
    fun loadPlugins(context: Context): List<PluginMetadata> {
        val plugins = mutableListOf<PluginMetadata>()
        
        val pm = context.packageManager
        
        val intent = Intent(PLUGIN_ACTION)
        val resolveInfos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            pm.queryIntentActivities(intent, 0)
        }
        
        resolveInfos.forEach { resolveInfo ->
            val packageName = resolveInfo.activityInfo.packageName
            
            try {
                val pkgInfo = pm.getPackageInfo(packageName, PACKAGE_FLAGS)
                val apkPath = pkgInfo.applicationInfo?.publicSourceDir
                val nativeLibDir = pkgInfo.applicationInfo?.nativeLibraryDir
                
                if (!nativeLibDir.isNullOrBlank()) {
                    Log.d(TAG, "Plugin $packageName nativeLibraryDir: $nativeLibDir")
                    addNativeLibraryDir(context, nativeLibDir)
                }
                
                if (apkPath != null) {
                    plugins.addAll(loadPluginsFromApkCached(context, apkPath))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load plugin: $packageName", e)
            }
        }
        
        plugins.addAll(loadPluginsFromPrivateDir(context))
        
        return plugins
    }
    
    private fun loadPluginsFromApkCached(context: Context, apkPath: String): List<PluginMetadata> {
        val cached = loadedApks[apkPath]
        if (cached != null) {
            Log.d(TAG, "Returning cached plugins for APK: $apkPath (${cached.size} plugins)")
            return cached
        }
        
        Log.d(TAG, "Loading new plugins from APK: $apkPath")
        val plugins = loadPluginsFromApk(context, apkPath)
        if (plugins.isNotEmpty()) {
            loadedApks[apkPath] = plugins
            plugins.forEach { plugin ->
                loadedPlugins[plugin.id] = plugin
            }
            Log.d(TAG, "Cached ${plugins.size} plugins from APK: $apkPath")
        }
        return plugins
    }
    
    private fun loadPluginsFromApk(context: Context, apkPath: String): List<PluginMetadata> {
        return try {
            val packageInfo = context.packageManager.getPackageArchiveInfo(
                apkPath,
                PACKAGE_FLAGS
            ) ?: return emptyList()
            
            val appInfo = packageInfo.applicationInfo ?: return emptyList()
            
            appInfo.sourceDir = apkPath
            appInfo.publicSourceDir = apkPath
            
            val nativeLibDir = appInfo.nativeLibraryDir
            if (!nativeLibDir.isNullOrBlank()) {
                addNativeLibraryDir(context, nativeLibDir)
            }
            
            val classNames = getFactoryClassNames(apkPath, context)
            if (classNames.isEmpty()) {
                return emptyList()
            }
            
            val classLoader = PathClassLoader(apkPath, context.classLoader)
            
            classNames.flatMap { className ->
                loadPluginsFromFactory(classLoader, className, context, packageInfo.packageName, apkPath)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load plugins from APK: $apkPath", e)
            emptyList()
        }
    }
    
    private fun getFactoryClassNames(apkPath: String, context: Context): List<String> {
        return try {
            val packageInfo = context.packageManager.getPackageArchiveInfo(
                apkPath,
                PackageManager.GET_META_DATA
            )
            
            val metaData = packageInfo?.applicationInfo?.metaData
            if (metaData != null && metaData.containsKey(PLUGIN_FACTORY_CLASS_META)) {
                val className = metaData.getString(PLUGIN_FACTORY_CLASS_META)
                if (className != null) {
                    listOf(className)
                } else {
                    emptyList()
                }
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get factory class names", e)
            emptyList()
        }
    }
    
    private fun loadPluginsFromFactory(
        classLoader: PathClassLoader,
        className: String,
        context: Context,
        pluginPackageName: String?,
        apkPath: String?
    ): List<PluginMetadata> {
        return try {
            val clazz = classLoader.loadClass(className)
            
            if (!PluginFactory::class.java.isAssignableFrom(clazz)) {
                Log.e(TAG, "Class $className is not a PluginFactory")
                return emptyList()
            }
            
            val factory = clazz.getDeclaredConstructor().newInstance() as PluginFactory
            val plugins = mutableListOf<PluginMetadata>()
            
            val predictionPlugin = factory.createPredictionPlugin()
            if (predictionPlugin != null) {
                if (initializePlugin(predictionPlugin, context, pluginPackageName, apkPath)) {
                    plugins.add(predictionPlugin)
                    Log.d(TAG, "Loaded prediction plugin: ${predictionPlugin.name}")
                }
            }
            
            val emojiPlugin = factory.createEmojiPlugin()
            if (emojiPlugin != null) {
                if (initializePlugin(emojiPlugin, context, pluginPackageName, apkPath)) {
                    plugins.add(emojiPlugin)
                    Log.d(TAG, "Loaded emoji plugin: ${emojiPlugin.name}")
                }
            }
            
            val speechPlugin = factory.createSpeechPlugin()
            if (speechPlugin != null) {
                if (initializePlugin(speechPlugin, context, pluginPackageName, apkPath)) {
                    plugins.add(speechPlugin)
                    Log.d(TAG, "Loaded speech plugin: ${speechPlugin.name}")
                }
            }
            
            plugins
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load plugins from factory: $className", e)
            emptyList()
        }
    }
    
    private fun initializePlugin(
        plugin: PluginMetadata,
        context: Context,
        pluginPackageName: String?,
        apkPath: String?
    ): Boolean {
        return try {
            val pluginContext = if (pluginPackageName != null && pluginPackageName != context.packageName) {
                context.createPackageContext(
                    pluginPackageName,
                    Context.CONTEXT_INCLUDE_CODE or Context.CONTEXT_IGNORE_SECURITY
                )
            } else {
                context
            }
            
            val success = plugin.initialize(pluginContext, apkPath)
            if (!success) {
                plugin.release()
                Log.e(TAG, "Failed to initialize plugin: ${plugin.name}")
            }
            success
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize plugin: ${plugin.name}", e)
            plugin.release()
            false
        }
    }
    
    fun loadPluginsFromPrivateDir(context: Context): List<PluginMetadata> {
        val plugins = mutableListOf<PluginMetadata>()
        
        val extDir = File(context.filesDir, "extensions")
        if (!extDir.exists()) {
            return plugins
        }
        
        extDir.listFiles()?.forEach { file ->
            if (file.isFile && file.name.endsWith(".apk")) {
                plugins.addAll(loadPluginsFromApkCached(context, file.absolutePath))
            }
        }
        
        return plugins
    }
    
    fun getLoadedPlugin(id: String): PluginMetadata? = loadedPlugins[id]
    
    fun removeLoadedPlugin(id: String) {
        loadedPlugins.remove(id)
        Log.d(TAG, "Removed plugin: $id")
    }
    
    fun clearCachedApk(apkPath: String) {
        loadedApks.remove(apkPath)
        Log.d(TAG, "Cleared cached APK: $apkPath")
    }
    
    fun clearAllCachedPlugins() {
        loadedApks.clear()
        loadedPlugins.clear()
        Log.d(TAG, "Cleared all cached plugins")
    }
}