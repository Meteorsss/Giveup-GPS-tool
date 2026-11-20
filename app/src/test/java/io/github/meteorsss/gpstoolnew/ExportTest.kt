package io.github.meteorsss.gpstoolnew

import org.junit.Assert.*
import org.junit.Test
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory
import org.xml.sax.InputSource

class ExportTest {
    private fun point(segment: Int, accepted: Boolean = true, reason: String = "") = TrackPoint(trackId = 1, latitude = 31.0, longitude = 121.0, time = 0, elapsedNanos = 1, accuracy = 4.0, altitude = 20.0, speed = 1.0, bearing = null, mock = false, accepted = accepted, segment = segment, reason = reason)
    @Test fun gpxEscapesNamesAndPreservesSegments() {
        val xml = Export.gpx(Track(id = 1, name = "A<&B", startedAt = 0), listOf(point(0), point(0, false), point(1)))
        val factory = DocumentBuilderFactory.newInstance(); factory.isNamespaceAware = true
        val doc = factory.newDocumentBuilder().parse(InputSource(StringReader(xml)))
        assertEquals("A<&B", doc.getElementsByTagName("name").item(0).textContent)
        assertEquals(2, doc.getElementsByTagName("trkseg").length)
        assertEquals(2, doc.getElementsByTagName("trkpt").length)
        assertTrue(xml.contains("WGS84 ellipsoid"))
    }
    @Test fun gpxEmptyTrackRemainsValidXml() {
        val xml = Export.gpx(Track(id = 1, name = "empty", startedAt = 0), emptyList())
        DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(InputSource(StringReader(xml)))
        assertFalse(xml.contains("<trkseg>"))
    }
    @Test fun csvIncludesRejectedRawDataAndQuotesReasons() {
        val csv = Export.csv(listOf(point(0, false, "bad, \"fix\"")))
        assertTrue(csv.contains("\"false\"")); assertTrue(csv.contains("\"bad, \"\"fix\"\"\""))
        assertTrue(csv.startsWith("time_utc,latitude_wgs84"))
    }
}
