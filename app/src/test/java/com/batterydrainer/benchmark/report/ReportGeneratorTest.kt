package com.batterydrainer.benchmark.report

import android.content.Context
import com.batterydrainer.benchmark.data.*
import com.batterydrainer.benchmark.monitor.BatteryMonitor
import com.google.common.truth.Truth.assertThat
import io.mockk.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/**
 * Unit tests for ReportGenerator
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class ReportGeneratorTest {

    private lateinit var context: Context
    private lateinit var reportGenerator: ReportGenerator
    private lateinit var mockBatteryMonitor: BatteryMonitor

    @Before
    fun setup() {
        context = RuntimeEnvironment.getApplication()
        reportGenerator = ReportGenerator(context)
        mockBatteryMonitor = mockk(relaxed = true)

        every { mockBatteryMonitor.getBatteryCapacity() } returns 4000
        every { mockBatteryMonitor.getBatteryHealth() } returns "Good"
    }

    @After
    fun tearDown() {
        clearAllMocks()
    }

    // ===== HELPER FUNCTIONS =====

    private fun createTestSession(
        readings: List<BatteryReading> = createSampleReadings(),
        profileId: String = "test_profile",
        profileName: String = "Test Profile",
        startTime: Long = 1000000L,
        endTime: Long? = 1600000L,
        startBatteryLevel: Int = 100,
        endBatteryLevel: Int? = 90
    ) = TestSession(
        id = "test-session-123",
        profileId = profileId,
        profileName = profileName,
        startTime = startTime,
        endTime = endTime,
        startBatteryLevel = startBatteryLevel,
        endBatteryLevel = endBatteryLevel,
        readings = readings.toMutableList(),
        stressors = listOf("CPU", "GPU"),
        wasAborted = false,
        abortReason = null
    )

    private fun createSampleReadings(): List<BatteryReading> {
        val baseTime = 1000000L
        return listOf(
            BatteryReading(
                timestamp = baseTime,
                level = 100,
                voltage = 4200,
                current = -500000,  // -500mA (discharging)
                temperature = 30f,
                isCharging = false,
                energyCounter = 1000000000L
            ),
            BatteryReading(
                timestamp = baseTime + 300000,  // +5 min
                level = 97,
                voltage = 4150,
                current = -600000,
                temperature = 35f,
                isCharging = false,
                energyCounter = 1050000000L
            ),
            BatteryReading(
                timestamp = baseTime + 600000,  // +10 min
                level = 93,
                voltage = 4100,
                current = -700000,
                temperature = 42f,
                isCharging = false,
                energyCounter = 1100000000L
            )
        )
    }

    // ===== REPORT GENERATION TESTS =====

    @Test
    fun `generateReport creates valid report`() {
        val session = createTestSession()
        val report = reportGenerator.generateReport(session, mockBatteryMonitor)

        assertThat(report).isNotNull()
        assertThat(report.session).isEqualTo(session)
        assertThat(report.summary).isNotNull()
        assertThat(report.charts).isNotNull()
        assertThat(report.deviceInfo).isNotNull()
    }

    @Test
    fun `generateReport handles empty readings`() {
        val session = createTestSession(readings = emptyList())
        val report = reportGenerator.generateReport(session, mockBatteryMonitor)

        assertThat(report.summary.totalDurationMinutes).isEqualTo(0.0)
        assertThat(report.summary.batteryDrainPercent).isEqualTo(0)
    }

    // ===== SUMMARY CALCULATION TESTS =====

    @Test
    fun `summary calculates battery drain correctly`() {
        val session = createTestSession(
            startBatteryLevel = 100,
            endBatteryLevel = 90
        )
        val report = reportGenerator.generateReport(session, mockBatteryMonitor)

        assertThat(report.summary.batteryDrainPercent).isEqualTo(10)
    }

    @Test
    fun `summary calculates duration correctly`() {
        val session = createTestSession(
            startTime = 0L,
            endTime = 600000L  // 10 minutes in ms
        )
        val report = reportGenerator.generateReport(session, mockBatteryMonitor)

        assertThat(report.summary.totalDurationMinutes).isEqualTo(10.0)
    }

    @Test
    fun `summary detects thermal throttling when peak temp exceeds threshold`() {
        val readings = listOf(
            BatteryReading(
                timestamp = 0L,
                level = 100,
                voltage = 4200,
                current = -500000,
                temperature = 44f,  // Above 43C threshold
                isCharging = false
            )
        )
        val session = createTestSession(readings = readings)
        val report = reportGenerator.generateReport(session, mockBatteryMonitor)

        assertThat(report.summary.thermalThrottlingOccurred).isTrue()
    }

    @Test
    fun `summary does not flag thermal throttling for normal temps`() {
        val readings = listOf(
            BatteryReading(
                timestamp = 0L,
                level = 100,
                voltage = 4200,
                current = -500000,
                temperature = 35f,  // Normal temp
                isCharging = false
            )
        )
        val session = createTestSession(readings = readings)
        val report = reportGenerator.generateReport(session, mockBatteryMonitor)

        assertThat(report.summary.thermalThrottlingOccurred).isFalse()
    }

    // ===== JSON EXPORT TESTS =====

    @Test
    fun `exportToJson produces valid JSON`() {
        val session = createTestSession()
        val report = reportGenerator.generateReport(session, mockBatteryMonitor)
        val json = reportGenerator.exportToJson(report)

        assertThat(json).isNotEmpty()
        assertThat(json).contains("session")
        assertThat(json).contains("summary")
        assertThat(json).contains("deviceInfo")
    }

    @Test
    fun `exportToJson contains profile information`() {
        val session = createTestSession(profileName = "Heavy Gaming")
        val report = reportGenerator.generateReport(session, mockBatteryMonitor)
        val json = reportGenerator.exportToJson(report)

        assertThat(json).contains("Heavy Gaming")
    }

    // ===== CSV EXPORT TESTS =====

    @Test
    fun `exportToCsv produces valid CSV`() {
        val session = createTestSession()
        val report = reportGenerator.generateReport(session, mockBatteryMonitor)
        val csv = reportGenerator.exportToCsv(report)

        assertThat(csv).isNotEmpty()
        assertThat(csv).contains("Timestamp")
        assertThat(csv).contains("Battery %")
        assertThat(csv).contains("Voltage")
        assertThat(csv).contains("Current")
        assertThat(csv).contains("Temperature")
    }

    @Test
    fun `exportToCsv has correct number of data rows`() {
        val readings = createSampleReadings()
        val session = createTestSession(readings = readings)
        val report = reportGenerator.generateReport(session, mockBatteryMonitor)
        val csv = reportGenerator.exportToCsv(report)

        val lines = csv.trim().lines()
        // 1 header + 3 data rows
        assertThat(lines.size).isEqualTo(4)
    }

    @Test
    fun `exportToCsv handles empty readings`() {
        val session = createTestSession(readings = emptyList())
        val report = reportGenerator.generateReport(session, mockBatteryMonitor)
        val csv = reportGenerator.exportToCsv(report)

        val lines = csv.trim().lines()
        assertThat(lines.size).isEqualTo(1)  // Only header
    }

    // ===== HTML EXPORT TESTS =====

    @Test
    fun `exportToHtml produces valid HTML`() {
        val session = createTestSession()
        val report = reportGenerator.generateReport(session, mockBatteryMonitor)
        val html = reportGenerator.exportToHtml(report)

        assertThat(html).contains("<!DOCTYPE html>")
        assertThat(html).contains("<html")
        assertThat(html).contains("</html>")
    }

    @Test
    fun `exportToHtml contains profile name`() {
        val session = createTestSession(profileName = "CPU Meltdown")
        val report = reportGenerator.generateReport(session, mockBatteryMonitor)
        val html = reportGenerator.exportToHtml(report)

        assertThat(html).contains("CPU Meltdown")
    }

    @Test
    fun `exportToHtml contains ChartJS script`() {
        val session = createTestSession()
        val report = reportGenerator.generateReport(session, mockBatteryMonitor)
        val html = reportGenerator.exportToHtml(report)

        assertThat(html).contains("chart.js")
        assertThat(html).contains("batteryChart")
        assertThat(html).contains("tempChart")
    }

    @Test
    fun `exportToHtml contains summary cards`() {
        val session = createTestSession()
        val report = reportGenerator.generateReport(session, mockBatteryMonitor)
        val html = reportGenerator.exportToHtml(report)

        assertThat(html).contains("Duration")
        assertThat(html).contains("Battery Drop")
        assertThat(html).contains("Avg Current")
        assertThat(html).contains("Peak Temp")
    }

    // ===== TEXT EXPORT TESTS =====

    @Test
    fun `exportToText produces readable output`() {
        val session = createTestSession(profileName = "Test Profile")
        val report = reportGenerator.generateReport(session, mockBatteryMonitor)
        val text = reportGenerator.exportToText(report)

        assertThat(text).contains("BatteryDrainer Report")
        assertThat(text).contains("Profile: Test Profile")
        assertThat(text).contains("Duration:")
        assertThat(text).contains("Battery drop:")
    }

    @Test
    fun `exportToText includes stressors`() {
        val session = createTestSession()
        val report = reportGenerator.generateReport(session, mockBatteryMonitor)
        val text = reportGenerator.exportToText(report)

        assertThat(text).contains("Stressors: CPU, GPU")
    }

    // ===== CHARTS DATA TESTS =====

    @Test
    fun `charts contain discharge curve data`() {
        val session = createTestSession()
        val report = reportGenerator.generateReport(session, mockBatteryMonitor)

        assertThat(report.charts.dischargeCurve).isNotEmpty()
    }

    @Test
    fun `charts contain temperature curve data`() {
        val session = createTestSession()
        val report = reportGenerator.generateReport(session, mockBatteryMonitor)

        assertThat(report.charts.temperatureCurve).isNotEmpty()
    }

    @Test
    fun `chart points have correct time offset from start`() {
        val baseTime = 1000000L
        val readings = listOf(
            BatteryReading(
                timestamp = baseTime,
                level = 100,
                voltage = 4200,
                current = -500000,
                temperature = 30f,
                isCharging = false
            ),
            BatteryReading(
                timestamp = baseTime + 60000,  // +1 minute
                level = 99,
                voltage = 4190,
                current = -510000,
                temperature = 31f,
                isCharging = false
            )
        )
        val session = createTestSession(readings = readings, startTime = baseTime)
        val report = reportGenerator.generateReport(session, mockBatteryMonitor)

        val firstPoint = report.charts.dischargeCurve.first()
        val secondPoint = report.charts.dischargeCurve[1]

        assertThat(firstPoint.x).isEqualTo(0f)
        assertThat(secondPoint.x).isEqualTo(1f)  // 1 minute
    }

    // ===== RECOMMENDATIONS TESTS =====

    @Test
    fun `recommendations include thermal warning when throttling occurred`() {
        val readings = listOf(
            BatteryReading(
                timestamp = 0L,
                level = 100,
                voltage = 4200,
                current = -500000,
                temperature = 46f,  // High temp
                isCharging = false
            )
        )
        val session = createTestSession(readings = readings)
        val report = reportGenerator.generateReport(session, mockBatteryMonitor)

        assertThat(report.recommendations.any { it.contains("thermal") }).isTrue()
    }

    @Test
    fun `recommendations include abort reason when test was aborted`() {
        val session = TestSession(
            id = "test-aborted",
            profileId = "test",
            profileName = "Test",
            startTime = 0L,
            endTime = 1000L,
            startBatteryLevel = 100,
            endBatteryLevel = 99,
            readings = mutableListOf(),
            stressors = listOf("CPU"),
            wasAborted = true,
            abortReason = "User stopped"
        )
        val report = reportGenerator.generateReport(session, mockBatteryMonitor)

        assertThat(report.recommendations.any { it.contains("aborted") }).isTrue()
    }

    // ===== DEVICE INFO TESTS =====

    @Test
    fun `deviceInfo contains manufacturer and model`() {
        val session = createTestSession()
        val report = reportGenerator.generateReport(session, mockBatteryMonitor)

        assertThat(report.deviceInfo.manufacturer).isNotEmpty()
        assertThat(report.deviceInfo.model).isNotEmpty()
    }

    @Test
    fun `deviceInfo includes battery capacity from monitor`() {
        val session = createTestSession()
        val report = reportGenerator.generateReport(session, mockBatteryMonitor)

        assertThat(report.deviceInfo.batteryCapacityMah).isEqualTo(4000)
    }

    @Test
    fun `deviceInfo includes battery health from monitor`() {
        val session = createTestSession()
        val report = reportGenerator.generateReport(session, mockBatteryMonitor)

        assertThat(report.deviceInfo.batteryHealth).isEqualTo("Good")
    }

    // ===== FILE SAVING TESTS =====

    @Test
    fun `saveReport creates file with correct extension for JSON`() {
        val session = createTestSession()
        val report = reportGenerator.generateReport(session, mockBatteryMonitor)

        val file = reportGenerator.saveReport(report, ExportFormat.JSON)

        assertThat(file.name).endsWith(".json")
        assertThat(file.exists()).isTrue()

        // Cleanup
        file.delete()
    }

    @Test
    fun `saveReport creates file with correct extension for CSV`() {
        val session = createTestSession()
        val report = reportGenerator.generateReport(session, mockBatteryMonitor)

        val file = reportGenerator.saveReport(report, ExportFormat.CSV)

        assertThat(file.name).endsWith(".csv")

        // Cleanup
        file.delete()
    }

    @Test
    fun `saveReport creates file with correct extension for HTML`() {
        val session = createTestSession()
        val report = reportGenerator.generateReport(session, mockBatteryMonitor)

        val file = reportGenerator.saveReport(report, ExportFormat.HTML)

        assertThat(file.name).endsWith(".html")

        // Cleanup
        file.delete()
    }

    @Test
    fun `saveReportBundle creates multiple files`() {
        val session = createTestSession()
        val report = reportGenerator.generateReport(session, mockBatteryMonitor)

        val files = reportGenerator.saveReportBundle(report)

        assertThat(files).hasSize(4)
        assertThat(files.any { it.name.endsWith(".html") }).isTrue()
        assertThat(files.any { it.name.endsWith(".csv") }).isTrue()
        assertThat(files.any { it.name.endsWith(".json") }).isTrue()
        assertThat(files.any { it.name.endsWith(".txt") }).isTrue()

        // Cleanup
        files.forEach { it.delete() }
    }
}

/**
 * Tests for ExportFormat enum
 */
class ExportFormatTest {

    @Test
    fun `ExportFormat has all expected values`() {
        val formats = ExportFormat.entries
        assertThat(formats).contains(ExportFormat.JSON)
        assertThat(formats).contains(ExportFormat.CSV)
        assertThat(formats).contains(ExportFormat.HTML)
        assertThat(formats).contains(ExportFormat.PDF)
        assertThat(formats).contains(ExportFormat.TEXT)
    }

    @Test
    fun `ExportFormat has exactly 5 formats`() {
        assertThat(ExportFormat.entries).hasSize(5)
    }
}
