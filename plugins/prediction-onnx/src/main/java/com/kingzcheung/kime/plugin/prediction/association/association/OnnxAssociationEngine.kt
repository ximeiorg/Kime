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

    private fun extractModelFromAssets(context: Context): Boolean {
        val modelDir = File(context.filesDir, MODEL_DIR)
        
        if (modelDir.exists() && modelDir.listFiles()?.isNotEmpty() == true) {
            Log.d(TAG, "Model already extracted")
            return true
        }
        
        return try {
            modelDir.mkdirs()
            
            val assets = context.assets
            val filesToCopy = listOf("vocab.json", "model.onnx")
            
            filesToCopy.forEach { fileName ->
                val inputStream = assets.open("$MODEL_DIR/$fileName")
                val outputFile = File(modelDir, fileName)
                inputStream.use { input ->
                    outputFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                Log.d(TAG, "Extracted $fileName to ${outputFile.absolutePath}")
            }
            
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract model from assets", e)
            false
        }
    }

    suspend fun initialize(context: Context): Boolean = withContext(Dispatchers.IO) {
        if (isInitialized) return@withContext true

        try {
            // 从 assets 解压模型
            val extracted = extractModelFromAssets(context)
            if (!extracted) {
                Log.e(TAG, "Failed to extract model from assets")
                return@withContext false
            }
            
            val modelDir = File(context.filesDir, MODEL_DIR)

            val vocabFile = File(modelDir, "vocab.json")
            if (!vocabFile.exists()) {
                Log.e(TAG, "Vocab file not found")
                return@withContext false
            }

            val vocabJson = JSONObject(vocabFile.readText())
            val vocabMap = vocabJson.getJSONObject("model").getJSONObject("vocab")
            vocab = vocabMap.keys().asSequence().associateWith { vocabMap.getInt(it) }
            id2word = vocab.entries.associate { it.value to it.key }
            Log.d(TAG, "Vocabulary loaded: ${vocab.size} words")

            val modelFile = File(modelDir, "model.onnx")
            if (!modelFile.exists()) {
                Log.e(TAG, "Model file not found")
                return@withContext false
            }

            environment = OrtEnvironment.getEnvironment()
            val sessionOptions = OrtSession.SessionOptions()
            sessionOptions.setIntraOpNumThreads(4)
            
            session = environment?.createSession(modelFile.absolutePath, sessionOptions)
            
            isInitialized = session != null
            Log.i(TAG, "ONNX Runtime initialized: $isInitialized")
            isInitialized
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize ONNX Runtime", e)
            false
        }
    }

    suspend fun predict(inputText: String, topK: Int = 5): List<AssociationCandidate> = withContext(Dispatchers.Default) {
        if (!isInitialized || session == null) {
            return@withContext emptyList()
        }

        try {
            val inputIds = encodeText(inputText)
            if (inputIds.isEmpty()) {
                return@withContext emptyList()
            }

            val inputArray = Array(1) { inputIds.map { it.toLong() }.toLongArray() }
            val inputTensor = OnnxTensor.createTensor(environment, inputArray)

            val inputs = mapOf("input_ids" to inputTensor)
            val results = session?.run(inputs)

            val outputTensor = results?.get(0) as? OnnxTensor
            val outputArray = outputTensor?.value as? Array<Array<FloatArray>>

            inputTensor.close()
            results?.close()

            if (outputArray == null) {
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