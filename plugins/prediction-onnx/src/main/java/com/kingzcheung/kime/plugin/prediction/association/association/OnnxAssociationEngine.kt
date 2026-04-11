package com.kingzcheung.kime.association

import android.content.Context
import android.util.Log
import com.kingzcheung.kime.plugin.prediction.association.association.NativeOnnxEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipFile

object OnnxAssociationEngine {
    private const val TAG = "OnnxAssociationEngine"
    private const val MODEL_DIR = "association_model"
    
    private var vocab: Map<String, Int> = emptyMap()
    private var id2word: Map<Int, String> = emptyMap()
    private var isInitialized = false

    fun initialize(context: Context, apkPath: String? = null): Boolean {
        if (isInitialized) return true

        try {
            val mainAppFilesDir = File("/data/data/com.kingzcheung.kime/files")
            val modelDir = File(mainAppFilesDir, MODEL_DIR)
            
            modelDir.mkdirs()
            
            val filesToCopy = listOf("vocab.json", "model.onnx", "model.onnx.data")
            val actualApkPath = apkPath ?: context.applicationInfo?.sourceDir
            
            Log.d(TAG, "Using APK path: $actualApkPath")
            
            for (fileName in filesToCopy) {
                val targetFile = File(modelDir, fileName)
                if (!targetFile.exists()) {
                    Log.d(TAG, "Extracting missing file: $fileName")
                    
                    val extractedFromApk = if (actualApkPath != null) {
                        extractSingleAssetFromApk(actualApkPath, fileName, targetFile)
                    } else {
                        false
                    }
                    
                    if (!extractedFromApk) {
                        Log.d(TAG, "Trying context.assets fallback for $fileName...")
                        try {
                            context.assets.open(fileName).use { input ->
                                targetFile.outputStream().use { output ->
                                    input.copyTo(output)
                                }
                            }
                            Log.d(TAG, "Extracted $fileName via assets (${targetFile.length()} bytes)")
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to extract $fileName", e)
                            return false
                        }
                    }
                } else {
                    Log.d(TAG, "File already exists: $fileName (${targetFile.length()} bytes)")
                }
            }
            
            if (!modelDir.exists()) {
                Log.e(TAG, "Model directory not found: ${modelDir.absolutePath}")
                return false
            }

            val vocabFile = File(modelDir, "vocab.json")
            if (!vocabFile.exists()) {
                Log.e(TAG, "Vocab file not found")
                return false
            }

            val vocabJson = JSONObject(vocabFile.readText())
            val vocabMap = vocabJson.getJSONObject("model").getJSONObject("vocab")
            vocab = vocabMap.keys().asSequence().associateWith { vocabMap.getInt(it) }
            id2word = vocab.entries.associate { it.value to it.key }
            Log.d(TAG, "Vocabulary loaded: ${vocab.size} words")

            val modelFile = File(modelDir, "model.onnx")
            if (!modelFile.exists()) {
                Log.e(TAG, "Model file not found")
                return false
            }
            Log.d(TAG, "Using model: ${modelFile.name} (${modelFile.length()} bytes)")

            val success = NativeOnnxEngine.initialize(context, apkPath, modelFile.absolutePath)
            if (success) {
                isInitialized = true
                Log.i(TAG, "ONNX Runtime initialized successfully via native JNI")
                return true
            } else {
                Log.e(TAG, "Failed to initialize ONNX Runtime")
                return false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize ONNX Runtime", e)
            return false
        }
    }

    suspend fun predict(inputText: String, topK: Int = 5): List<AssociationCandidate> = withContext(Dispatchers.Default) {
        if (!isInitialized) {
            Log.e(TAG, "Engine not initialized")
            return@withContext emptyList()
        }

        try {
            val inputIds = encodeText(inputText)
            if (inputIds.isEmpty()) {
                Log.d(TAG, "Empty input encoding for: '$inputText'")
                return@withContext emptyList()
            }

            Log.d(TAG, "Predicting for: '$inputText', tokens: $inputIds")

            val inputIdsLong = inputIds.map { it.toLong() }.toLongArray()
            val scores = NativeOnnxEngine.predict(inputIdsLong, topK)

            val candidates = scores.mapNotNull { (id, score) ->
                id2word[id]?.let { word ->
                    AssociationCandidate(word, score)
                }
            }

            Log.d(TAG, "Predicted ${candidates.size} candidates: ${candidates.map { it.text }}")
            candidates

        } catch (e: Exception) {
            Log.e(TAG, "Prediction failed", e)
            emptyList()
        }
    }

    private fun encodeText(text: String): List<Int> {
        val ids = mutableListOf<Int>()
        var i = 0
        while (i < text.length) {
            val char = text[i].toString()
            val id = vocab[char] ?: 3
            ids.add(id)
            i++
        }
        return ids
    }

    fun release() {
        NativeOnnxEngine.release()
        isInitialized = false
        Log.d(TAG, "ONNX Runtime released")
    }

    fun isInitialized(): Boolean = isInitialized
    
    private fun extractSingleAssetFromApk(apkPath: String, fileName: String, targetFile: File): Boolean {
        return try {
            Log.d(TAG, "Extracting $fileName from APK: $apkPath")
            
            val apkFile = File(apkPath)
            if (!apkFile.exists()) {
                Log.e(TAG, "APK file not found: $apkPath")
                return false
            }
            
            ZipFile(apkFile).use { zipFile ->
                val entryName = "assets/$fileName"
                val entry = zipFile.getEntry(entryName)
                
                if (entry == null) {
                    Log.e(TAG, "Asset not found in APK: $entryName")
                    return false
                }
                
                zipFile.getInputStream(entry).use { input ->
                    targetFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                Log.d(TAG, "Extracted $fileName from APK (${targetFile.length()} bytes)")
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract $fileName from APK", e)
            false
        }
    }
    
    private fun extractAssetsFromApk(apkPath: String, fileNames: List<String>, targetDir: File): Boolean {
        return try {
            Log.d(TAG, "Extracting from APK: $apkPath")
            
            val apkFile = File(apkPath)
            if (!apkFile.exists()) {
                Log.e(TAG, "APK file not found: $apkPath")
                return false
            }
            
            ZipFile(apkFile).use { zipFile ->
                for (fileName in fileNames) {
                    val entryName = "assets/$fileName"
                    val entry = zipFile.getEntry(entryName)
                    
                    if (entry == null) {
                        Log.e(TAG, "Asset not found in APK: $entryName")
                        return false
                    }
                    
                    val outputFile = File(targetDir, fileName)
                    zipFile.getInputStream(entry).use { input ->
                        outputFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    Log.d(TAG, "Extracted $fileName from APK (${outputFile.length()} bytes)")
                }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract assets from APK", e)
            false
        }
    }
}