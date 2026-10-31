package com.tai.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.tai.app.core.CoreHostManager
import com.tai.app.core.TaiCoreEntryImpl
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    private var pendingSharedUrl by mutableStateOf<String?>(null)

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        permissions.forEach { (perm, granted) ->
            Log.i("TaiHost", "Permissão $perm -> Concedida: $granted")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        checkPermissions()
        handleIncomingIntent(intent)

        setContent {
            var coreReloadTrigger by remember { mutableIntStateOf(0) }
            var isSwappingCore by remember {
                mutableStateOf(CoreHostManager.hasPendingUpdate(applicationContext))
            }

            if (isSwappingCore) {
                TwoStageHotSwapScreen(
                    onSwapFinished = {
                        viewModelStore.clear()
                        coreReloadTrigger++
                        isSwappingCore = false
                    }
                )
            } else {
                val activeCore = remember(coreReloadTrigger) {
                    CoreHostManager.loadActiveCore(applicationContext) {
                        TaiCoreEntryImpl()
                    }
                }

                activeCore.Content(
                    activity = this@MainActivity,
                    sharedUrl = pendingSharedUrl,
                    onConsumeSharedUrl = { pendingSharedUrl = null },
                    onRequestCoreReload = { isSwappingCore = true }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return
        if (Intent.ACTION_SEND == intent.action && "text/plain" == intent.type) {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (!sharedText.isNullOrBlank()) {
                pendingSharedUrl = sharedText
            }
        }
    }

    private fun checkPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.READ_MEDIA_IMAGES)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.READ_MEDIA_VIDEO)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.READ_MEDIA_AUDIO)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                    permissionsToRequest.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            requestPermissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }
}

@Composable
private fun TwoStageHotSwapScreen(onSwapFinished: () -> Unit) {
    var step by remember { mutableIntStateOf(0) }
    val targetProgress = when (step) {
        0 -> 0.35f
        1 -> 0.75f
        else -> 1.0f
    }
    val animatedProgress by animateFloatAsState(
        targetValue = targetProgress,
        animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
        label = "swapProgress"
    )

    val statusText = when (step) {
        0 -> "Verificando integridade BootGuard..."
        1 -> "Trocando módulo Filho (Zero-Install)..."
        else -> "Recarregando Taí Core..."
    }

    LaunchedEffect(Unit) {
        delay(320)
        step = 1
        delay(360)
        step = 2
        delay(280)
        onSwapFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF000000)),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            color = Color(0xFF0D111A),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth(0.84f)
                .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(24.dp))
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Bolt,
                    contentDescription = null,
                    tint = Color(0xFF00E676),
                    modifier = Modifier.size(48.dp)
                )

                Text(
                    text = "Taí! Modular Updater",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold
                )

                Text(
                    text = statusText,
                    color = Color(0xFF38BDF8),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )

                LinearProgressIndicator(
                    progress = { animatedProgress },
                    color = Color(0xFF00E676),
                    trackColor = Color(0xFF1E293B),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                )

                Text(
                    text = "Arquitetura Two-Stage • Sem Play Protect",
                    color = Color(0xFF64748B),
                    fontSize = 11.sp
                )
            }
        }
    }
}
