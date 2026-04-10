package com.kingzcheung.kime.plugin.emoji

import com.kingzcheung.kime.plugin.api.KimeExtension
import com.kingzcheung.kime.plugin.api.KimeExtensionFactory

class EmojiPluginFactory : KimeExtensionFactory {
    
    override fun createExtensions(): List<KimeExtension> {
        return listOf(
            EmojiStickerPlugin()
        )
    }
}