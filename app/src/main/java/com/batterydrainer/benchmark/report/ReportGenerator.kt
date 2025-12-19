package com.batterydrainer.benchmark.report

import android.content.Context
import android.os.Build
import com.batterydrainer.benchmark.data.*
import com.batterydrainer.benchmark.monitor.BatteryMonitor
import com.google.gson.GsonBuilder
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Generates professional reports from test sessions
 */
class ReportGenerator(private val context: Context) {
    
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    private val fileDateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
    
    /**
     * Generate a complete test report
     */
    fun generateReport(session: TestSession, batteryMonitor: BatteryMonitor): TestReport {
        val summary = generateSummary(session)
        val charts = generateCharts(session)
        val deviceInfo = getDeviceInfo(batteryMonitor)
        val recommendations = generateRecommendations(session, summary)
        
        return TestReport(
            session = session,
            summary = summary,
            charts = charts,
            deviceInfo = deviceInfo,
            recommendations = recommendations
        )
    }
    
    /**
     * Export report to JSON
     */
    fun exportToJson(report: TestReport): String {
        return gson.toJson(report)
    }
    
    /**
     * Export report to CSV (readings only)
     */
    fun exportToCsv(report: TestReport): String {
        val sb = StringBuilder()
        
        // Header
        sb.appendLine("Timestamp,Battery %,Voltage (mV),Current (µA),Temperature (°C),Charging")
        
        // Data rows
        for (reading in report.session.readings) {
            sb.appendLine(
                "${dateFormat.format(Date(reading.timestamp))}," +
                "${reading.level}," +
                "${reading.voltage}," +
                "${reading.current}," +
                "${reading.temperature}," +
                "${reading.isCharging}"
            )
        }
        
        return sb.toString()
    }

    /**
     * Export a compact plain-text summary (for quick sharing/logging).
     */
    fun exportToText(report: TestReport): String {
        return buildString {
            appendLine("BatteryDrainer Report")
            appendLine("Profile: ${report.session.profileName}")
            appendLine("Start: ${dateFormat.format(Date(report.session.startTime))}")
            appendLine("End: ${report.session.endTime?.let { dateFormat.format(Date(it)) } ?: "running"}")
            appendLine("Duration: ${String.format("%.1f", report.summary.totalDurationMinutes)} min")
            appendLine("Battery drop: ${report.summary.batteryDrainPercent}%")
            appendLine("Avg current: ${String.format("%.0f", report.summary.averageCurrentMa)} mA")
            appendLine("Peak temp: ${String.format("%.1f", report.summary.peakTemperatureCelsius)} °C")
            appendLine("Drain rate: ${String.format("%.1f", report.summary.drainRatePercentPerHour)} %/hr")
            if (report.summary.totalEnergyUsedMwh != null) {
                appendLine("Energy used: ${String.format("%.1f", report.summary.totalEnergyUsedMwh)} mWh")
            }
            appendLine("Stressors: ${report.session.stressors.joinToString()}")
        }
    }
    
    /**
     * Export report to HTML
     */
    fun exportToHtml(report: TestReport): String {
        return buildString {
            appendLine("<!DOCTYPE html>")
            appendLine("<html><head>")
            appendLine("<meta charset='UTF-8'>")
            appendLine("<meta name='viewport' content='width=device-width, initial-scale=1'>")
            appendLine("<title>Battery Drain Report - ${report.session.profileName}</title>")
            appendLine("<style>")
            appendLine(CSS_STYLES)
            appendLine("</style>")
            appendLine("<script src='https://cdn.jsdelivr.net/npm/chart.js'></script>")
            appendLine("</head><body>")
            
            // Header
            appendLine("<div class='header'>")
            appendLine("<h1>🔋 Battery Drain Report</h1>")
            appendLine("<p>Profile: ${report.session.profileName}</p>")
            appendLine("<p>Date: ${dateFormat.format(Date(report.session.startTime))}</p>")
            appendLine("</div>")
            
            // Summary Cards
            appendLine("<div class='summary-grid'>")
            appendCard("Duration", "${String.format("%.1f", report.summary.totalDurationMinutes)} min", "⏱️")
            appendCard("Battery Drop", "${report.summary.batteryDrainPercent}%", "🔋")
            appendCard("Avg Current", "${String.format("%.0f", report.summary.averageCurrentMa)} mA", "⚡")
            appendCard("Peak Temp", "${String.format("%.1f", report.summary.peakTemperatureCelsius)}°C", "🌡️")
            appendCard("Est. SOT", "${String.format("%.1f", report.summary.estimatedScreenOnTimeHours)} hrs", "📱")
            appendCard("Drain Rate", "${String.format("%.1f", report.summary.drainRatePercentPerHour)}%/hr", "📉")
            appendLine("</div>")
            
            // Device Info
            appendLine("<div class='section'>")
            appendLine("<h2>Device Information</h2>")
            appendLine("<table>")
            appendLine("<tr><td>Device</td><td>${report.deviceInfo.manufacturer} ${report.deviceInfo.model}</td></tr>")
            appendLine("<tr><td>Android</td><td>${report.deviceInfo.androidVersion} (SDK ${report.deviceInfo.sdkVersion})</td></tr>")
            appendLine("<tr><td>Battery Capacity</td><td>${report.deviceInfo.batteryCapacityMah ?: "Unknown"} mAh</td></tr>")
            appendLine("<tr><td>Battery Health</td><td>${report.deviceInfo.batteryHealth}</td></tr>")
            appendLine("<tr><td>CPU Cores</td><td>${report.deviceInfo.cpuCores}</td></tr>")
            appendLine("</table>")
            appendLine("</div>")
            
            // Stressors Used
            appendLine("<div class='section'>")
            appendLine("<h2>Stressors Used</h2>")
            appendLine("<ul>")
            for (stressor in report.session.stressors) {
                appendLine("<li>$stressor</li>")
            }
            appendLine("</ul>")
            appendLine("</div>")
            
            // Charts
            appendLine("<div class='section'>")
            appendLine("<h2>Discharge Curve</h2>")
            appendLine("<canvas id='batteryChart'></canvas>")
            appendLine("</div>")
            
            appendLine("<div class='section'>")
            appendLine("<h2>Temperature Over Time</h2>")
            appendLine("<canvas id='tempChart'></canvas>")
            appendLine("</div>")
            
            // Recommendations
            if (report.recommendations.isNotEmpty()) {
                appendLine("<div class='section recommendations'>")
                appendLine("<h2>Recommendations</h2>")
                appendLine("<ul>")
                for (rec in report.recommendations) {
                    appendLine("<li>$rec</li>")
                }
                appendLine("</ul>")
                appendLine("</div>")
            }
            
            // Chart.js initialization
            appendLine("<script>")
            appendLine(generateChartJs(report))
            appendLine("</script>")
            
            appendLine("</body></html>")
        }
    }
    
    /**
     * Save report to file
     */
    fun saveReport(report: TestReport, format: ExportFormat): File {
        val timestamp = fileDateFormat.format(Date(report.session.startTime))
        
        val (content, extension) = when (format) {
            ExportFormat.JSON -> exportToJson(report) to "json"
            ExportFormat.CSV -> exportToCsv(report) to "csv"
            ExportFormat.HTML -> exportToHtml(report) to "html"
            ExportFormat.PDF -> exportToHtml(report) to "html" // PDF generation requires iText
            ExportFormat.TEXT -> exportToText(report) to "txt"
        }
        
        val filename = "battery_report_${report.session.profileId}_$timestamp.$extension"
        val file = File(context.getExternalFilesDir(null), "reports/$filename")
        file.parentFile?.mkdirs()
        file.writeText(content)
        
        return file
    }

    /**
     * Save a bundle of outputs (HTML, CSV, JSON, TXT) for easy export.
     */
    fun saveReportBundle(report: TestReport): List<File> {
        return listOf(
            saveReport(report, ExportFormat.HTML),
            saveReport(report, ExportFormat.CSV),
            saveReport(report, ExportFormat.JSON),
            saveReport(report, ExportFormat.TEXT)
        )
    }
    
    private fun generateSummary(session: TestSession): ReportSummary {
        val readings = session.readings
        if (readings.isEmpty()) {
            return ReportSummary(
                totalDurationMinutes = 0.0,
                batteryDrainPercent = 0,
                averageCurrentMa = 0.0,
                peakCurrentMa = 0.0,
                averageTemperatureCelsius = 0.0,
                peakTemperatureCelsius = 0.0,
                estimatedScreenOnTimeHours = 0.0,
                drainRatePercentPerHour = 0.0,
                thermalThrottlingOccurred = false,
                totalEnergyUsedMwh = null
            )
        }
        
        val durationMs = (session.endTime ?: System.currentTimeMillis()) - session.startTime
        val durationMinutes = durationMs / 60000.0
        
        val batteryDrain = session.startBatteryLevel - (session.endBatteryLevel ?: readings.last().level)
        
        // Current is in microamps, convert to milliamps
        val currents = readings.map { it.current / 1000.0 }
        val avgCurrent = currents.average()
        val peakCurrent = currents.maxOrNull() ?: 0.0
        
        val avgTemp = readings.map { it.temperature.toDouble() }.average()
        val peakTemp = readings.maxOfOrNull { it.temperature } ?: 0f
        
        // Estimate SOT: If we drained X% in Y hours, SOT = 100 / (X/Y)
        val durationHours = durationMinutes / 60.0
        val drainRate = if (durationHours > 0) batteryDrain / durationHours else 0.0
        val estimatedSot = if (drainRate > 0) 100.0 / drainRate else 0.0
        
        val thermalThrottling = peakTemp > 43f
        
        // Energy calculation if available
        val energyStart = readings.firstOrNull()?.energyCounter
        val energyEnd = readings.lastOrNull()?.energyCounter
        val totalEnergy = if (energyStart != null && energyEnd != null && energyEnd > energyStart) {
            (energyEnd - energyStart) / 1_000_000.0 // Convert nanowatt-hours to milliwatt-hours
        } else null
        
        return ReportSummary(
            totalDurationMinutes = durationMinutes,
            batteryDrainPercent = batteryDrain,
            averageCurrentMa = avgCurrent,
            peakCurrentMa = peakCurrent,
            averageTemperatureCelsius = avgTemp,
            peakTemperatureCelsius = peakTemp.toDouble(),
            estimatedScreenOnTimeHours = estimatedSot,
            drainRatePercentPerHour = drainRate,
            thermalThrottlingOccurred = thermalThrottling,
            totalEnergyUsedMwh = totalEnergy
        )
    }
    
    private fun generateCharts(session: TestSession): ReportCharts {
        val readings = session.readings
        val startTime = session.startTime
        
        fun toMinutes(timestamp: Long): Float = ((timestamp - startTime) / 60000f)
        
        return ReportCharts(
            dischargeCurve = readings.map { ChartPoint(toMinutes(it.timestamp), it.level.toFloat()) },
            currentCurve = readings.map { ChartPoint(toMinutes(it.timestamp), it.current / 1000f) },
            temperatureCurve = readings.map { ChartPoint(toMinutes(it.timestamp), it.temperature) },
            voltageCurve = readings.map { ChartPoint(toMinutes(it.timestamp), it.voltage.toFloat()) }
        )
    }
    
    private fun getDeviceInfo(batteryMonitor: BatteryMonitor): DeviceInfo {
        val runtime = Runtime.getRuntime()
        return DeviceInfo(
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
            androidVersion = Build.VERSION.RELEASE,
            sdkVersion = Build.VERSION.SDK_INT,
            batteryCapacityMah = batteryMonitor.getBatteryCapacity(),
            batteryHealth = batteryMonitor.getBatteryHealth(),
            cpuCores = runtime.availableProcessors(),
            totalRamMb = runtime.maxMemory() / (1024 * 1024)
        )
    }
    
    private fun generateRecommendations(session: TestSession, summary: ReportSummary): List<String> {
        val recommendations = mutableListOf<String>()
        
        if (summary.thermalThrottlingOccurred) {
            recommendations.add("⚠️ Device experienced thermal throttling. Consider testing in a cooler environment.")
        }
        
        if (summary.drainRatePercentPerHour > 20) {
            recommendations.add("🔋 High drain rate detected. This profile is very demanding on battery.")
        }
        
        if (summary.estimatedScreenOnTimeHours < 3) {
            recommendations.add("📱 Estimated screen-on time is below 3 hours under this load.")
        }
        
        if (summary.peakCurrentMa > 2000) {
            recommendations.add("⚡ Peak current draw exceeded 2A. This is typical for gaming/heavy workloads.")
        }
        
        if (session.wasAborted) {
            recommendations.add("⏹️ Test was aborted: ${session.abortReason ?: "Unknown reason"}")
        }
        
        return recommendations
    }
    
    private fun StringBuilder.appendCard(title: String, value: String, icon: String) {
        appendLine("<div class='card'>")
        appendLine("<div class='card-icon'>$icon</div>")
        appendLine("<div class='card-value'>$value</div>")
        appendLine("<div class='card-title'>$title</div>")
        appendLine("</div>")
    }
    
    private fun generateChartJs(report: TestReport): String {
        val batteryData = report.charts.dischargeCurve.map { 
            "{ x: ${it.x}, y: ${it.y} }" 
        }.joinToString(",")
        
        val tempData = report.charts.temperatureCurve.map { 
            "{ x: ${it.x}, y: ${it.y} }" 
        }.joinToString(",")
        
        return """
            new Chart(document.getElementById('batteryChart'), {
                type: 'line',
                data: {
                    datasets: [{
                        label: 'Battery %',
                        data: [$batteryData],
                        borderColor: '#4CAF50',
                        fill: false
                    }]
                },
                options: {
                    scales: {
                        x: { type: 'linear', title: { display: true, text: 'Time (minutes)' } },
                        y: { min: 0, max: 100, title: { display: true, text: 'Battery %' } }
                    }
                }
            });
            
            new Chart(document.getElementById('tempChart'), {
                type: 'line',
                data: {
                    datasets: [{
                        label: 'Temperature °C',
                        data: [$tempData],
                        borderColor: '#FF5722',
                        fill: false
                    }]
                },
                options: {
                    scales: {
                        x: { type: 'linear', title: { display: true, text: 'Time (minutes)' } },
                        y: { title: { display: true, text: 'Temperature (°C)' } }
                    }
                }
            });
        """.trimIndent()
    }
    
    companion object {
        private val CSS_STYLES = """
            * { box-sizing: border-box; }
            body { 
                font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
                margin: 0; padding: 20px;
                background: #f5f5f5;
                color: #333;
            }
            .header {
                background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
                color: white;
                padding: 30px;
                border-radius: 12px;
                margin-bottom: 20px;
            }
            .header h1 { margin: 0 0 10px 0; }
            .header p { margin: 5px 0; opacity: 0.9; }
            .summary-grid {
                display: grid;
                grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
                gap: 15px;
                margin-bottom: 20px;
            }
            .card {
                background: white;
                border-radius: 12px;
                padding: 20px;
                text-align: center;
                box-shadow: 0 2px 8px rgba(0,0,0,0.1);
            }
            .card-icon { font-size: 2em; margin-bottom: 10px; }
            .card-value { font-size: 1.5em; font-weight: bold; color: #333; }
            .card-title { font-size: 0.9em; color: #666; margin-top: 5px; }
            .section {
                background: white;
                border-radius: 12px;
                padding: 20px;
                margin-bottom: 20px;
                box-shadow: 0 2px 8px rgba(0,0,0,0.1);
            }
            .section h2 { margin-top: 0; color: #333; }
            table { width: 100%; border-collapse: collapse; }
            table td { padding: 10px; border-bottom: 1px solid #eee; }
            table td:first-child { font-weight: 500; color: #666; }
            ul { margin: 0; padding-left: 20px; }
            li { margin: 8px 0; }
            .recommendations { background: #fff3e0; }
            .recommendations h2 { color: #e65100; }
            canvas { max-width: 100%; }
        """.trimIndent()
    }
}
