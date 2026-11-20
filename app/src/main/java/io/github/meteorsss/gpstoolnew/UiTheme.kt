package io.github.meteorsss.gpstoolnew

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*

val Mint = Color(0xFF6DE5CB)
val Amber = Color(0xFFF0BC76)
val Sky = Color(0xFF80BFFF)
private val Dark = darkColorScheme(primary = Mint, onPrimary = Color(0xFF073B31),
    primaryContainer = Color(0xFF183C38), onPrimaryContainer = Color(0xFFB3F5E5),
    background = Color(0xFF0C1219), surface = Color(0xFF0C1219), surfaceContainer = Color(0xFF14202B),
    surfaceContainerHigh = Color(0xFF1B2A36), onBackground = Color(0xFFEAF1F6), onSurface = Color(0xFFEAF1F6),
    onSurfaceVariant = Color(0xFF92A5B6), outlineVariant = Color(0xFF293B49), secondary = Sky)
private val Light = lightColorScheme(primary = Color(0xFF006B58), onPrimary = Color.White,
    primaryContainer = Color(0xFFD5F5EA), onPrimaryContainer = Color(0xFF064E3E),
    background = Color(0xFFF2F5F8), surface = Color(0xFFF2F5F8), surfaceContainer = Color.White,
    surfaceContainerHigh = Color(0xFFE4EBF1), onSurfaceVariant = Color(0xFF536575), secondary = Color(0xFF245F96))
@Composable fun ToolboxTheme(mode: String, content: @Composable () -> Unit) {
    val dark = mode == "dark" || (mode == "system" && isSystemInDarkTheme())
    MaterialTheme(colorScheme = if (dark) Dark else Light,
        shapes = Shapes(medium = RoundedCornerShape(20.dp), large = RoundedCornerShape(26.dp)), content = content)
}
@Composable fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}
@Composable fun Caption(text: String) { Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
@Composable fun SectionTitle(title: String, subtitle: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        subtitle?.let { Caption(it) }
    }
}
@Composable fun Metric(label: String, value: String, unit: String = "", modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Caption(label)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(value, fontSize = 26.sp, fontWeight = FontWeight.Medium)
            Text(unit, modifier = Modifier.padding(top = 10.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
@Composable fun NavGlyph(index: Int, selected: Boolean) {
    val color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(Modifier.size(23.dp)) {
        val w = size.width; val stroke = 1.8.dp.toPx()
        when (index) {
            0 -> { drawArc(color, 140f, 260f, false, topLeft = Offset(w * .1f, w * .1f), size = androidx.compose.ui.geometry.Size(w * .8f, w * .8f), style = Stroke(stroke)); drawLine(color, center, Offset(w * .73f, w * .28f), stroke); drawCircle(color, stroke, center) }
            1 -> { drawCircle(color, w * .38f, style = Stroke(stroke)); drawCircle(color, w * .18f, style = Stroke(stroke)); drawLine(color, Offset(w * .5f, 0f), Offset(w * .5f, w), stroke); drawLine(color, Offset(0f, w * .5f), Offset(w, w * .5f), stroke) }
            2 -> { val path = Path(); path.moveTo(w * .2f, w * .8f); path.cubicTo(w * .9f, w * .8f, w * .1f, w * .2f, w * .8f, w * .2f); drawPath(path, color, style = Stroke(stroke)); drawCircle(color, w * .1f, Offset(w * .2f, w * .8f)); drawCircle(color, w * .1f, Offset(w * .8f, w * .2f)) }
            else -> { for (x in listOf(.1f, .58f)) for (y in listOf(.1f, .58f)) drawRoundRect(color, Offset(w * x, w * y), androidx.compose.ui.geometry.Size(w * .32f, w * .32f), androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()), style = Stroke(stroke)) }
        }
    }
}
