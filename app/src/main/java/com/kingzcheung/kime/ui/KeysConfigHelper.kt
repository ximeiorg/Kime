package com.kingzcheung.kime.ui

import android.content.Context
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader

data class KeysConfig(
    val swipeUp: Map<String, String> = emptyMap(),
    val swipeDownEnglish: Map<String, String> = emptyMap(),
    val swipeDownWubi: Map<String, String> = emptyMap()
)

object KeysConfigHelper {
    private const val TAG = "KeysConfigHelper"
    private const val CONFIG_FILE = "kime.keys.yaml"
    
    private var config: KeysConfig = KeysConfig()
    
    fun loadConfig(context: Context): KeysConfig {
        try {
            val inputStream = context.assets.open(CONFIG_FILE)
            val reader = BufferedReader(InputStreamReader(inputStream))
            val content = reader.readText()
            reader.close()
            inputStream.close()
            
            config = parseYaml(content)
            Log.d(TAG, "Loaded keys config from $CONFIG_FILE: swipeUp=${config.swipeUp.size} keys")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load $CONFIG_FILE, using default config: ${e.message}")
            config = getDefaultConfig()
        }
        return config
    }
    
    fun getConfig(): KeysConfig = config
    
    fun getSwipeUpText(key: String): String? = config.swipeUp[key.lowercase()]
    
    fun getSwipeDownEnglishText(key: String): String? = config.swipeDownEnglish[key.lowercase()]
    
    fun getSwipeDownWubiText(key: String): String? = config.swipeDownWubi[key.lowercase()]
    
    private fun getDefaultConfig(): KeysConfig {
        return KeysConfig(
            swipeUp = mapOf(
                "q" to "1", "w" to "2", "e" to "3", "r" to "4", "t" to "5",
                "y" to "6", "u" to "7", "i" to "8", "o" to "9", "p" to "0",
                "a" to "!", "s" to "@", "d" to "#", "f" to "$", "g" to "%",
                "h" to "^", "j" to "&", "k" to "*", "l" to "(",
                "z" to "`", "x" to "~", "c" to "\\", "v" to "|", "b" to "_",
                "n" to "-", "m" to "+"
            ),
            swipeDownEnglish = mapOf(
                "q" to "Q", "w" to "W", "e" to "E", "r" to "R", "t" to "T",
                "y" to "Y", "u" to "U", "i" to "I", "o" to "O", "p" to "P",
                "a" to "A", "s" to "S", "d" to "D", "f" to "F", "g" to "G",
                "h" to "H", "j" to "J", "k" to "K", "l" to "L",
                "z" to "Z", "x" to "X", "c" to "C", "v" to "V", "b" to "B",
                "n" to "N", "m" to "M"
            ),
            swipeDownWubi = mapOf(
                "g" to "王 五 一 戈 七 夫 戋 廿",
                "f" to "土 士 二 干 十 寸 雨 未 四",
                "d" to "大 犬 三 羊 古 石 厂 卢 虎",
                "s" to "木 丁 西",
                "a" to "工 戈 艹 匚 七 弋",
                "h" to "目 具 上 止 卜 丨 虎 皮",
                "j" to "日 早 刂 虫 丿 Ⅱ",
                "k" to "口 川 Ⅲ",
                "l" to "田 甲 囗 四 车 力",
                "m" to "山 由 贝 冂 几 门",
                "t" to "禾 竹 丿 彳 夂 攵",
                "r" to "白 手 扌 斤 爪 爫",
                "e" to "月 彡 乃 用 家 衣 底 豕",
                "w" to "人 亻 八 登 头",
                "q" to "金 钅 勺 夕 犭 鱼 鸟",
                "y" to "言 讠 文 方 广 丶 乀",
                "u" to "立 辛 冫 六 门 疒 丷",
                "i" to "水 氵 小 癶 兴 头",
                "o" to "火 灬 业 米",
                "p" to "之 辶 冖 宀 建 道 底",
                "n" to "已 己 巳 心 忄 羽 乙 折 九",
                "b" to "子 耳 了 也 阝 框 向 上",
                "v" to "女 刀 九 臼 巛 彐",
                "c" to "又 巴 马 厶 矢 矣",
                "x" to "弓 匕 纟 母 幺"
            )
        )
    }
    
    private fun parseYaml(content: String): KeysConfig {
        val swipeUp = mutableMapOf<String, String>()
        val swipeDownEnglish = mutableMapOf<String, String>()
        val swipeDownWubi = mutableMapOf<String, String>()
        
        var currentSection = ""
        
        for (line in content.lines()) {
            val trimmed = line.trim()
            
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue
            
            when {
                trimmed == "swipe_up:" -> currentSection = "swipe_up"
                trimmed == "swipe_down_english:" -> currentSection = "swipe_down_english"
                trimmed == "swipe_down_wubi:" -> currentSection = "swipe_down_wubi"
                currentSection.isNotEmpty() && trimmed.contains(":") -> {
                    val parts = trimmed.split(":")
                    if (parts.size == 2) {
                        val key = parts[0].trim()
                        var value = parts[1].trim()
                        
                        // Remove surrounding quotes
                        if (value.startsWith("\"") && value.endsWith("\"")) {
                            value = value.substring(1, value.length - 1)
                        }
                        
                        // Process escape sequences
                        value = value
                            .replace("\\\\", "\\")
                            .replace("\\n", "\n")
                            .replace("\\t", "\t")
                            .replace("\\r", "\r")
                        
                        when (currentSection) {
                            "swipe_up" -> swipeUp[key] = value
                            "swipe_down_english" -> swipeDownEnglish[key] = value
                            "swipe_down_wubi" -> swipeDownWubi[key] = value
                        }
                    }
                }
            }
        }
        
        return KeysConfig(
            swipeUp = swipeUp,
            swipeDownEnglish = swipeDownEnglish,
            swipeDownWubi = swipeDownWubi
        )
    }
}