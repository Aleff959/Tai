package com.tai.app.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tai.app.ota.OtaStatus
import com.tai.app.ui.screens.components.CatCompanionWidget
import com.tai.app.ui.screens.components.LogsSheetDialog
import com.tai.app.ui.screens.components.PhotoAudioChoiceDialog
import com.tai.app.ui.theme.*
import com.tai.app.ui.viewmodel.DownloadState
import com.tai.app.ui.viewmodel.DownloaderViewModel
import com.tai.app.util.AppLogger
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun MainScreen(viewModel: DownloaderViewModel) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    val urlInput by viewModel.urlInput.collectAsStateWithLifecycle()
    val downloadState by viewModel.downloadState.collectAsStateWithLifecycle()
    val pendingPhotoWithAudio by viewModel.pendingPhotoWithAudio.collectAsStateWithLifecycle()
    val otaStatus by viewModel.otaManager.status.collectAsStateWithLifecycle()
    val logs by AppLogger.logs.collectAsStateWithLifecycle()

    var showOtaDialog by remember { mutableStateOf(false) }
    var showLogsDialog by remember { mutableStateOf(false) }
    var promptOpenGalleryForState by remember { mutableStateOf<DownloadState.Success?>(null) }

    LaunchedEffect(otaStatus) {
        if (otaStatus is OtaStatus.UpdateAvailable) {
            showOtaDialog = true
        }
    }

    LaunchedEffect(downloadState) {
        if (downloadState is DownloadState.Success) {
            promptOpenGalleryForState = downloadState as DownloadState.Success
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PureBlack)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(28.dp))

            // Cabeçalho
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Taí!",
                            color = TextPrimary,
                            fontSize = 34.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = (-0.5).sp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Rounded.Bolt,
                            contentDescription = null,
                            tint = NeonGreen,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Surface(
                        color = DarkSurfaceVariant,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Text(
                            text = "0.0.2 | Comp. 9 - Indra",
                            color = NeonCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                // Ações do cabeçalho
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(
                        onClick = { showLogsDialog = true },
                        modifier = Modifier
                            .size(44.dp)
                            .background(DarkSurfaceVariant, CircleShape)
                            .border(1.dp, CardBorder, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Terminal,
                            contentDescription = "Logs e Diagnóstico",
                            tint = NeonCyan,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    val hasUpdate = otaStatus is OtaStatus.UpdateAvailable
                    val updatePulseAlpha by rememberInfiniteTransition(label = "otaPulse").animateFloat(
                        initialValue = 0.4f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(800, easing = LinearEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "otaAlpha"
                    )

                    IconButton(
                        onClick = {
                            scope.launch {
                                viewModel.otaManager.checkForUpdates()
                                showOtaDialog = true
                            }
                        },
                        modifier = Modifier
                            .size(44.dp)
                            .background(DarkSurfaceVariant, CircleShape)
                            .border(
                                width = if (hasUpdate) 1.5.dp else 1.dp,
                                color = if (hasUpdate) NeonGreen.copy(alpha = updatePulseAlpha) else CardBorder,
                                shape = CircleShape
                            )
                    ) {
                        if (hasUpdate) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.SystemUpdate,
                                    contentDescription = "Atualização Disponível",
                                    tint = NeonGreen,
                                    modifier = Modifier.size(22.dp)
                                )
                                // Ponto de notificação pulsante no topo do ícone
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .background(NeonGreen.copy(alpha = updatePulseAlpha), CircleShape)
                                        .border(1.5.dp, PureBlack, CircleShape)
                                        .align(Alignment.TopEnd)
                                )
                            }
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.CloudDownload,
                                contentDescription = "Checar Atualizações",
                                tint = TextSecondary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(30.dp))

            // Badges das 4 redes suportadas - com ícones oficiais
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PlatformBadge(name = "TikTok", iconRes = com.tai.app.R.drawable.ic_tiktok, tint = Color(0xFFFE2C55), modifier = Modifier.weight(1f))
                PlatformBadge(name = "Reels", iconRes = com.tai.app.R.drawable.ic_instagram, tint = Color(0xFFE1306C), modifier = Modifier.weight(1f))
                PlatformBadge(name = "X", iconRes = com.tai.app.R.drawable.ic_x_twitter, tint = Color.White, modifier = Modifier.weight(1f))
                PlatformBadge(name = "YouTube", iconRes = com.tai.app.R.drawable.ic_youtube, tint = Color(0xFFFF0000), modifier = Modifier.weight(1f))
            }

            Spacer(modifier = Modifier.height(30.dp))

            // Campo de Input com botão de colar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkSurface, RoundedCornerShape(16.dp))
                    .border(1.dp, CardBorder, RoundedCornerShape(16.dp))
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Link,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(24.dp)
                    )

                    Spacer(modifier = Modifier.width(10.dp))

                    OutlinedTextField(
                        value = urlInput,
                        onValueChange = { viewModel.onUrlChanged(it) },
                        placeholder = {
                            Text(
                                text = "Cole o link do post aqui...",
                                color = TextMuted,
                                fontSize = 14.sp
                            )
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            cursorColor = NeonGreen,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )

                    if (urlInput.isNotEmpty()) {
                        IconButton(onClick = { viewModel.clearInput() }) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Limpar",
                                tint = TextMuted,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Button(
                        onClick = {
                            val clipText = clipboardManager.getText()?.text
                            if (!clipText.isNullOrEmpty()) {
                                AppLogger.action("UI", "Colou link da área de transferência")
                                viewModel.onUrlChanged(clipText)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DarkSurfaceVariant,
                            contentColor = NeonCyan
                        ),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.height(38.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ContentPaste,
                            contentDescription = null,
                            tint = NeonCyan,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Colar",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Botão Baixar Mídia
            val isDownloading = downloadState is DownloadState.Loading
            Button(
                onClick = { viewModel.startDownload() },
                enabled = urlInput.isNotBlank() && !isDownloading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = NeonGreen,
                    contentColor = PureBlack,
                    disabledContainerColor = DarkSurfaceVariant,
                    disabledContentColor = TextMuted
                )
            ) {
                if (isDownloading) {
                    CircularProgressIndicator(
                        color = PureBlack,
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Baixando...",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.Download,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Baixar Mídia",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Card de Status / Progresso / Sucesso
            AnimatedVisibility(
                visible = downloadState !is DownloadState.Idle,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                when (val state = downloadState) {
                    is DownloadState.Loading -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(DarkSurface, RoundedCornerShape(16.dp))
                                .border(1.dp, CardBorder, RoundedCornerShape(16.dp))
                                .padding(20.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = state.message,
                                    color = TextPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold
                                )

                                Spacer(modifier = Modifier.height(14.dp))

                                if (state.progress >= 0) {
                                    LinearProgressIndicator(
                                        progress = { state.progress / 100f },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(8.dp)
                                            .clip(RoundedCornerShape(4.dp)),
                                        color = NeonGreen,
                                        trackColor = DarkSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "${state.progress}%",
                                        color = TextSecondary,
                                        fontSize = 12.sp
                                    )
                                } else {
                                    LinearProgressIndicator(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(6.dp)
                                            .clip(RoundedCornerShape(3.dp)),
                                        color = NeonCyan,
                                        trackColor = DarkSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    is DownloadState.Success -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(DarkSurface, RoundedCornerShape(16.dp))
                                .border(1.dp, NeonGreen.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                                .padding(20.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Rounded.CheckCircle,
                                    contentDescription = null,
                                    tint = NeonGreen,
                                    modifier = Modifier.size(44.dp)
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                Text(
                                    text = "Download Concluído!",
                                    color = TextPrimary,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                Spacer(modifier = Modifier.height(6.dp))

                                Text(
                                    text = state.message,
                                    color = TextSecondary,
                                    fontSize = 13.sp,
                                    textAlign = TextAlign.Center
                                )

                                Spacer(modifier = Modifier.height(18.dp))

                                // Atalho 1: Abrir diretamente na Galeria
                                Button(
                                    onClick = {
                                        openInGallery(context, state.file, state.uri, state.mimeType)
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = NeonGreen,
                                        contentColor = PureBlack
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth().height(48.dp)
                                ) {
                                    Icon(
                                        imageVector = if (state.mimeType.startsWith("video")) Icons.Rounded.PlayCircle else Icons.Rounded.PhotoLibrary,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                        tint = PureBlack
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Abrir na Galeria",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                // Atalhos secundários: Compartilhar e Abrir pasta
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            shareMedia(context, state.file, state.uri, state.mimeType)
                                        },
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.weight(1f).height(42.dp),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder)
                                    ) {
                                        Icon(imageVector = Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(16.dp), tint = NeonCyan)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Compartilhar", fontSize = 12.sp)
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            openDownloadsFolder(context)
                                        },
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.weight(1f).height(42.dp),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder)
                                    ) {
                                        Icon(imageVector = Icons.Rounded.Folder, contentDescription = null, modifier = Modifier.size(16.dp), tint = TextSecondary)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Ver na Pasta", fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }

                    is DownloadState.Error -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(DarkSurface, RoundedCornerShape(16.dp))
                                .border(1.dp, ErrorRed.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                                .padding(18.dp)
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Rounded.ErrorOutline,
                                        contentDescription = null,
                                        tint = ErrorRed,
                                        modifier = Modifier.size(28.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = state.message,
                                        color = TextPrimary,
                                        fontSize = 13.sp,
                                        lineHeight = 18.sp,
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                TextButton(
                                    onClick = { showLogsDialog = true },
                                    modifier = Modifier.align(Alignment.End)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.BugReport,
                                        contentDescription = null,
                                        tint = NeonCyan,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Ver Diagnóstico / Logs",
                                        color = NeonCyan,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    else -> {}
                }
            }

            Spacer(modifier = Modifier.height(40.dp))

            // Rodapé com pasta destino
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.FolderOpen,
                    contentDescription = null,
                    tint = TextMuted,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Arquivos salvos em: Download/Tai/",
                    color = TextMuted,
                    fontSize = 12.sp
                )
            }

            // Espaço inferior para não ficar atrás do gatinho
            Spacer(modifier = Modifier.height(180.dp))
        }

        // Gatinho Companion interativo fixo na parte de baixo do app
        CatCompanionWidget(
            modifier = Modifier.align(Alignment.BottomCenter)
        )

        // Diálogo para post com 1 foto e áudio
        if (pendingPhotoWithAudio != null) {
            PhotoAudioChoiceDialog(
                onChoicePhotoOnly = { viewModel.handlePhotoChoice(saveAsVideo = false) },
                onChoiceVideoWithAudio = { viewModel.handlePhotoChoice(saveAsVideo = true) },
                onDismiss = { viewModel.dismissPhotoChoice() }
            )
        }

        // Atalho que aparece após o download perguntando se quer abrir na Galeria
        promptOpenGalleryForState?.let { successState ->
            AlertDialog(
                onDismissRequest = { promptOpenGalleryForState = null },
                containerColor = PureBlack,
                shape = RoundedCornerShape(20.dp),
                icon = {
                    Icon(
                        imageVector = if (successState.mimeType.startsWith("video")) Icons.Rounded.PlayCircle else Icons.Rounded.PhotoLibrary,
                        contentDescription = null,
                        tint = NeonGreen,
                        modifier = Modifier.size(36.dp)
                    )
                },
                title = {
                    Text(
                        text = "Abrir na Galeria?",
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                },
                text = {
                    Text(
                        text = "O download foi concluído com sucesso. Deseja visualizar agora na galeria do seu celular?",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            openInGallery(context, successState.file, successState.uri, successState.mimeType)
                            promptOpenGalleryForState = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NeonGreen, contentColor = PureBlack),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Sim, abrir", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { promptOpenGalleryForState = null }) {
                        Text("Agora não", color = TextMuted)
                    }
                },
                modifier = Modifier.border(1.dp, CardBorder, RoundedCornerShape(20.dp))
            )
        }

        // Diálogo de Logs & Diagnóstico
        if (showLogsDialog) {
            LogsSheetDialog(
                logs = logs,
                onDismiss = { showLogsDialog = false }
            )
        }

        // Diálogo OTA Updates
        if (showOtaDialog) {
            OtaDialog(
                status = otaStatus,
                onDismiss = {
                    showOtaDialog = false
                    viewModel.otaManager.resetStatus()
                },
                onInstall = { apkFile ->
                    viewModel.otaManager.launchInstaller(apkFile)
                },
                onDownload = { url, name ->
                    scope.launch {
                        viewModel.otaManager.downloadAndPrepareInstall(url, name)
                    }
                }
            )
        }
    }
}

private fun openInGallery(context: Context, file: File?, mediaUri: Uri?, mimeType: String) {
    try {
        val targetUri: Uri = if (file != null && file.exists()) {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        } else if (mediaUri != null) {
            mediaUri
        } else {
            return
        }

        AppLogger.action("UI", "Abrindo na galeria: $targetUri ($mimeType)")

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(targetUri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        context.startActivity(Intent.createChooser(intent, "Visualizar com"))
    } catch (e: Exception) {
        AppLogger.e("UI", "Erro ao abrir na galeria", e)
    }
}

private fun shareMedia(context: Context, file: File?, mediaUri: Uri?, mimeType: String) {
    try {
        val targetUri: Uri = if (file != null && file.exists()) {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        } else if (mediaUri != null) {
            mediaUri
        } else {
            return
        }

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, targetUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Compartilhar mídia"))
    } catch (e: Exception) {
        AppLogger.e("UI", "Erro ao compartilhar", e)
    }
}

private fun openDownloadsFolder(context: Context) {
    try {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse("content://media/external/file"), "*/*")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (_: Exception) {}
}

@Composable
fun PlatformBadge(name: String, iconRes: Int, tint: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(42.dp)
            .background(DarkSurface, RoundedCornerShape(12.dp))
            .border(1.dp, CardBorder, RoundedCornerShape(12.dp))
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = name,
                tint = tint,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = name,
                color = TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun OtaDialog(
    status: OtaStatus,
    onDismiss: () -> Unit,
    onInstall: (java.io.File) -> Unit,
    onDownload: (String, String) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(PureBlack, shape = RoundedCornerShape(20.dp))
                .border(1.dp, CardBorder, shape = RoundedCornerShape(20.dp))
                .padding(24.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                when (status) {
                    is OtaStatus.Checking -> {
                        CircularProgressIndicator(color = NeonCyan)
                        Text(text = "Verificando atualizações...", color = TextPrimary)
                    }
                    is OtaStatus.UpToDate -> {
                        Icon(imageVector = Icons.Rounded.CheckCircle, contentDescription = null, tint = NeonGreen, modifier = Modifier.size(40.dp))
                        Text(text = "Você já está na versão mais recente!", color = TextPrimary, fontWeight = FontWeight.Bold)
                        TextButton(onClick = onDismiss) { Text("Fechar", color = TextSecondary) }
                    }
                    is OtaStatus.UpdateAvailable -> {
                        Icon(imageVector = Icons.Rounded.SystemUpdate, contentDescription = null, tint = NeonGreen, modifier = Modifier.size(40.dp))
                        Text(text = "Nova Versão Disponível!", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text(text = status.title, color = NeonCyan, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text(text = status.changelog, color = TextSecondary, fontSize = 12.sp, maxLines = 5)

                        Button(
                            onClick = { onDownload(status.downloadUrl, status.assetName) },
                            colors = ButtonDefaults.buttonColors(containerColor = NeonGreen, contentColor = PureBlack),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            Text(text = "Baixar e Atualizar", fontWeight = FontWeight.Bold)
                        }

                        TextButton(onClick = onDismiss) { Text("Depois", color = TextMuted) }
                    }
                    is OtaStatus.Downloading -> {
                        Text(text = "Baixando atualização (${status.progressPercent}%)...", color = TextPrimary)
                        LinearProgressIndicator(
                            progress = { status.progressPercent / 100f },
                            color = NeonGreen,
                            trackColor = DarkSurfaceVariant,
                            modifier = Modifier.fillMaxWidth().height(8.dp)
                        )
                    }
                    is OtaStatus.ReadyToInstall -> {
                        Icon(imageVector = Icons.Rounded.DownloadDone, contentDescription = null, tint = NeonGreen, modifier = Modifier.size(40.dp))
                        Text(text = "Download Concluído!", color = TextPrimary, fontWeight = FontWeight.Bold)
                        Button(
                            onClick = { onInstall(status.apkFile) },
                            colors = ButtonDefaults.buttonColors(containerColor = NeonGreen, contentColor = PureBlack),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            Text(text = "Instalar Agora", fontWeight = FontWeight.Bold)
                        }
                    }
                    is OtaStatus.Error -> {
                        Icon(imageVector = Icons.Rounded.ErrorOutline, contentDescription = null, tint = ErrorRed, modifier = Modifier.size(40.dp))
                        Text(text = status.message, color = TextPrimary, textAlign = TextAlign.Center)
                        TextButton(onClick = onDismiss) { Text("Fechar", color = TextSecondary) }
                    }
                    else -> {
                        TextButton(onClick = onDismiss) { Text("Fechar", color = TextSecondary) }
                    }
                }
            }
        }
    }
}
