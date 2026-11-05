package sa.factory.qalamcampussmarted

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import sa.factory.qalamcampussmarted.hardware.digitizer.AttendanceStatus
import sa.factory.qalamcampussmarted.hardware.digitizer.BleAttendanceBeaconTracker
import sa.factory.qalamcampussmarted.hardware.digitizer.StylusPressureCurveEngine
import sa.factory.qalamcampussmarted.hardware.digitizer.StylusPressureProfile

/**
 * Unit Tests for Stylus Pressure Curve Engine & BLE Attendance Proximity Tracker
 * Strict Invariant: Zero Unicode Emojis.
 */
class StylusPressureAndAttendanceTest {

    @Test
    fun testStylusPressureEvaluation() {
        val engine = StylusPressureCurveEngine(profile = StylusPressureProfile.SOFT_SENSITIVE)

        // 1. Tip not down or negligible force
        val noTouch = engine.evaluateStroke(rawPressure = 10, tipDown = false)
        assertFalse("Must not register contact when tip is up", noTouch.isContactDetected)
        assertEquals(0.0, noTouch.strokeWidthPt, 0.001)

        // 2. Medium pressure touch (2048 / 4095)
        val midTouch = engine.evaluateStroke(rawPressure = 2048, tipDown = true)
        assertTrue("Must detect active contact", midTouch.isContactDetected)
        assertTrue("Stroke width must be positive", midTouch.strokeWidthPt > 1.0)
        assertTrue("Opacity must be in 0..1 range", midTouch.opacity in 0.0..1.0)

        // 3. Max force touch (4095)
        val maxTouch = engine.evaluateStroke(rawPressure = 4095, tipDown = true)
        assertTrue("Max pressure must yield greater stroke than mid pressure", maxTouch.strokeWidthPt > midTouch.strokeWidthPt)
        assertEquals(1.0, maxTouch.opacity, 0.05)
    }

    @Test
    fun testBleAttendanceDistanceAndVerification() {
        val tracker = BleAttendanceBeaconTracker(measuredPowerAt1m = -59, maxClassroomRadiusMeters = 4.0)

        // 1. Close proximity inside classroom (RSSI -63 dBm)
        val resultNear = tracker.evaluateAttendance("STU-101", "BCN-ROOM-A", -63)
        assertEquals(AttendanceStatus.VERIFIED_PRESENT, resultNear.status)
        assertTrue("Distance should be under 4.0m", resultNear.estimatedDistanceMeters < 4.0)

        // 2. Far outside classroom boundary (RSSI -88 dBm)
        val resultFar = tracker.evaluateAttendance("STU-102", "BCN-ROOM-A", -88)
        assertEquals(AttendanceStatus.OUT_OF_CLASSROOM_BOUNDS, resultFar.status)
        assertTrue("Distance should exceed 5.0m", resultFar.estimatedDistanceMeters > 5.0)

        // 3. Complete signal loss
        val resultLost = tracker.evaluateAttendance("STU-103", "BCN-ROOM-A", -105)
        assertEquals(AttendanceStatus.NO_BEACON_SIGNAL, resultLost.status)
    }
}
