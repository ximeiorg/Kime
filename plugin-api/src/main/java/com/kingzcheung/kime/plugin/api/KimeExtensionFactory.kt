package com.kingzcheung.kime.plugin.api

interface KimeExtensionFactory {
    fun createExtensions(): List<KimeExtension>
}