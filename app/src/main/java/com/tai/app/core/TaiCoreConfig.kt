package com.tai.app.core

object TaiCoreConfig {
    fun versionName(): String = "0.0.2"
    fun compNumber(): Int = 16
    fun codename(): String = "Pierce"
    fun fullLabel(): String = "${versionName()} | Comp. ${compNumber()} - ${codename()}"
}
