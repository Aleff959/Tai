package com.tai.app.core

object TaiCoreConfig {
    fun versionName(): String = "0.0.2"
    fun compNumber(): Int = 17
    fun codename(): String = "Quicksilver"
    fun fullLabel(): String = "${versionName()} | Comp. ${compNumber()} - ${codename()}"
}
