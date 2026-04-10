package com.kingzcheung.kime.plugin.emoji

import android.content.Context
import android.content.Intent
import android.util.Log
import com.kingzcheung.kime.plugin.api.*
import java.io.File
import java.util.zip.ZipFile

class EmojiStickerPlugin : KimeExtension {
    
    override val id: String = "emoji_sticker_plugin"
    override val name: String = "恶搞兔表情包"
    override val description: String = "提供8个恶搞兔表情包"
    override val type: ExtensionType = ExtensionType.EMOJI
    override val version: String = "1.0.0"
    
    private lateinit var context: Context
    private var apkPath: String? = null
    private var emojiList: List<EmojiItem> = emptyList()
    
    companion object {
        private const val TAG = "EmojiStickerPlugin"
    }
    
    override fun initialize(context: Context): Boolean {
        return initialize(context, null)
    }
    
    override fun initialize(context: Context, apkPath: String?): Boolean {
        this.context = context
        this.apkPath = apkPath
        try {
            Log.d(TAG, "Initializing with context: ${context.packageName}")
            Log.d(TAG, "APK path: $apkPath")
            
            val mainAppFilesDir = File("/data/data/com.kingzcheung.kime/files")
            if (!mainAppFilesDir.exists()) {
                mainAppFilesDir.mkdirs()
            }
            
            loadEmojis(mainAppFilesDir)
            Log.d(TAG, "Loaded ${emojiList.size} emojis")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Initialization failed", e)
            return false
        }
    }
    
    private fun loadEmojis(filesDir: File) {
        Log.d(TAG, "Loading emojis to: ${filesDir.absolutePath}")
        
        val emojisDir = File(filesDir, "emojis")
        Log.d(TAG, "Emojis dir: ${emojisDir.absolutePath}, exists: ${emojisDir.exists()}")
        
        if (!emojisDir.exists()) {
            Log.d(TAG, "Copying emojis from assets")
            copyEmojisFromAssets(emojisDir)
        }
        
        emojiList = emojisDir.listFiles()
            ?.filter { it.extension == "jpg" || it.extension == "png" || it.extension == "gif" }
            ?.sortedBy { it.nameWithoutExtension.toIntOrNull() ?: 0 }
            ?.mapIndexed { index, file ->
                EmojiItem(
                    id = "emoji_$index",
                    displayText = file.nameWithoutExtension,
                    insertText = "[表情${file.nameWithoutExtension}]",
                    imageUrl = file.absolutePath,
                    category = "恶搞兔"
                )
            } ?: emptyList()
        
        Log.d(TAG, "Emoji list size: ${emojiList.size}")
    }
    
    private fun copyEmojisFromAssets(emojisDir: File) {
        emojisDir.mkdirs()
        
        val actualApkPath = apkPath ?: context.applicationInfo?.sourceDir
        
        if (actualApkPath != null) {
            Log.d(TAG, "Extracting emojis from APK: $actualApkPath")
            try {
                ZipFile(File(actualApkPath)).use { zipFile ->
                    val entries = zipFile.entries().asSequence()
                        .filter { it.name.startsWith("assets/emojis/") && !it.isDirectory }
                    
                    for (entry in entries) {
                        val fileName = entry.name.substringAfter("assets/emojis/")
                        Log.d(TAG, "Copying: $fileName")
                        zipFile.getInputStream(entry).use { input ->
                            File(emojisDir, fileName).outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                    }
                }
                Log.d(TAG, "Copy from APK completed")
                return
            } catch (e: Exception) {
                Log.e(TAG, "Failed to copy emojis from APK", e)
            }
        }
        
        try {
            val pluginContext = context.createPackageContext(
                "com.kingzcheung.kime.plugin.emoji",
                Context.CONTEXT_INCLUDE_CODE or Context.CONTEXT_IGNORE_SECURITY
            )
            val assetManager = pluginContext.assets
            Log.d(TAG, "Plugin AssetManager: $assetManager")
            
            val emojiFiles = assetManager.list("emojis") ?: emptyArray()
            Log.d(TAG, "Found ${emojiFiles.size} emoji files in assets")
            
            for (fileName in emojiFiles) {
                Log.d(TAG, "Copying: $fileName")
                assetManager.open("emojis/$fileName").use { input ->
                    File(emojisDir, fileName).outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
            Log.d(TAG, "Copy completed")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy emojis from assets", e)
            throw e
        }
    }
    
    override suspend fun process(input: ExtensionInput): ExtensionResult {
        val searchText = input.text ?: ""
        
        val filtered = if (searchText.isEmpty()) {
            emojiList
        } else {
            emojiList.filter { emoji ->
                emoji.displayText.contains(searchText) || 
                emoji.insertText.contains(searchText)
            }
        }
        
        val result = filtered.take(input.topK)
        
        return ExtensionResult.Emojis(result)
    }
    
    override fun release() {
        emojiList = emptyList()
    }
    
    override fun hasSettings(): Boolean = true
    
    override fun createSettingsIntent(context: Context): Intent {
        val intent = Intent()
        intent.setClassName(
            "com.kingzcheung.kime.plugin.emoji",
            "com.kingzcheung.kime.plugin.emoji.PluginSettingsActivity"
        )
        return intent
    }
}