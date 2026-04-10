package com.kingzcheung.kime.association

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipFile

object OnnxAssociationEngine {
    private const val TAG = "OnnxAssociationEngine"
    private const val MODEL_DIR = "association_model"
    private const val NATIVE_LIB_NAME = "onnxruntime4j_jni"
    
    private var environment: OrtEnvironment? = null
    private var session: OrtSession? = null
    private var vocab: Map<String, Int> = emptyMap()
    private var id2word: Map<Int, String> = emptyMap()
    private var isInitialized = false
    private var nativeLoaded = false

    fun initialize(context: Context, apkPath: String? = null): Boolean {
        if (isInitialized) return true
        
        if (!nativeLoaded) {
            loadNativeLibrary(apkPath)
        }
        
        if (!nativeLoaded) {
            Log.e(TAG, "Native library not loaded")
            return false
        }

        try {
            val mainAppFilesDir = File("/data/data/com.kingzcheung.kime/files")
            val modelDir = File(mainAppFilesDir, MODEL_DIR)
            
            if (!modelDir.exists() || modelDir.listFiles()?.isEmpty() != false) {
                Log.d(TAG, "Extracting model from assets...")
                modelDir.mkdirs()
                
                val filesToCopy = listOf("vocab.json", "model.onnx")
                
                val actualApkPath = apkPath ?: context.applicationInfo?.sourceDir
                
                Log.d(TAG, "Using APK path: $actualApkPath")
                
                val extractedFromApk = if (actualApkPath != null) {
                    extractAssetsFromApk(actualApkPath, filesToCopy, modelDir)
                } else {
                    false
                }
                
                if (!extractedFromApk) {
                    Log.d(TAG, "Trying context.assets fallback...")
                    filesToCopy.forEach { fileName ->
                        try {
                            val inputStream = context.assets.open(fileName)
                            val outputFile = File(modelDir, fileName)
                            inputStream.use { input ->
                                outputFile.outputStream().use { output ->
                                    input.copyTo(output)
                                }
                            }
                            Log.d(TAG, "Extracted $fileName via assets (${outputFile.length()} bytes)")
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to extract $fileName from assets", e)
                            return false
                        }
                    }
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

            environment = OrtEnvironment.getEnvironment()
            val sessionOptions = OrtSession.SessionOptions()
            sessionOptions.setIntraOpNumThreads(4)
            
            session = environment?.createSession(modelFile.absolutePath, sessionOptions)
            
            if (session != null) {
                isInitialized = true
                Log.i(TAG, "ONNX Runtime initialized successfully")
                return true
            } else {
                Log.e(TAG, "Failed to create ONNX session")
                return false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize ONNX Runtime", e)
            return false
        }
    }

    suspend fun predict(inputText: String, topK: Int = 5): List<AssociationCandidate> = withContext(Dispatchers.Default) {
        if (!isInitialized || session == null) {
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

            val inputArray = Array(1) { inputIds.map { it.toLong() }.toLongArray() }
            val inputTensor = OnnxTensor.createTensor(environment, inputArray)

            val inputs = mapOf("input_ids" to inputTensor)
            val results = session?.run(inputs)

            val outputTensor = results?.get(0) as? OnnxTensor
            val outputArray = outputTensor?.value as? Array<Array<FloatArray>>

            inputTensor.close()
            results?.close()

            if (outputArray == null) {
                Log.e(TAG, "Failed to get output")
                return@withContext emptyList()
            }

            val lastPos = outputArray[0].size - 1
            val logits = outputArray[0][lastPos]

            val scores = logits.mapIndexed { index, score ->
                index to score
            }.filter { it.first >= 4 }
                .sortedByDescending { it.second }
                .take(topK)

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
        session?.close()
        session = null
        isInitialized = false
        Log.d(TAG, "ONNX Runtime released")
    }

    fun isInitialized(): Boolean = isInitialized
    
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