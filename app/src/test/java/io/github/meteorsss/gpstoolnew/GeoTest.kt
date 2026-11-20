package io.github.meteorsss.gpstoolnew

import org.junit.Assert.*
import org.junit.Test

class GeoTest {
    private fun fix(lat: Double = 0.0, lon: Double = 0.0, second: Long = 1, accuracy: Double? = 5.0, speed: Double? = 2.0, mock: Boolean = false) = Fix(lat, lon, second * 1000, second * 1_000_000_000, accuracy, null, speed, null, mock)
    @Test fun samePointHasZeroDistance() { assertEquals(0.0, Geo.distance(31.0, 121.0, 31.0, 121.0), .0001) }
    @Test fun equatorialDegreeIsAbout111Km() { assertEquals(111195.0, Geo.distance(0.0, 0.0, 0.0, 1.0), 10.0) }
    @Test fun distanceIsSymmetric() { assertEquals(Geo.distance(30.0, 120.0, 31.0, 121.0), Geo.distance(31.0, 121.0, 30.0, 120.0), .001) }
    @Test fun datelineUsesShortDistance() { assertTrue(Geo.distance(0.0, 179.999, 0.0, -179.999) < 230) }
    @Test fun antipodesAreFinite() { assertTrue(Geo.distance(90.0, 0.0, -90.0, 180.0).isFinite()) }
    @Test fun bearingNorthAndEast() { assertEquals(0.0, Geo.bearing(0.0, 0.0, 1.0, 0.0), .001); assertEquals(90.0, Geo.bearing(0.0, 0.0, 0.0, 1.0), .001) }
    @Test fun invalidCoordinatesRejected() { assertFalse(Geo.valid(Double.NaN, 0.0)); assertFalse(Geo.valid(91.0, 0.0)); assertFalse(Geo.valid(0.0, 181.0)) }
    @Test fun durationHandlesNegativeAndHours() { assertEquals("00:00:00", Geo.duration(-1)); assertEquals("01:01:01", Geo.duration(3661000)) }
    @Test fun missingNumberIsNotInventedZero() { assertEquals("—", Geo.decimal(null)); assertEquals("—", Geo.decimal(Double.NaN)) }
    @Test fun firstFixAddsNoDistance() { val d = TrackFilter().consume(fix()); assertTrue(d.accepted); assertEquals(0.0, d.distance, 0.0) }
    @Test fun movingPointsContributeDistance() { val f = TrackFilter(); f.consume(fix()); assertTrue(f.consume(fix(lon = .0001, second = 2)).distance > 10) }
    @Test fun stationaryDriftNotCounted() { val f = TrackFilter(); f.consume(fix(speed = 0.0)); assertEquals(0.0, f.consume(fix(lon = .00005, second = 2, speed = 0.0)).distance, 0.0) }
    @Test fun slowMovementAccumulatesAgainstAnchor() { val f = TrackFilter(); f.consume(fix()); val a = f.consume(fix(lon = .00001, second = 2)); val b = f.consume(fix(lon = .00002, second = 3)); assertEquals(0.0, a.distance, .001); assertTrue(b.distance > 2) }
    @Test fun pauseStartsNewSegment() { val f = TrackFilter(); f.consume(fix()); f.breakSegment(); val d = f.consume(fix(lon = .001, second = 2)); assertEquals(1, d.segment); assertEquals(0.0, d.distance, 0.0) }
    @Test fun longGapIsNotConnected() { val f = TrackFilter(); f.consume(fix()); val d = f.consume(fix(lon = .1, second = 30)); assertEquals(1, d.segment); assertEquals(0.0, d.distance, 0.0) }
    @Test fun poorAccuracyBreaksTrack() { val f = TrackFilter(); f.consume(fix()); assertFalse(f.consume(fix(second = 2, accuracy = 100.0)).accepted); assertEquals(1, f.consume(fix(second = 3)).segment) }
    @Test fun missingAccuracyIsRejected() { assertFalse(TrackFilter().consume(fix(accuracy = null)).accepted) }
    @Test fun mockDataNotIncluded() { assertFalse(TrackFilter().consume(fix(mock = true)).accepted) }
    @Test fun teleportIsFlagged() { val f = TrackFilter(); f.consume(fix()); assertFalse(f.consume(fix(lon = 1.0, second = 2)).accepted); assertEquals(1, f.consume(fix(lon = 1.00001, second = 3)).segment) }
    @Test fun duplicateTimestampIsRejected() { val f = TrackFilter(); f.consume(fix()); assertFalse(f.consume(fix()).accepted) }
}
