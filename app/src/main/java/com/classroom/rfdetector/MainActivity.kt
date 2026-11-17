package com.classroom.rfdetector

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.classroom.rfdetector.ui.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(10, 17, 24)
        window.navigationBarColor = android.graphics.Color.rgb(10, 17, 24)
        setContent { AppRfTheme { App() } }
    }
}

/** 화면은 셋뿐이다: 감독관 화면, 한 전파의 위치 찾기, 안내. */
@Composable
fun App() {
    val context = LocalContext.current
    val vm = remember(context) { ViewModelProvider(context as ComponentActivity, object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = AppViewModel(context.applicationContext) as T
    })[AppViewModel::class.java] }
    val lifecycle = (context as ComponentActivity).lifecycle
    var guide by rememberSaveable { mutableStateOf(false) }
    var findKey by rememberSaveable { mutableStateOf<String?>(null) }
    /** 권한 창이 닫힌 뒤 시작할 감시. null이면 없음, true면 AI 안경 바로 탐지. */
    var pendingStart by remember { mutableStateOf<Boolean?>(null) }
    val needed = remember { vm.requiredPermissions() }
    // 화면이 꺼진 동안 안경 신호 알림을 띄우는 데 쓴다. 거절해도 감시와 진동은 동작하므로 필수는 아니다.
    val asked = remember { needed + if (android.os.Build.VERSION.SDK_INT >= 33) listOf("android.permission.POST_NOTIFICATIONS") else emptyList() }
    fun hasPermissions() = needed.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
    var allGranted by remember { mutableStateOf(hasPermissions()) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        allGranted = hasPermissions()
        val quick = pendingStart
        if (quick != null && allGranted) { if (quick) vm.startQuick() else vm.startScan() }
        pendingStart = null
    }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) { allGranted = hasPermissions(); vm.checkExamMode() }
            if (event == Lifecycle.Event.ON_START) vm.resumeFromBackground()
            if (event == Lifecycle.Event.ON_STOP) vm.pauseForBackground()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    // 알림창을 내려 비행기 모드를 바꾸면 화면이 다시 시작되지 않으므로, 창이 다시 앞에 올 때도 확인한다.
    val focused = androidx.compose.ui.platform.LocalWindowInfo.current.isWindowFocused
    LaunchedEffect(focused) { vm.checkExamMode() }
    val scanning by vm.scanning.collectAsState()
    val simMode by vm.simMode.collectAsState()
    DisposableEffect(scanning) {
        val window = (context as? Activity)?.window
        if (scanning) window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
    val openSettings = { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:${context.packageName}"))) }
    fun begin(quick: Boolean) {
        if (simMode || hasPermissions()) { if (quick) vm.startQuick() else vm.startScan() }
        else { pendingStart = quick; launcher.launch(asked.toTypedArray()) }
    }
    val start = { begin(quick = false) }
    val startQuick = { begin(quick = true) }
    val closeFind = { vm.findTarget.value = null; vm.setGeiger(false); findKey = null }
    BackHandler(enabled = guide || findKey != null) { if (findKey != null) closeFind() else guide = false }

    Surface(Modifier.fillMaxSize(), color = RfColors.bg) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                val sub = guide || findKey != null
                if (sub) TextButton(onClick = { if (findKey != null) closeFind() else guide = false }, modifier = Modifier.semantics { contentDescription = "돌아가기" }) { Text("‹", style = MaterialTheme.typography.headlineLarge) }
                Text(if (findKey != null) "위치 찾기" else if (guide) "안내" else "KoriyoSCAN", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                if (!sub) TextButton(onClick = { guide = true }) { Text("안내") }
            }
            Box(Modifier.weight(1f)) {
                val key = findKey
                when {
                    key != null -> FindScreen(vm, key, onBack = { findKey = null })
                    guide -> GuideScreen()
                    else -> GuardScreen(vm, !allGranted, start, startQuick, { launcher.launch(asked.toTypedArray()) }, openSettings, onFind = { findKey = it })
                }
            }
        }
    }
}
