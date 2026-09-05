package com.ecodimmer

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.PixelFormat
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import java.util.Calendar
import kotlin.math.sqrt

class DimmerAccessibilityService : AccessibilityService(), SensorEventListener {

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private lateinit var prefsManager: PrefsManager
    private var sensorManager: SensorManager? = null
    private var accelerometer: Sensor? = null
    private var isSensorRegistered = false

    private var lastAcceleration = 0f
    private var currentAcceleration = SensorManager.GRAVITY_EARTH
    private var acceleration = 0f

    companion object {
        const val ACTION_TOGGLE_DIMMER = "com.ecodimmer.TOGGLE_DIMMER"
        const val ACTION_UPDATE_ALPHA = "com.ecodimmer.UPDATE_ALPHA"
        const val ACTION_STATE_CHANGED = "com.ecodimmer.STATE_CHANGED"

        var isDimming = false
            private set
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_TOGGLE_DIMMER -> {
                    isDimming = !isDimming
                    prefsManager.isDimmingActive = isDimming
                    updateOverlay()
                    broadcastState()
                }
                ACTION_UPDATE_ALPHA -> {
                    overlayView?.alpha = prefsManager.dimmingLevel
                }
                Intent.ACTION_TIME_TICK -> {
                    checkSchedule()
                }
            }
        }
    }

    // Hört live auf Änderungen des Shake-to-Rescue Schalters in den Einstellungen
    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "shake_to_rescue_enabled") {
            if (prefsManager.isShakeToRescueEnabled && isDimming) {
                registerShakeListener()
            } else {
                unregisterShakeListener()
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        prefsManager = PrefsManager(this)
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        isDimming = prefsManager.isDimmingActive

        // SharedPreferences Listener registrieren
        try {
            prefsManager.sharedPreferences.registerOnSharedPreferenceChangeListener(preferenceListener)
        } catch (e: Exception) {
            // Fallback falls sharedPreferences intern anders benannt ist
        }

        val systemFilter = IntentFilter(Intent.ACTION_TIME_TICK)
        val customFilter = IntentFilter().apply {
            addAction(ACTION_TOGGLE_DIMMER)
            addAction(ACTION_UPDATE_ALPHA)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, systemFilter, Context.RECEIVER_EXPORTED)
            registerReceiver(receiver, customFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(receiver, systemFilter)
            registerReceiver(receiver, customFilter)
        }

        updateOverlay()
    }

    private fun checkSchedule() {
        if (!prefsManager.isScheduleEnabled) return

        val calendar = Calendar.getInstance()
        val currentHour = calendar.get(Calendar.HOUR_OF_DAY)
        val currentMinute = calendar.get(Calendar.MINUTE)

        if (currentHour == prefsManager.scheduleStartHour && currentMinute == prefsManager.scheduleStartMinute) {
            if (!isDimming) {
                isDimming = true
                prefsManager.isDimmingActive = true
                updateOverlay()
                broadcastState()
            }
        } else if (currentHour == prefsManager.scheduleEndHour && currentMinute == prefsManager.scheduleEndMinute) {
            if (isDimming) {
                isDimming = false
                prefsManager.isDimmingActive = false
                updateOverlay()
                broadcastState()
            }
        }
    }

    private fun broadcastState() {
        val intent = Intent(ACTION_STATE_CHANGED)
        intent.setPackage(packageName)
        sendBroadcast(intent)
    }

    private fun updateOverlay() {
        if (isDimming) {
            showOverlay()
            registerShakeListener()
        } else {
            hideOverlay()
            unregisterShakeListener()
        }
    }

    private fun showOverlay() {
        if (overlayView == null) {
            overlayView = View(this).apply {
                setBackgroundColor(Color.BLACK)
            }

            val layoutParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutParams.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }

            windowManager?.addView(overlayView, layoutParams)
        }

        overlayView?.alpha = prefsManager.dimmingLevel
    }

    private fun hideOverlay() {
        overlayView?.let {
            windowManager?.removeView(it)
            overlayView = null
        }
    }

    // ---------- Dynamisches Sensor-Management (Akkuschonend) ----------
    private fun registerShakeListener() {
        if (prefsManager.isShakeToRescueEnabled && accelerometer != null && !isSensorRegistered) {
            sensorManager?.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI)
            isSensorRegistered = true
        }
    }

    private fun unregisterShakeListener() {
        if (isSensorRegistered) {
            sensorManager?.unregisterListener(this)
            isSensorRegistered = false
        }
    }

    // ---------- SensorEventListener ----------
    override fun onSensorChanged(event: SensorEvent?) {
        event?.let {
            val x = it.values[0]
            val y = it.values[1]
            val z = it.values[2]

            lastAcceleration = currentAcceleration
            currentAcceleration = sqrt((x * x + y * y + z * z).toDouble()).toFloat()
            val delta = currentAcceleration - lastAcceleration
            acceleration = acceleration * 0.9f + delta

            // Schwellenwert auf 16f angehoben
            if (acceleration > 16f) {
                if (isDimming) {
                    isDimming = false
                    prefsManager.isDimmingActive = false
                    updateOverlay() // unregistert intern automatisch den Sensor
                    broadcastState()
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {
        if (::prefsManager.isInitialized) {
            isDimming = false
            prefsManager.isDimmingActive = false
            updateOverlay()
            broadcastState()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        hideOverlay()
        unregisterShakeListener()
        try {
            prefsManager.sharedPreferences.unregisterOnSharedPreferenceChangeListener(preferenceListener)
        } catch (e: Exception) {}
        try {
            unregisterReceiver(receiver)
        } catch (e: Exception) {}
    }
}