package com.batterydrainer.benchmark.ui

import android.Manifest
import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.hardware.camera2.CameraAccessException
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.VibrationEffect
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.batterydrainer.benchmark.BuildConfig
import com.batterydrainer.benchmark.R
import com.batterydrainer.benchmark.data.StressProfile
import com.batterydrainer.benchmark.data.TestConfig
import com.batterydrainer.benchmark.databinding.ActivityMainBinding
import com.batterydrainer.benchmark.monitor.BatteryMonitor
import com.batterydrainer.benchmark.monitor.ThermalProtection
import com.batterydrainer.benchmark.report.ReportGenerator
import com.batterydrainer.benchmark.service.DrainerService
import com.batterydrainer.benchmark.util.SystemStatusChecker
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.DecimalFormat

class MainActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainBinding
    private lateinit var batteryMonitor: BatteryMonitor
    private lateinit var thermalProtection: ThermalProtection
    private lateinit var reportGenerator: ReportGenerator
    private lateinit var systemStatusChecker: SystemStatusChecker
    
    private var drainerService: DrainerService? = null
    private var serviceBound = false
    
    private var selectedProfile: StressProfile = StressProfile.PRESETS.first()
    
    private val currentFormat = DecimalFormat("#,##0")
    private val tempFormat = DecimalFormat("0.0")
    
    // Quick toggle states
    private var isFlashlightOn = false
    private var isVibrating = false
    private var vibrationJob: Job? = null

    private fun isRunningUiTest(): Boolean = try {
        Class.forName("androidx.test.platform.app.InstrumentationRegistry")
        true
    } catch (_: Throwable) {
        false
    }
    
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as DrainerService.LocalBinder
            drainerService = binder.getService()
            serviceBound = true
            observeServiceState()
        }
        
        override fun onServiceDisconnected(name: ComponentName?) {
            drainerService = null
            serviceBound = false
            updateTestControls(false)
        }
    }
    
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (!allGranted) {
            Toast.makeText(this, "Some permissions were denied. Features may be limited.", Toast.LENGTH_LONG).show()
        }
        // Refresh system status after permission changes
        updateSystemStatus()
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        batteryMonitor = BatteryMonitor(this)
        thermalProtection = ThermalProtection(this)
        reportGenerator = ReportGenerator(this)
        systemStatusChecker = SystemStatusChecker(this)
        
        setupUI()
        requestPermissions()
        startMonitoring()
    }
    
    override fun onStart() {
        super.onStart()
        // Bind to DrainerService if it's running
        if (DrainerService.isRunning()) {
            bindService(
                Intent(this, DrainerService::class.java),
                serviceConnection,
                Context.BIND_AUTO_CREATE
            )
        }
    }
    
    override fun onResume() {
        super.onResume()
        // Refresh system status when returning to the app
        updateSystemStatus()
    }
    
    override fun onStop() {
        super.onStop()
        if (serviceBound) {
            unbindService(serviceConnection)
            serviceBound = false
        }
        // Turn off flashlight and vibration when leaving the app
        turnOffFlashlight()
        stopVibrationLoop()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        batteryMonitor.stopMonitoring()
        thermalProtection.stopMonitoring()
        turnOffFlashlight()
        stopVibrationLoop()
    }
    
    private fun setupUI() {
        // Profile selection
        binding.btnSelectProfile.setOnClickListener {
            showProfileSelector()
        }
        
        // Start/Stop button
        binding.btnStartStop.setOnClickListener {
            if (DrainerService.isRunning()) {
                stopTest()
            } else {
                startTest()
            }
        }
        
        // Quick stressor toggles - these actually work now!
        binding.toggleFlashlight.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (systemStatusChecker.hasFlashlight()) {
                    turnOnFlashlight()
                } else {
                    binding.toggleFlashlight.isChecked = false
                    Toast.makeText(this, "Flashlight not available on this device", Toast.LENGTH_SHORT).show()
                }
            } else {
                turnOffFlashlight()
            }
        }
        
        binding.toggleVibrate.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (systemStatusChecker.hasVibrator()) {
                    startVibrationLoop()
                } else {
                    binding.toggleVibrate.isChecked = false
                    Toast.makeText(this, "Vibrator not available on this device", Toast.LENGTH_SHORT).show()
                }
            } else {
                stopVibrationLoop()
            }
        }
        
        // Settings
        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        
        // View Reports
        binding.btnReports.setOnClickListener {
            startActivity(Intent(this, ReportActivity::class.java))
        }
        
        updateProfileDisplay()
        updateSystemStatus()
    }
    
    private fun requestPermissions() {
        if (isRunningUiTest()) return

        val permissions = mutableListOf<String>()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        
        if (permissions.isNotEmpty()) {
            permissionLauncher.launch(permissions.toTypedArray())
        }
        
        // Request battery optimization exemption (skip in debug/tests to avoid UI-blocking dialog)
        if (!BuildConfig.DEBUG && !isRunningUiTest() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                showBatteryOptimizationDialog()
            }
        }
    }
    
    private fun showBatteryOptimizationDialog() {
        AlertDialog.Builder(this)
            .setTitle("Battery Optimization")
            .setMessage("For accurate testing, this app needs to be excluded from battery optimization. This prevents Android from killing the test.")
            .setPositiveButton("Allow") { _, _ ->
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            }
            .setNegativeButton("Later", null)
            .show()
    }
    
    private fun startMonitoring() {
        batteryMonitor.startMonitoring(1000)
        thermalProtection.startMonitoring(2000)
        
        lifecycleScope.launch {
            batteryMonitor.currentReading.collectLatest { reading ->
                reading?.let { updateBatteryDisplay(it) }
            }
        }
        
        lifecycleScope.launch {
            thermalProtection.thermalState.collectLatest { state ->
                updateThermalDisplay()
            }
        }
    }
    
    private fun updateBatteryDisplay(reading: com.batterydrainer.benchmark.data.BatteryReading) {
        binding.apply {
            // Battery level
            batteryLevelText.text = "${reading.level}%"
            batteryProgressBar.progress = reading.level
            
            // Current (convert from microamps to milliamps)
            val currentMa = reading.current / 1000
            currentText.text = "${currentFormat.format(currentMa)} mA"
            currentText.setTextColor(
                if (currentMa < 0) getColor(R.color.discharge_color)
                else getColor(R.color.charge_color)
            )
            
            // Voltage
            val voltageV = reading.voltage / 1000.0
            voltageText.text = "${tempFormat.format(voltageV)} V"
            
            // Temperature
            temperatureText.text = "${tempFormat.format(reading.temperature)}°C"
            temperatureText.setTextColor(
                when {
                    reading.temperature >= 45 -> getColor(R.color.temp_critical)
                    reading.temperature >= 40 -> getColor(R.color.temp_warning)
                    else -> getColor(R.color.temp_normal)
                }
            )
            
            // Charging status
            chargingIndicator.visibility = if (reading.isCharging) View.VISIBLE else View.GONE
        }
    }
    
    private fun updateThermalDisplay() {
        binding.thermalStatus.text = thermalProtection.getThermalStateDescription()
        binding.thermalStatus.setTextColor(
            when (thermalProtection.thermalState.value) {
                com.batterydrainer.benchmark.data.ThermalState.NONE -> getColor(R.color.temp_normal)
                com.batterydrainer.benchmark.data.ThermalState.LIGHT -> getColor(R.color.temp_normal)
                com.batterydrainer.benchmark.data.ThermalState.MODERATE -> getColor(R.color.temp_warning)
                else -> getColor(R.color.temp_critical)
            }
        )
    }
    
    private fun showProfileSelector() {
        val profiles = StressProfile.PRESETS
        val items = profiles.map { "${it.icon} ${it.name}" }.toTypedArray()
        
        AlertDialog.Builder(this)
            .setTitle("Select Test Profile")
            .setItems(items) { _, which ->
                selectedProfile = profiles[which]
                updateProfileDisplay()
            }
            .show()
    }
    
    private fun updateProfileDisplay() {
        binding.apply {
            profileIcon.text = selectedProfile.icon
            profileName.text = selectedProfile.name
            profileDescription.text = selectedProfile.description
            
            // Show stressor intensities
            cpuIndicator.text = "CPU: ${selectedProfile.cpuLoad}%"
            gpuIndicator.text = "GPU: ${selectedProfile.gpuLoad}%"
            networkIndicator.text = "Net: ${selectedProfile.networkLoad}%"
            
            gpsIndicator.visibility = if (selectedProfile.gpsEnabled) View.VISIBLE else View.GONE
            flashIndicator.visibility = if (selectedProfile.flashlightEnabled) View.VISIBLE else View.GONE
            vibrateIndicator.visibility = if (selectedProfile.vibrateEnabled) View.VISIBLE else View.GONE
        }
    }

    private fun isGpuAvailable(): Boolean {
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = am.deviceConfigurationInfo
        return info.reqGlEsVersion >= 0x20000
    }
    
    private fun startTest() {
        // Validate that required features are available
        val status = systemStatusChecker.getSystemStatus()
        val warnings = mutableListOf<String>()
        
        if (selectedProfile.gpsEnabled && !status.gpsSystemEnabled) {
            warnings.add("GPS is OFF in system settings")
        }
        if (selectedProfile.gpsEnabled && !status.gpsPermissionGranted) {
            warnings.add("Location permission not granted")
        }
        if (selectedProfile.networkLoad > 0 && !status.networkConnected) {
            warnings.add("No network connection")
        }
        if (selectedProfile.flashlightEnabled && !status.flashlightAvailable) {
            warnings.add("Flashlight not available")
        }
        if (selectedProfile.vibrateEnabled && !status.vibratorAvailable) {
            warnings.add("Vibrator not available")
        }
        if (selectedProfile.gpuLoad > 0 && !isGpuAvailable()) {
            warnings.add("GPU does not support OpenGL ES 2.0")
        }
        
        if (warnings.isNotEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("⚠️ Test Cannot Start")
                .setMessage("The selected profile requires features that are unavailable:\n\n• ${warnings.joinToString("\n• ")}")
                .setPositiveButton("OK", null)
                .show()
            return
        }
        
        doStartTest()
    }
    
    private fun doStartTest() {
        val config = TestConfig(
            targetBatteryDrop = 10,
            maxDurationMinutes = 60,
            enableThermalProtection = true
        )
        
        // Start and bind to service
        val intent = Intent(this, DrainerService::class.java).apply {
            action = DrainerService.ACTION_START_TEST
            putExtra(DrainerService.EXTRA_PROFILE_ID, selectedProfile.id)
            putExtra(DrainerService.EXTRA_DURATION_MINUTES, config.maxDurationMinutes)
            putExtra(DrainerService.EXTRA_TARGET_BATTERY_DROP, config.targetBatteryDrop)
        }
        
        startForegroundService(intent)
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }
    
    private fun stopTest() {
        val intent = Intent(this, DrainerService::class.java).apply {
            action = DrainerService.ACTION_STOP_TEST
        }
        startService(intent)
        
        updateTestControls(false)
    }
    
    private fun updateTestControls(isRunning: Boolean) {
        binding.apply {
            btnStartStop.text = if (isRunning) "STOP TEST" else "START TEST"
            btnStartStop.setBackgroundColor(
                getColor(if (isRunning) R.color.stop_button else R.color.start_button)
            )
            btnSelectProfile.isEnabled = !isRunning
            profileCard.alpha = if (isRunning) 0.6f else 1.0f
        }
    }
    
    private fun observeServiceState() {
        drainerService?.let { service ->
            lifecycleScope.launch {
                service.isTestRunning.collectLatest { running ->
                    updateTestControls(running)
                }
            }
            
            lifecycleScope.launch {
                service.testProgress.collectLatest { progress ->
                    binding.testProgressBar.progress = (progress * 100).toInt()
                }
            }
            
            lifecycleScope.launch {
                service.statusMessage.collectLatest { message ->
                    binding.statusText.text = message
                }
            }

            service.onTestError = { message ->
                runOnUiThread {
                    updateTestControls(false)
                    AlertDialog.Builder(this)
                        .setTitle("⚠️ Test Cannot Start")
                        .setMessage(message)
                        .setPositiveButton("OK", null)
                        .show()
                }
            }
            
            service.onTestComplete = { session ->
                runOnUiThread {
                    // Generate and save reports silently
                    val report = reportGenerator.generateReport(session, service.batteryMonitor)
                    reportGenerator.saveReportBundle(report)

                    // Update UI to show test is complete
                    val drop = session.startBatteryLevel - (session.endBatteryLevel ?: 0)
                    binding.statusText.text = "Test complete! Battery dropped ${drop}%"
                }
            }
        }
    }
    
    // ========== SYSTEM STATUS ==========
    
    private fun updateSystemStatus() {
        try {
            val status = systemStatusChecker.getSystemStatus()
            android.util.Log.d("MainActivity", "System Status: GPS=${status.gpsSystemEnabled}, Net=${status.networkConnected}, Flash=${status.flashlightAvailable}, Vib=${status.vibratorAvailable}")
            
            binding.apply {
                // GPS Status
                val gpsReady = status.gpsSystemEnabled && status.gpsPermissionGranted && status.gpsHardwareAvailable
                gpsStatusIcon.alpha = if (gpsReady) 1.0f else 0.4f
                gpsStatusValue.text = when {
                    !status.gpsHardwareAvailable -> "N/A"
                    !status.gpsSystemEnabled -> "Off"
                    !status.gpsPermissionGranted -> "No perm"
                    else -> status.locationMode
                }
            gpsStatusValue.setTextColor(getColor(
                if (gpsReady) R.color.charge_color else R.color.text_secondary
            ))
            
            // Network Status
            networkStatusIcon.alpha = if (status.networkConnected) 1.0f else 0.4f
            networkStatusValue.text = status.networkType
            networkStatusValue.setTextColor(getColor(
                if (status.networkConnected) R.color.charge_color else R.color.text_secondary
            ))
            
            // Flash Status
            flashStatusIcon.alpha = if (status.flashlightAvailable) 1.0f else 0.4f
            flashStatusValue.text = when {
                !status.flashlightAvailable -> "N/A"
                isFlashlightOn -> "On"
                else -> "Ready"
            }
            flashStatusValue.setTextColor(getColor(
                if (isFlashlightOn) R.color.charge_color else R.color.text_secondary
            ))
            
            // Vibrator Status
            vibratorStatusIcon.alpha = if (status.vibratorAvailable) 1.0f else 0.4f
            vibratorStatusValue.text = when {
                !status.vibratorAvailable -> "N/A"
                isVibrating -> "On"
                else -> "Ready"
            }
            vibratorStatusValue.setTextColor(getColor(
                if (isVibrating) R.color.charge_color else R.color.text_secondary
            ))
            
            // Show hint if GPS needs to be enabled
            if (!status.gpsSystemEnabled && status.gpsHardwareAvailable) {
                statusHint.visibility = View.VISIBLE
                statusHint.text = "📍 Turn on GPS in system settings to use location features"
            } else if (!status.gpsPermissionGranted && status.gpsHardwareAvailable) {
                statusHint.visibility = View.VISIBLE
                statusHint.text = "📍 Grant location permission to use GPS"
            } else {
                statusHint.visibility = View.GONE
            }
        }
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error updating system status", e)
        }
    }
    
    // ========== FLASHLIGHT CONTROL ==========
    
    private fun turnOnFlashlight() {
        val cameraManager = systemStatusChecker.getCameraManager() ?: return
        val cameraId = systemStatusChecker.getFlashlightCameraId() ?: return
        
        try {
            cameraManager.setTorchMode(cameraId, true)
            isFlashlightOn = true
            updateSystemStatus()
        } catch (e: CameraAccessException) {
            isFlashlightOn = false
            binding.toggleFlashlight.isChecked = false
            Toast.makeText(this, "Could not turn on flashlight: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun turnOffFlashlight() {
        if (!isFlashlightOn) return
        
        val cameraManager = systemStatusChecker.getCameraManager() ?: return
        val cameraId = systemStatusChecker.getFlashlightCameraId() ?: return
        
        try {
            cameraManager.setTorchMode(cameraId, false)
        } catch (e: CameraAccessException) {
            // Ignore errors when turning off
        }
        isFlashlightOn = false
        updateSystemStatus()
    }
    
    // ========== VIBRATION CONTROL ==========
    
    private fun startVibrationLoop() {
        val vibrator = systemStatusChecker.getVibrator() ?: return
        
        isVibrating = true
        updateSystemStatus()
        
        vibrationJob = lifecycleScope.launch {
            while (isActive && isVibrating) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(400, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(400)
                }
                delay(600) // 400ms vibration + 200ms pause
            }
        }
    }
    
    private fun stopVibrationLoop() {
        vibrationJob?.cancel()
        vibrationJob = null
        systemStatusChecker.getVibrator()?.cancel()
        isVibrating = false
        
        // Only update UI if view is still valid
        if (!isFinishing && !isDestroyed) {
            binding.toggleVibrate.isChecked = false
            updateSystemStatus()
        }
    }
}
