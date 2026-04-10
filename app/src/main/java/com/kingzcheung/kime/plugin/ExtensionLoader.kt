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
            val packageInfo = context.packageManager.getPackageArchiveInfo(
                apkPath,
                PACKAGE_FLAGS
            ) ?: return emptyList()
            
            val appInfo = packageInfo.applicationInfo ?: return emptyList()
            
            appInfo.sourceDir = apkPath
            appInfo.publicSourceDir = apkPath
            
            val classNames = getExtensionFactoryClassNames(apkPath, context)
            if (classNames.isEmpty()) {
                return emptyList()
            }
            
            val classLoader = PathClassLoader(apkPath, context.classLoader)
            
            classNames.flatMap { className ->
                loadExtensionsFromClass(classLoader, className, context, packageInfo.packageName)
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
                    listOf(className)
                } else {
                    emptyList()
                }
            } else {
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
        context: Context,
        pluginPackageName: String? = null
    ): List<KimeExtension> {
        return try {
            val clazz = classLoader.loadClass(className)
            
            if (!KimeExtensionFactory::class.java.isAssignableFrom(clazz)) {
                return emptyList()
            }
            
            val factory = clazz.getDeclaredConstructor().newInstance() as KimeExtensionFactory
            val extensions = factory.createExtensions()
            
            extensions.map { extension ->
                try {
                    val pluginContext = if (pluginPackageName != null && pluginPackageName != context.packageName) {
                        context.createPackageContext(
                            pluginPackageName,
                            Context.CONTEXT_INCLUDE_CODE or Context.CONTEXT_IGNORE_SECURITY
                        )
                    } else {
                        context
                    }
                    
                    val initSuccess = extension.initialize(pluginContext)
                    if (initSuccess) {
                        extension
                    } else {
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
        
        val intent = Intent(EXTENSION_ACTION)
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
                
                if (apkPath != null) {
                    extensions.addAll(loadExtensionsFromApk(context, apkPath))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load plugin: $packageName", e)
            }
        }
        
        return extensions
    }
    
    fun loadExtensionsFromPrivateDir(context: Context): List<KimeExtension> {
        val extensions = mutableListOf<KimeExtension>()
        
        val extDir = File(context.filesDir, "extensions")
        if (!extDir.exists()) {
            return emptyList()
        }
        
        extDir.listFiles()?.forEach { file ->
            if (file.isFile && file.name.endsWith(".apk")) {
                extensions.addAll(loadExtensionsFromApk(context, file.absolutePath))
            }
        }
        
        return extensions
    }
}