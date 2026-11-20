package io.github.meteorsss.gpstoolnew

import java.util.Locale
import kotlin.math.*

data class Fix(
    val latitude: Double, val longitude: Double, val time: Long, val elapsedNanos: Long,
    val accuracy: Double?, val altitude: Double?, val speed: Double?, val bearing: Double?,
    val mock: Boolean = false
)
data class Satellite(val id: Int, val system: String, val cn0: Float, val elevation: Float,
    val azimuth: Float, val used: Boolean, val frequencyMhz: Float?)
data class TrackDecision(val accepted: Boolean, val segment: Int, val distance: Double, val reason: String)
object Geo {
    fun valid(lat: Double, lon: Double) = lat.isFinite() && lon.isFinite() && lat in -90.0..90.0 && lon in -180.0..180.0
    fun distance(a: Fix, b: Fix) = distance(a.latitude, a.longitude, b.latitude, b.longitude)
    fun distance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val p1 = Math.toRadians(lat1); val p2 = Math.toRadians(lat2)
        val dp = p2 - p1; val dl = Math.toRadians(lon2 - lon1)
        val h = sin(dp / 2).pow(2) + cos(p1) * cos(p2) * sin(dl / 2).pow(2)
        return 6371008.8 * 2 * asin(sqrt(h.coerceIn(0.0, 1.0)))
    }
    fun bearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val a = Math.toRadians(lat1); val b = Math.toRadians(lat2); val d = Math.toRadians(lon2 - lon1)
        return (Math.toDegrees(atan2(sin(d) * cos(b), cos(a) * sin(b) - sin(a) * cos(b) * cos(d))) + 360) % 360
    }
    fun duration(ms: Long): String {
        val seconds = ms.coerceAtLeast(0) / 1000
        return String.format(Locale.ROOT, "%02d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)
    }
    fun distanceText(meters: Double) = if (meters < 1000) String.format(Locale.ROOT, "%.0f m", meters)
        else String.format(Locale.ROOT, "%.2f km", meters / 1000)
    fun decimal(value: Double?, digits: Int = 1): String = if (value == null || !value.isFinite()) "—"
        else String.format(Locale.ROOT, "%.${digits}f", value)
}
/** Raw fixes are kept. Only qualified, continuous points contribute to filtered distance. */
class TrackFilter {
    private var previous: Fix? = null
    private var anchor: Fix? = null
    private var segment = 0
    private var everAccepted = false
    fun breakSegment() { previous = null; anchor = null }
    fun consume(fix: Fix): TrackDecision {
        if (!Geo.valid(fix.latitude, fix.longitude) || fix.accuracy == null || !fix.accuracy.isFinite() || fix.accuracy !in 0.0..60.0 || fix.mock) {
            breakSegment()
            return TrackDecision(false, segment, 0.0, if (fix.mock) "模拟位置" else "精度不足")
        }
        val prev = previous
        var distance = 0.0
        if (prev == null || (fix.elapsedNanos - prev.elapsedNanos) / 1e9 > 20.0) {
            if (everAccepted) segment++
            anchor = fix
        } else {
            val dt = (fix.elapsedNanos - prev.elapsedNanos) / 1e9
            if (dt <= 0) return TrackDecision(false, segment, 0.0, "重复或乱序")
            val hop = Geo.distance(prev, fix)
            if (hop > dt * 120.0 + (prev.accuracy ?: 0.0) + fix.accuracy) {
                breakSegment()
                return TrackDecision(false, segment, 0.0, "疑似跳点")
            }
            val base = anchor ?: prev
            val displacement = Geo.distance(base, fix)
            val stationary = fix.speed != null && prev.speed != null && fix.speed < 0.5 && prev.speed < 0.5
            if (stationary) anchor = fix
            else if (displacement >= max(2.0, min(base.accuracy ?: 5.0, fix.accuracy) * 0.4)) {
                distance = displacement
                anchor = fix
            }
        }
        previous = fix; everAccepted = true
        return TrackDecision(true, segment, distance, "")
    }
}
