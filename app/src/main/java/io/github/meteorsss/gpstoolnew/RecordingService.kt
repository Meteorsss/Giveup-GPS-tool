package io.github.meteorsss.gpstoolnew

import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.os.*
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.text.SimpleDateFormat
import java.util.*

class RecordingService : Service() {
    private val app get() = application as ToolboxApp
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val events = Channel<Event>(Channel.UNLIMITED)
    private var track: Track? = null
    private var activeSince = 0L
    private var accumulated = 0L
    private var cleanStop = false
    private val filter = TrackFilter()
    private sealed interface Event {
        data class Command(val action: String) : Event
        data class Position(val fix: Fix) : Event
        data object Tick : Event
    }
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "轨迹记录", NotificationManager.IMPORTANCE_LOW))
        scope.launch { app.engine.samples.collect { events.send(Event.Position(it)) } }
        scope.launch { while (isActive) { delay(1000); events.send(Event.Tick) } }
        scope.launch {
            app.ready.await()
            try {
                for (event in events) when (event) {
                    is Event.Command -> command(event.action)
                    is Event.Position -> append(event.fix)
                    Event.Tick -> tick()
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                app.message.value = "轨迹保存失败，记录已停止；请检查剩余存储空间。"
                stopSelf()
            }
        }
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == START) {
            try {
                ServiceCompat.startForeground(this, NOTIFICATION_ID, notification("正在启动轨迹记录"), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
            } catch (_: SecurityException) {
                app.message.value = "无法启动记录，请允许精确定位权限。"; stopSelf(); return START_NOT_STICKY
            }
        }
        events.trySend(Event.Command(intent?.action ?: STOP))
        return START_NOT_STICKY
    }
    private fun duration(): Long = accumulated + if (track?.state == "recording") SystemClock.elapsedRealtime() - activeSince else 0
    private suspend fun command(action: String) {
        when (action) {
            START -> if (track == null) {
                if (!app.engine.hasPermission() || !app.engine.enabled.value) { finish(); return }
                val now = System.currentTimeMillis()
                val initial = Track(name = "轨迹 " + SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date(now)), startedAt = now)
                track = initial.copy(id = app.db.dao().insertTrack(initial))
                activeSince = SystemClock.elapsedRealtime()
                app.engine.setRecording(true)
                publish()
            }
            PAUSE -> if (track?.state == "recording") {
                accumulated = duration()
                track = track?.copy(state = "paused", durationMs = accumulated)
                filter.breakSegment()
                app.engine.setRecording(false)
                track?.let { app.db.dao().updateTrack(it) }; publish()
            }
            RESUME -> if (track?.state == "paused") {
                if (!app.engine.hasPermission() || !app.engine.enabled.value) { app.message.value = "请先开启定位并授予精确位置权限。"; return }
                activeSince = SystemClock.elapsedRealtime()
                track = track?.copy(state = "recording")
                filter.breakSegment()
                app.engine.setRecording(true)
                track?.let { app.db.dao().updateTrack(it) }; publish()
            }
            STOP -> finish()
        }
    }
    private suspend fun append(fix: Fix) {
        val current = track ?: return
        if (current.state != "recording" || fix.elapsedNanos / 1000000 < activeSince) return
        val decision = filter.consume(fix)
        val next = current.copy(durationMs = duration(), distance = current.distance + decision.distance,
            count = current.count + 1, acceptedCount = current.acceptedCount + if (decision.accepted) 1 else 0,
            maxSpeed = if (decision.accepted) maxOf(current.maxSpeed, fix.speed ?: 0.0) else current.maxSpeed)
        app.db.dao().append(TrackPoint(trackId = current.id, latitude = fix.latitude, longitude = fix.longitude,
            time = fix.time, elapsedNanos = fix.elapsedNanos, accuracy = fix.accuracy, altitude = fix.altitude,
            speed = fix.speed, bearing = fix.bearing, mock = fix.mock, accepted = decision.accepted,
            segment = decision.segment, reason = decision.reason), next)
        track = next
        app.recording.value = next
    }
    private suspend fun tick() {
        val current = track ?: return
        track = current.copy(durationMs = duration())
        if (track!!.durationMs / 1000 % 5 == 0L) app.db.dao().updateTrack(track!!)
        publish()
    }
    @android.annotation.SuppressLint("MissingPermission")
    private fun publish() {
        app.recording.value = track
        val current = track ?: return
        val text = (if (current.state == "paused") "已暂停 · " else "记录中 · ") + Geo.duration(current.durationMs) + " · " + Geo.distanceText(current.distance)
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(text))
    }
    private suspend fun finish() {
        track?.let { app.db.dao().updateTrack(it.copy(state = "completed", endedAt = System.currentTimeMillis(), durationMs = duration())) }
        cleanStop = true
        track = null
        app.recording.value = null
        app.engine.setRecording(false)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }
    private fun notification(text: String): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        fun action(name: String, request: Int) = PendingIntent.getService(this, request, Intent(this, RecordingService::class.java).setAction(name), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val paused = track?.state == "paused"
        return NotificationCompat.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_satellite)
            .setContentTitle("GPS 工具箱 · 轨迹记录").setContentText(text).setContentIntent(open)
            .setOngoing(true).setOnlyAlertOnce(true).setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(0, if (paused) "继续" else "暂停", action(if (paused) RESUME else PAUSE, 1))
            .addAction(0, "结束并保存", action(STOP, 2)).build()
    }
    override fun onDestroy() {
        val unfinished = track
        scope.cancel()
        app.engine.setRecording(false)
        app.recording.value = null
        if (!cleanStop && unfinished != null) app.scope.launch {
            app.db.dao().updateTrack(unfinished.copy(state = "interrupted", endedAt = System.currentTimeMillis(), durationMs = duration()))
        }
        super.onDestroy()
    }
    companion object {
        const val START = "gps.START"
        const val PAUSE = "gps.PAUSE"
        const val RESUME = "gps.RESUME"
        const val STOP = "gps.STOP"
        private const val CHANNEL = "recording"
        private const val NOTIFICATION_ID = 100
    }
}
