package com.kingzcheung.kime.plugin.prediction

import com.kingzcheung.kime.plugin.api.EmojiPlugin
import com.kingzcheung.kime.plugin.api.PluginFactory
import com.kingzcheung.kime.plugin.api.PredictionPlugin
import com.kingzcheung.kime.plugin.api.SpeechPlugin

class PredictionPluginFactory : PluginFactory {
    
    override fun createPredictionPlugin(): PredictionPlugin {
        return OnnxPredictionPlugin()
    }
    
    override fun createEmojiPlugin(): EmojiPlugin? = null
    
    override fun createSpeechPlugin(): SpeechPlugin? = null
}