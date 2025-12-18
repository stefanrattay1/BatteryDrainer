package com.batterydrainer.benchmark.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.batterydrainer.benchmark.R
import com.batterydrainer.benchmark.data.*
import com.batterydrainer.benchmark.monitor.BatteryMonitor
import com.batterydrainer.benchmark.monitor.ThermalProtection
import com.batterydrainer.benchmark.stressors.StressorManager
import com.batterydrainer.benchmark.ui.MainActivity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * Foreground Service that runs the battery drain benchmark
 * 
 * This service MUST run as a foreground service to prevent Android from
 * killing it through Doze mode or app standby. The notification keeps
 * the user informed of test progress.
 */
class DrainerService : LifecycleService() {
    
    companion object {
        const val CHANNEL_ID = "battery_drainer_channel"
        const val NOTIFICATION_ID = 1001
        
        const val ACTION_START_TEST = "com.batterydrainer.START_TEST"
        const val ACTION_STOP_TEST = "com.batterydrainer.STOP_TEST"
        const val ACTION_PAUSE_TEST = "com.batterydrainer.PAUSE_TEST"
        const val ACTION_RESUME_TEST = "com.batterydrainer.RESUME_TEST"
        
        const val EXTRA_PROFILE_ID = "profile_id"
        const val EXTRA_DURATION_MINUTES = "duration_minutes"
        const val EXTRA_TARGET_BATTERY_DROP = "target_battery_drop"
        
        private var instance: DrainerService? = null
        
        fun getInstance(): DrainerService? = instance
        
        fun isRunning(): Boolean = instance?.isTestRunning?.value == true
    }
    
    // Service binding
    private val binder = LocalBinder()
    
    inner class LocalBinder : Binder() {
        fun getService(): DrainerService = this@DrainerService
    }
    
    // Core components
    lateinit var stressorManager: StressorManager
        private set
    lateinit var batteryMonitor: BatteryMonitor
        private set
    lateinit var thermalProtection: ThermalProtection
        private set
    
    // Test state
    private val _isTestRunning = MutableStateFlow(false)
    val isTestRunning: StateFlow<Boolean> = _isTestRunning.asStateFlow()
    
    private val _isPaused = MutableStateFlow(false)
    val isPaused: StateFlow<Boolean> = _isPaused.asStateFlow()
    
    private val _currentSession = MutableStateFlow<TestSession?>(null)
    val currentSession: StateFlow<TestSession?> = _currentSession.asStateFlow()
    
    private val _testProgress = MutableStateFlow(0f)
    val testProgress: StateFlow<Float> = _testProgress.asStateFlow()
    
    private val _statusMessage = MutableStateFlow("Ready")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()
    
    // Test configuration
    private var currentConfig = TestConfig()
    private var currentProfile: StressProfile? = null
    
    // Wake lock to prevent CPU sleep
    private var wakeLock: PowerManager.WakeLock? = null
    
    // Callbacks for UI
    var onTestComplete: ((TestSession) -> Unit)? = null
    var onTestError: ((String) -> Unit)? = null
    
    override fun onCreate() {
        super.onCreate()
        instance = this
        
        // Initialize components
        stressorManager = StressorManager(this)
        batteryMonitor = BatteryMonitor(this)
        thermalProtection = ThermalProtection(this)
        
        createNotificationChannel()
        setupThermalCallbacks()
    }
    
    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        return binder
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        
        when (intent?.action) {
            ACTION_START_TEST -> {
                val profileId = intent.getStringExtra(EXTRA_PROFILE_ID) ?: "idle"
                val duration = intent.getIntExtra(EXTRA_DURATION_MINUTES, 60)
                val targetDrop = intent.getIntExtra(EXTRA_TARGET_BATTERY_DROP, 10)
                
                val config = TestConfig(
                    targetBatteryDrop = targetDrop,
                    maxDurationMinutes = duration
                )
                
                StressProfile.getById(profileId)?.let { profile ->
                    startTest(profile, config)
                }
            }
            ACTION_STOP_TEST -> stopTest()
            ACTION_PAUSE_TEST -> pauseTest()
            ACTION_RESUME_TEST -> resumeTest()
        }
        
        return START_STICKY
    }
    
    override fun onDestroy() {
        stopTest()
        instance = null
        super.onDestroy()
    }
    
    /**
     * Start a battery drain test
     */
    fun startTest(profile: StressProfile, config: TestConfig = TestConfig()) {
        if (_isTestRunning.value) {
            stopTest()
        }
        
        currentProfile = profile
        currentConfig = config
        
        // Start foreground service
        startForegroundWithNotification()
        
        // Acquire wake lock
        acquireWakeLock()
        
        // Start monitoring
        batteryMonitor.clearHistory()
        batteryMonitor.startMonitoring(config.samplingIntervalMs)
        
        if (config.enableThermalProtection) {
            thermalProtection.startMonitoring()
        }
        
        // Create test session
        val initialReading = batteryMonitor.getCurrentReadingSync()
        val session = TestSession(
            id = UUID.randomUUID().toString(),
            profileId = profile.id,
            profileName = profile.name,
            startTime = System.currentTimeMillis(),
            startBatteryLevel = initialReading.level,
            stressors = buildList {
                if (profile.cpuLoad > 0) add("CPU (${profile.cpuLoad}%)")
                if (profile.gpuLoad > 0) add("GPU (${profile.gpuLoad}%)")
                if (profile.networkLoad > 0) add("Network (${profile.networkLoad}%)")
                if (profile.gpsEnabled) add("GPS")
                if (profile.flashlightEnabled) add("Flashlight")
                if (profile.vibrateEnabled) add("Vibration")
            }
        )
        _currentSession.value = session
        
        _isTestRunning.value = true
        _isPaused.value = false
        _statusMessage.value = "Running: ${profile.name}"
        
        // Start stressors
        lifecycleScope.launch {
            stressorManager.startProfile(profile)
            monitorTestProgress()
        }
        
        updateNotification()
    }
    
    /**
     * Stop the current test
     */
    fun stopTest(wasAborted: Boolean = false, abortReason: String? = null) {
        lifecycleScope.launch {
            stressorManager.stopAll()
        }
        
        batteryMonitor.stopMonitoring()
        thermalProtection.stopMonitoring()
        thermalProtection.reset()
        
        releaseWakeLock()
        
        // Finalize session
        _currentSession.value?.let { session ->
            val finalReading = batteryMonitor.getCurrentReadingSync()
            val finalSession = session.copy(
                endTime = System.currentTimeMillis(),
                endBatteryLevel = finalReading.level,
                readings = batteryMonitor.readingsHistory.toMutableList(),
                wasAborted = wasAborted,
                abortReason = abortReason
            )
            _currentSession.value = finalSession
            onTestComplete?.invoke(finalSession)
        }
        
        _isTestRunning.value = false
        _isPaused.value = false
        _testProgress.value = 0f
        _statusMessage.value = "Test complete"
        
        updateNotification()
        
        // Stop foreground after a delay to show completion
        lifecycleScope.launch {
            delay(2000)
            if (!_isTestRunning.value) {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }
    
    /**
     * Pause the current test
     */
    fun pauseTest() {
        if (!_isTestRunning.value || _isPaused.value) return
        
        _isPaused.value = true
        _statusMessage.value = "Paused"
        
        lifecycleScope.launch {
            stressorManager.stopAll()
        }
        
        updateNotification()
    }
    
    /**
     * Resume a paused test
     */
    fun resumeTest() {
        if (!_isTestRunning.value || !_isPaused.value) return
        
        _isPaused.value = false
        _statusMessage.value = "Running: ${currentProfile?.name ?: "Unknown"}"
        
        currentProfile?.let { profile ->
            lifecycleScope.launch {
                stressorManager.startProfile(profile)
            }
        }
        
        updateNotification()
    }
    
    private fun monitorTestProgress() {
        lifecycleScope.launch {
            val startBattery = _currentSession.value?.startBatteryLevel ?: 100
            val targetDrop = currentConfig.targetBatteryDrop
            val maxDuration = currentConfig.maxDurationMinutes * 60 * 1000L
            val startTime = _currentSession.value?.startTime ?: System.currentTimeMillis()
            
            while (_isTestRunning.value) {
                delay(1000)
                
                // Check thermal protection
                if (thermalProtection.shouldStopTest.value) {
                    stopTest(wasAborted = true, abortReason = "Thermal protection triggered")
                    return@launch
                }
                
                if (thermalProtection.shouldPauseTest.value && !_isPaused.value) {
                    pauseTest()
                    _statusMessage.value = "Paused: Cooling down (${thermalProtection.currentTemperature.value}°C)"
                }
                
                // Resume when cooled down
                if (_isPaused.value && !thermalProtection.isInCooldown.value && 
                    thermalProtection.currentTemperature.value < ThermalProtection.COOLDOWN_TARGET) {
                    resumeTest()
                }
                
                val currentReading = batteryMonitor.currentReading.value ?: continue
                
                // Calculate progress
                val batteryDropped = startBattery - currentReading.level
                val batteryProgress = (batteryDropped.toFloat() / targetDrop).coerceIn(0f, 1f)
                
                val elapsed = System.currentTimeMillis() - startTime
                val timeProgress = (elapsed.toFloat() / maxDuration).coerceIn(0f, 1f)
                
                _testProgress.value = maxOf(batteryProgress, timeProgress)
                
                // Check completion conditions
                if (batteryDropped >= targetDrop) {
                    stopTest(wasAborted = false, abortReason = null)
                    return@launch
                }
                
                if (elapsed >= maxDuration) {
                    stopTest(wasAborted = true, abortReason = "Maximum duration reached")
                    return@launch
                }
                
                // Check if charging
                if (currentReading.isCharging) {
                    pauseTest()
                    _statusMessage.value = "Paused: Charger connected"
                }
                
                // Update notification
                updateNotification()
            }
        }
    }
    
    private fun setupThermalCallbacks() {
        thermalProtection.onPause = { temp ->
            if (_isTestRunning.value) {
                pauseTest()
                _statusMessage.value = "Paused: High temperature (${temp.toInt()}°C)"
            }
        }
        
        thermalProtection.onStop = { temp ->
            if (_isTestRunning.value) {
                stopTest(wasAborted = true, abortReason = "Temperature exceeded ${temp.toInt()}°C")
            }
        }
        
        thermalProtection.onCooldownComplete = {
            if (_isTestRunning.value && _isPaused.value) {
                resumeTest()
            }
        }
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Battery Drainer Test",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows progress of battery drain tests"
                setShowBadge(false)
            }
            
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
    
    private fun startForegroundWithNotification() {
        val notification = buildNotification()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID, 
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }
    
    private fun buildNotification(): Notification {
        val contentIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val stopIntent = Intent(this, DrainerService::class.java).apply {
            action = ACTION_STOP_TEST
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val reading = batteryMonitor.currentReading.value
        val contentText = if (_isTestRunning.value) {
            val profile = currentProfile?.name ?: "Test"
            val battery = reading?.level ?: 0
            val current = reading?.current?.let { "${it / 1000} mA" } ?: "--"
            val temp = reading?.temperature?.let { "${it.toInt()}°C" } ?: "--"
            "$profile | 🔋$battery% | ⚡$current | 🌡️$temp"
        } else {
            "Ready to test"
        }
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Battery Drainer")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(_isTestRunning.value)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .apply {
                if (_isTestRunning.value) {
                    addAction(R.drawable.ic_stop, "Stop", stopPendingIntent)
                    setProgress(100, (_testProgress.value * 100).toInt(), false)
                }
            }
            .build()
    }
    
    private fun updateNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification())
    }
    
    private fun acquireWakeLock() {
        if (wakeLock == null) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "BatteryDrainer:TestWakeLock"
            )
        }
        wakeLock?.acquire(currentConfig.maxDurationMinutes * 60 * 1000L + 60000L)
    }
    
    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
    }
}
