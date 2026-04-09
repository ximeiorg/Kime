package com.kingzcheung.kime.plugin.builtin

import com.kingzcheung.kime.plugin.api.KimeExtension
import com.kingzcheung.kime.plugin.api.KimeExtensionFactory

class BuiltinExtensionFactory : KimeExtensionFactory {
    
    override fun createExtensions(): List<KimeExtension> {
        return listOf(
            SpeechToTextExtension()
        )
    }
}