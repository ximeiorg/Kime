package com.kingzcheung.kime.plugin.prediction

import com.kingzcheung.kime.plugin.api.KimeExtension
import com.kingzcheung.kime.plugin.api.KimeExtensionFactory

class PredictionPluginFactory : KimeExtensionFactory {
    
    override fun createExtensions(): List<KimeExtension> {
        return listOf(
            OnnxPredictionPlugin()
        )
    }
}