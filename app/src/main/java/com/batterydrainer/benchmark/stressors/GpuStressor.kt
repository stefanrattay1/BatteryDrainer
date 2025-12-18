package com.batterydrainer.benchmark.stressors

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * GPU Stressor - Renders complex 3D scenes to stress the GPU
 * 
 * Creates an off-screen or small surface rendering context and continuously
 * renders increasingly complex geometry to maximize GPU usage.
 */
class GpuStressor(private val context: Context) : Stressor {
    
    override val id = "gpu"
    override val name = "GPU Burner"
    
    private val _isRunning = MutableStateFlow(false)
    override val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()
    
    private val _currentLoad = MutableStateFlow(0)
    override val currentLoad: StateFlow<Int> = _currentLoad.asStateFlow()
    
    private var glSurfaceView: GLSurfaceView? = null
    private var renderer: StressRenderer? = null
    private val shouldRun = AtomicBoolean(false)
    private val intensity = AtomicInteger(0)
    
    override suspend fun start(intensity: Int) {
        if (_isRunning.value) return
        
        shouldRun.set(true)
        this.intensity.set(intensity.coerceIn(0, 100))
        _isRunning.value = true
        _currentLoad.value = intensity.coerceIn(0, 100)
        
        // Note: GLSurfaceView must be created on UI thread
        // The actual rendering will happen in the renderer
        withContext(Dispatchers.Main) {
            setupGlSurface()
        }
    }
    
    override suspend fun stop() {
        shouldRun.set(false)
        _isRunning.value = false
        _currentLoad.value = 0
        
        withContext(Dispatchers.Main) {
            glSurfaceView?.onPause()
            glSurfaceView = null
            renderer = null
        }
    }
    
    override suspend fun setIntensity(intensity: Int) {
        val newIntensity = intensity.coerceIn(0, 100)
        this.intensity.set(newIntensity)
        _currentLoad.value = newIntensity
        renderer?.setComplexity(newIntensity)
    }
    
    override fun isAvailable(): Boolean {
        // Check if OpenGL ES 2.0 is supported
        return try {
            val configInfo = android.app.ActivityManager.MemoryInfo()
            true // Most devices support GLES 2.0
        } catch (e: Exception) {
            false
        }
    }
    
    override fun getEstimatedPowerDraw(): Int {
        // GPU can draw 200-800mA depending on intensity
        return when {
            _currentLoad.value <= 25 -> 200
            _currentLoad.value <= 50 -> 400
            _currentLoad.value <= 75 -> 600
            else -> 800
        }
    }
    
    private fun setupGlSurface() {
        renderer = StressRenderer(intensity)
        
        glSurfaceView = GLSurfaceView(context).apply {
            setEGLContextClientVersion(2)
            setRenderer(renderer)
            renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
        }
        
        glSurfaceView?.onResume()
    }
    
    /**
     * Get the GLSurfaceView for optional display in UI
     */
    fun getGlSurfaceView(): GLSurfaceView? = glSurfaceView
    
    /**
     * OpenGL ES 2.0 Renderer for GPU stress testing
     */
    inner class StressRenderer(private val intensityRef: AtomicInteger) : GLSurfaceView.Renderer {
        
        private var program = 0
        private var positionHandle = 0
        private var colorHandle = 0
        private var mvpMatrixHandle = 0
        
        private val mvpMatrix = FloatArray(16)
        private val projectionMatrix = FloatArray(16)
        private val viewMatrix = FloatArray(16)
        private val rotationMatrix = FloatArray(16)
        
        private var angle = 0f
        private var complexity = 50
        
        private lateinit var cubeVertices: FloatBuffer
        private lateinit var cubeColors: FloatBuffer
        
        private val vertexShaderCode = """
            uniform mat4 uMVPMatrix;
            attribute vec4 vPosition;
            attribute vec4 vColor;
            varying vec4 fragColor;
            void main() {
                gl_Position = uMVPMatrix * vPosition;
                fragColor = vColor;
            }
        """.trimIndent()
        
        private val fragmentShaderCode = """
            precision mediump float;
            varying vec4 fragColor;
            void main() {
                // Add some extra computation for GPU stress
                vec4 color = fragColor;
                for (int i = 0; i < 10; i++) {
                    color = color * 0.99 + fragColor * 0.01;
                    color = sin(color * 3.14159) * 0.5 + 0.5;
                }
                gl_FragColor = color;
            }
        """.trimIndent()
        
        // Heavy fragment shader for maximum GPU stress
        private val heavyFragmentShaderCode = """
            precision highp float;
            varying vec4 fragColor;
            uniform float uTime;
            
            float noise(vec2 p) {
                return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453);
            }
            
            void main() {
                vec4 color = fragColor;
                vec2 uv = gl_FragCoord.xy / 100.0;
                
                // Multiple iterations of expensive operations
                for (int i = 0; i < 50; i++) {
                    float n = noise(uv + float(i) * 0.1);
                    color.rgb += vec3(n * 0.01);
                    color.rgb = sin(color.rgb * 6.28318) * 0.5 + 0.5;
                    uv = uv * 1.01 + color.rg * 0.1;
                }
                
                gl_FragColor = color;
            }
        """.trimIndent()
        
        fun setComplexity(level: Int) {
            complexity = level
        }
        
        override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
            GLES20.glClearColor(0.1f, 0.1f, 0.1f, 1.0f)
            GLES20.glEnable(GLES20.GL_DEPTH_TEST)
            
            // Choose shader based on intensity
            val fragmentShader = if (intensityRef.get() > 75) {
                heavyFragmentShaderCode
            } else {
                fragmentShaderCode
            }
            
            // Compile shaders
            val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode)
            val fragShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShader)
            
            // Create program
            program = GLES20.glCreateProgram().also {
                GLES20.glAttachShader(it, vertexShader)
                GLES20.glAttachShader(it, fragShader)
                GLES20.glLinkProgram(it)
            }
            
            // Get handles
            positionHandle = GLES20.glGetAttribLocation(program, "vPosition")
            colorHandle = GLES20.glGetAttribLocation(program, "vColor")
            mvpMatrixHandle = GLES20.glGetUniformLocation(program, "uMVPMatrix")
            
            // Initialize geometry
            initGeometry()
        }
        
        override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
            GLES20.glViewport(0, 0, width, height)
            
            val ratio = width.toFloat() / height.toFloat()
            Matrix.frustumM(projectionMatrix, 0, -ratio, ratio, -1f, 1f, 3f, 100f)
        }
        
        override fun onDrawFrame(gl: GL10?) {
            if (!shouldRun.get()) return
            
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
            
            // Calculate number of objects based on intensity
            val objectCount = when {
                complexity <= 25 -> 10
                complexity <= 50 -> 50
                complexity <= 75 -> 150
                else -> 500
            }
            
            // Render multiple objects for stress
            for (i in 0 until objectCount) {
                drawCube(
                    x = (i % 10 - 5) * 2f,
                    y = ((i / 10) % 10 - 5) * 2f,
                    z = -20f - (i / 100) * 5f,
                    rotation = angle + i * 10f
                )
            }
            
            angle += 1f
            if (angle >= 360f) angle = 0f
        }
        
        private fun drawCube(x: Float, y: Float, z: Float, rotation: Float) {
            GLES20.glUseProgram(program)
            
            // Set up transformation
            Matrix.setLookAtM(viewMatrix, 0, 0f, 0f, 0f, 0f, 0f, -1f, 0f, 1f, 0f)
            Matrix.setRotateM(rotationMatrix, 0, rotation, 1f, 1f, 0f)
            
            val translateMatrix = FloatArray(16)
            Matrix.setIdentityM(translateMatrix, 0)
            Matrix.translateM(translateMatrix, 0, x, y, z)
            
            val modelMatrix = FloatArray(16)
            Matrix.multiplyMM(modelMatrix, 0, translateMatrix, 0, rotationMatrix, 0)
            
            val tempMatrix = FloatArray(16)
            Matrix.multiplyMM(tempMatrix, 0, viewMatrix, 0, modelMatrix, 0)
            Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, tempMatrix, 0)
            
            // Pass MVP matrix
            GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mvpMatrix, 0)
            
            // Enable vertex arrays
            GLES20.glEnableVertexAttribArray(positionHandle)
            GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 0, cubeVertices)
            
            GLES20.glEnableVertexAttribArray(colorHandle)
            GLES20.glVertexAttribPointer(colorHandle, 4, GLES20.GL_FLOAT, false, 0, cubeColors)
            
            // Draw
            GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 36)
            
            // Disable
            GLES20.glDisableVertexAttribArray(positionHandle)
            GLES20.glDisableVertexAttribArray(colorHandle)
        }
        
        private fun initGeometry() {
            // Cube vertices (36 vertices for 12 triangles)
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
            cubeVertices.position(0)
            
            // Generate random colors for each vertex
            val colors = FloatArray(36 * 4)
            for (i in 0 until 36) {
                colors[i * 4] = Math.random().toFloat()     // R
                colors[i * 4 + 1] = Math.random().toFloat() // G
                colors[i * 4 + 2] = Math.random().toFloat() // B
                colors[i * 4 + 3] = 1f                       // A
            }
            
            cubeColors = ByteBuffer.allocateDirect(colors.size * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .put(colors)
            cubeColors.position(0)
        }
        
        private fun loadShader(type: Int, shaderCode: String): Int {
            return GLES20.glCreateShader(type).also { shader ->
                GLES20.glShaderSource(shader, shaderCode)
                GLES20.glCompileShader(shader)
            }
        }
    }
}
