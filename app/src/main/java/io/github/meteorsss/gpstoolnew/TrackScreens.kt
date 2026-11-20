package io.github.meteorsss.gpstoolnew

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.*

fun dateText(time: Long): String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(Date(time))
fun stateText(state: String) = when (state) { "recording" -> "记录中"; "paused" -> "已暂停"; "interrupted" -> "异常中断 · 已保存已有数据"; else -> "已完成" }
@Composable fun TracksScreen(dao: ToolboxDao, tracks: List<Track>, recording: Track?, onStart: () -> Unit,
    onCommand: (String) -> Unit, onExport: (Track, String) -> Unit, onDelete: (Track) -> Unit) {
    var selectedId by rememberSaveable { mutableStateOf<Long?>(null) }
    var confirmStop by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<Track?>(null) }
    val selected = tracks.find { it.id == selectedId }
    BackHandler(selectedId != null) { selectedId = null }
    if (selected != null) {
        val points by remember(selected.id) { dao.points(selected.id) }.collectAsStateWithLifecycle(emptyList())
        LazyColumn(Modifier.fillMaxSize().testTag("track-detail"), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item { TextButton(onClick = { selectedId = null }) { Text("‹ 全部轨迹") } }
            item { SectionTitle(selected.name, dateText(selected.startedAt) + " · " + stateText(selected.state)) }
            item { Panel { TrackPlot(points); Caption("轨迹预览 · 无地图底图 · 北向上\n仅连接连续合格点；暂停、信号中断和跳点处断开。") } }
            item {
                Panel {
                    Row { Metric("累计距离", Geo.distanceText(selected.distance), modifier = Modifier.weight(1f)); Metric("记录时间", Geo.duration(selected.durationMs), modifier = Modifier.weight(1f)) }
                    HorizontalDivider()
                    Row { Metric("最高速度", Geo.decimal(selected.maxSpeed * 3.6), "km/h", Modifier.weight(1f)); Metric("平均速度", Geo.decimal(if (selected.durationMs > 0) selected.distance / selected.durationMs * 3600 else null), "km/h", Modifier.weight(1f)) }
                    Caption("合格点 " + selected.acceptedCount + " / 原始点 " + selected.count + "。平均速度包含静止时间，不包含主动暂停。距离经过基础滤波，仅供参考。")
                }
            }
            item {
                Panel {
                    Text("导出记录", fontWeight = FontWeight.SemiBold)
                    Caption("GPX：合格点及分段。CSV：原始点和质量标记。\n导出包含精确位置，请谨慎分享。")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { onExport(selected, "gpx") }, enabled = selected.acceptedCount > 0) { Text("导出 GPX") }
                        OutlinedButton(onClick = { onExport(selected, "csv") }, enabled = selected.count > 0) { Text("导出 CSV") }
                    }
                }
            }
            if (recording?.id != selected.id) item { TextButton(onClick = { confirmDelete = selected }) { Text("删除这条轨迹", color = MaterialTheme.colorScheme.error) } }
        }
    } else {
        LazyColumn(Modifier.fillMaxSize().testTag("track-screen"), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item { SectionTitle("记录你的路", "逐点保存 · 不上传 · 随时导出") }
            item {
                Panel {
                    if (recording == null) {
                        Text("每段旅程，都值得留下", style = MaterialTheme.typography.titleMedium)
                        Caption("开始后通过前台服务持续记录；通知栏可暂停或结束。首次使用请实测熄屏记录。")
                        Button(onClick = onStart, modifier = Modifier.fillMaxWidth().testTag("start-recording")) { Text("开始记录轨迹") }
                    } else {
                        Text(stateText(recording.state), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                        Text(Geo.duration(recording.durationMs), fontSize = 42.sp, fontWeight = FontWeight.Light)
                        Text(Geo.distanceText(recording.distance) + "   ·   " + recording.count + " 个原始点")
                        if (recording.count == 0) Caption("等待首次定位；暂未收到可记录的点。")
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(onClick = { onCommand(if (recording.state == "paused") RecordingService.RESUME else RecordingService.PAUSE) }) { Text(if (recording.state == "paused") "继续记录" else "暂停") }
                            OutlinedButton(onClick = { confirmStop = true }, modifier = Modifier.testTag("stop-recording")) { Text("结束并保存") }
                        }
                    }
                }
            }
            item { Text("历史轨迹", style = MaterialTheme.typography.titleMedium) }
            if (tracks.isEmpty()) item { Panel { Text("还没有轨迹"); Caption("开始一次短途记录，先确认定位和后台运行正常。") } }
            items(tracks, key = { it.id }) { track ->
                Panel(Modifier.clickable { selectedId = track.id }) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(track.name, fontWeight = FontWeight.SemiBold)
                        Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(Geo.distanceText(track.distance) + "    " + Geo.duration(track.durationMs))
                    Caption(stateText(track.state) + " · " + track.count + " 个点")
                }
            }
            item { Caption("强制停止、系统回收或重启可能中断记录。下次打开会标记为异常中断，不伪造后续轨迹。") }
        }
    }
    if (confirmStop) AlertDialog(onDismissRequest = { confirmStop = false }, title = { Text("结束本次记录？") },
        text = { Text("已经收到的数据会保留，可在历史轨迹中查看和导出。") },
        confirmButton = { TextButton(onClick = { confirmStop = false; onCommand(RecordingService.STOP) }, modifier = Modifier.testTag("confirm-stop")) { Text("结束并保存") } },
        dismissButton = { TextButton(onClick = { confirmStop = false }) { Text("继续记录") } })
    confirmDelete?.let { track -> AlertDialog(onDismissRequest = { confirmDelete = null }, title = { Text("永久删除轨迹？") },
        text = { Text("此操作无法撤销。建议先导出需要保留的数据。") },
        confirmButton = { TextButton(onClick = { onDelete(track); confirmDelete = null; selectedId = null }) { Text("删除") } },
        dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("取消") } }) }
}
@Composable fun TrackPlot(points: List<TrackPoint>) {
    val line = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    val valid = remember(points) { points.filter { it.accepted } }
    if (valid.isEmpty()) {
        Box(Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) { Caption("暂无合格轨迹点") }
        return
    }
    val projected = remember(valid) {
        val lat0 = valid.map { it.latitude }.average()
        val lon0 = valid.first().longitude
        valid.map { p ->
            val deltaLon = ((p.longitude - lon0 + 540.0) % 360.0) - 180.0
            Triple(deltaLon * cos(Math.toRadians(lat0)), -p.latitude, p.segment)
        }
    }
    Canvas(Modifier.fillMaxWidth().height(260.dp).semantics { contentDescription = "无底图轨迹预览，北向上" }) {
        for (i in 1..4) {
            drawLine(grid.copy(alpha = .5f), Offset(size.width * i / 5, 0f), Offset(size.width * i / 5, size.height), 1.dp.toPx())
            drawLine(grid.copy(alpha = .5f), Offset(0f, size.height * i / 5), Offset(size.width, size.height * i / 5), 1.dp.toPx())
        }
        val minX = projected.minOf { it.first }; val maxX = projected.maxOf { it.first }
        val minY = projected.minOf { it.second }; val maxY = projected.maxOf { it.second }
        val scale = min((size.width - 40.dp.toPx()) / max(maxX - minX, .00005), (size.height - 40.dp.toPx()) / max(maxY - minY, .00005))
        fun position(p: Triple<Double, Double, Int>) = Offset((center.x + (p.first - (minX + maxX) / 2) * scale).toFloat(), (center.y + (p.second - (minY + maxY) / 2) * scale).toFloat())
        var last: Triple<Double, Double, Int>? = null
        projected.forEach { p ->
            last?.let { if (it.third == p.third) drawLine(line, position(it), position(p), 3.dp.toPx(), StrokeCap.Round) }
            last = p
        }
        drawCircle(Sky, 6.dp.toPx(), position(projected.first()))
        drawCircle(Amber, 6.dp.toPx(), position(projected.last()))
    }
}
