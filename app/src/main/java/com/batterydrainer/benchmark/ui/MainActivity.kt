package com.batterydrainer.benchmark.ui

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.batterydrainer.benchmark.R
import com.batterydrainer.benchmark.data.StressProfile
import com.batterydrainer.benchmark.data.TestConfig
import com.batterydrainer.benchmark.databinding.ActivityMainBinding
import com.batterydrainer.benchmark.monitor.BatteryMonitor
import com.batterydrainer.benchmark.monitor.ThermalProtection
import com.batterydrainer.benchmark.report.ReportGenerator
import com.batterydrainer.benchmark.service.DrainerService
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.DecimalFormat

class MainActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainBinding
    private lateinit var batteryMonitor: BatteryMonitor
    private lateinit var thermalProtection: ThermalProtection
    private lateinit var reportGenerator: ReportGenerator
    
    private var drainerService: DrainerService? = null
    private var serviceBound = false
    
    private var selectedProfile: StressProfile = StressProfile.PRESETS.first()
    
    private val currentFormat = DecimalFormat("#,##0")
    private val tempFormat = DecimalFormat("0.0")
    
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
        }
    }
    
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (!allGranted) {
            Toast.makeText(this, "Some permissions were denied. Features may be limited.", Toast.LENGTH_LONG).show()
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        batteryMonitor = BatteryMonitor(this)
        thermalProtection = ThermalProtection(this)
        reportGenerator = ReportGenerator(this)
        
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
    
    override fun onStop() {
        super.onStop()
        if (serviceBound) {
            unbindService(serviceConnection)
            serviceBound = false
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        batteryMonitor.stopMonitoring()
        thermalProtection.stopMonitoring()
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
        
        // Quick stressor toggles
        binding.toggleFlashlight.setOnCheckedChangeListener { _, isChecked ->
            // Quick toggle for flashlight only
        }
        
        binding.toggleVibrate.setOnCheckedChangeListener { _, isChecked ->
            // Quick toggle for vibration only
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
    }
    
    private fun requestPermissions() {
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
        
        // Request battery optimization exemption
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
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
    
    private fun startTest() {
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
        
        updateTestControls(true)
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
            
            service.onTestComplete = { session ->
                runOnUiThread {
                    Toast.makeText(this, "Test complete! Battery dropped ${session.startBatteryLevel - (session.endBatteryLevel ?: 0)}%", Toast.LENGTH_LONG).show()
                    
                    // Generate and save report
                    val report = reportGenerator.generateReport(session, service.batteryMonitor)
                    reportGenerator.saveReport(report, com.batterydrainer.benchmark.data.ExportFormat.HTML)
                }
            }
        }
    }
}
