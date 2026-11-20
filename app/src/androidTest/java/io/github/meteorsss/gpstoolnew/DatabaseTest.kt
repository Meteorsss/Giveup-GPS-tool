package io.github.meteorsss.gpstoolnew

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class DatabaseTest {
    @Test fun savedPointsSurviveStateChangeAndCascadeDelete() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, ToolboxDatabase::class.java).build()
        try {
            val dao = db.dao()
            val id = dao.insertTrack(Track(name = "test", startedAt = 1))
            val point = TrackPoint(trackId = id, latitude = 31.0, longitude = 121.0, time = 1, elapsedNanos = 1,
                accuracy = 5.0, altitude = null, speed = null, bearing = null, mock = false, accepted = true, segment = 0, reason = "")
            dao.append(point, dao.getTrack(id)!!.copy(count = 1, acceptedCount = 1))
            dao.markInterrupted(100)
            assertEquals("interrupted", dao.getTrack(id)!!.state)
            assertEquals(1, dao.getPoints(id).size)
            dao.deleteTrack(id)
            assertTrue(dao.getPoints(id).isEmpty())
        } finally { db.close() }
    }
}
