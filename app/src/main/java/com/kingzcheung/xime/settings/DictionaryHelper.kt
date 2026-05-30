package com.kingzcheung.xime.settings

import android.content.Context
import android.util.Log
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader

data class DictEntry(
    val word: String,
    val code: String
)

object DictionaryHelper {
    private const val TAG = "DictionaryHelper"

    private fun getDictFile(context: Context, schemaId: String): File? {
        val dictName = SchemaManager.getReferencedDictName(context, schemaId) ?: schemaId
        val f = File(SchemaManager.getSharedDir(context), "$dictName.dict.yaml")
        return if (f.exists()) f else null
    }

    fun loadDictionary(context: Context, schemaId: String): List<DictEntry> {
        val dictFile = getDictFile(context, schemaId) ?: return emptyList()
        val entries = mutableListOf<DictEntry>()

        try {
            FileInputStream(dictFile).use { input ->
                val reader = BufferedReader(InputStreamReader(input, "UTF-8"))
                var inDataSection = false
                var line = reader.readLine()

                while (line != null) {
                    val trimmed = line.trimStart()

                    if (trimmed == "...") {
                        inDataSection = true
                        line = reader.readLine()
                        continue
                    }

                    if (inDataSection && trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                        val parts = trimmed.split("\t", "  ", " ")
                        if (parts.size >= 2) {
                            entries.add(DictEntry(parts[0], parts[1]))
                        }
                    }

                    line = reader.readLine()
                }
                reader.close()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load dictionary for $schemaId", e)
        }

        return entries
    }

    fun searchDictionary(entries: List<DictEntry>, query: String): List<DictEntry> {
        if (query.isEmpty()) return entries.take(100)
        return entries.filter {
            it.word.contains(query) || it.code.contains(query)
        }.take(100)
    }
}
