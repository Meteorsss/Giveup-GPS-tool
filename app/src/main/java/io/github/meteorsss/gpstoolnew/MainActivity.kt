package io.github.meteorsss.gpstoolnew

import android.Manifest
import android.app.NotificationManager
import android.content.*
import android.net.Uri
import android.os.*
import android.provider.Settings as AndroidSettings
import androidx.activity.ComponentActivity
import androidx.activity.compose.*
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.*

class MainActivity : ComponentActivity() {
    private val app get() = application as ToolboxApp
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { ToolboxRoot(app, this) }
    }
    override fun onStart() {
        super.onStart()
        app.engine.setVisible(true)
        app.compass.rotation = display?.rotation ?: 0
        app.compass.setActive(true)
    }
    override fun onResume() { super.onResume(); app.engine.refresh(); app.compass.rotation = display?.rotation ?: 0 }
    override fun onStop() { app.engine.setVisible(false); app.compass.setActive(false); super.onStop() }
    fun open(intent: Intent) {
        try { startActivity(intent) } catch (_: ActivityNotFoundException) { app.message.value = "没有可处理此操作的应用。" }
        catch (_: SecurityException) { app.message.value = "系统不允许打开此页面，请手动进入设置。" }
    }
    fun appSettings() = open(Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + packageName)))
    fun autostartSettings() {
        try { startActivity(Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"))) }
        catch (_: Exception) { appSettings() }
    }
    fun map(lat: Double, lon: Double, label: String) {
        open(Intent(Intent.ACTION_VIEW, Uri.parse("geo:" + lat + "," + lon + "?q=" + lat + "," + lon + "(" + Uri.encode(label) + ")")))
    }
    fun command(action: String) {
        try {
            val intent = Intent(this, RecordingService::class.java).setAction(action)
            if (action == RecordingService.START) ContextCompat.startForegroundService(this, intent) else startService(intent)
        } catch (_: Exception) { app.message.value = "无法启动记录服务，请检查定位权限和后台限制。" }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun ToolboxRoot(app: ToolboxApp, activity: MainActivity) {
    val theme by app.settings.theme.collectAsStateWithLifecycle("dark")
    val fix by app.engine.fix.collectAsStateWithLifecycle()
    val satellites by app.engine.satellites.collectAsStateWithLifecycle()
    val status by app.engine.status.collectAsStateWithLifecycle()
    val enabled by app.engine.enabled.collectAsStateWithLifecycle()
    val recording by app.recording.collectAsStateWithLifecycle()
    val heading by app.compass.heading.collectAsStateWithLifecycle()
    val reliable by app.compass.reliable.collectAsStateWithLifecycle()
    val tracks by remember { app.db.dao().tracks() }.collectAsStateWithLifecycle(emptyList())
    val places by remember { app.db.dao().places() }.collectAsStateWithLifecycle(emptyList())
    val message by app.message.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtimeNanos()) }
    var pendingStart by rememberSaveable { mutableStateOf(false) }
    var saveFix by remember { mutableStateOf<Fix?>(null) }
    var exportTrackId by rememberSaveable { mutableLongStateOf(0) }
    var exportKind by rememberSaveable { mutableStateOf("gpx") }
    var busyExport by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { while (true) { now = SystemClock.elapsedRealtimeNanos(); delay(1000) } }
    LaunchedEffect(message) { message?.let { app.message.value = null; snackbar.showSnackbar(it) } }
    val permission = app.engine.hasPermission()
    val fresh = permission && enabled && fix?.let { now - it.elapsedNanos in 0L..10_000_000_000L } == true
    val notifications = activity.getSystemService(NotificationManager::class.java).areNotificationsEnabled()
    val batteryExempt = activity.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(activity.packageName)
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        app.engine.refresh()
        if (pendingStart && app.engine.hasPermission() && app.engine.enabled.value) activity.command(RecordingService.START)
        else if (!app.engine.hasPermission()) app.message.value = "需要精确位置权限。若已拒绝多次，请在应用设置中开启。"
        pendingStart = false
    }
    fun requestPermission() {
        val list = mutableListOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= 33) list += Manifest.permission.POST_NOTIFICATIONS
        permissions.launch(list.toTypedArray())
    }
    fun startRecording() {
        tab = 2
        if (recording != null) return
        if (!enabled) { app.message.value = "请先开启系统定位。"; activity.open(Intent(AndroidSettings.ACTION_LOCATION_SOURCE_SETTINGS)); return }
        if (!permission || (!notifications && Build.VERSION.SDK_INT >= 33)) { pendingStart = true; requestPermission() }
        else activity.command(RecordingService.START)
    }
    fun saveCurrent() {
        if (fresh && fix != null && fix?.mock == false) saveFix = fix
        else app.message.value = "请等待有效的真实定位后再保存。"
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) {
            val kind = exportKind; val id = exportTrackId; val placesSnapshot = places
            busyExport = true
            app.scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        val text = if (kind == "geojson") Export.places(placesSnapshot) else {
                            val track = app.db.dao().getTrack(id) ?: error("轨迹不存在")
                            val points = app.db.dao().getPoints(id)
                            if (kind == "gpx") Export.gpx(track, points) else Export.csv(points)
                        }
                        val output = activity.contentResolver.openOutputStream(uri, "wt") ?: error("无法打开目标文件")
                        output.bufferedWriter(Charsets.UTF_8).use { it.write(text) }
                    }
                    app.message.value = "导出完成。文件包含精确位置，请谨慎分享。"
                } catch (_: Exception) { app.message.value = "导出失败，请检查目标位置和存储空间。" }
                finally { busyExport = false }
            }
        }
    }
    fun exportTrack(track: Track, kind: String) {
        if (busyExport) { app.message.value = "正在导出，请稍候。"; return }
        exportTrackId = track.id; exportKind = kind
        exportLauncher.launch("GPS-track-" + track.id + "." + kind)
    }
    fun coordinateText(): String {
        val current = fix ?: return "暂无位置"
        return (if (fresh) "当前位置" else "最后一次位置（已过期）") + " · WGS84\n纬度 " + Geo.decimal(current.latitude, 6) + "\n经度 " + Geo.decimal(current.longitude, 6) + "\n时间 " + dateText(current.time) + "\n估计水平精度 " + Geo.decimal(current.accuracy, 0) + " m"
    }
    ToolboxTheme(theme) {
        Scaffold(
            topBar = { TopAppBar(title = { Column { Text("GPS 工具箱", fontWeight = FontWeight.SemiBold, fontSize = 21.sp); Text("GPS TOOL NEW", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 2.sp) } },
                actions = { TextButton(onClick = { showSettings = true }, modifier = Modifier.testTag("open-settings")) { Text("设置") } }) },
            bottomBar = { NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                listOf("总览", "卫星", "轨迹", "工具").forEachIndexed { index, label ->
                    NavigationBarItem(selected = tab == index, onClick = { tab = index }, icon = { NavGlyph(index, tab == index) }, label = { Text(label) }, modifier = Modifier.testTag("nav-" + index))
                }
            } },
            snackbarHost = { SnackbarHost(snackbar) }
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when (tab) {
                    0 -> HomeScreen(fix, satellites, status, fresh, permission, enabled, recording,
                        onPermission = { requestPermission() }, onSettings = { activity.open(Intent(AndroidSettings.ACTION_LOCATION_SOURCE_SETTINGS)) },
                        onRecord = { startRecording() }, onSave = { saveCurrent() },
                        onCopy = {
                            activity.getSystemService(android.content.ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("WGS84 坐标", coordinateText()))
                            app.message.value = "坐标已复制。"
                        }, onShare = { activity.open(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, coordinateText()), "分享位置")) },
                        onMap = { fix?.let { activity.map(it.latitude, it.longitude, "WGS84 位置") } })
                    1 -> SatellitesScreen(satellites, status)
                    2 -> TracksScreen(app.db.dao(), tracks, recording, onStart = { startRecording() },
                        onCommand = activity::command, onExport = { track, kind -> exportTrack(track, kind) },
                        onDelete = { track -> app.scope.launch { app.db.dao().deleteTrack(track.id) } })
                    3 -> ToolsScreen(fix, fresh, heading, if (fresh) app.compass.trueHeading(fix) else null, reliable,
                        app.compass.available, places, permission, enabled, notifications, batteryExempt,
                        onSave = { saveCurrent() }, onManualSave = { lat, lon -> saveFix = Fix(lat, lon, System.currentTimeMillis(), SystemClock.elapsedRealtimeNanos(), null, null, null, null) },
                        onDelete = { place -> app.scope.launch { app.db.dao().deletePlace(place) } },
                        onExportPlaces = { if (!busyExport) { exportKind = "geojson"; exportLauncher.launch("GPS-places.geojson") } },
                        onAppSettings = activity::appSettings, onLocationSettings = { activity.open(Intent(AndroidSettings.ACTION_LOCATION_SOURCE_SETTINGS)) },
                        onBatterySettings = { activity.open(Intent(AndroidSettings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) },
                        onAutostartSettings = activity::autostartSettings, onMap = activity::map)
                }
                if (busyExport) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        }
        saveFix?.let { captured -> SavePlaceDialog(captured, onDismiss = { saveFix = null }) { name, note ->
            saveFix = null
            app.scope.launch {
                try {
                    app.db.dao().insertPlace(Place(name = name, note = note, latitude = captured.latitude, longitude = captured.longitude,
                        altitude = captured.altitude, accuracy = captured.accuracy, createdAt = captured.time))
                    app.message.value = "位置已保存，可在工具 → 收藏位置查看。"
                } catch (_: Exception) { app.message.value = "保存失败，请检查剩余存储空间。" }
            }
        } }
        if (showSettings) AlertDialog(onDismissRequest = { showSettings = false }, title = { Text("外观与关于") }, text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("显示模式", fontWeight = FontWeight.Medium)
                listOf("dark" to "深色", "light" to "浅色 / 户外", "system" to "跟随系统").forEach { (value, label) ->
                    FilterChip(selected = theme == value, onClick = { app.scope.launch { app.settings.setTheme(value) } }, label = { Text(label) })
                }
                Text("GPS Tool New " + BuildConfig.VERSION_NAME)
                Caption("面向红米 K70 / Android 16 的个人工具。核心功能无需联网；无网络权限、无账号、无统计 SDK。")
                Caption("系统云备份和设备迁移已显式排除应用数据。卸载前请导出轨迹与收藏。")
                Caption("外部地图由其他应用处理坐标，坐标系解释和隐私政策以目标应用为准。")
                Caption("不保证测量级精度或后台永不中断；不要把方向箭头作为安全路线。")
            }
        }, confirmButton = { TextButton(onClick = { showSettings = false }) { Text("完成") } })
    }
}
@Composable private fun SavePlaceDialog(fix: Fix, onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var name by remember { mutableStateOf("位置 " + dateText(fix.time).substring(5, 16)) }
    var note by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("保存位置") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Caption(Geo.decimal(fix.latitude, 6) + ", " + Geo.decimal(fix.longitude, 6) + " · WGS84")
            Caption(if (fix.accuracy == null) "手动输入的坐标" else "采集时间 " + dateText(fix.time))
            OutlinedTextField(value = name, onValueChange = { name = it.take(80) }, label = { Text("名称") }, singleLine = true)
            OutlinedTextField(value = note, onValueChange = { note = it.take(500) }, label = { Text("备注（可选）") }, minLines = 2, maxLines = 4)
        }
    }, confirmButton = { TextButton(onClick = { onSave(name.trim(), note.trim()) }, enabled = name.isNotBlank()) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}
