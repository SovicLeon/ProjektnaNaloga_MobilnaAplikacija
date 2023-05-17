package com.example.project

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlin.math.abs
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

class MainActivity : AppCompatActivity() {
    private lateinit var sensorManager: SensorManager
    private var accelerometerSensor: Sensor? = null
    private var gyroscopeSensor: Sensor? = null

    private val gravityV = FloatArray(3) { 0f }
    private var x: Float = 0f
    private var y: Float = 0f
    private var z: Float = 0f

    private val maxGyroValue = FloatArray(3) { 0f }
    private val maxAccValue = FloatArray(3) { 0f }

    private val handler: Handler = Handler(Looper.getMainLooper())
    private val runnable = object : Runnable {
        override fun run() {
            Log.d("MaxValues", "Max Acc X: ${maxAccValue[0]}, Y: ${maxAccValue[1]}, Z: ${maxAccValue[2]}")
            Log.d("MaxValues", "Max Gyro X: ${maxGyroValue[0]}, Y: ${maxGyroValue[1]}, Z: ${maxGyroValue[2]}")
            val jsonData = """
                                {
                                    "sessionID": "id",
                                    "gyro_x" : ${maxGyroValue[0]},
                                    "gyro_y" : ${maxGyroValue[1]},
                                    "gyro_z" : ${maxGyroValue[2]},
                                    "acc_acceleration" : ${maxAccValue[0]+maxAccValue[1]+maxAccValue[2]},
                                    "acc_x" : ${maxAccValue[0]},
                                    "acc_y" : ${maxAccValue[1]},
                                    "acc_z" : ${maxAccValue[2]}
                                }
                            """
            postJsonData("http://localhost:3001/vehicleData/",jsonData)
            maxAccValue.fill(0f)
            maxGyroValue.fill(0f)
            handler.postDelayed(this, 1000)
        }
    }

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

                val accValues = floatArrayOf(abs(x), abs(y), abs(z))
                for (i in accValues.indices) {
                    if (accValues[i] > maxAccValue[i]) maxAccValue[i] = accValues[i]
                }
            }
        }
    }

    private val gyroscopeListener = object : SensorEventListener {
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
            // Handle accuracy changes
        }

        override fun onSensorChanged(event: SensorEvent?) {
            event?.let {
                val gyroValues = floatArrayOf(abs(event.values[0]), abs(event.values[1]), abs(event.values[2]))
                for (i in gyroValues.indices) {
                    if (gyroValues[i] > maxGyroValue[i]) maxGyroValue[i] = gyroValues[i]
                }
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

        handler.post(runnable)
    }

    override fun onDestroy() {
        super.onDestroy()
        sensorManager.unregisterListener(accelerationListener)
        sensorManager.unregisterListener(gyroscopeListener)
        handler.removeCallbacks(runnable)
    }

    private fun postJsonData(url: String, jsonData: String) {
        val client = OkHttpClient()
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val body = jsonData.toRequestBody(mediaType)

        val request = Request.Builder()
            .url(url)
            .post(body)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Unexpected code $response")
            println(response.body?.string())
        }
    }
}
