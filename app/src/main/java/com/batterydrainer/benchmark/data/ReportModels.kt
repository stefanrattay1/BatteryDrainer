package com.batterydrainer.benchmark.data

/**
 * Test report generated after a benchmark session
 */
data class TestReport(
    val session: TestSession,
    val summary: ReportSummary,
    val charts: ReportCharts,
    val deviceInfo: DeviceInfo,
    val recommendations: List<String>
)

/**
 * Summary statistics from the test
 */
data class ReportSummary(
    val totalDurationMinutes: Double,
    val batteryDrainPercent: Int,
    val averageCurrentMa: Double,
    val peakCurrentMa: Double,
    val averageTemperatureCelsius: Double,
    val peakTemperatureCelsius: Double,
    val estimatedScreenOnTimeHours: Double,
    val drainRatePercentPerHour: Double,
    val thermalThrottlingOccurred: Boolean,
    val totalEnergyUsedMwh: Double?
)

/**
 * Chart data for visualization
 */
data class ReportCharts(
    val dischargeCurve: List<ChartPoint>,      // Time vs Battery %
    val currentCurve: List<ChartPoint>,        // Time vs Current (mA)
    val temperatureCurve: List<ChartPoint>,    // Time vs Temperature
    val voltageCurve: List<ChartPoint>         // Time vs Voltage
)

/**
 * Single point on a chart
 */
data class ChartPoint(
    val x: Float,  // Time in minutes from start
    val y: Float   // Value
)

/**
 * Device information for the report
 */
data class DeviceInfo(
    val manufacturer: String,
    val model: String,
    val androidVersion: String,
    val sdkVersion: Int,
    val batteryCapacityMah: Int?,
    val batteryHealth: String,
    val cpuCores: Int,
    val totalRamMb: Long
)

/**
 * Export format options
 */
enum class ExportFormat {
    JSON,
    CSV,
    PDF,
    HTML,
    TEXT
}
