package com.tai.app.core

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import com.tai.app.ui.screens.MainScreen
import com.tai.app.ui.theme.TaiTheme
import com.tai.app.ui.viewmodel.DownloaderViewModel

class TaiCoreEntryImpl : TaiCoreEntry {
    override fun getVersionName(): String = TaiCoreConfig.versionName()
    override fun getCompilationNumber(): Int = TaiCoreConfig.compNumber()
    override fun getCodename(): String = TaiCoreConfig.codename()

    @Composable
    override fun Content(
        activity: Activity,
        sharedUrl: String?,
        onConsumeSharedUrl: () -> Unit,
        onRequestCoreReload: () -> Unit
    ) {
        val viewModel = remember(activity, getCompilationNumber()) {
            DownloaderViewModel(activity.application)
        }

        LaunchedEffect(sharedUrl) {
            if (!sharedUrl.isNullOrBlank()) {
                viewModel.onUrlChanged(sharedUrl)
                viewModel.startDownload(sharedUrl)
                onConsumeSharedUrl()
            }
        }

        TaiTheme {
            MainScreen(
                viewModel = viewModel,
                onHotReloadCore = onRequestCoreReload
            )
        }
    }
}
