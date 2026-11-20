package io.github.meteorsss.gpstoolnew

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.*
import kotlinx.coroutines.flow.*

@Entity(tableName = "tracks")
data class Track(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String, val startedAt: Long, val endedAt: Long? = null,
    val durationMs: Long = 0, val distance: Double = 0.0, val maxSpeed: Double = 0.0,
    val count: Int = 0, val acceptedCount: Int = 0, val state: String = "recording"
)
@Entity(tableName = "points", foreignKeys = [ForeignKey(entity = Track::class,
    parentColumns = ["id"], childColumns = ["trackId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("trackId")])
data class TrackPoint(
    @PrimaryKey(autoGenerate = true) val id: Long = 0, val trackId: Long,
    val latitude: Double, val longitude: Double, val time: Long, val elapsedNanos: Long,
    val accuracy: Double?, val altitude: Double?, val speed: Double?, val bearing: Double?,
    val mock: Boolean, val accepted: Boolean, val segment: Int, val reason: String
)
@Entity(tableName = "places")
data class Place(@PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String, val note: String, val latitude: Double, val longitude: Double,
    val altitude: Double?, val accuracy: Double?, val createdAt: Long)
@Dao
interface ToolboxDao {
    @Query("SELECT * FROM tracks ORDER BY startedAt DESC") fun tracks(): Flow<List<Track>>
    @Query("SELECT * FROM places ORDER BY createdAt DESC") fun places(): Flow<List<Place>>
    @Query("SELECT * FROM points WHERE trackId = :id ORDER BY elapsedNanos, id") fun points(id: Long): Flow<List<TrackPoint>>
    @Query("SELECT * FROM tracks WHERE id = :id") suspend fun getTrack(id: Long): Track?
    @Query("SELECT * FROM points WHERE trackId = :id ORDER BY elapsedNanos, id") suspend fun getPoints(id: Long): List<TrackPoint>
    @Insert suspend fun insertTrack(track: Track): Long
    @Update suspend fun updateTrack(track: Track)
    @Insert suspend fun insertPoint(point: TrackPoint)
    @Insert suspend fun insertPlace(place: Place): Long
    @Delete suspend fun deletePlace(place: Place)
    @Query("DELETE FROM tracks WHERE id = :id") suspend fun deleteTrack(id: Long)
    @Query("UPDATE tracks SET state = 'interrupted', endedAt = :now WHERE state IN ('recording', 'paused')") suspend fun markInterrupted(now: Long)
    @Transaction suspend fun append(point: TrackPoint, track: Track) { insertPoint(point); updateTrack(track) }
}
@Database(entities = [Track::class, TrackPoint::class, Place::class], version = 1, exportSchema = true)
abstract class ToolboxDatabase : RoomDatabase() {
    abstract fun dao(): ToolboxDao
    companion object {
        fun create(context: Context) = Room.databaseBuilder(context, ToolboxDatabase::class.java, "gps-toolbox.db").build()
    }
}
private val Context.settingsStore by preferencesDataStore(name = "settings")
class Settings(private val context: Context) {
    private val themeKey = stringPreferencesKey("theme")
    val theme = context.settingsStore.data.map { it[themeKey] ?: "dark" }
    suspend fun setTheme(theme: String) { context.settingsStore.edit { it[themeKey] = theme } }
}
