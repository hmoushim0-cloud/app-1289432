package sa.factory.qalamcampussmarted.hardware

import sa.factory.qalamcampussmarted.hardware.digitizer.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.Locale
import kotlin.random.Random

data class HardwareTelemetryFrame(
    val peripheralName: String,
    val connectionState: String,
    val livePayload: String,
    val timestamp: Long = System.currentTimeMillis()
)

class HardwareSensorDriver {
    private val peripheral = "Smart Stylus Digitizer 4096-Level + BLE Beacon"
    private val protocol = SmartStylusBeaconProtocol()

    fun getProtocol(): SmartStylusBeaconProtocol = protocol

    fun observePeripheralStream(): Flow<HardwareTelemetryFrame> = flow {
        emit(HardwareTelemetryFrame(peripheral, "CONNECTING", "BLE & HID Digitizer Driver: Initializing UART & BLE Radio..."))
        delay(300)

        // Generate sample BLE Beacon Frame
        val classroomUuid = "E2C56DB5DFFB48D2B060D0F5A71096E0"
        val beaconAd = protocol.encodeBeaconAdvertisement(classroomUuid, 301, 12, -59)
        emit(HardwareTelemetryFrame(
            peripheral,
            "ONLINE",
            "BLE Beacon Broadcast UP: Room 301 (Lab A) | TxPower: -59dBm | AdBytes: ${beaconAd.toHexString().substring(0, 16)}..."
        ))

        var frameCounter = 1
        while (true) {
            delay(1500)
            val jitter = Random.nextInt(1, 10)
            val simulatedPressure = Random.nextInt(300, 3800)
            val tiltAlt = Random.nextInt(20, 65)
            val tiltAz = Random.nextInt(0, 359)

            val point = StylusDigitizerPoint(
                x = 10000 + frameCounter * 50,
                y = 15000 + frameCounter * 30,
                rawPressure = simulatedPressure,
                normalizedPressure = Math.round(simulatedPressure.toDouble() / 4095.0 * 1000.0) / 1000.0,
                tiltAltitudeDeg = tiltAlt,
                tiltAzimuthDeg = tiltAz,
                buttons = StylusButtonState(isTipDown = true, isInRange = true),
                strokeThicknessPt = protocol.calculateStrokeThickness(simulatedPressure)
            )

            val rawPacket = protocol.encodeStylusPoint(point)
            val decoded = protocol.decodeStylusPoint(rawPacket)

            val simulatedRssi = -65 + Random.nextInt(-8, 5)
            val beaconFrame = protocol.decodeBeaconAdvertisement(beaconAd, simulatedRssi)

            val payload = String.format(
                Locale.US,
                "Stylus Frame #%d: Pressure %d/4095 (%.1f pt) | Tilt: %d°/%d° | BLE Beacon: RSSI %d dBm (%.2fm - %s) (+%dms)",
                frameCounter,
                decoded.rawPressure,
                decoded.strokeThicknessPt,
                decoded.tiltAltitudeDeg,
                decoded.tiltAzimuthDeg,
                simulatedRssi,
                beaconFrame?.calculatedDistanceMeters ?: 1.8,
                beaconFrame?.proximityZone?.name ?: "NEAR",
                jitter
            )

            emit(HardwareTelemetryFrame(peripheral, "ACTIVE_STREAMING", payload))
            frameCounter++
        }
    }
}
