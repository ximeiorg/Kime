package com.kingzcheung.kime.settings

import android.content.Context
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader

object SchemaConfigHelper {
    private const val TAG = "SchemaConfigHelper"
    private const val RIME_ASSETS_DIR = "rime"
    
    fun loadSchemas(context: Context): List<SchemaInfo> {
        val schemas = mutableListOf<SchemaInfo>()
        
        try {
            val assetManager = context.assets
            val files = assetManager.list(RIME_ASSETS_DIR)
            
            if (files != null) {
                for (fileName in files) {
                    if (fileName.endsWith(".schema.yaml")) {
                        val schema = parseSchemaFile(context, "$RIME_ASSETS_DIR/$fileName")
                        if (schema != null) {
                            schemas.add(schema)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load schemas", e)
        }
        
        return schemas
    }
    
    private fun parseSchemaFile(context: Context, filePath: String): SchemaInfo? {
        return try {
            val inputStream = context.assets.open(filePath)
            val reader = BufferedReader(InputStreamReader(inputStream))
            val content = reader.readText()
            reader.close()
            inputStream.close()
            
            parseSchemaYaml(content)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse schema file: $filePath", e)
            null
        }
    }
    
    private fun parseSchemaYaml(content: String): SchemaInfo? {
        var schemaId = ""
        var name = ""
        var version = ""
        var author = ""
        var description = ""
        
        var inSchemaSection = false
        var inAuthorSection = false
        val authorList = mutableListOf<String>()
        
        for (line in content.lines()) {
            val trimmed = line.trim()
            
            if (trimmed.startsWith("schema:")) {
                inSchemaSection = true
                continue
            }
            
            if (inSchemaSection) {
                when {
                    trimmed.startsWith("schema_id:") -> {
                        schemaId = extractValue(trimmed)
                    }
                    trimmed.startsWith("name:") -> {
                        name = extractValue(trimmed)
                    }
                    trimmed.startsWith("version:") -> {
                        version = extractValue(trimmed)
                    }
                    trimmed.startsWith("author:") -> {
                        inAuthorSection = true
                    }
                    trimmed.startsWith("description:") -> {
                        description = extractValue(trimmed)
                        inSchemaSection = false
                    }
                    inAuthorSection && trimmed.startsWith("-") -> {
                        authorList.add(extractListItem(trimmed))
                    }
                    inAuthorSection && !trimmed.startsWith("-") && !trimmed.startsWith(" ") -> {
                        inAuthorSection = false
                    }
                }
            }
        }
        
        author = authorList.joinToString(", ")
        
        return if (schemaId.isNotEmpty() && name.isNotEmpty()) {
            SchemaInfo(schemaId, name, version, author, description)
        } else {
            null
        }
    }
    
    private fun extractValue(line: String): String {
        val parts = line.split(":", limit = 2)
        return if (parts.size == 2) {
            parts[1].trim().replace("\"", "")
        } else {
            ""
        }
    }
    
    private fun extractListItem(line: String): String {
        return line.removePrefix("-").trim()
    }
}