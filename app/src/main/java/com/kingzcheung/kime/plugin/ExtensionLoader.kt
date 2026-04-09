package com.kingzcheung.kime.plugin

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.kingzcheung.kime.plugin.api.KimeExtension
import com.kingzcheung.kime.plugin.api.KimeExtensionFactory
import dalvik.system.PathClassLoader
import java.io.File

object ExtensionLoader {
    private const val TAG = "ExtensionLoader"
    private const val EXTENSION_ACTION = "com.kingzcheung.kime.plugin.EXTENSION"
    private const val EXTENSION_FACTORY_CLASS_META = "com.kingzcheung.kime.extension.factory.class"
    
    @Suppress("DEPRECATION")
    private val PACKAGE_FLAGS = PackageManager.GET_CONFIGURATIONS or
        PackageManager.GET_META_DATA or
        PackageManager.GET_SIGNATURES or
        (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.GET_SIGNING_CERTIFICATES else 0)
    
    fun loadExtensionsFromApk(context: Context, apkPath: String): List<KimeExtension> {
        return try {
            Log.d(TAG, "Loading extensions from APK: $apkPath")
            
            val packageInfo = context.packageManager.getPackageArchiveInfo(
                apkPath,
                PACKAGE_FLAGS
            )
            
            if (packageInfo == null) {
                Log.e(TAG, "Failed to get package info for: $apkPath")
                return emptyList()
            }
            
            val appInfo = packageInfo.applicationInfo
            if (appInfo == null) {
                Log.e(TAG, "ApplicationInfo is null for: $apkPath")
                return emptyList()
            }
            appInfo.sourceDir = apkPath
            appInfo.publicSourceDir = apkPath
            
            val classNames = getExtensionFactoryClassNames(apkPath, context)
            if (classNames.isEmpty()) {
                Log.d(TAG, "No extension factory classes found in: $apkPath")
                return emptyList()
            }
            
            val classLoader = PathClassLoader(apkPath, context.classLoader)
            
            classNames.flatMap { className ->
                loadExtensionsFromClass(classLoader, className, context)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load extensions from APK: $apkPath", e)
            emptyList()
        }
    }
    
    private fun getExtensionFactoryClassNames(apkPath: String, context: Context): List<String> {
        return try {
            val packageInfo = context.packageManager.getPackageArchiveInfo(
                apkPath,
                PackageManager.GET_META_DATA
            )
            
            val metaData = packageInfo?.applicationInfo?.metaData
            if (metaData != null && metaData.containsKey(EXTENSION_FACTORY_CLASS_META)) {
                val className = metaData.getString(EXTENSION_FACTORY_CLASS_META)
                if (className != null) {
                    Log.d(TAG, "Found extension factory class: $className")
                    listOf(className)
                } else {
                    emptyList()
                }
            } else {
                Log.d(TAG, "No extension factory metadata found")
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get extension factory class names", e)
            emptyList()
        }
    }
    
    private fun loadExtensionsFromClass(
        classLoader: PathClassLoader,
        className: String,
        context: Context
    ): List<KimeExtension> {
        return try {
            Log.d(TAG, "Loading class: $className")
            
            val clazz = classLoader.loadClass(className)
            
            if (!KimeExtensionFactory::class.java.isAssignableFrom(clazz)) {
                Log.e(TAG, "Class $className does not implement KimeExtensionFactory")
                return emptyList()
            }
            
            val factory = clazz.getDeclaredConstructor().newInstance() as KimeExtensionFactory
            val extensions = factory.createExtensions()
            
            Log.d(TAG, "Created ${extensions.size} extensions from factory")
            
            extensions.map { extension ->
                try {
                    val initSuccess = extension.initialize(context)
                    if (initSuccess) {
                        Log.d(TAG, "Extension ${extension.id} initialized successfully")
                        extension
                    } else {
                        Log.e(TAG, "Extension ${extension.id} initialization failed")
                        extension.release()
                        null
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to initialize extension ${extension.id}", e)
                    extension.release()
                    null
                }
            }.filterNotNull()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load extensions from class: $className", e)
            emptyList()
        }
    }
    
    fun loadInstalledExtensions(context: Context): List<KimeExtension> {
        val extensions = mutableListOf<KimeExtension>()
        
        val pm = context.packageManager
        
        // 通过 Intent Filter 查找插件（类似 fcitx-android 方案）
        Log.d(TAG, "Querying packages with intent: $EXTENSION_ACTION")
        
        val intent = Intent(EXTENSION_ACTION)
        val resolveInfos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            pm.queryIntentActivities(intent, 0)
        }
        
        Log.d(TAG, "Found ${resolveInfos.size} packages with plugin intent")
        
        resolveInfos.forEach { resolveInfo ->
            val packageName = resolveInfo.activityInfo.packageName
            Log.d(TAG, "Found plugin package: $packageName")
            
            try {
                val pkgInfo = pm.getPackageInfo(packageName, PACKAGE_FLAGS)
                val apkPath = pkgInfo.applicationInfo?.publicSourceDir
                
                if (apkPath != null) {
                    extensions.addAll(loadExtensionsFromApk(context, apkPath))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load plugin: $packageName", e)
            }
        }
        
        Log.d(TAG, "Total extensions loaded: ${extensions.size}")
        return extensions
    }
    
    fun loadExtensionsFromPrivateDir(context: Context): List<KimeExtension> {
        val extensions = mutableListOf<KimeExtension>()
        
        val extDir = File(context.filesDir, "extensions")
        if (!extDir.exists()) {
            Log.d(TAG, "Extensions directory does not exist")
            return emptyList()
        }
        
        extDir.listFiles()?.forEach { file ->
            if (file.isFile && file.name.endsWith(".apk")) {
                Log.d(TAG, "Loading extension from private dir: ${file.absolutePath}")
                extensions.addAll(loadExtensionsFromApk(context, file.absolutePath))
            }
        }
        
        return extensions
    }
}