package io.github.meteorsss.gpstoolnew

import android.Manifest
import android.annotation.SuppressLint
import android.content.*
import android.content.pm.PackageManager
import android.hardware.*
import android.location.*
import android.os.*
import android.view.Surface
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import kotlin.math.*

class LocationEngine(private val context: Context) {
    private val manager = context.getSystemService(LocationManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    val fix = MutableStateFlow<Fix?>(null)
    val satellites = MutableStateFlow<List<Satellite>>(emptyList())
    val status = MutableStateFlow("等待定位权限")
    val enabled = MutableStateFlow(manager.isProviderEnabled(LocationManager.GPS_PROVIDER))
    val samples = MutableSharedFlow<Fix>(extraBufferCapacity = 128, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private var visible = false
    private var recording = false
    private var running = false
    private var latestElapsed = -1L
    fun hasPermission() = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    fun setVisible(value: Boolean) { visible = value; refresh() }
    fun setRecording(value: Boolean) { recording = value; refresh() }
    private val callback = object : GnssStatus.Callback() {
        override fun onStopped() { satellites.value = emptyList() }
        override fun onSatelliteStatusChanged(s: GnssStatus) {
            satellites.value = (0 until s.satelliteCount).map { i ->
                Satellite(s.getSvid(i), when (s.getConstellationType(i)) {
                    GnssStatus.CONSTELLATION_GPS -> "GPS"
                    GnssStatus.CONSTELLATION_BEIDOU -> "北斗"
                    GnssStatus.CONSTELLATION_GALILEO -> "Galileo"
                    GnssStatus.CONSTELLATION_GLONASS -> "GLONASS"
                    GnssStatus.CONSTELLATION_QZSS -> "QZSS"
                    GnssStatus.CONSTELLATION_SBAS -> "SBAS"
                    GnssStatus.CONSTELLATION_IRNSS -> "NavIC"
                    else -> "未知"
                }, s.getCn0DbHz(i), s.getElevationDegrees(i), s.getAzimuthDegrees(i), s.usedInFix(i),
                    if (s.hasCarrierFrequencyHz(i)) s.getCarrierFrequencyHz(i) / 1e6f else null)
            }
        }
    }
    private val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            if (!Geo.valid(location.latitude, location.longitude) || location.elapsedRealtimeNanos <= latestElapsed) return
            latestElapsed = location.elapsedRealtimeNanos
            val next = Fix(location.latitude, location.longitude, location.time, location.elapsedRealtimeNanos,
                if (location.hasAccuracy()) location.accuracy.toDouble() else null,
                if (location.hasAltitude()) location.altitude else null,
                if (location.hasSpeed()) location.speed.toDouble().takeIf { it >= 0 && it.isFinite() } else null,
                if (location.hasBearing()) location.bearing.toDouble() else null, location.isMock)
            fix.value = next
            samples.tryEmit(next)
            status.value = if (next.mock) "模拟位置 · 不计入轨迹里程" else "GNSS 已定位"
        }
        override fun onProviderEnabled(provider: String) { refresh() }
        override fun onProviderDisabled(provider: String) { refresh() }
    }
    init {
        ContextCompat.registerReceiver(context, object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) { refresh() }
        }, IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION), ContextCompat.RECEIVER_NOT_EXPORTED)
    }
    @SuppressLint("MissingPermission")
    fun refresh() {
        enabled.value = manager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        val desired = (visible || recording) && hasPermission() && enabled.value
        if (!desired) {
            if (running) {
                manager.removeUpdates(listener)
                manager.unregisterGnssStatusCallback(callback)
                running = false
            }
            satellites.value = emptyList()
            status.value = when {
                !hasPermission() -> "需要精确定位权限"
                !enabled.value -> "系统定位已关闭"
                else -> "定位已暂停"
            }
            return
        }
        if (running) return
        try {
            manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, listener, Looper.getMainLooper())
            manager.registerGnssStatusCallback(callback, handler)
            running = true
            status.value = "正在搜索卫星 · 建议到室外"
        } catch (_: SecurityException) { status.value = "定位权限不可用" }
        catch (_: IllegalArgumentException) { status.value = "设备未提供 GNSS 定位服务" }
    }
}

class CompassEngine(private val context: Context) : SensorEventListener {
    private val manager = context.getSystemService(SensorManager::class.java)
    private val sensor = manager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    val heading = MutableStateFlow<Double?>(null)
    val reliable = MutableStateFlow(false)
    val available = sensor != null
    var rotation: Int = Surface.ROTATION_0
    private var filteredX = 0.0
    private var filteredY = 0.0
    private var initialized = false
    fun setActive(active: Boolean) {
        if (active && sensor != null) manager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
        else { manager.unregisterListener(this); initialized = false }
    }
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) { reliable.value = accuracy >= SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM }
    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ROTATION_VECTOR) return
        reliable.value = event.accuracy >= SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM
        val matrix = FloatArray(9); val remapped = FloatArray(9); val angles = FloatArray(3)
        SensorManager.getRotationMatrixFromVector(matrix, event.values)
        val axes = when (rotation) {
            Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
            Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
            Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
            else -> SensorManager.AXIS_X to SensorManager.AXIS_Y
        }
        SensorManager.remapCoordinateSystem(matrix, axes.first, axes.second, remapped)
        SensorManager.getOrientation(remapped, angles)
        val radians = angles[0].toDouble()
        if (!initialized) { filteredX = cos(radians); filteredY = sin(radians); initialized = true }
        else { filteredX = 0.85 * filteredX + 0.15 * cos(radians); filteredY = 0.85 * filteredY + 0.15 * sin(radians) }
        heading.value = (Math.toDegrees(atan2(filteredY, filteredX)) + 360) % 360
    }
    fun trueHeading(fix: Fix?): Double? {
        val magnetic = heading.value ?: return null
        if (fix == null) return null
        val field = GeomagneticField(fix.latitude.toFloat(), fix.longitude.toFloat(), (fix.altitude ?: 0.0).toFloat(), fix.time)
        return (magnetic + field.declination + 360) % 360
    }
}
