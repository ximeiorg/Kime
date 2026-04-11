package com.kingzcheung.kime.plugin.api

interface PluginFactory {
    fun createPredictionPlugin(): PredictionPlugin?
    fun createEmojiPlugin(): EmojiPlugin?
    fun createSpeechPlugin(): SpeechPlugin?
}