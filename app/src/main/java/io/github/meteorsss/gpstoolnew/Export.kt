package io.github.meteorsss.gpstoolnew

import java.time.Instant
import org.json.JSONArray
import org.json.JSONObject

object Export {
    private fun xml(value: String) = value.filter { it == '\n' || it == '\r' || it == '\t' || it.code >= 32 }
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;")
    private fun csv(value: Any?): String = "\"" + (value?.toString() ?: "").replace("\"", "\"\"") + "\""
    fun gpx(track: Track, points: List<TrackPoint>): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        append("<gpx version=\"1.1\" creator=\"GPS Tool New\" xmlns=\"http://www.topografix.com/GPX/1/1\" xmlns:gps=\"https://github.com/Meteorsss/GPS-tool-new\">\n")
        append("<metadata><time>").append(Instant.ofEpochMilli(track.startedAt)).append("</time></metadata>\n")
        append("<trk><name>").append(xml(track.name)).append("</name><desc>WGS84; filtered segments; straight-line distances, not navigation.</desc>\n")
        var segment: Int? = null
        points.filter { it.accepted }.forEach { point ->
            if (segment != point.segment) {
                if (segment != null) append("</trkseg>\n")
                append("<trkseg>\n"); segment = point.segment
            }
            append("<trkpt lat=\"").append(point.latitude).append("\" lon=\"").append(point.longitude).append("\">")
            point.altitude?.let { append("<ele>").append(it).append("</ele>") }
            append("<time>").append(Instant.ofEpochMilli(point.time)).append("</time><extensions>")
            point.accuracy?.let { append("<gps:accuracyMeters>").append(it).append("</gps:accuracyMeters>") }
            point.speed?.let { append("<gps:speedMps>").append(it).append("</gps:speedMps>") }
            append("<gps:altitudeReference>WGS84 ellipsoid</gps:altitudeReference></extensions></trkpt>\n")
        }
        if (segment != null) append("</trkseg>\n")
        append("</trk></gpx>\n")
    }
    fun csv(points: List<TrackPoint>): String = buildString {
        append("time_utc,latitude_wgs84,longitude_wgs84,ellipsoid_altitude_m,accuracy_m,speed_mps,bearing_deg,accepted,segment,reason,mock\r\n")
        points.forEach { p ->
            append(listOf(Instant.ofEpochMilli(p.time), p.latitude, p.longitude, p.altitude,
                p.accuracy, p.speed, p.bearing, p.accepted, p.segment, p.reason, p.mock).joinToString(",") { csv(it) })
            append("\r\n")
        }
    }
    fun places(places: List<Place>): String {
        val features = JSONArray()
        places.forEach { p ->
            val coordinates = JSONArray().put(p.longitude).put(p.latitude)
            features.put(JSONObject().put("type", "Feature")
                .put("geometry", JSONObject().put("type", "Point").put("coordinates", coordinates))
                .put("properties", JSONObject().put("name", p.name).put("note", p.note)
                    .put("accuracyMeters", p.accuracy ?: JSONObject.NULL)
                    .put("ellipsoidAltitudeMeters", p.altitude ?: JSONObject.NULL)
                    .put("createdAt", Instant.ofEpochMilli(p.createdAt).toString())))
        }
        return JSONObject().put("type", "FeatureCollection").put("features", features).toString(2)
    }
}
