package sa.factory.qalamcampussmarted.hardware.digitizer

import java.io.ByteArrayOutputStream
import java.io.Serializable
import java.util.Locale

/**
 * Pressure Response Curves for Digital Calligraphy & Smart Classroom Stylus
 */
enum class StylusPressureProfile {
    LINEAR,
    SOFT_SENSITIVE,
    FIRM_CONTROL
}

/**
 * Physical button / contact state of Smart Stylus
 */
data class StylusButtonState(
    val isTipDown: Boolean = false,
    val isBarrelSwitch1: Boolean = false, // Eraser toggle
    val isBarrelSwitch2: Boolean = false, // Tool selector / lasso
    val isInRange: Boolean = true
) : Serializable

/**
 * High-Precision Digitizer Telemetry Point (4096-level pressure + 2-axis tilt)
 */
data class StylusDigitizerPoint(
    val x: Int,                     // 0..65535 normalized grid
    val y: Int,                     // 0..65535 normalized grid
    val rawPressure: Int,           // 0..4095 levels (12-bit ADC)
    val normalizedPressure: Double, // 0.0..1.0
    val tiltAltitudeDeg: Int,       // -90..90 degrees
    val tiltAzimuthDeg: Int,        // 0..360 degrees
    val buttons: StylusButtonState,
    val strokeThicknessPt: Double,  // Calculated stroke weight in points
    val timestamp: Long = System.currentTimeMillis()
) : Serializable

/**
 * BLE Proximity Categorization based on RSSI Path Loss
 */
enum class BleProximityZone(val titleAr: String, val maxDistanceM: Double) {
    IMMEDIATE("ملاصق للمنصة (أقل من 0.5م)", 0.5),
    NEAR("داخل نطاق القاعة الذكية (0.5م - 3.0م)", 3.0),
    FAR("أطراف القاعة أو المدخل (3.0م - 8.0م)", 8.0),
    OUT_OF_RANGE("خارج نطاق الحضور الأكاديمي", Double.MAX_VALUE)
}

/**
 * BLE Attendance Beacon Telemetry Frame
 */
data class BleAttendanceBeaconFrame(
    val uuidHex: String,
    val major: Int,
    val minor: Int,
    val txPowerDbm: Int,
    val rssiDbm: Int,
    val calculatedDistanceMeters: Double,
    val proximityZone: BleProximityZone,
    val timestamp: Long = System.currentTimeMillis()
) : Serializable

/**
 * Binary Protocol Kernel for Smart Stylus Digitizer (4096-level) & BLE Classroom Beacon
 */
class SmartStylusBeaconProtocol {

    companion object {
        const val STYLUS_SYNC_HEADER: Byte = 0xD7.toByte()
        const val STYLUS_PACKET_LENGTH = 10
        const val MAX_PRESSURE_LEVEL = 4095
        const val IBEACON_HEADER_BYTE1: Byte = 0x02.toByte()
        const val IBEACON_HEADER_BYTE2: Byte = 0x15.toByte() // 21 bytes payload
    }

    /**
     * Encodes 10-byte binary HID digitizer packet
     * [0] Sync 0xD7
     * [1] Flags (Bit0: Tip, Bit1: Btn1, Bit2: Btn2, Bit3: InRange)
     * [2-3] X-coord (16-bit Big-Endian)
     * [4-5] Y-coord (16-bit Big-Endian)
     * [6-7] Pressure (12-bit Big-Endian, 0..4095)
     * [8] Tilt Altitude (signed byte, -90..90)
     * [9] Tilt Azimuth (scaled 0..255 representing 0..360 deg)
     */
    fun encodeStylusPoint(point: StylusDigitizerPoint): ByteArray {
        val out = ByteArray(STYLUS_PACKET_LENGTH)
        out[0] = STYLUS_SYNC_HEADER

        var flags = 0
        if (point.buttons.isTipDown) flags = flags or 0x01
        if (point.buttons.isBarrelSwitch1) flags = flags or 0x02
        if (point.buttons.isBarrelSwitch2) flags = flags or 0x04
        if (point.buttons.isInRange) flags = flags or 0x08
        out[1] = flags.toByte()

        out[2] = ((point.x ushr 8) and 0xFF).toByte()
        out[3] = (point.x and 0xFF).toByte()

        out[4] = ((point.y ushr 8) and 0xFF).toByte()
        out[5] = (point.y and 0xFF).toByte()

        val clampedPressure = Math.max(0, Math.min(MAX_PRESSURE_LEVEL, point.rawPressure))
        out[6] = ((clampedPressure ushr 8) and 0x0F).toByte()
        out[7] = (clampedPressure and 0xFF).toByte()

        out[8] = Math.max(-90, Math.min(90, point.tiltAltitudeDeg)).toByte()

        val azScaled = Math.round((point.tiltAzimuthDeg % 360) / 360.0 * 255.0).toInt()
        out[9] = (azScaled and 0xFF).toByte()

        return out
    }

    /**
     * Decodes 10-byte binary HID digitizer packet
     */
    fun decodeStylusPoint(
        bytes: ByteArray,
        offset: Int = 0,
        pressureProfile: StylusPressureProfile = StylusPressureProfile.LINEAR
    ): StylusDigitizerPoint {
        require(bytes.size - offset >= STYLUS_PACKET_LENGTH) { "Insufficient bytes for stylus frame" }
        require(bytes[offset] == STYLUS_SYNC_HEADER) { "Invalid stylus frame sync header: expected 0xD7" }

        val flags = bytes[offset + 1].toInt() and 0xFF
        val tipDown = (flags and 0x01) != 0
        val btn1 = (flags and 0x02) != 0
        val btn2 = (flags and 0x04) != 0
        val inRange = (flags and 0x08) != 0

        val x = ((bytes[offset + 2].toInt() and 0xFF) shl 8) or (bytes[offset + 3].toInt() and 0xFF)
        val y = ((bytes[offset + 4].toInt() and 0xFF) shl 8) or (bytes[offset + 5].toInt() and 0xFF)

        val pressure = ((bytes[offset + 6].toInt() and 0x0F) shl 8) or (bytes[offset + 7].toInt() and 0xFF)
        val normPressure = pressure.toDouble() / MAX_PRESSURE_LEVEL.toDouble()

        val altDeg = bytes[offset + 8].toInt()
        val azRaw = bytes[offset + 9].toInt() and 0xFF
        val azDeg = Math.round(azRaw.toDouble() / 255.0 * 360.0).toInt()

        val thickness = calculateStrokeThickness(pressure, pressureProfile)

        return StylusDigitizerPoint(
            x = x,
            y = y,
            rawPressure = pressure,
            normalizedPressure = Math.round(normPressure * 1000.0) / 1000.0,
            tiltAltitudeDeg = altDeg,
            tiltAzimuthDeg = azDeg,
            buttons = StylusButtonState(tipDown, btn1, btn2, inRange),
            strokeThicknessPt = thickness,
            timestamp = System.currentTimeMillis()
        )
    }

    /**
     * Calculates stroke thickness with pressure curve interpolation
     */
    fun calculateStrokeThickness(
        rawPressure: Int,
        profile: StylusPressureProfile = StylusPressureProfile.LINEAR,
        minPt: Double = 0.5,
        maxPt: Double = 12.0
    ): Double {
        val t = Math.max(0.0, Math.min(1.0, rawPressure.toDouble() / MAX_PRESSURE_LEVEL.toDouble()))
        val curved = when (profile) {
            StylusPressureProfile.LINEAR -> t
            StylusPressureProfile.SOFT_SENSITIVE -> Math.sqrt(t) // Rapid thickness build-up for calligraphy
            StylusPressureProfile.FIRM_CONTROL -> t * t          // Requires deliberate heavy hand pressure
        }
        val calculated = minPt + (maxPt - minPt) * curved
        return Math.round(calculated * 10.0) / 10.0
    }

    /**
     * Calculates distance from BLE Beacon using Log-Distance Path Loss model
     */
    fun calculateDistanceMeters(
        rssiDbm: Int,
        txPowerDbm: Int,
        pathLossExponent: Double = 2.4
    ): Double {
        if (rssiDbm == 0) return -1.0
        val ratio = (txPowerDbm - rssiDbm).toDouble() / (10.0 * pathLossExponent)
        val distance = Math.pow(10.0, ratio)
        return Math.round(distance * 100.0) / 100.0
    }

    /**
     * Classifies distance into attendance proximity zones
     */
    fun resolveProximityZone(distanceMeters: Double): BleProximityZone {
        return when {
            distanceMeters < 0.0 -> BleProximityZone.OUT_OF_RANGE
            distanceMeters <= BleProximityZone.IMMEDIATE.maxDistanceM -> BleProximityZone.IMMEDIATE
            distanceMeters <= BleProximityZone.NEAR.maxDistanceM -> BleProximityZone.NEAR
            distanceMeters <= BleProximityZone.FAR.maxDistanceM -> BleProximityZone.FAR
            else -> BleProximityZone.OUT_OF_RANGE
        }
    }

    /**
     * Encodes 30-byte BLE iBeacon Advertisement Payload
     */
    fun encodeBeaconAdvertisement(
        uuidHex: String,
        major: Int,
        minor: Int,
        txPowerDbm: Int
    ): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(IBEACON_HEADER_BYTE1.toInt())
        out.write(IBEACON_HEADER_BYTE2.toInt())

        val uuidBytes = uuidHex.hexToByteArray()
        require(uuidBytes.size == 16) { "UUID must be exactly 16 bytes" }
        out.write(uuidBytes)

        out.write((major ushr 8) and 0xFF)
        out.write(major and 0xFF)

        out.write((minor ushr 8) and 0xFF)
        out.write(minor and 0xFF)

        out.write(txPowerDbm and 0xFF)
        return out.toByteArray()
    }

    /**
     * Decodes BLE iBeacon Advertisement Frame
     */
    fun decodeBeaconAdvertisement(
        payload: ByteArray,
        rssiDbm: Int
    ): BleAttendanceBeaconFrame? {
        if (payload.size < 23) return null
        if (payload[0] != IBEACON_HEADER_BYTE1 || payload[1] != IBEACON_HEADER_BYTE2) return null

        val uuidBytes = ByteArray(16)
        System.arraycopy(payload, 2, uuidBytes, 0, 16)
        val uuidHex = uuidBytes.toHexString()

        val major = ((payload[18].toInt() and 0xFF) shl 8) or (payload[19].toInt() and 0xFF)
        val minor = ((payload[20].toInt() and 0xFF) shl 8) or (payload[21].toInt() and 0xFF)
        val txPower = payload[22].toInt() // signed byte

        val dist = calculateDistanceMeters(rssiDbm, txPower)
        val zone = resolveProximityZone(dist)

        return BleAttendanceBeaconFrame(
            uuidHex = uuidHex,
            major = major,
            minor = minor,
            txPowerDbm = txPower,
            rssiDbm = rssiDbm,
            calculatedDistanceMeters = dist,
            proximityZone = zone,
            timestamp = System.currentTimeMillis()
        )
    }
}

// Utility Byte Extensions
fun ByteArray.toHexString(): String {
    val hexChars = "0123456789ABCDEF"
    val result = StringBuilder(size * 2)
    for (b in this) {
        val octet = b.toInt() and 0xFF
        result.append(hexChars[octet ushr 4])
        result.append(hexChars[octet and 0x0F])
    }
    return result.toString()
}

fun String.hexToByteArray(): ByteArray {
    val clean = this.replace(" ", "").replace("-", "").replace(":", "")
    val len = clean.length
    val data = ByteArray(len / 2)
    var i = 0
    while (i < len) {
        val high = Character.digit(clean[i], 16)
        val low = Character.digit(clean[i + 1], 16)
        data[i / 2] = ((high shl 4) + low).toByte()
        i += 2
    }
    return data
}
