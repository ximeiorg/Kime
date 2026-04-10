package com.kingzcheung.kime.association

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

object OnnxAssociationEngine {
    private const val TAG = "OnnxAssociationEngine"
    private const val MODEL_DIR = "association_model"
    
    private var environment: OrtEnvironment? = null
    private var session: OrtSession? = null
    private var vocab: Map<String, Int> = emptyMap()
    private var id2word: Map<Int, String> = emptyMap()
    private var isInitialized = false

    fun initialize(context: Context): Boolean {
        if (isInitialized) return true

        try {
            // 使用主应用的filesDir（插件需要写入到主应用目录）
            val mainAppFilesDir = File("/data/data/com.kingzcheung.kime/files")
            val modelDir = File(mainAppFilesDir, MODEL_DIR)
            
            // 如果模型不存在，从assets解压
            if (!modelDir.exists() || modelDir.listFiles()?.isEmpty() != false) {
                Log.d(TAG, "Extracting model from assets...")
                modelDir.mkdirs()
                
                val assets = context.assets
                val filesToCopy = listOf("vocab.json", "model.onnx")
                
                filesToCopy.forEach { fileName ->
                    val assetPath = "$MODEL_DIR/$fileName"
                    try {
                        val inputStream = assets.open(assetPath)
                        val outputFile = File(modelDir, fileName)
                        inputStream.use { input ->
                            outputFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                        Log.d(TAG, "Extracted $fileName (${outputFile.length()} bytes)")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to extract $fileName", e)
                        return false
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
}