package com.batterydrainer.benchmark.stressors

import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.*
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Network Stressor - Forces the cellular/WiFi radio to stay in high-power state
 * 
 * Downloads large files or makes continuous requests to keep the modem active.
 * This simulates streaming, downloads, or poor connectivity scenarios.
 */
class NetworkStressor(private val context: Context) : Stressor {
    
    override val id = "network"
    override val name = "Network Burner"
    
    private val _isRunning = MutableStateFlow(false)
    override val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()
    
    private val _currentLoad = MutableStateFlow(0)
    override val currentLoad: StateFlow<Int> = _currentLoad.asStateFlow()
    
    private val shouldRun = AtomicBoolean(false)
    private var downloadJobs = mutableListOf<Job>()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    val bytesDownloaded = AtomicLong(0)
    val bytesPerSecond = MutableStateFlow(0L)
    
    // Test URLs - large files for downloading
    private val testUrls = listOf(
        // Speed test files (various sizes)
        "http://speedtest.tele2.net/10MB.zip",
        "http://speedtest.tele2.net/100MB.zip",
        "http://ipv4.download.thinkbroadband.com/10MB.zip",
        "http://ipv4.download.thinkbroadband.com/50MB.zip",
        // Fallback ping endpoints
        "https://www.google.com/generate_204",
        "https://connectivity-check.ubuntu.com/"
    )
    
    // Ping endpoints for light load
    private val pingUrls = listOf(
        "https://www.google.com/generate_204",
        "https://www.cloudflare.com/cdn-cgi/trace",
        "https://httpbin.org/get",
        "https://api.ipify.org"
    )
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    
    override suspend fun start(intensity: Int) {
        if (_isRunning.value) return
        
        shouldRun.set(true)
        _isRunning.value = true
        _currentLoad.value = intensity.coerceIn(0, 100)
        bytesDownloaded.set(0)
        
        startNetworkLoad(intensity)
        startBandwidthMonitor()
    }
    
    override suspend fun stop() {
        shouldRun.set(false)
        _isRunning.value = false
        _currentLoad.value = 0
        bytesPerSecond.value = 0
        
        downloadJobs.forEach { it.cancel() }
        downloadJobs.clear()
    }
    
    override suspend fun setIntensity(intensity: Int) {
        val newIntensity = intensity.coerceIn(0, 100)
        if (newIntensity == _currentLoad.value) return
        
        _currentLoad.value = newIntensity
        
        if (_isRunning.value) {
            downloadJobs.forEach { it.cancel() }
            downloadJobs.clear()
            startNetworkLoad(newIntensity)
        }
    }
    
    override fun isAvailable(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) 
            as? android.net.ConnectivityManager
        return connectivityManager?.activeNetwork != null
    }
    
    override fun getEstimatedPowerDraw(): Int {
        // Cellular radio can draw 100-400mA when active
        return when {
            _currentLoad.value <= 25 -> 100
            _currentLoad.value <= 50 -> 200
            _currentLoad.value <= 75 -> 300
            else -> 400
        }
    }
    
    private fun startNetworkLoad(intensity: Int) {
        val parallelDownloads = when {
            intensity <= 25 -> 1
            intensity <= 50 -> 2
            intensity <= 75 -> 4
            else -> 8
        }
        
        val mode = when {
            intensity <= 25 -> LoadMode.PING
            intensity <= 50 -> LoadMode.SMALL_DOWNLOADS
            else -> LoadMode.LARGE_DOWNLOADS
        }
        
        repeat(parallelDownloads) { index ->
            val job = scope.launch {
                when (mode) {
                    LoadMode.PING -> runPingLoop(index)
                    LoadMode.SMALL_DOWNLOADS -> runDownloadLoop(index, small = true)
                    LoadMode.LARGE_DOWNLOADS -> runDownloadLoop(index, small = false)
                }
            }
            downloadJobs.add(job)
        }
    }
    
    private fun startBandwidthMonitor() {
        scope.launch {
            var lastBytes = 0L
            while (shouldRun.get()) {
                delay(1000)
                val currentBytes = bytesDownloaded.get()
                bytesPerSecond.value = currentBytes - lastBytes
                lastBytes = currentBytes
            }
        }
    }
    
    private suspend fun runPingLoop(workerIndex: Int) {
        val delayMs = 200L // 5 requests per second per worker
        
        while (shouldRun.get()) {
            try {
                val url = pingUrls[workerIndex % pingUrls.size]
                val request = Request.Builder()
                    .url(url)
                    .head() // Just a HEAD request
                    .build()
                
                client.newCall(request).execute().use { response ->
                    // Just discard the response
                    bytesDownloaded.addAndGet(response.headers.byteCount())
                }
            } catch (e: Exception) {
                // Ignore errors, try again
            }
            
            delay(delayMs)
        }
    }
    
    private suspend fun runDownloadLoop(workerIndex: Int, small: Boolean) {
        val urls = if (small) {
            testUrls.filter { it.contains("10MB") || it.contains("generate_204") }
        } else {
            testUrls.filter { it.contains("100MB") || it.contains("50MB") }
        }
        
        var urlIndex = workerIndex % urls.size.coerceAtLeast(1)
        
        while (shouldRun.get()) {
            try {
                val url = if (urls.isNotEmpty()) urls[urlIndex % urls.size] else testUrls[0]
                urlIndex++
                
                downloadFile(url)
            } catch (e: Exception) {
                // On error, wait briefly then retry
                delay(1000)
            }
        }
    }
    
    private suspend fun downloadFile(url: String) {
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(url)
                .build()
            
            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use
                    
                    response.body?.let { body ->
                        val buffer = ByteArray(8192)
                        body.byteStream().use { stream ->
                            while (shouldRun.get()) {
                                val bytesRead = stream.read(buffer)
                                if (bytesRead == -1) break
                                bytesDownloaded.addAndGet(bytesRead.toLong())
                            }
                        }
                    }
                }
            } catch (e: IOException) {
                // Network error, will retry
            }
        }
    }
    
    private enum class LoadMode {
        PING,
        SMALL_DOWNLOADS,
        LARGE_DOWNLOADS
    }
}
