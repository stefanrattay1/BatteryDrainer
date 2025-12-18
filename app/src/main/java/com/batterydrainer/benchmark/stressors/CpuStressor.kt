package com.batterydrainer.benchmark.stressors

import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.math.BigDecimal
import java.math.MathContext
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max
import kotlin.math.min

/**
 * CPU Stressor - Runs intensive calculations to stress the CPU
 * 
 * Supports different load levels:
 * - Light (25%): 1-2 cores with moderate work
 * - Medium (50%): Half the cores at full speed
 * - Heavy (75%): Most cores at full speed  
 * - Meltdown (100%): All cores maxed out
 */
class CpuStressor(private val context: Context) : Stressor {
    
    override val id = "cpu"
    override val name = "CPU Cruncher"
    
    private val _isRunning = MutableStateFlow(false)
    override val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()
    
    private val _currentLoad = MutableStateFlow(0)
    override val currentLoad: StateFlow<Int> = _currentLoad.asStateFlow()
    
    private val availableCores = Runtime.getRuntime().availableProcessors()
    private var workerJobs = mutableListOf<Job>()
    private var controlJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    private val shouldRun = AtomicBoolean(false)
    
    override suspend fun start(intensity: Int) {
        if (_isRunning.value) return
        
        shouldRun.set(true)
        _isRunning.value = true
        _currentLoad.value = intensity.coerceIn(0, 100)
        
        startWorkers(intensity)
    }
    
    override suspend fun stop() {
        shouldRun.set(false)
        _isRunning.value = false
        _currentLoad.value = 0
        
        controlJob?.cancel()
        workerJobs.forEach { it.cancel() }
        workerJobs.clear()
    }
    
    override suspend fun setIntensity(intensity: Int) {
        val newIntensity = intensity.coerceIn(0, 100)
        if (newIntensity == _currentLoad.value) return
        
        _currentLoad.value = newIntensity
        
        if (_isRunning.value) {
            // Restart workers with new intensity
            workerJobs.forEach { it.cancel() }
            workerJobs.clear()
            startWorkers(newIntensity)
        }
    }
    
    override fun isAvailable(): Boolean = true
    
    override fun getEstimatedPowerDraw(): Int {
        // Rough estimate: ~50-200mA per active core at full load
        val activeCores = calculateActiveCores(_currentLoad.value)
        return activeCores * 150
    }
    
    private fun calculateActiveCores(intensity: Int): Int {
        return when {
            intensity <= 0 -> 0
            intensity <= 25 -> 1
            intensity <= 50 -> max(2, availableCores / 2)
            intensity <= 75 -> max(3, (availableCores * 3) / 4)
            else -> availableCores
        }
    }
    
    private fun startWorkers(intensity: Int) {
        val activeCores = calculateActiveCores(intensity)
        val workIntensity = when {
            intensity <= 25 -> WorkIntensity.LIGHT
            intensity <= 50 -> WorkIntensity.MEDIUM
            intensity <= 75 -> WorkIntensity.HEAVY
            else -> WorkIntensity.EXTREME
        }
        
        repeat(activeCores) { coreIndex ->
            val job = scope.launch {
                runCpuWork(coreIndex, workIntensity)
            }
            workerJobs.add(job)
        }
    }
    
    /**
     * CPU-intensive work - calculates Pi to many digits using Machin's formula
     */
    private suspend fun runCpuWork(coreIndex: Int, intensity: WorkIntensity) {
        val precision = when (intensity) {
            WorkIntensity.LIGHT -> 100
            WorkIntensity.MEDIUM -> 500
            WorkIntensity.HEAVY -> 1000
            WorkIntensity.EXTREME -> 2000
        }
        
        val sleepMs = when (intensity) {
            WorkIntensity.LIGHT -> 50L
            WorkIntensity.MEDIUM -> 20L
            WorkIntensity.HEAVY -> 5L
            WorkIntensity.EXTREME -> 0L
        }
        
        while (shouldRun.get()) {
            // Calculate Pi using Machin's formula for CPU stress
            calculatePi(precision)
            
            // Also do some prime number calculations
            findPrimesInRange(10000 + (coreIndex * 1000), 11000 + (coreIndex * 1000))
            
            // Matrix multiplication for additional load
            matrixMultiply(50 + (precision / 20))
            
            if (sleepMs > 0) {
                delay(sleepMs)
            }
            
            // Yield to prevent complete system lockup
            yield()
        }
    }
    
    /**
     * Calculate Pi using Machin's formula: π/4 = 4*arctan(1/5) - arctan(1/239)
     */
    private fun calculatePi(precision: Int): BigDecimal {
        val mc = MathContext(precision)
        
        fun arctan(x: BigDecimal, numTerms: Int): BigDecimal {
            var result = BigDecimal.ZERO
            var xPower = x
            val xSquared = x.multiply(x, mc)
            
            for (i in 0 until numTerms) {
                val term = xPower.divide(BigDecimal(2 * i + 1), mc)
                result = if (i % 2 == 0) {
                    result.add(term, mc)
                } else {
                    result.subtract(term, mc)
                }
                xPower = xPower.multiply(xSquared, mc)
            }
            return result
        }
        
        val terms = precision / 2
        val arctan1_5 = arctan(BigDecimal.ONE.divide(BigDecimal(5), mc), terms)
        val arctan1_239 = arctan(BigDecimal.ONE.divide(BigDecimal(239), mc), terms)
        
        return BigDecimal(4).multiply(
            BigDecimal(4).multiply(arctan1_5, mc).subtract(arctan1_239, mc),
            mc
        )
    }
    
    /**
     * Find prime numbers in a range using trial division
     */
    private fun findPrimesInRange(start: Int, end: Int): List<Int> {
        val primes = mutableListOf<Int>()
        for (num in start..end) {
            if (isPrime(num)) {
                primes.add(num)
            }
        }
        return primes
    }
    
    private fun isPrime(n: Int): Boolean {
        if (n < 2) return false
        if (n == 2) return true
        if (n % 2 == 0) return false
        
        var i = 3
        while (i * i <= n) {
            if (n % i == 0) return false
            i += 2
        }
        return true
    }
    
    /**
     * Matrix multiplication for additional CPU stress
     */
    private fun matrixMultiply(size: Int) {
        val a = Array(size) { DoubleArray(size) { Math.random() } }
        val b = Array(size) { DoubleArray(size) { Math.random() } }
        val result = Array(size) { DoubleArray(size) }
        
        for (i in 0 until size) {
            for (j in 0 until size) {
                var sum = 0.0
                for (k in 0 until size) {
                    sum += a[i][k] * b[k][j]
                }
                result[i][j] = sum
            }
        }
    }
    
    private enum class WorkIntensity {
        LIGHT, MEDIUM, HEAVY, EXTREME
    }
}
