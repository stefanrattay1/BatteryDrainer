package com.batterydrainer.benchmark.ui

import android.os.Bundle
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import com.batterydrainer.benchmark.databinding.ActivitySettingsBinding
import com.batterydrainer.benchmark.monitor.ThermalProtection

class SettingsActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivitySettingsBinding
    
    // Settings stored in SharedPreferences
    private val prefs by lazy {
        getSharedPreferences("battery_drainer_settings", MODE_PRIVATE)
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Settings"
        
        loadSettings()
        setupListeners()
    }
    
    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
    
    private fun loadSettings() {
        binding.apply {
            // Thermal Protection
            switchThermalProtection.isChecked = prefs.getBoolean("thermal_protection", true)
            
            val thermalLimit = prefs.getInt("thermal_limit", 45)
            seekbarThermalLimit.progress = thermalLimit - 35  // Range 35-55
            textThermalLimit.text = "${thermalLimit}°C"
            
            // Test Defaults
            val defaultDuration = prefs.getInt("default_duration", 60)
            seekbarDefaultDuration.progress = defaultDuration
            textDefaultDuration.text = "${defaultDuration} min"
            
            val defaultDrop = prefs.getInt("default_drop", 10)
            seekbarDefaultDrop.progress = defaultDrop
            textDefaultDrop.text = "${defaultDrop}%"
            
            // Sampling
            val samplingInterval = prefs.getInt("sampling_interval", 1000)
            seekbarSamplingInterval.progress = samplingInterval / 100  // 100ms - 5000ms
            textSamplingInterval.text = "${samplingInterval}ms"
            
            // Pro Features
            switchAdbSupport.isChecked = prefs.getBoolean("adb_support", true)
            switchAutoExport.isChecked = prefs.getBoolean("auto_export", false)
        }
    }
    
    private fun setupListeners() {
        binding.apply {
            switchThermalProtection.setOnCheckedChangeListener { _, isChecked ->
                prefs.edit().putBoolean("thermal_protection", isChecked).apply()
            }
            
            seekbarThermalLimit.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    val temp = progress + 35
                    textThermalLimit.text = "${temp}°C"
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    val temp = (seekBar?.progress ?: 10) + 35
                    prefs.edit().putInt("thermal_limit", temp).apply()
                }
            })
            
            seekbarDefaultDuration.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    textDefaultDuration.text = "${progress} min"
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    prefs.edit().putInt("default_duration", seekBar?.progress ?: 60).apply()
                }
            })
            
            seekbarDefaultDrop.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    textDefaultDrop.text = "${progress}%"
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    prefs.edit().putInt("default_drop", seekBar?.progress ?: 10).apply()
                }
            })
            
            seekbarSamplingInterval.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    val interval = progress * 100
                    textSamplingInterval.text = "${interval}ms"
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    val interval = (seekBar?.progress ?: 10) * 100
                    prefs.edit().putInt("sampling_interval", interval).apply()
                }
            })
            
            switchAdbSupport.setOnCheckedChangeListener { _, isChecked ->
                prefs.edit().putBoolean("adb_support", isChecked).apply()
            }
            
            switchAutoExport.setOnCheckedChangeListener { _, isChecked ->
                prefs.edit().putBoolean("auto_export", isChecked).apply()
            }
        }
    }
}
