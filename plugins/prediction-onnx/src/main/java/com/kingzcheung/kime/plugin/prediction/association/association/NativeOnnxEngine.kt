package com.kingzcheung.kime.plugin.prediction.association.association

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.util.zip.ZipFile

object NativeOnnxEngine {
    private const val TAG = "NativeOnnxEngine"
    private var nativeLoaded = false
    
    fun loadNativeLibrary(context: Context, apkPath: String?): Boolean {
        val libsToLoad = listOf("libonnxruntime.so", "libonnx_jni.so")
        
        for (libName in libsToLoad) {
            if (!loadSingleLibrary(context, apkPath, libName)) {
                Log.e(TAG, "Failed to load $libName")
                return false
            }
        }
        
        nativeLoaded = true
        Log.d(TAG, "All native libraries loaded successfully")
        return true
    }
    
    private fun loadSingleLibrary(context: Context, apkPath: String?, libName: String): Boolean {
        val simpleName = libName.removePrefix("lib").removeSuffix(".so")
        
        try {
            System.loadLibrary(simpleName)
            Log.d(TAG, "Loaded $libName via System.loadLibrary")
            return true
        } catch (e: UnsatisfiedLinkError) {
            if (e.message?.contains("already opened") == true || e.message?.contains("already loaded") == true) {
                Log.d(TAG, "$libName already loaded, skipping")
                return true
            }
            Log.d(TAG, "System.loadLibrary failed for $libName, trying alternative methods...")
        }
        
        val nativeLibDir = context.applicationInfo?.nativeLibraryDir
        if (nativeLibDir != null) {
            val libFile = File(nativeLibDir, libName)
            if (libFile.exists()) {
                try {
                    System.load(libFile.absolutePath)
                    Log.d(TAG, "Loaded $libName from nativeLibraryDir: ${libFile.absolutePath}")
                    return true
                } catch (e: UnsatisfiedLinkError) {
                    if (e.message?.contains("already opened") == true || e.message?.contains("already loaded") == true) {
                        Log.d(TAG, "$libName already loaded, skipping")
                        return true
                    }
                    Log.e(TAG, "Failed to load from nativeLibraryDir: ${e.message}")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to load from nativeLibraryDir", e)
                }
            }
        }
        
        if (apkPath != null) {
            val extractedPath = extractLibFromApk(apkPath, libName)
            if (extractedPath != null) {
                try {
                    System.load(extractedPath)
                    Log.d(TAG, "Loaded $libName from extracted APK: $extractedPath")
                    return true
                } catch (e: UnsatisfiedLinkError) {
                    if (e.message?.contains("already opened") == true || e.message?.contains("already loaded") == true) {
                        Log.d(TAG, "$libName already loaded, skipping")
                        return true
                    }
                    Log.e(TAG, "Failed to load extracted $libName: ${e.message}")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to load extracted $libName", e)
                }
            }
        }
        
        return false
    }
    
    private fun extractLibFromApk(apkPath: String, libName: String): String? {
        try {
            val apkFile = File(apkPath)
            if (!apkFile.exists()) return null
            
            val abi = Build.SUPPORTED_ABIS.first()
            val entryName = "lib/$abi/$libName"
            
            val tmpDir = File("/data/data/com.kingzcheung.kime/files/native_libs")
            tmpDir.mkdirs()
            val libFile = File(tmpDir, libName)
            
            ZipFile(apkFile).use { zipFile ->
                val entry = zipFile.getEntry(entryName)
                if (entry == null) {
                    Log.e(TAG, "Library not found in APK: $entryName")
                    
                    for (altAbi in Build.SUPPORTED_ABIS) {
                        val altEntryName = "lib/$altAbi/$libName"
                        val altEntry = zipFile.getEntry(altEntryName)
                        if (altEntry != null) {
                            Log.d(TAG, "Found library in alternative ABI: $altAbi")
                            zipFile.getInputStream(altEntry).use { input ->
                                libFile.outputStream().use { output ->
                                    input.copyTo(output)
                                }
                            }
                            return libFile.absolutePath
                        }
                    }
                    return null
                }
                
                zipFile.getInputStream(entry).use { input ->
                    libFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
            
            Log.d(TAG, "Extracted $libName to: ${libFile.absolutePath}")
            return libFile.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract $libName from APK", e)
            return null
        }
    }
    
    fun initialize(context: Context, apkPath: String?, modelPath: String): Boolean {
        try {
            nativeInitialize(modelPath)
            Log.d(TAG, "Native method already available")
            return true
        } catch (e: UnsatisfiedLinkError) {
            Log.d(TAG, "Native method not available, loading libraries...")
        }
        
        if (!loadNativeLibrary(context, apkPath)) {
            Log.e(TAG, "Native libraries not loaded")
            return false
        }
        
        return try {
            nativeInitialize(modelPath)
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Native method still unavailable after loading: ${e.message}")
            nativeLoaded = false
            false
        }
    }
    
    fun predict(inputIds: LongArray, topK: Int): Array<Pair<Int, Float>> {
        val result = nativePredict(inputIds, topK)
        if (result == null) {
            return emptyArray()
        }
        
        val pairs = mutableListOf<Pair<Int, Float>>()
        for (i in result.indices step 2) {
            val idx = result[i].toIntOrNull() ?: continue
            val score = result[i + 1].toFloatOrNull() ?: continue
            pairs.add(Pair(idx, score))
        }
        return pairs.toTypedArray()
    }
    
    fun release() {
        nativeRelease()
    }
    
    fun isInitialized(): Boolean {
        return nativeIsInitialized()
    }
    
    private external fun nativeInitialize(modelPath: String): Boolean
    private external fun nativePredict(inputIds: LongArray, topK: Int): Array<String>?
    private external fun nativeRelease()
    private external fun nativeIsInitialized(): Boolean
}