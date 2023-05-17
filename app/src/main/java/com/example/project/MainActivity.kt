package com.example.project

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log

class MainActivity : AppCompatActivity() {
    private lateinit var sensorManager: SensorManager
    private var accelerometerSensor: Sensor? = null
    private var gyroscopeSensor: Sensor? = null

    private val gravityV = FloatArray(3) { 0f }
    private var x: Float = 0f
    private var y: Float = 0f
    private var z: Float = 0f

    private val accelerationListener = object : SensorEventListener {
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
            // Handle accuracy changes
        }

        override fun onSensorChanged(event: SensorEvent?) {
            event?.let {
                val alpha = 0.8f

                gravityV[0] = alpha * gravityV[0] + (1 - alpha) * event.values[0]
                gravityV[1] = alpha * gravityV[1] + (1 - alpha)* event.values[1]
                gravityV[2] = alpha * gravityV[2] + (1 - alpha) * event.values[2]

                x = event.values[0] - gravityV[0]
                y = event.values[1] - gravityV[1]
                z = event.values[2] - gravityV[2]

                Log.d("AccelerationValues", "x: $x, y: $y, z: $z")
            }
        }
    }

    private val gyroscopeListener = object : SensorEventListener {
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
            // Handle accuracy changes
        }

        override fun onSensorChanged(event: SensorEvent?) {
            event?.let {
                Log.d("GyroscopeValues", "x: ${event.values[0]}, y: ${event.values[1]}, z: ${event.values[2]}")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        gyroscopeSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

        accelerometerSensor?.let {
            sensorManager.registerListener(accelerationListener, it, SensorManager.SENSOR_DELAY_GAME)
        }

        gyroscopeSensor?.let {
            sensorManager.registerListener(gyroscopeListener, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        sensorManager.unregisterListener(accelerationListener)
        sensorManager.unregisterListener(gyroscopeListener)
    }
}
