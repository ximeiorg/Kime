package com.kingzcheung.xime.settings

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SettingsPreferencesTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        context.getSharedPreferences("kime_settings", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun `current schema uses default then persists value`() {
        assertEquals("wubi86", SettingsPreferences.getCurrentSchema(context))

        SettingsPreferences.setCurrentSchema(context, "wubi98")

        assertEquals("wubi98", SettingsPreferences.getCurrentSchema(context))
    }

    @Test
    fun `dark mode persists integer setting`() {
        assertEquals(2, SettingsPreferences.getDarkMode(context))

        SettingsPreferences.setDarkMode(context, 0)

        assertEquals(0, SettingsPreferences.getDarkMode(context))
    }

    @Test
    fun `dark mode values`() {
        SettingsPreferences.setDarkMode(context, 0)
        assertEquals(0, SettingsPreferences.getDarkMode(context))
        
        SettingsPreferences.setDarkMode(context, 1)
        assertEquals(1, SettingsPreferences.getDarkMode(context))
        
        SettingsPreferences.setDarkMode(context, 2)
        assertEquals(2, SettingsPreferences.getDarkMode(context))
    }

    @Test
    fun `sound and vibration defaults and updates work`() {
        assertTrue(SettingsPreferences.isSoundEnabled(context))
        assertEquals(50, SettingsPreferences.getSoundVolume(context))
        assertTrue(SettingsPreferences.isVibrationEnabled(context))
        assertEquals(50, SettingsPreferences.getVibrationIntensity(context))

        SettingsPreferences.setSoundEnabled(context, false)
        SettingsPreferences.setSoundVolume(context, 72)
        SettingsPreferences.setVibrationEnabled(context, false)
        SettingsPreferences.setVibrationIntensity(context, 66)

        assertFalse(SettingsPreferences.isSoundEnabled(context))
        assertEquals(72, SettingsPreferences.getSoundVolume(context))
        assertFalse(SettingsPreferences.isVibrationEnabled(context))
        assertEquals(66, SettingsPreferences.getVibrationIntensity(context))
    }

    @Test
    fun `sound volume boundary values`() {
        SettingsPreferences.setSoundVolume(context, 0)
        assertEquals(0, SettingsPreferences.getSoundVolume(context))
        
        SettingsPreferences.setSoundVolume(context, 100)
        assertEquals(100, SettingsPreferences.getSoundVolume(context))
    }

    @Test
    fun `vibration intensity boundary values`() {
        SettingsPreferences.setVibrationIntensity(context, 0)
        assertEquals(0, SettingsPreferences.getVibrationIntensity(context))
        
        SettingsPreferences.setVibrationIntensity(context, 100)
        assertEquals(100, SettingsPreferences.getVibrationIntensity(context))
    }

    @Test
    fun `keyboard theme and bottom buttons persist`() {
        assertEquals("lavender_purple", SettingsPreferences.getKeyboardTheme(context))
        assertFalse(SettingsPreferences.showBottomButtons(context))

        SettingsPreferences.setKeyboardTheme(context, "sunset")
        SettingsPreferences.setShowBottomButtons(context, true)

        assertEquals("sunset", SettingsPreferences.getKeyboardTheme(context))
        assertTrue(SettingsPreferences.showBottomButtons(context))
    }

    @Test
    fun `multiple theme changes`() {
        SettingsPreferences.setKeyboardTheme(context, "ocean_blue")
        assertEquals("ocean_blue", SettingsPreferences.getKeyboardTheme(context))
        
        SettingsPreferences.setKeyboardTheme(context, "lavender_purple")
        assertEquals("lavender_purple", SettingsPreferences.getKeyboardTheme(context))
        
        SettingsPreferences.setKeyboardTheme(context, "sunset")
        assertEquals("sunset", SettingsPreferences.getKeyboardTheme(context))
    }

    @Test
    fun `plugin enabled state is isolated by plugin id`() {
        val predictionPlugin = "prediction-onnx"
        val emojiPlugin = "meme-bunny"

        assertFalse(SettingsPreferences.isPluginEnabled(context, predictionPlugin))
        assertFalse(SettingsPreferences.isPluginEnabled(context, emojiPlugin))

        SettingsPreferences.setPluginEnabled(context, predictionPlugin, true)

        assertTrue(SettingsPreferences.isPluginEnabled(context, predictionPlugin))
        assertFalse(SettingsPreferences.isPluginEnabled(context, emojiPlugin))

        SettingsPreferences.setPluginEnabled(context, predictionPlugin, false)
        assertFalse(SettingsPreferences.isPluginEnabled(context, predictionPlugin))
    }
    
    @Test
    fun `multiple plugins can be enabled independently`() {
        val plugins = listOf("plugin1", "plugin2", "plugin3")
        
        for (plugin in plugins) {
            SettingsPreferences.setPluginEnabled(context, plugin, true)
        }
        
        for (plugin in plugins) {
            assertTrue("Plugin $plugin should be enabled", 
                SettingsPreferences.isPluginEnabled(context, plugin))
        }
        
        SettingsPreferences.setPluginEnabled(context, "plugin2", false)
        
        assertTrue("plugin1 should still be enabled", 
            SettingsPreferences.isPluginEnabled(context, "plugin1"))
        assertFalse("plugin2 should be disabled", 
            SettingsPreferences.isPluginEnabled(context, "plugin2"))
        assertTrue("plugin3 should still be enabled", 
            SettingsPreferences.isPluginEnabled(context, "plugin3"))
    }
    
    @Test
    fun `smart prediction settings`() {
        assertFalse("Smart prediction should be disabled by default", 
            SettingsPreferences.isSmartPredictionEnabled(context))
        
        SettingsPreferences.setSmartPredictionEnabled(context, true)
        assertTrue("Smart prediction should be enabled", 
            SettingsPreferences.isSmartPredictionEnabled(context))
        
        SettingsPreferences.setSmartPredictionEnabled(context, false)
        assertFalse("Smart prediction should be disabled", 
            SettingsPreferences.isSmartPredictionEnabled(context))
    }
    
    @Test
    fun `prediction model repo settings`() {
        val defaultRepo = "https://www.modelscope.cn/models/bikeand/predictive-text-small"
        assertEquals(defaultRepo, SettingsPreferences.getPredictionModelRepo(context))
        
        val customRepo = "https://custom.model.repo/model"
        SettingsPreferences.setPredictionModelRepo(context, customRepo)
        assertEquals(customRepo, SettingsPreferences.getPredictionModelRepo(context))
    }
    
    @Test
    fun `STT settings`() {
        assertFalse("STT should be disabled by default", 
            SettingsPreferences.isSttEnabled(context))
        
        SettingsPreferences.setSttEnabled(context, true)
        assertTrue("STT should be enabled", 
            SettingsPreferences.isSttEnabled(context))
    }
    
    @Test
    fun `STT provider settings`() {
        assertEquals("funasr", SettingsPreferences.getSttProvider(context))
        
        SettingsPreferences.setSttProvider(context, "whisper")
        assertEquals("whisper", SettingsPreferences.getSttProvider(context))
    }
    
    @Test
    fun `FunASR API key settings`() {
        assertEquals("", SettingsPreferences.getFunAsrApiKey(context))
        
        val apiKey = "test-api-key-12345"
        SettingsPreferences.setFunAsrApiKey(context, apiKey)
        assertEquals(apiKey, SettingsPreferences.getFunAsrApiKey(context))
    }
    
    @Test
    fun `schema switching sequence`() {
        val schemas = listOf("wubi86", "wubi98", "wubi_pinyin")
        
        for (schema in schemas) {
            SettingsPreferences.setCurrentSchema(context, schema)
            assertEquals("Current schema should be $schema", 
                schema, SettingsPreferences.getCurrentSchema(context))
        }
    }
    
    @Test
    fun `bottom buttons toggle`() {
        SettingsPreferences.setShowBottomButtons(context, true)
        assertTrue(SettingsPreferences.showBottomButtons(context))
        
        SettingsPreferences.setShowBottomButtons(context, false)
        assertFalse(SettingsPreferences.showBottomButtons(context))
        
        SettingsPreferences.setShowBottomButtons(context, true)
        assertTrue(SettingsPreferences.showBottomButtons(context))
    }
    
    @Test
    fun `clearing preferences resets to defaults`() {
        SettingsPreferences.setCurrentSchema(context, "custom_schema")
        SettingsPreferences.setDarkMode(context, 2)
        SettingsPreferences.setSoundEnabled(context, false)
        
        context.getSharedPreferences("kime_settings", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        
        assertEquals("wubi86", SettingsPreferences.getCurrentSchema(context))
        assertEquals(0, SettingsPreferences.getDarkMode(context))
        assertTrue(SettingsPreferences.isSoundEnabled(context))
    }
}
