package com.kingzcheung.kime.plugin.kaomoji

import com.kingzcheung.kime.plugin.api.EmojiPlugin
import com.kingzcheung.kime.plugin.api.PluginFactory
import com.kingzcheung.kime.plugin.api.PredictionPlugin
import com.kingzcheung.kime.plugin.api.SpeechPlugin

class KaomojiPluginFactory : PluginFactory {
    
    override fun createPredictionPlugin(): PredictionPlugin? = null
    
    override fun createEmojiPlugin(): EmojiPlugin {
        return KaomojiPlugin()
    }
    
    override fun createSpeechPlugin(): SpeechPlugin? = null
}