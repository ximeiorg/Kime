package com.kingzcheung.kime.plugin.kaomoji

import com.kingzcheung.kime.plugin.api.KimeExtension
import com.kingzcheung.kime.plugin.api.KimeExtensionFactory

class KaomojiPluginFactory : KimeExtensionFactory {
    
    override fun createExtensions(): List<KimeExtension> {
        return listOf(KaomojiPlugin())
    }
}