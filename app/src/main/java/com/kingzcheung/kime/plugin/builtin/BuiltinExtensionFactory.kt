package com.kingzcheung.kime.plugin.builtin

import com.kingzcheung.kime.plugin.api.PluginFactory
import com.kingzcheung.kime.plugin.api.SpeechPlugin

class BuiltinExtensionFactory : PluginFactory {
    
    override fun createPredictionPlugin(): com.kingzcheung.kime.plugin.api.PredictionPlugin? = null
    
    override fun createEmojiPlugin(): com.kingzcheung.kime.plugin.api.EmojiPlugin? = null
    
    override fun createSpeechPlugin(): SpeechPlugin? = SpeechToTextExtension()
}