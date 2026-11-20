package io.github.meteorsss.gpstoolnew

import android.app.Application
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow

class ToolboxApp : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    lateinit var db: ToolboxDatabase
    lateinit var engine: LocationEngine
    lateinit var compass: CompassEngine
    lateinit var settings: Settings
    lateinit var ready: Deferred<Unit>
    val recording = MutableStateFlow<Track?>(null)
    val message = MutableStateFlow<String?>(null)
    override fun onCreate() {
        super.onCreate()
        db = ToolboxDatabase.create(this)
        settings = Settings(this)
        engine = LocationEngine(this)
        compass = CompassEngine(this)
        ready = scope.async(Dispatchers.IO) { db.dao().markInterrupted(System.currentTimeMillis()) }
    }
}
