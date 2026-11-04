package sa.factory.qalamcampussmarted

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import sa.factory.qalamcampussmarted.domain.academy.*
import sa.factory.qalamcampussmarted.hardware.digitizer.*

class QalamCampusSmartEducationFeatureTest {

    private lateinit var protocol: SmartStylusBeaconProtocol
    private lateinit var bootcampEngine: SmartClassroomBootcampEngine

    @Before
    fun setUp() {
        protocol = SmartStylusBeaconProtocol()
        bootcampEngine = SmartClassroomBootcampEngine()
    }

    @Test
    fun testStylusBinaryPacketEncodingAndDecoding() {
        val originalPoint = StylusDigitizerPoint(
            x = 32000,
            y = 48000,
            rawPressure = 2048, // 50% pressure
            normalizedPressure = 0.5,
            tiltAltitudeDeg = 45,
            tiltAzimuthDeg = 180,
            buttons = StylusButtonState(isTipDown = true, isBarrelSwitch1 = false, isBarrelSwitch2 = true, isInRange = true),
            strokeThicknessPt = 6.25
        )

        val packet = protocol.encodeStylusPoint(originalPoint)
        assertEquals("Packet must be exactly 10 bytes", 10, packet.size)
        assertEquals("Byte 0 must be sync header 0xD7", 0xD7.toByte(), packet[0])

        val decoded = protocol.decodeStylusPoint(packet)
        assertEquals(originalPoint.x, decoded.x)
        assertEquals(originalPoint.y, decoded.y)
        assertEquals(originalPoint.rawPressure, decoded.rawPressure)
        assertEquals(originalPoint.tiltAltitudeDeg, decoded.tiltAltitudeDeg)
        assertTrue("Azimuth must be within 1 degree quantization", Math.abs(originalPoint.tiltAzimuthDeg - decoded.tiltAzimuthDeg) <= 1)
        assertTrue(decoded.buttons.isTipDown)
        assertFalse(decoded.buttons.isBarrelSwitch1)
        assertTrue(decoded.buttons.isBarrelSwitch2)
        assertTrue(decoded.buttons.isInRange)
    }

    @Test
    fun testPressureProfileThicknessCurves() {
        // Zero pressure: minimum stroke
        val minStroke = protocol.calculateStrokeThickness(0, StylusPressureProfile.LINEAR)
        assertEquals(0.5, minStroke, 0.01)

        // Full pressure: maximum stroke
        val maxStroke = protocol.calculateStrokeThickness(4095, StylusPressureProfile.LINEAR)
        assertEquals(12.0, maxStroke, 0.01)

        // Half pressure comparison: Soft curve should produce thicker stroke than Linear
        val halfPressure = 2048
        val linearStroke = protocol.calculateStrokeThickness(halfPressure, StylusPressureProfile.LINEAR)
        val softStroke = protocol.calculateStrokeThickness(halfPressure, StylusPressureProfile.SOFT_SENSITIVE)
        val firmStroke = protocol.calculateStrokeThickness(halfPressure, StylusPressureProfile.FIRM_CONTROL)

        assertTrue("Soft sensitive profile must be thicker than linear at 50% pressure", softStroke > linearStroke)
        assertTrue("Linear profile must be thicker than firm control at 50% pressure", linearStroke > firmStroke)
    }

    @Test
    fun testBleBeaconAdEncodingAndDistanceCalculation() {
        val uuid = "E2C56DB5DFFB48D2B060D0F5A71096E0"
        val major = 301 // Room 301
        val minor = 12  // Desk 12
        val txPower = -59

        val adBytes = protocol.encodeBeaconAdvertisement(uuid, major, minor, txPower)
        assertTrue("Advertisement must contain at least 23 bytes", adBytes.size >= 23)

        // When measured RSSI equals TxPower at 1m, distance must be approximately 1.0m
        val frameAt1m = protocol.decodeBeaconAdvertisement(adBytes, rssiDbm = -59)
        assertNotNull(frameAt1m)
        assertEquals(uuid, frameAt1m?.uuidHex)
        assertEquals(major, frameAt1m?.major)
        assertEquals(minor, frameAt1m?.minor)
        assertEquals(1.0, frameAt1m?.calculatedDistanceMeters ?: 0.0, 0.1)
        assertEquals(BleProximityZone.NEAR, frameAt1m?.proximityZone)

        // Close range: RSSI -45 dBm
        val closeFrame = protocol.decodeBeaconAdvertisement(adBytes, rssiDbm = -45)
        assertEquals(BleProximityZone.IMMEDIATE, closeFrame?.proximityZone)
        assertTrue((closeFrame?.calculatedDistanceMeters ?: 1.0) < 0.5)
    }

    @Test
    fun testStudentAttendanceVerificationOnTimeAndLate() {
        val student = StudentProfile(
            studentId = "STD-2026-9041",
            fullNameAr = "سارة بنت عبدالعزيز الشمري",
            nationalId = "1098273645",
            track = BootcampTrack.AI_ENGINEERING,
            beaconDeviceId = "BLE-TAG-882"
        )

        val sessionStart = 1793740000000L
        val session = ClassroomSession(
            sessionId = "SES-AI-101",
            track = BootcampTrack.AI_ENGINEERING,
            titleAr = "محاضرة نماذج الرؤية الحاسوبية",
            instructorNameAr = "د. عبدالرحمن آل سعود",
            roomCode = "LAB-301",
            beaconMajor = 301,
            beaconMinor = 1,
            startTimeMillis = sessionStart,
            endTimeMillis = sessionStart + (2 * 3600 * 1000L)
        )

        // 1. On-time check-in (5 minutes after start)
        val validBeaconFrame = BleAttendanceBeaconFrame(
            uuidHex = "E2C56DB5DFFB48D2B060D0F5A71096E0",
            major = 301,
            minor = 1,
            txPowerDbm = -59,
            rssiDbm = -65,
            calculatedDistanceMeters = 1.77,
            proximityZone = BleProximityZone.NEAR
        )

        val onTimeEntry = bootcampEngine.verifyStudentAttendance(
            student = student,
            session = session,
            beaconFrame = validBeaconFrame,
            checkinTimeMillis = sessionStart + (5 * 60 * 1000L)
        )
        assertTrue(onTimeEntry.isVerified)
        assertEquals(AttendanceRecordStatus.PRESENT_ON_TIME, onTimeEntry.status)
        assertEquals(1, student.totalSessionsAttended)

        // 2. Late check-in (20 minutes after start > 15m grace)
        val lateEntry = bootcampEngine.verifyStudentAttendance(
            student = student,
            session = session,
            beaconFrame = validBeaconFrame,
            checkinTimeMillis = sessionStart + (20 * 60 * 1000L)
        )
        assertTrue(lateEntry.isVerified)
        assertEquals(AttendanceRecordStatus.LATE_ATTENDANCE, lateEntry.status)
        assertEquals(1, student.totalLateCount)

        // 3. Beacon major mismatch (student in Room 402 instead of 301)
        val wrongRoomFrame = validBeaconFrame.copy(major = 402)
        val wrongRoomEntry = bootcampEngine.verifyStudentAttendance(
            student = student,
            session = session,
            beaconFrame = wrongRoomFrame,
            checkinTimeMillis = sessionStart + (5 * 60 * 1000L)
        )
        assertFalse(wrongRoomEntry.isVerified)
        assertEquals(AttendanceRecordStatus.BEACON_MISMATCH, wrongRoomEntry.status)
    }

    @Test
    fun testInteractiveStylusAnnotationStrokeProcessing() {
        val now = 1793741000000L
        val points = listOf(
            StylusDigitizerPoint(1000, 2000, 1024, 0.25, 40, 90, StylusButtonState(true), 3.37, now),
            StylusDigitizerPoint(1050, 2050, 2048, 0.50, 42, 92, StylusButtonState(true), 6.25, now + 20),
            StylusDigitizerPoint(1100, 2100, 3072, 0.75, 45, 95, StylusButtonState(true), 9.12, now + 40),
            StylusDigitizerPoint(1150, 2150, 4095, 1.00, 48, 98, StylusButtonState(true), 12.00, now + 60)
        )

        val stroke = bootcampEngine.processStylusStroke(
            strokeId = "STRK-001",
            sessionId = "SES-AI-101",
            authorId = "INST-01",
            colorHex = "#2563EB",
            points = points
        )

        assertEquals("STRK-001", stroke.strokeId)
        assertEquals(4, stroke.pointCount)
        assertEquals(60L, stroke.durationMs)
        assertEquals(0.625, stroke.averagePressure, 0.01)
        assertEquals(7.7, stroke.averageThicknessPt, 0.1)
    }

    @Test
    fun testBootcampTuitionInvoiceAndZatcaVat() {
        val invoice = bootcampEngine.generateTuitionInvoice(
            invoiceId = "INV-2026-AI-88",
            studentId = "STD-2026-9041",
            track = BootcampTrack.AI_ENGINEERING, // 14,500 SAR
            scholarshipDiscountSar = 2500.0       // 2,500 SAR talent scholarship
        )

        // Subtotal = 14,500 - 2,500 = 12,000 SAR
        assertEquals(12000.0, invoice.subtotalSar, 0.01)
        // ZATCA VAT (15%) = 12,000 * 0.15 = 1,800 SAR
        assertEquals(1800.0, invoice.vatSar, 0.01)
        // Grand Total = 13,800 SAR
        assertEquals(13800.0, invoice.totalSar, 0.01)
    }
}
