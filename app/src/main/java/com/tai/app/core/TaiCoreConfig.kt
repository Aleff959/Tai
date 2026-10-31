package com.tai.app.core

object TaiCoreConfig {
    fun versionName(): String = "0.0.2"
    fun compNumber(): Int = 15
    fun codename(): String = "Ororo"
    fun fullLabel(): String = "${versionName()} | Comp. ${compNumber()} - ${codename()}"
}
