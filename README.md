# تطبيق قلم الأكاديمية — تحليل أداء الطلاب وتحديد الف (`qalam-campus-smart-education-1289432`)
> ت](./ colors=#f59e0b) ت](./ analysis, support decision-makers & educators

- **Project ID**: `#1289432`
- **Commercial Archetype**: `smart_education`
- **Bound Hardware Peripheral**: `Smart Stylus Digitizer 4096-Level + BLE Beacon`
- **Package Name**: `sa.factory.qalamcampussmarted`
- **Zero-Emoji Invariant**: Strictly Enforced (100% Vector Icons)

## [Sidekick Autonomous Update]

### 1. Executive Summary & Architecture Modernization
Sidekick autonomously enhanced the `qalam-campus-smart-education-1289432` codebase (Smart Education, Tech Academies & Interactive Classrooms Archetype) by implementing a high-precision binary HID digitizer protocol kernel (4096 pressure levels + 2-axis tilt azimuth/altitude), a Bluetooth Low Energy (BLE) classroom attendance beacon receiver with log-distance path loss proximity analysis, and an interactive Saudi tech bootcamp management and ZATCA 15% VAT tuition billing engine.

### 2. Newly Built Features & Core Modules

#### A. Smart Stylus Digitizer & BLE Attendance Beacon Protocol Engine (`SmartStylusBeaconProtocol.kt`)
- **Binary HID Digitizer Packet Architecture**: Encodes and decodes 10-byte binary stylus telemetry packets with sync header `0xD7`, 16-bit X/Y canvas coordinates, 12-bit ADC raw pressure ($0 \dots 4095$ levels), barrel buttons (Tip, Eraser, Lasso, In-Range hover), signed tilt altitude ($-90^\circ \dots +90^\circ$), and 8-bit quantized tilt azimuth ($0^\circ \dots 360^\circ$).
- **Pressure Calibration Curves**: Implements dynamic stroke thickness interpolation across `LINEAR`, `SOFT_SENSITIVE` (rapid line weight response for Arabic calligraphy), and `FIRM_CONTROL` (strict tactile line work).
- **BLE Proximity Attendance Protocol**: Encodes and decodes 23-byte BLE advertisement payloads with 16-byte Academy UUID, Major (Room / Lab ID), Minor (Desk ID), and calibrated 1-meter TxPower.
- **Log-Distance Path Loss Model**: Calculates physical student proximity ($d = 10^{\frac{\text{TxPower} - \text{RSSI}}{10 \cdot n}}$) and maps signals into discrete attendance zones (`IMMEDIATE`, `NEAR`, `FAR`, `OUT_OF_RANGE`).

#### B. Saudi Tech Academy Classroom & Interactive Bootcamp Engine (`SmartClassroomBootcampEngine.kt`)
- **Curriculum & Bootcamp Tracks**: Coverage across `AI_ENGINEERING`, `CYBERSECURITY`, `FULLSTACK_MOBILE`, and `CLOUD_DEVOPS`.
- **Automated Proximity Attendance Verification**: Correlates student BLE tags with scheduled lecture room beacons and timestamps, applying a 15-minute grace period to distinguish between `PRESENT_ON_TIME`, `LATE_ATTENDANCE`, `ABSENT_OUT_OF_RANGE`, and `BEACON_MISMATCH`.
- **Interactive Whiteboard Vector Strokes**: Aggregates point telemetry, calculating stroke duration, average contact pressure, and calligraphic line weight.
- **ZATCA-Compliant Tuition Billing**: Computes base tuition, Saudi Vision 2030 talent scholarship deductions, 15% ZATCA VAT, and finalized grand totals.

#### C. Upgraded Hardware Driver Stream (`HardwareSensorDriver.kt`)
- Live simulation of UART/BLE radio broadcast, room 301 beacon packets, and 4096-level stylus drawing streams with tilt telemetry.

### 3. Verification & Test Suite
Comprehensive JUnit 4 test suite (`QalamCampusSmartEducationFeatureTest.kt`) verified:
- Binary 10-byte stylus packet serialization, sync header validation, and quantization tolerance.
- Non-linear stroke thickness curve calculations (`SOFT_SENSITIVE` vs `LINEAR` vs `FIRM_CONTROL`).
- BLE beacon packet decoding and 1-meter RSSI path loss distance calculation.
- Student attendance state transitions (on-time, late, room mismatch, out-of-range).
- Multi-point whiteboard stroke annotation metrics.
- ZATCA 15% VAT tuition invoice generation with scholarship discount.

### 4. Build & Execution Guide
```bash
# Set Java 17 and Kotlin classpath
export JAVA_HOME=/usr/lib/jvm/java-1.17.0-openjdk-arm64
export PATH=$JAVA_HOME/bin:$PATH
CP="/usr/share/java/junit4-4.13.2.jar:/usr/share/java/hamcrest-2.2.jar:/usr/share/java/kotlin-stdlib.jar"

# Compile Kotlin modules
kotlinc -Xskip-metadata-version-check -cp "$CP" -d /tmp/qalam_test_bin \
  app/src/main/java/sa/factory/qalamcampussmarted/hardware/digitizer/SmartStylusBeaconProtocol.kt \
  app/src/main/java/sa/factory/qalamcampussmarted/domain/academy/SmartClassroomBootcampEngine.kt \
  app/src/test/java/sa/factory/qalamcampussmarted/QalamCampusSmartEducationFeatureTest.kt

# Run verification suite
java -cp "/tmp/qalam_test_bin:$CP" org.junit.runner.JUnitCore sa.factory.qalamcampussmarted.QalamCampusSmartEducationFeatureTest
```


## [Sidekick Autonomous Update]

### Summary of Completed Fixes and Enhancements
1. **Integrated Stylus Pressure Dynamics Engine**:
   - Built `StylusPressureCurveEngine.kt` mapping 12-bit ADC stylus pressure (0..4095) into calibrated digital ink stroke width, opacity, and velocity dampening across linear, soft, and firm calligraphy profiles.

2. **Implemented BLE Attendance Proximity Tracker**:
   - Built `BleAttendanceBeaconTracker.kt` providing physical presence evaluation in smart classrooms via Log-Distance Path Loss (LDPL) signal attenuation modeling (`VERIFIED_PRESENT`, `PROXIMITY_MARGINAL`, `OUT_OF_CLASSROOM_BOUNDS`).

3. **Injected Production Gradle Wrapper & Fixed Imports**:
   - Added `gradlew`, `gradlew.bat`, and `gradle/wrapper/gradle-wrapper.properties` for automated cloud emulation.
   - Fixed Compose typography imports (`FontWeight`, `sp`) and string interpolation quoting.
   - Cleaned stray test artifacts from the production source tree.

4. **Added Unit Test Suite**:
   - Created `StylusPressureAndAttendanceTest.kt` verifying stroke curve dynamics and BLE beacon proximity distance calculations.

### Step-by-Step Build & Test Instructions
```bash
# Build Debug APK with Gradle
./gradlew :app:assembleDebug

# Run Unit Test Suite
./gradlew testDebugUnitTest
```
