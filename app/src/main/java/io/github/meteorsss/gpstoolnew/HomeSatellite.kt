package io.github.meteorsss.gpstoolnew

import android.graphics.Paint
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import kotlin.math.*

@Composable fun HomeScreen(
    fix: Fix?, satellites: List<Satellite>, status: String, fresh: Boolean,
    hasPermission: Boolean, enabled: Boolean, recording: Track?, onPermission: () -> Unit,
    onSettings: () -> Unit, onRecord: () -> Unit, onSave: () -> Unit,
    onCopy: () -> Unit, onShare: () -> Unit, onMap: () -> Unit
) {
    val color = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    LazyColumn(Modifier.fillMaxSize().testTag("home-screen"), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column { Caption("PERSONAL / GNSS"); Text("此刻，知道自己在哪里", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 6.dp)) }
                Surface(color = if (fresh) color.copy(alpha = .12f) else MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(30.dp)) {
                    Text(if (fresh) "● 实时" else "○ 等待", Modifier.padding(horizontal = 12.dp, vertical = 7.dp), color = if (fresh) color else muted, fontSize = 12.sp)
                }
            }
        }
        item {
            Surface(shape = RoundedCornerShape(30.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("实时速度", style = MaterialTheme.typography.titleSmall, color = muted)
                    Box(Modifier.fillMaxWidth().height(205.dp), contentAlignment = Alignment.Center) {
                        Canvas(Modifier.fillMaxSize()) {
                            val diameter = min(size.width, size.height * 1.34f)
                            val top = Offset((size.width - diameter) / 2, 12.dp.toPx())
                            val arcSize = Size(diameter, diameter)
                            drawArc(muted.copy(alpha = .12f), 160f, 220f, false, top, arcSize, style = Stroke(4.dp.toPx(), cap = StrokeCap.Round))
                            val ratio = if (fresh) ((fix?.speed ?: 0.0) * 3.6 / 120).coerceIn(.025, 1.0).toFloat() else .025f
                            drawArc(color, 160f, 220f * ratio, false, top, arcSize, style = Stroke(4.dp.toPx(), cap = StrokeCap.Round))
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = 35.dp)) {
                            Text(Geo.decimal(if (fresh) fix?.speed?.times(3.6) else null),
                                fontSize = 78.sp, lineHeight = 88.sp, letterSpacing = (-3).sp, fontWeight = FontWeight.Light,
                                modifier = Modifier.testTag("hero-speed").semantics { contentDescription = "当前速度，单位千米每小时" })
                            Text("km/h", color = color, fontSize = 16.sp)
                        }
                    }
                    Text(if (!fresh && fix != null) "定位数据已过期 · 不显示实时速度" else status,
                        color = if (fresh) color else muted, style = MaterialTheme.typography.bodySmall)
                    if (fix?.mock == true) Text("当前为模拟位置，不作为真实测量", color = Amber, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        if (!hasPermission || !enabled) item {
            Panel {
                Text(if (!hasPermission) "允许精确定位，开始使用" else "请打开系统定位", fontWeight = FontWeight.Medium)
                Caption("只在查看页面或主动记录轨迹时使用定位；不上传位置。")
                Button(onClick = if (!hasPermission) onPermission else onSettings) { Text(if (!hasPermission) "授予定位权限" else "打开定位设置") }
            }
        }
        item {
            Panel {
                Row(Modifier.fillMaxWidth()) {
                    Metric("估计水平精度", Geo.decimal(if (fresh) fix?.accuracy else null, 0), "m", Modifier.weight(1f))
                    Metric("GNSS 高度", Geo.decimal(if (fresh) fix?.altitude else null, 0), "m", Modifier.weight(1f))
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(Modifier.fillMaxWidth()) {
                    Metric("观测卫星", satellites.distinctBy { it.system to it.id }.size.toString(), modifier = Modifier.weight(1f))
                    Metric("参与定位", satellites.filter { it.used }.distinctBy { it.system to it.id }.size.toString(), modifier = Modifier.weight(1f))
                }
                Caption("精度为系统估计值；高度以 WGS84 椭球为基准，不等于海拔。")
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onRecord, enabled = hasPermission && enabled || recording != null, modifier = Modifier.weight(1f).height(52.dp)) { Text(if (recording == null) "开始记录" else "查看当前记录") }
                OutlinedButton(onClick = onSave, enabled = fresh, modifier = Modifier.weight(1f).height(52.dp)) { Text("保存位置") }
            }
        }
        if (recording != null) item {
            Panel(Modifier.clickable(onClick = onRecord)) {
                Text(if (recording.state == "paused") "轨迹已暂停" else "正在记录轨迹", color = color)
                Text(Geo.duration(recording.durationMs) + "   /   " + Geo.distanceText(recording.distance), style = MaterialTheme.typography.titleLarge)
            }
        }
        item {
            Panel {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("位置坐标", fontWeight = FontWeight.SemiBold)
                    Caption(if (fresh) "WGS84" else "WGS84 · 上次位置")
                }
                Text("纬度   " + Geo.decimal(fix?.latitude, 6) + "°", style = MaterialTheme.typography.bodyLarge)
                Text("经度   " + Geo.decimal(fix?.longitude, 6) + "°", style = MaterialTheme.typography.bodyLarge)
                Row {
                    TextButton(onClick = onCopy, enabled = fix != null) { Text("复制") }
                    TextButton(onClick = onShare, enabled = fix != null) { Text("分享") }
                    TextButton(onClick = onMap, enabled = fix != null) { Text("外部地图") }
                }
                if (!fresh && fix != null) Caption("以上是最后一次位置，不能当作当前位置。")
            }
        }
        item { Caption("离线优先 · 无账号 · 无广告 · 不上传位置") }
    }
}

private fun satelliteColor(system: String) = when (system) {
    "北斗" -> Mint; "GPS" -> Sky; "Galileo" -> Color(0xFFC3A7FF); "GLONASS" -> Amber; else -> Color(0xFFB0BEC5)
}
private fun shortName(s: Satellite) = when (s.system) { "北斗" -> "B"; "GPS" -> "G"; "Galileo" -> "E"; "GLONASS" -> "R"; "QZSS" -> "J"; "NavIC" -> "I"; else -> "S" } + s.id
@Composable fun SatellitesScreen(satellites: List<Satellite>, status: String) {
    var filter by remember { mutableStateOf("全部") }
    var selected by remember { mutableStateOf<Satellite?>(null) }
    val systems = listOf("全部") + satellites.map { it.system }.distinct().sorted()
    val shown = satellites.filter { filter == "全部" || it.system == filter }.sortedWith(compareByDescending<Satellite> { it.used }.thenByDescending { it.cn0 })
    LazyColumn(Modifier.fillMaxSize().testTag("satellite-screen"), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { SectionTitle("卫星观测", "天空图与信号 · 来自系统实时 GNSS 数据") }
        item { Panel { SkyPlot(shown); Caption("实心：参与定位   空心：已观测未参与\n外圈为地平线，中心为天顶；天空图固定北向上。") } }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                systems.forEach { name -> FilterChip(selected = filter == name, onClick = { filter = name }, label = { Text(name) }) }
            }
        }
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("信号强度", fontWeight = FontWeight.SemiBold); Caption("C/N₀ · dB-Hz") } }
        if (shown.isEmpty()) item { Panel { Text("暂无卫星数据"); Caption(status + "。请到室外开阔处等待；室内无法搜星不一定是故障。") } }
        items(shown) { satellite ->
            Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth().clickable { selected = satellite }) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(satellite.system + " " + satellite.id, fontWeight = FontWeight.Medium)
                        Text(Geo.decimal(satellite.cn0.toDouble(), 1), color = MaterialTheme.colorScheme.primary)
                    }
                    LinearProgressIndicator(progress = { (satellite.cn0 / 60).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(5.dp), color = satelliteColor(satellite.system), trackColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                    Caption((if (satellite.used) "参与定位" else "未参与定位") + "  ·  " + (satellite.frequencyMhz?.let { Geo.decimal(it.toDouble(), 2) + " MHz" } ?: "频率未提供"))
                }
            }
        }
        item { Caption("频率等字段取决于手机芯片与系统，缺失时不作推测。") }
    }
    selected?.let { s -> AlertDialog(onDismissRequest = { selected = null }, title = { Text(s.system + " · " + s.id) },
        text = { Text("载噪比：" + Geo.decimal(s.cn0.toDouble()) + " dB-Hz\n方位角：" + Geo.decimal(s.azimuth.toDouble()) + "°\n仰角：" + Geo.decimal(s.elevation.toDouble()) + "°\n频率：" + (s.frequencyMhz?.let { Geo.decimal(it.toDouble(), 2) + " MHz" } ?: "系统未提供") + "\n" + if (s.used) "参与当前定位" else "未参与当前定位") },
        confirmButton = { TextButton(onClick = { selected = null }) { Text("关闭") } }) }
}
@Composable fun SkyPlot(satellites: List<Satellite>) {
    val grid = MaterialTheme.colorScheme.outlineVariant
    val label = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(Modifier.fillMaxWidth().aspectRatio(1f).semantics { contentDescription = "卫星天空图，详细数据见下方列表" }) {
        val radius = size.minDimension * .41f
        for (fraction in listOf(1f, .666f, .333f)) drawCircle(grid, radius * fraction, style = Stroke(1.dp.toPx()))
        drawLine(grid, Offset(center.x - radius, center.y), Offset(center.x + radius, center.y), 1.dp.toPx())
        drawLine(grid, Offset(center.x, center.y - radius), Offset(center.x, center.y + radius), 1.dp.toPx())
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = label.toArgb(); textSize = 12.sp.toPx(); textAlign = Paint.Align.CENTER }
        drawContext.canvas.nativeCanvas.apply {
            drawText("北 N", center.x, center.y - radius - 10.dp.toPx(), paint)
            drawText("南 S", center.x, center.y + radius + 20.dp.toPx(), paint)
            drawText("西", center.x - radius - 12.dp.toPx(), center.y + 4.dp.toPx(), paint)
            drawText("东", center.x + radius + 12.dp.toPx(), center.y + 4.dp.toPx(), paint)
        }
        satellites.groupBy { it.system to it.id }.values.map { rows -> rows.maxBy { it.cn0 } }.forEach { s ->
            val angle = Math.toRadians(s.azimuth.toDouble())
            val r = radius * (1 - s.elevation.coerceIn(0f, 90f) / 90)
            val point = Offset(center.x + sin(angle).toFloat() * r, center.y - cos(angle).toFloat() * r)
            val color = satelliteColor(s.system)
            if (s.used) drawCircle(color, 10.dp.toPx(), point) else drawCircle(color, 10.dp.toPx(), point, style = Stroke(1.5.dp.toPx()))
            paint.color = label.toArgb(); paint.textSize = 10.sp.toPx()
            drawContext.canvas.nativeCanvas.drawText(shortName(s), point.x, point.y + 23.dp.toPx(), paint)
        }
    }
}
