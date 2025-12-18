package com.batterydrainer.benchmark.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.batterydrainer.benchmark.databinding.ActivityProfileBinding

class ProfileActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityProfileBinding
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Test Profiles"
    }
    
    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}
