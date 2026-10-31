package com.tai.app.core

import android.app.Activity
import androidx.compose.runtime.Composable

interface TaiCoreEntry {
    fun getVersionName(): String
    fun getCompilationNumber(): Int
    fun getCodename(): String

    @Composable
    fun Content(
        activity: Activity,
        sharedUrl: String?,
        onConsumeSharedUrl: () -> Unit,
        onRequestCoreReload: () -> Unit
    )
}
