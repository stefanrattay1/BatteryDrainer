package com.batterydrainer.benchmark.stressors

import android.content.Context
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES20
import android.opengl.Matrix
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.cos
import kotlin.math.sin

/**
 * GPU Stressor - Uses EGL for off-screen OpenGL ES rendering
 * 
 * Creates a Pbuffer surface (off-screen) and continuously renders
 * complex scenes to stress the GPU without requiring a visible view.
 */
class GpuStressor(private val context: Context) : Stressor {
    
    companion object {
        private const val TAG = "GpuStressor"
        private const val SURFACE_WIDTH = 1920
        private const val SURFACE_HEIGHT = 1080
    }
    
    override val id = "gpu"
    override val name = "GPU Burner"
    
    private val _isRunning = MutableStateFlow(false)
    override val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()
    
    private val _currentLoad = MutableStateFlow(0)
    override val currentLoad: StateFlow<Int> = _currentLoad.asStateFlow()
    
    private val shouldRun = AtomicBoolean(false)
    private val intensity = AtomicInteger(0)
    
    private var renderJob: Job? = null
    private var eglDisplay: EGLDisplay? = null
    private var eglContext: EGLContext? = null
    private var eglSurface: EGLSurface? = null
    
    // Shader program handles
    private var program = 0
    private var positionHandle = 0
    private var colorHandle = 0
    private var mvpMatrixHandle = 0
    private var timeHandle = 0
    
    // Matrices
    private val mvpMatrix = FloatArray(16)
    private val projectionMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val modelMatrix = FloatArray(16)
    private val rotationMatrix = FloatArray(16)
    
    // Geometry buffers
    private var cubeVertices: FloatBuffer? = null
    private var cubeColors: FloatBuffer? = null
    private var sphereVertices: FloatBuffer? = null
    private var sphereColors: FloatBuffer? = null
    private var sphereVertexCount = 0
    
    private var frameCount = 0L
    private var startTime = 0L
    
    override suspend fun start(intensity: Int) {
        if (_isRunning.value) return

        shouldRun.set(true)
        this.intensity.set(intensity.coerceIn(0, 100))
        _currentLoad.value = intensity.coerceIn(0, 100)
        startTime = System.currentTimeMillis()
        frameCount = 0

        renderJob = CoroutineScope(Dispatchers.Default).launch {
            try {
                Log.i(TAG, "Starting GPU stressor with intensity: $intensity")
                initEGL()
                initShaders()
                initGeometry()
                _isRunning.value = true
                Log.i(TAG, "GPU initialization complete, starting render loop")
                renderLoop()
            } catch (e: Exception) {
                Log.e(TAG, "GPU stress error: ${e.message}", e)
                _isRunning.value = false
                _currentLoad.value = 0
            } finally {
                cleanupEGL()
                _isRunning.value = false
            }
        }

        // Wait briefly for initialization to complete
        delay(100)
    }
    
    override suspend fun stop() {
        shouldRun.set(false)
        renderJob?.cancelAndJoin()
        renderJob = null
        _isRunning.value = false
        _currentLoad.value = 0
    }
    
    override suspend fun setIntensity(intensity: Int) {
        val newIntensity = intensity.coerceIn(0, 100)
        this.intensity.set(newIntensity)
        _currentLoad.value = newIntensity
    }
    
    override fun isAvailable(): Boolean {
        return try {
            val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            display != EGL14.EGL_NO_DISPLAY
        } catch (e: Exception) {
            false
        }
    }
    
    override fun getEstimatedPowerDraw(): Int {
        return when {
            _currentLoad.value <= 25 -> 200
            _currentLoad.value <= 50 -> 400
            _currentLoad.value <= 75 -> 600
            else -> 800
        }
    }
    
    private fun initEGL() {
        // Get default display
        eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (eglDisplay == EGL14.EGL_NO_DISPLAY) {
            throw RuntimeException("Unable to get EGL display")
        }
        
        // Initialize EGL
        val version = IntArray(2)
        if (!EGL14.eglInitialize(eglDisplay, version, 0, version, 1)) {
            throw RuntimeException("Unable to initialize EGL")
        }
        
        Log.d(TAG, "EGL initialized: ${version[0]}.${version[1]}")
        
        // Configure EGL for OpenGL ES 2.0 with Pbuffer support
        val configAttribs = intArrayOf(
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_DEPTH_SIZE, 16,
            EGL14.EGL_NONE
        )
        
        val configs = arrayOfNulls<EGLConfig>(1)
        val numConfigs = IntArray(1)
        if (!EGL14.eglChooseConfig(eglDisplay, configAttribs, 0, configs, 0, 1, numConfigs, 0)) {
            throw RuntimeException("Unable to choose EGL config")
        }
        
        val config = configs[0] ?: throw RuntimeException("No EGL config found")
        
        // Create OpenGL ES 2.0 context
        val contextAttribs = intArrayOf(
            EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
            EGL14.EGL_NONE
        )
        
        eglContext = EGL14.eglCreateContext(eglDisplay, config, EGL14.EGL_NO_CONTEXT, contextAttribs, 0)
        if (eglContext == EGL14.EGL_NO_CONTEXT) {
            throw RuntimeException("Unable to create EGL context")
        }
        
        // Create Pbuffer surface (off-screen)
        val surfaceAttribs = intArrayOf(
            EGL14.EGL_WIDTH, SURFACE_WIDTH,
            EGL14.EGL_HEIGHT, SURFACE_HEIGHT,
            EGL14.EGL_NONE
        )
        
        eglSurface = EGL14.eglCreatePbufferSurface(eglDisplay, config, surfaceAttribs, 0)
        if (eglSurface == EGL14.EGL_NO_SURFACE) {
            throw RuntimeException("Unable to create EGL Pbuffer surface")
        }
        
        // Make context current
        if (!EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
            throw RuntimeException("Unable to make EGL context current")
        }
        
        Log.d(TAG, "EGL setup complete - off-screen rendering ready")
    }
    
    private fun initShaders() {
        // Simple vertex shader
        val vertexShaderCode = """
            uniform mat4 uMVPMatrix;
            attribute vec4 vPosition;
            attribute vec4 vColor;
            varying vec4 fragColor;
            varying vec3 fragPos;
            void main() {
                gl_Position = uMVPMatrix * vPosition;
                fragColor = vColor;
                fragPos = vPosition.xyz;
            }
        """.trimIndent()
        
        // Heavy fragment shader - more iterations = more GPU work
        val fragmentShaderCode = """
            precision highp float;
            varying vec4 fragColor;
            varying vec3 fragPos;
            uniform float uTime;
            
            // Pseudo-random noise
            float hash(vec2 p) {
                return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
            }
            
            // Smooth noise
            float noise(vec2 p) {
                vec2 i = floor(p);
                vec2 f = fract(p);
                f = f * f * (3.0 - 2.0 * f);
                float a = hash(i);
                float b = hash(i + vec2(1.0, 0.0));
                float c = hash(i + vec2(0.0, 1.0));
                float d = hash(i + vec2(1.0, 1.0));
                return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
            }
            
            // Fractal Brownian motion
            float fbm(vec2 p, int octaves) {
                float value = 0.0;
                float amplitude = 0.5;
                for (int i = 0; i < 8; i++) {
                    if (i >= octaves) break;
                    value += amplitude * noise(p);
                    p *= 2.0;
                    amplitude *= 0.5;
                }
                return value;
            }
            
            void main() {
                vec2 uv = fragPos.xy * 2.0 + uTime * 0.1;
                vec4 color = fragColor;
                
                // Expensive calculations based on intensity (controlled by iterations)
                float n = fbm(uv, 8);
                color.rgb += vec3(n * 0.3);
                
                // Swirl effect
                float angle = n * 6.28318;
                vec2 offset = vec2(cos(angle), sin(angle)) * 0.1;
                n = fbm(uv + offset, 6);
                color.rgb = mix(color.rgb, vec3(n), 0.3);
                
                // Color cycling
                color.rgb = sin(color.rgb * 3.14159 + uTime) * 0.5 + 0.5;
                
                // Lighting simulation
                vec3 lightDir = normalize(vec3(sin(uTime), cos(uTime), 1.0));
                vec3 normal = normalize(vec3(
                    noise(uv + vec2(0.01, 0.0)) - noise(uv - vec2(0.01, 0.0)),
                    noise(uv + vec2(0.0, 0.01)) - noise(uv - vec2(0.0, 0.01)),
                    0.2
                ));
                float diff = max(dot(normal, lightDir), 0.2);
                color.rgb *= diff;
                
                gl_FragColor = color;
            }
        """.trimIndent()
        
        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode)
        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode)
        
        program = GLES20.glCreateProgram().also {
            GLES20.glAttachShader(it, vertexShader)
            GLES20.glAttachShader(it, fragmentShader)
            GLES20.glLinkProgram(it)
            
            // Check link status
            val linkStatus = IntArray(1)
            GLES20.glGetProgramiv(it, GLES20.GL_LINK_STATUS, linkStatus, 0)
            if (linkStatus[0] == 0) {
                val error = GLES20.glGetProgramInfoLog(it)
                Log.e(TAG, "Shader link error: $error")
            }
        }
        
        positionHandle = GLES20.glGetAttribLocation(program, "vPosition")
        colorHandle = GLES20.glGetAttribLocation(program, "vColor")
        mvpMatrixHandle = GLES20.glGetUniformLocation(program, "uMVPMatrix")
        timeHandle = GLES20.glGetUniformLocation(program, "uTime")
        
        // Setup viewport and projection
        GLES20.glViewport(0, 0, SURFACE_WIDTH, SURFACE_HEIGHT)
        val ratio = SURFACE_WIDTH.toFloat() / SURFACE_HEIGHT.toFloat()
        Matrix.frustumM(projectionMatrix, 0, -ratio, ratio, -1f, 1f, 3f, 100f)
        Matrix.setLookAtM(viewMatrix, 0, 0f, 0f, 5f, 0f, 0f, 0f, 0f, 1f, 0f)
        
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glClearColor(0.05f, 0.05f, 0.1f, 1.0f)
        
        Log.d(TAG, "Shaders initialized")
    }
    
    private fun loadShader(type: Int, shaderCode: String): Int {
        return GLES20.glCreateShader(type).also { shader ->
            GLES20.glShaderSource(shader, shaderCode)
            GLES20.glCompileShader(shader)
            
            // Check compile status
            val compileStatus = IntArray(1)
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compileStatus, 0)
            if (compileStatus[0] == 0) {
                val error = GLES20.glGetShaderInfoLog(shader)
                Log.e(TAG, "Shader compile error: $error")
            }
        }
    }
    
    private fun initGeometry() {
        initCubeGeometry()
        initSphereGeometry(32, 32)
        Log.d(TAG, "Geometry initialized")
    }
    
    private fun initCubeGeometry() {
        val vertices = floatArrayOf(
            // Front face
            -1f, -1f, 1f,  1f, -1f, 1f,  1f, 1f, 1f,
            -1f, -1f, 1f,  1f, 1f, 1f,  -1f, 1f, 1f,
            // Back face
            -1f, -1f, -1f,  -1f, 1f, -1f,  1f, 1f, -1f,
            -1f, -1f, -1f,  1f, 1f, -1f,  1f, -1f, -1f,
            // Top face
            -1f, 1f, -1f,  -1f, 1f, 1f,  1f, 1f, 1f,
            -1f, 1f, -1f,  1f, 1f, 1f,  1f, 1f, -1f,
            // Bottom face
            -1f, -1f, -1f,  1f, -1f, -1f,  1f, -1f, 1f,
            -1f, -1f, -1f,  1f, -1f, 1f,  -1f, -1f, 1f,
            // Right face
            1f, -1f, -1f,  1f, 1f, -1f,  1f, 1f, 1f,
            1f, -1f, -1f,  1f, 1f, 1f,  1f, -1f, 1f,
            // Left face
            -1f, -1f, -1f,  -1f, -1f, 1f,  -1f, 1f, 1f,
            -1f, -1f, -1f,  -1f, 1f, 1f,  -1f, 1f, -1f
        )
        
        cubeVertices = ByteBuffer.allocateDirect(vertices.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(vertices)
        cubeVertices?.position(0)
        
        // Generate gradient colors
        val colors = FloatArray(36 * 4)
        for (i in 0 until 36) {
            val face = i / 6
            colors[i * 4] = if (face % 3 == 0) 1f else 0.3f     // R
            colors[i * 4 + 1] = if (face % 3 == 1) 1f else 0.3f // G
            colors[i * 4 + 2] = if (face % 3 == 2) 1f else 0.3f // B
            colors[i * 4 + 3] = 1f                               // A
        }
        
        cubeColors = ByteBuffer.allocateDirect(colors.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(colors)
        cubeColors?.position(0)
    }
    
    private fun initSphereGeometry(latBands: Int, longBands: Int) {
        val vertexList = mutableListOf<Float>()
        val colorList = mutableListOf<Float>()
        
        for (lat in 0 until latBands) {
            val theta1 = lat * Math.PI / latBands
            val theta2 = (lat + 1) * Math.PI / latBands
            
            for (long in 0 until longBands) {
                val phi1 = long * 2 * Math.PI / longBands
                val phi2 = (long + 1) * 2 * Math.PI / longBands
                
                // Create two triangles for each quad
                val vertices = arrayOf(
                    sphereVertex(theta1, phi1),
                    sphereVertex(theta2, phi1),
                    sphereVertex(theta1, phi2),
                    sphereVertex(theta2, phi1),
                    sphereVertex(theta2, phi2),
                    sphereVertex(theta1, phi2)
                )
                
                for (v in vertices) {
                    vertexList.addAll(v.toList())
                    // Color based on position
                    colorList.add((v[0] + 1f) / 2f)
                    colorList.add((v[1] + 1f) / 2f)
                    colorList.add((v[2] + 1f) / 2f)
                    colorList.add(1f)
                }
            }
        }
        
        sphereVertexCount = vertexList.size / 3
        
        sphereVertices = ByteBuffer.allocateDirect(vertexList.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(vertexList.toFloatArray())
        sphereVertices?.position(0)
        
        sphereColors = ByteBuffer.allocateDirect(colorList.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(colorList.toFloatArray())
        sphereColors?.position(0)
    }
    
    private fun sphereVertex(theta: Double, phi: Double): FloatArray {
        return floatArrayOf(
            (sin(theta) * cos(phi)).toFloat(),
            cos(theta).toFloat(),
            (sin(theta) * sin(phi)).toFloat()
        )
    }
    
    private suspend fun renderLoop() {
        var angle = 0f
        val time = floatArrayOf(0f)
        
        while (shouldRun.get()) {
            val currentIntensity = intensity.get()
            
            // Calculate object count based on intensity
            val objectCount = when {
                currentIntensity <= 10 -> 5
                currentIntensity <= 25 -> 20
                currentIntensity <= 50 -> 80
                currentIntensity <= 75 -> 200
                else -> 500
            }
            
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
            GLES20.glUseProgram(program)
            
            // Update time uniform
            time[0] = (System.currentTimeMillis() - startTime) / 1000f
            GLES20.glUniform1f(timeHandle, time[0])
            
            // Render grid of objects
            val gridSize = kotlin.math.sqrt(objectCount.toDouble()).toInt()
            for (i in 0 until objectCount) {
                val x = (i % gridSize - gridSize / 2) * 3f
                val y = ((i / gridSize) % gridSize - gridSize / 2) * 3f
                val z = -30f - (i / (gridSize * gridSize)) * 5f
                
                // Alternate between cubes and spheres
                if (i % 2 == 0) {
                    drawCube(x, y, z, angle + i * 5f)
                } else {
                    drawSphere(x, y, z, angle + i * 5f, 0.8f)
                }
            }
            
            // Swap buffers (even for Pbuffer, this completes the frame)
            EGL14.eglSwapBuffers(eglDisplay, eglSurface)
            
            angle += 0.5f
            if (angle >= 360f) angle = 0f
            
            frameCount++
            
            // Small yield to prevent blocking other coroutines entirely
            yield()
            
            // Optional frame limiting for lower intensities
            if (currentIntensity < 50) {
                delay(16 - currentIntensity / 5L) // ~30-60 fps for lower loads
            }
        }
        
        val elapsed = (System.currentTimeMillis() - startTime) / 1000f
        val fps = if (elapsed > 0) frameCount / elapsed else 0f
        Log.d(TAG, "GPU stress stopped. Rendered $frameCount frames in ${elapsed}s (${fps} fps)")
    }
    
    private fun drawCube(x: Float, y: Float, z: Float, rotation: Float) {
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(modelMatrix, 0, x, y, z)
        Matrix.rotateM(modelMatrix, 0, rotation, 1f, 1f, 0f)
        
        val tempMatrix = FloatArray(16)
        Matrix.multiplyMM(tempMatrix, 0, viewMatrix, 0, modelMatrix, 0)
        Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, tempMatrix, 0)
        
        GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mvpMatrix, 0)
        
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 0, cubeVertices)
        
        GLES20.glEnableVertexAttribArray(colorHandle)
        GLES20.glVertexAttribPointer(colorHandle, 4, GLES20.GL_FLOAT, false, 0, cubeColors)
        
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 36)
        
        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(colorHandle)
    }
    
    private fun drawSphere(x: Float, y: Float, z: Float, rotation: Float, scale: Float) {
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(modelMatrix, 0, x, y, z)
        Matrix.rotateM(modelMatrix, 0, rotation, 0f, 1f, 0f)
        Matrix.scaleM(modelMatrix, 0, scale, scale, scale)
        
        val tempMatrix = FloatArray(16)
        Matrix.multiplyMM(tempMatrix, 0, viewMatrix, 0, modelMatrix, 0)
        Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, tempMatrix, 0)
        
        GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mvpMatrix, 0)
        
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 0, sphereVertices)
        
        GLES20.glEnableVertexAttribArray(colorHandle)
        GLES20.glVertexAttribPointer(colorHandle, 4, GLES20.GL_FLOAT, false, 0, sphereColors)
        
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, sphereVertexCount)
        
        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(colorHandle)
    }
    
    private fun cleanupEGL() {
        if (program != 0) {
            GLES20.glDeleteProgram(program)
            program = 0
        }
        
        eglDisplay?.let { display ->
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            
            eglSurface?.let { surface ->
                EGL14.eglDestroySurface(display, surface)
            }
            eglContext?.let { context ->
                EGL14.eglDestroyContext(display, context)
            }
            EGL14.eglTerminate(display)
        }
        
        eglDisplay = null
        eglContext = null
        eglSurface = null
        cubeVertices = null
        cubeColors = null
        sphereVertices = null
        sphereColors = null
        
        Log.d(TAG, "EGL cleanup complete")
    }
}
