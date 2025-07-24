package com.cyment.nosleepingonyourstomach // Use your actual package name

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.MediaPlayer
import android.media.ToneGenerator
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlin.math.abs

class MainActivity : AppCompatActivity(), SensorEventListener {

    // --- UI Elements ---
    private lateinit var statusTextView: TextView
    private lateinit var orientationTextView: TextView // Renamed from pitchTextView
    private lateinit var instructionTextView: TextView
    private lateinit var startStopButton: Button

    // --- System Services ---
    private lateinit var sensorManager: SensorManager
    private lateinit var vibrator: Vibrator

    // --- Sensor Objects ---
    private var accelerometer: Sensor? = null
    private var magnetometer: Sensor? = null
    private var rotationVectorSensor: Sensor? = null // Preferred sensor

    // --- State ---
    private var isMonitoring = false
    private var usingRotationVector = false
    private var batteryOptimizedMode = false

    // --- Sensor Data Buffers ---
    // For Accel + Mag fallback
    private var gravity: FloatArray? = null
    private var geomagnetic: FloatArray? = null
    private val rotationMatrix = FloatArray(9)
    private val orientationValues = FloatArray(3) // azimuth, pitch, roll

    // For Rotation Vector primary
    private val rotationMatrixFromVector = FloatArray(9)
    private val orientationFromVector = FloatArray(3) // azimuth, pitch, roll

    // --- Alert Control ---
    private var alertStartTime: Long = 0
    private var isInDangerousPosition = false
    private var isVibrating = false
    private var isMakingSound = false
    private var vibrationHandler: Handler? = null
    private var soundHandler: Handler? = null
    private var toneGenerator: ToneGenerator? = null

    // --- Companion Object (Constants) ---
    companion object {
        private const val TAG = "NoSleepApp" // Log Tag

        // Threshold based on ROLL (in radians) to detect "on stomach" position.
        // Roll ~ 0 rad (0 deg) when screen UP.
        // Roll ~ 130+ deg when dangerously down on stomach.
        // Roll ~ 150 deg when completely upside down.
        private const val STOMACH_ROLL_THRESHOLD_RADIANS = 2.27 // Approx 130 degrees. TUNABLE!

        private const val VIBRATION_INTERVAL_MS: Long = 1500 // 1.5 seconds between vibrations (increased frequency)
        private const val VIBRATION_DURATION_MS: Long = 750 // 0.75 second vibration duration (intermediate)
        private const val SOUND_ALERT_DELAY_MS: Long = 30000 // 30 seconds
        private const val SOUND_INTERVAL_MS: Long = 3000 // 3 seconds between sound alerts (battery optimized)
    }

    // --- Lifecycle Methods ---
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        Log.d(TAG, "onCreate called")

        // --- Initialize UI ---
        statusTextView = findViewById(R.id.statusTextView)
        orientationTextView = findViewById(R.id.orientationTextView) // Use new ID
        instructionTextView = findViewById(R.id.instructionTextView)
        startStopButton = findViewById(R.id.startStopButton)
        orientationTextView.text = "Roll: N/A" // Initial state text

        // --- Initialize System Services ---
        initSensorManager()
        initVibrator()
        initSound()

        // --- Check Sensor Availability ---
        checkSensorAvailability()
        
        // Enable battery optimization for older devices
        enableBatteryOptimizationForOlderDevices()

        // --- Setup Button Listener ---
        startStopButton.setOnClickListener {
            if (!isMonitoring) {
                startMonitoring()
            } else {
                stopMonitoring()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume called")
        // If you wanted to automatically resume monitoring if it was paused:
        // if (wasMonitoringBeforePause && !isMonitoring) { startMonitoring() }
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "onPause called")
        // Stop monitoring when the activity is paused to save battery
        if (isMonitoring) {
            stopMonitoring()
            // wasMonitoringBeforePause = true // Flag for auto-resume logic if needed
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAllAlerts()
        toneGenerator?.release()
    }
    
    private fun enableBatteryOptimizationForOlderDevices() {
        // Enable battery optimization for devices with API level < 23 (Android 6.0)
        // or devices with limited RAM/CPU
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            batteryOptimizedMode = true
            Log.i(TAG, "Enabled battery optimization mode for older device (API ${Build.VERSION.SDK_INT})")
        }
    }

    // --- Initialization Helpers ---
    private fun initSensorManager() {
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        // Try to get the preferred Rotation Vector sensor first
        rotationVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        // Get fallback sensors
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

        if (rotationVectorSensor != null) {
            Log.i(TAG, "Rotation Vector sensor found.")
        } else {
            Log.w(TAG, "Rotation Vector sensor NOT found. Will use Accelerometer + Magnetometer.")
            if (accelerometer == null || magnetometer == null) {
                Log.e(TAG, "Required fallback sensors (Accel/Magnet) missing!")
            }
        }
    }

    private fun initVibrator() {
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        if (!vibrator.hasVibrator()) {
            Log.w(TAG, "Device reports no vibrator.")
        }
    }

    private fun initSound() {
        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_ALARM, 100)
        } catch (e: RuntimeException) {
            Log.e(TAG, "Failed to initialize ToneGenerator", e)
        }
    }

    private fun checkSensorAvailability() {
        val hasRotationVector = rotationVectorSensor != null
        val hasFallback = accelerometer != null && magnetometer != null
        if (!hasRotationVector && !hasFallback) {
            Toast.makeText(this, "Required orientation sensors not available!", Toast.LENGTH_LONG).show()
            statusTextView.text = "Error: Sensors missing!"
            startStopButton.isEnabled = false
            Log.e(TAG, "No suitable orientation sensors found.")
        } else {
            startStopButton.isEnabled = true
        }
    }

    // --- Monitoring Control ---
    private fun startMonitoring() {
        Log.d(TAG, "Attempting to start monitoring...")
        var sensorRegistered = false
        // Prioritize Rotation Vector for better battery efficiency
        if (rotationVectorSensor != null) {
            sensorManager.registerListener(this, rotationVectorSensor, SensorManager.SENSOR_DELAY_NORMAL)
            usingRotationVector = true
            sensorRegistered = true
            Log.i(TAG, "Registered listener for TYPE_ROTATION_VECTOR with SENSOR_DELAY_NORMAL")
        }
        // Fallback to Accelerometer + Magnetometer (less battery efficient)
        else if (accelerometer != null && magnetometer != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL)
            sensorManager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_NORMAL)
            usingRotationVector = false // Explicitly set
            sensorRegistered = true
            Log.i(TAG, "Registered listeners for TYPE_ACCELEROMETER and TYPE_MAGNETIC_FIELD with SENSOR_DELAY_NORMAL")
        }

        if (sensorRegistered) {
            isMonitoring = true
            // Reset UI
            statusTextView.text = "Status: Monitoring..."
            startStopButton.text = "Stop Monitoring"
            instructionTextView.text = "Monitoring active. Screen will turn off to save battery."
            // Allow screen to turn off for battery saving
            // window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) // Commented out for battery optimization
            Log.i(TAG, "Monitoring STARTED. Using RotationVector: $usingRotationVector")
        } else {
            // This case should ideally be caught by checkSensorAvailability, but as a safeguard:
            Toast.makeText(this, "Cannot start: No suitable sensors.", Toast.LENGTH_SHORT).show()
            Log.e(TAG, "Failed to start monitoring, no sensors registered.")
        }
    }

    private fun stopMonitoring() {
        Log.d(TAG, "Stopping monitoring...")
        sensorManager.unregisterListener(this) // Unregisters all sensors for this listener
        isMonitoring = false
        usingRotationVector = false // Reset flag
        
        // Stop all alerts
        stopAllAlerts()
        
        // Reset UI
        statusTextView.text = "Status: Idle"
        orientationTextView.text = "Roll: N/A"
        startStopButton.text = "Start Monitoring"
        instructionTextView.text = "Place phone on chest, screen up.\nTap Start to monitor."
        // Screen is already allowed to turn off (no flag to clear)
        // Reset sensor data buffers
        gravity = null
        geomagnetic = null
        Log.i(TAG, "Monitoring STOPPED.")
    }

    // --- SensorEventListener Implementation ---
    override fun onSensorChanged(event: SensorEvent?) {
        if (!isMonitoring || event == null) return

        var currentRollRadians: Float? = null
        var currentPitchRadians: Float? = null
        var currentAzimuthRadians: Float? = null
        var sensorMode = "Unknown"

        // Process based on which sensor type is active
        if (usingRotationVector && event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
            sensorMode = "RV"
            SensorManager.getRotationMatrixFromVector(rotationMatrixFromVector, event.values)
            SensorManager.getOrientation(rotationMatrixFromVector, orientationFromVector)
            currentAzimuthRadians = orientationFromVector[0]
            currentPitchRadians = orientationFromVector[1]
            currentRollRadians = orientationFromVector[2] // Roll is our primary value
        } else if (!usingRotationVector) { // Only process Accel/Mag if not using RV
            sensorMode = "A+M"
            // Update gravity or geomagnetic data
            when (event.sensor.type) {
                Sensor.TYPE_ACCELEROMETER -> gravity = event.values.clone()
                Sensor.TYPE_MAGNETIC_FIELD -> geomagnetic = event.values.clone()
                else -> return // Ignore other sensor types if somehow registered
            }

            val currentGravity = gravity
            val currentGeomagnetic = geomagnetic

            // Calculate orientation only if we have both readings
            if (currentGravity != null && currentGeomagnetic != null) {
                val success = SensorManager.getRotationMatrix(rotationMatrix, null, currentGravity, currentGeomagnetic)
                if (success) {
                    SensorManager.getOrientation(rotationMatrix, orientationValues)
                    currentAzimuthRadians = orientationValues[0]
                    currentPitchRadians = orientationValues[1]
                    currentRollRadians = orientationValues[2] // Roll is our primary value
                }
                // Consider resetting gravity = null, geomagnetic = null here if needed (see previous discussion)
            }
        } else {
            // Event is not from the sensor type we are currently listening for (shouldn't usually happen with current register logic)
            return
        }


        // --- Process Orientation Data (if available) ---
        if (currentRollRadians != null && currentPitchRadians != null && currentAzimuthRadians != null) {
            val rollDegrees = Math.toDegrees(currentRollRadians.toDouble())
            val pitchDegrees = Math.toDegrees(currentPitchRadians.toDouble())
            val azimuthDegrees = Math.toDegrees(currentAzimuthRadians.toDouble())

            // Log detailed orientation data
            Log.v(TAG, String.format( // Using Verbose level for frequent data
                "Orient(%s): Roll:%.1f°(%.2frad) Pitch:%.1f° Azim:%.1f°",
                sensorMode, rollDegrees, currentRollRadians, pitchDegrees, azimuthDegrees
            ))

            // Update UI - Display Roll prominently
            orientationTextView.text = String.format(
                "Roll: %.1f° (%.2f rad)\nPitch: %.1f°",
                rollDegrees, currentRollRadians, pitchDegrees
            )

            // --- Stomach Detection Logic (Based on Roll) ---
            if (abs(currentRollRadians) > STOMACH_ROLL_THRESHOLD_RADIANS) {
                if (!isInDangerousPosition) {
                    // Just entered dangerous position
                    isInDangerousPosition = true
                    alertStartTime = System.currentTimeMillis()
                    statusTextView.text = "Status: ON STOMACH!"
                    Log.i(TAG, "ON STOMACH detected (Roll: ${rollDegrees}°, Threshold: >${Math.toDegrees(STOMACH_ROLL_THRESHOLD_RADIANS.toDouble())}°)")
                    startContinuousAlerts()
                }
            } else {
                if (isInDangerousPosition) {
                    // Left dangerous position - stop all alerts
                    isInDangerousPosition = false
                    stopAllAlerts()
                    statusTextView.text = "Status: Monitoring..."
                    Log.i(TAG, "Off stomach (Roll: ${rollDegrees}°)")
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Log accuracy changes for debugging potential sensor issues
        sensor?.let {
            Log.d(TAG, "Accuracy for ${it.name} changed to: $accuracy")
        }
    }

    // --- Alert Management ---
    private fun startContinuousAlerts() {
        if (!isVibrating) {
            startContinuousVibration()
        }
        
        // Schedule sound alert after 30 seconds
        soundHandler = Handler(Looper.getMainLooper())
        soundHandler?.postDelayed({
            if (isInDangerousPosition && !isMakingSound) {
                startContinuousSound()
            }
        }, SOUND_ALERT_DELAY_MS)
    }
    
    private fun startContinuousVibration() {
        if (!vibrator.hasVibrator()) {
            Log.w(TAG, "Device has no vibrator")
            return
        }
        
        isVibrating = true
        vibrationHandler = Handler(Looper.getMainLooper())
        
        val vibrationRunnable = object : Runnable {
            override fun run() {
                if (isInDangerousPosition && isVibrating) {
                    Log.d(TAG, "Vibrating...")
                    val vibrationDuration = if (batteryOptimizedMode) VIBRATION_DURATION_MS / 2 else VIBRATION_DURATION_MS
                    val vibrationAmplitude = if (batteryOptimizedMode) 180 else 255 // Increased intensity
                    
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator.vibrate(VibrationEffect.createOneShot(vibrationDuration, vibrationAmplitude))
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator.vibrate(vibrationDuration)
                    }
                    
                    val nextInterval = if (batteryOptimizedMode) VIBRATION_INTERVAL_MS * 2 else VIBRATION_INTERVAL_MS
                    vibrationHandler?.postDelayed(this, nextInterval)
                }
            }
        }
        
        vibrationRunnable.run()
    }
    
    private fun startContinuousSound() {
        if (toneGenerator == null) {
            Log.w(TAG, "ToneGenerator not available")
            return
        }
        
        isMakingSound = true
        soundHandler = Handler(Looper.getMainLooper())
        
        val soundRunnable = object : Runnable {
            override fun run() {
                if (isInDangerousPosition && isMakingSound) {
                    Log.d(TAG, "Playing sound alert...")
                    try {
                        toneGenerator?.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 1000)
                    } catch (e: RuntimeException) {
                        Log.e(TAG, "Failed to play tone", e)
                    }
                    soundHandler?.postDelayed(this, SOUND_INTERVAL_MS)
                }
            }
        }
        
        soundRunnable.run()
    }
    
    private fun stopAllAlerts() {
        // Stop vibration
        isVibrating = false
        vibrationHandler?.removeCallbacksAndMessages(null)
        vibrationHandler = null
        
        // Stop sound
        isMakingSound = false
        soundHandler?.removeCallbacksAndMessages(null)
        soundHandler = null
        
        // Reset alert state
        isInDangerousPosition = false
        alertStartTime = 0
        
        Log.d(TAG, "All alerts stopped")
    }
}