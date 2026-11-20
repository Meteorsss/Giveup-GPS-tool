package io.github.meteorsss.gpstoolnew

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.*
import kotlin.math.*

@Composable fun ToolsScreen(
    fix: Fix?, fresh: Boolean, heading: Double?, trueHeading: Double?, reliable: Boolean,
    compassAvailable: Boolean, places: List<Place>, hasPermission: Boolean, locationEnabled: Boolean,
    notificationsEnabled: Boolean, batteryExempt: Boolean,
    onSave: () -> Unit, onManualSave: (Double, Double) -> Unit, onDelete: (Place) -> Unit,
    onExportPlaces: () -> Unit, onAppSettings: () -> Unit, onLocationSettings: () -> Unit,
    onBatterySettings: () -> Unit, onAutostartSettings: () -> Unit,
    onMap: (Double, Double, String) -> Unit
) {
    var page by rememberSaveable { mutableStateOf("") }
    var targetId by rememberSaveable { mutableStateOf<Long?>(null) }
    var delete by remember { mutableStateOf<Place?>(null) }
    val target = places.find { it.id == targetId }
    BackHandler(page.isNotEmpty()) { page = if (page == "target") "places" else "" }
    LazyColumn(Modifier.fillMaxSize().testTag("tools-screen"), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (page.isNotEmpty()) item { TextButton(onClick = { page = if (page == "target") "places" else "" }) { Text(if (page == "target") "‹ 收藏位置" else "‹ 全部工具") } }
        when (page) {
            "" -> {
                item { SectionTitle("随身工具", "少一点复杂，多一点实用") }
                item { ToolTile("指南针", "磁北方向 · 传感器状态") { page = "compass" } }
                item { ToolTile("收藏位置 / 返回目标", "停车点、出发点与常用地点") { page = "places" } }
                item { ToolTile("坐标工具", "输入 WGS84 坐标 · 计算直线距离") { page = "coordinates" } }
                item { ToolTile("定位与后台诊断", "权限、通知、电池和 HyperOS 设置") { page = "diagnostics" } }
                item { Panel { Text("你的数据，只在手机里", fontWeight = FontWeight.SemiBold); Caption("无账号、无统计服务、无网络权限。轨迹和收藏可主动导出；卸载会清除本地数据。") } }
            }
            "compass" -> {
                item { SectionTitle("指南针", "请平放手机，远离磁铁和金属物体") }
                item { Panel { DirectionDial(heading, "北 N"); Text(Geo.decimal(heading, 0) + "°", fontSize = 44.sp, modifier = Modifier.align(Alignment.CenterHorizontally)); Caption("上方刻度固定为磁北；箭头表示手机顶部朝向。") } }
                item { Panel {
                    Text(if (!compassAvailable) "未检测到旋转向量传感器" else if (reliable) "传感器状态正常" else "方向精度偏低", color = if (reliable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                    Caption("必要时在开阔处缓慢做 8 字移动校准。传感器报告正常不代表没有磁场干扰。")
                    if (fresh && trueHeading != null) Text("估算真北方位：" + Geo.decimal(trueHeading, 0) + "°")
                } }
            }
            "places" -> {
                item { SectionTitle("收藏位置", places.size.toString() + " 个地点 · WGS84") }
                item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onSave, enabled = fresh) { Text("保存当前位置") }
                    OutlinedButton(onClick = onExportPlaces, enabled = places.isNotEmpty()) { Text("导出 GeoJSON") }
                } }
                if (places.isEmpty()) item { Panel { Text("记住一个值得返回的地方"); Caption("可保存当前位置，或在坐标工具中手动添加。") } }
                items(places, key = { it.id }) { place ->
                    Panel {
                        Text(place.name, style = MaterialTheme.typography.titleMedium)
                        if (place.note.isNotBlank()) Text(place.note, style = MaterialTheme.typography.bodySmall)
                        Caption(Geo.decimal(place.latitude, 6) + ", " + Geo.decimal(place.longitude, 6))
                        if (fresh && fix != null) Text("直线距离 " + Geo.distanceText(Geo.distance(fix.latitude, fix.longitude, place.latitude, place.longitude)))
                        Row {
                            TextButton(onClick = { targetId = place.id; page = "target" }) { Text("目标方向") }
                            TextButton(onClick = { onMap(place.latitude, place.longitude, place.name) }) { Text("外部地图") }
                            TextButton(onClick = { delete = place }) { Text("删除") }
                        }
                    }
                }
                item { Caption("GeoJSON 是收藏点的通用导出文件；本版暂不支持导入恢复。") }
            }
            "target" -> {
                item { SectionTitle(target?.name ?: "目标已删除", "直线指向，不是道路导航") }
                if (target != null && fresh && fix != null) {
                    val bearing = Geo.bearing(fix.latitude, fix.longitude, target.latitude, target.longitude)
                    val distance = Geo.distance(fix.latitude, fix.longitude, target.latitude, target.longitude)
                    item { Panel {
                        DirectionDial(if (trueHeading != null) (bearing - trueHeading + 360) % 360 else null, "手机前方")
                        Text(Geo.distanceText(distance), fontSize = 40.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
                        Caption("目标真北方位 " + Geo.decimal(bearing, 0) + "°。箭头以手机当前朝向为参照。")
                        if (trueHeading == null || !reliable) Text("方向传感器不可用或精度偏低，请勿仅依赖箭头。", color = MaterialTheme.colorScheme.error)
                        if (distance < max(10.0, fix.accuracy ?: 0.0)) Text("已接近目标 · 请结合周围环境确认", color = MaterialTheme.colorScheme.primary)
                    } }
                } else item { Panel { Text("等待有效的当前位置"); Caption("定位过期时不会继续显示可能误导的方向和距离。") } }
                item { Panel { Text("注意通行安全"); Caption("直线可能穿越建筑、水域或道路。它不是安全路线规划，也不能替代正式户外导航装备。") } }
            }
            "coordinates" -> item { CoordinateTool(fix.takeIf { fresh }, onManualSave) }
            "diagnostics" -> {
                item { SectionTitle("定位与后台诊断", Build.MANUFACTURER + " " + Build.MODEL + " · Android " + Build.VERSION.RELEASE + " / API " + Build.VERSION.SDK_INT) }
                item { Panel {
                    DiagnosticRow("精确位置权限", hasPermission, "已允许", "未允许")
                    DiagnosticRow("系统卫星定位", locationEnabled, "已开启", "未开启")
                    DiagnosticRow("定位数据", fresh, "最近 10 秒内更新", "暂无或已过期")
                    DiagnosticRow("通知显示", notificationsEnabled, "已允许", "未允许")
                    DiagnosticRow("系统电池优化豁免", batteryExempt, "已豁免", "未豁免")
                    Caption("电池优化豁免不是记录成功的必要条件，也不能代表 HyperOS 的全部后台策略。")
                } }
                item { Panel {
                    Text("系统设置入口", fontWeight = FontWeight.SemiBold)
                    OutlinedButton(onClick = onAppSettings, modifier = Modifier.fillMaxWidth()) { Text("应用权限与通知") }
                    OutlinedButton(onClick = onLocationSettings, modifier = Modifier.fillMaxWidth()) { Text("定位开关") }
                    OutlinedButton(onClick = onBatterySettings, modifier = Modifier.fillMaxWidth()) { Text("系统电池优化设置") }
                    OutlinedButton(onClick = onAutostartSettings, modifier = Modifier.fillMaxWidth()) { Text("HyperOS 后台自启动") }
                } }
                item { Panel {
                    Text("红米 K70 实机检查", fontWeight = FontWeight.SemiBold)
                    Caption("1. 位置权限选择精确位置。\n2. 允许轨迹记录通知。\n3. 若熄屏后中断，在应用设置中检查省电策略，可尝试设为无限制，并检查后台自启动。\n4. 从 APP 内主动开始记录，再熄屏测试 10 分钟。\n5. 不要在记录时强制停止应用。")
                    Caption("入口会随系统版本变化；无法直接打开时会回退到应用详情。前台服务也无法保证永不被系统终止。")
                } }
            }
        }
    }
    delete?.let { place -> AlertDialog(onDismissRequest = { delete = null }, title = { Text("删除收藏位置？") }, text = { Text(place.name + " 将被永久删除。") },
        confirmButton = { TextButton(onClick = { onDelete(place); delete = null }) { Text("删除") } },
        dismissButton = { TextButton(onClick = { delete = null }) { Text("取消") } }) }
}
@Composable private fun ToolTile(title: String, subtitle: String, onClick: () -> Unit) {
    Panel(Modifier.clickable(onClick = onClick)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(title, fontWeight = FontWeight.SemiBold); Text("›") }
        Caption(subtitle)
    }
}
@Composable private fun DiagnosticRow(label: String, good: Boolean, yes: String, no: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(if (good) yes else no, style = MaterialTheme.typography.bodySmall, color = if (good) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
@Composable private fun CoordinateTool(fix: Fix?, onSave: (Double, Double) -> Unit) {
    var lat by rememberSaveable { mutableStateOf("") }
    var lon by rememberSaveable { mutableStateOf("") }
    val latitude = lat.trim().toDoubleOrNull()
    val longitude = lon.trim().toDoubleOrNull()
    val valid = latitude != null && longitude != null && Geo.valid(latitude, longitude)
    Panel {
        SectionTitle("坐标工具", "仅接受 WGS84 十进制度数；不是 GCJ-02 或 BD-09")
        OutlinedTextField(value = lat, onValueChange = { lat = it.take(32) }, label = { Text("纬度 · -90 到 90") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text), modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = lon, onValueChange = { lon = it.take(32) }, label = { Text("经度 · -180 到 180") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text), modifier = Modifier.fillMaxWidth())
        if (!valid && (lat.isNotBlank() || lon.isNotBlank())) Caption("请输入有效坐标，西经和南纬使用负数。")
        if (valid && fix != null) {
            Text("距当前位置 " + Geo.distanceText(Geo.distance(fix.latitude, fix.longitude, latitude!!, longitude!!)))
            Text("真北方位 " + Geo.decimal(Geo.bearing(fix.latitude, fix.longitude, latitude, longitude), 0) + "°")
        }
        Button(onClick = { if (valid) onSave(latitude!!, longitude!!) }, enabled = valid) { Text("保存为收藏位置") }
        Caption("此版本不提供坐标系转换，避免误把地图坐标当作原始 GNSS 坐标。")
    }
}
@Composable fun DirectionDial(angle: Double?, topLabel: String) {
    val primary = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(topLabel, color = primary, style = MaterialTheme.typography.labelMedium)
        Canvas(Modifier.fillMaxWidth().aspectRatio(1.15f).semantics { contentDescription = if (angle == null) "方向不可用" else "方向角 " + Geo.decimal(angle, 0) + " 度" }) {
            val radius = size.minDimension * .43f
            for (i in 0 until 72) {
                val radians = Math.toRadians(i * 5.0)
                val length = if (i % 6 == 0) 12.dp.toPx() else 5.dp.toPx()
                drawLine(muted.copy(alpha = if (i % 6 == 0) .8f else .35f),
                    Offset(center.x + sin(radians).toFloat() * (radius - length), center.y - cos(radians).toFloat() * (radius - length)),
                    Offset(center.x + sin(radians).toFloat() * radius, center.y - cos(radians).toFloat() * radius), 1.5.dp.toPx())
            }
            if (angle != null) rotate(angle.toFloat()) {
                val path = Path().apply {
                    moveTo(center.x, center.y - radius * .75f)
                    lineTo(center.x - 18.dp.toPx(), center.y + radius * .4f)
                    lineTo(center.x, center.y + radius * .2f)
                    lineTo(center.x + 18.dp.toPx(), center.y + radius * .4f)
                    close()
                }
                drawPath(path, primary)
            } else drawCircle(muted.copy(alpha = .3f), 5.dp.toPx())
        }
    }
}
