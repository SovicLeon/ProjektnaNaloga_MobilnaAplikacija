package com.example.project

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.ConnectivityManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import kotlin.math.abs
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import kotlinx.coroutines.*

class MainActivity : AppCompatActivity() {
    private lateinit var sensorManager: SensorManager
    private var accelerometerSensor: Sensor? = null
    private var gyroscopeSensor: Sensor? = null

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var longitude: Double = 0.0
    private var latitude: Double = 0.0

    private val gravityV = FloatArray(3) { 0f }
    private var x: Float = 0f
    private var y: Float = 0f
    private var z: Float = 0f

    private val maxGyroValue = FloatArray(3) { 0f }
    private val maxAccValue = FloatArray(3) { 0f }

    private val handler: Handler = Handler(Looper.getMainLooper())

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1
    }

    private val runnable = object : Runnable {
        override fun run() {
            Log.d("MaxValues", "Max Acc X: ${maxAccValue[0]}, Y: ${maxAccValue[1]}, Z: ${maxAccValue[2]}")
            Log.d("MaxValues", "Max Gyro X: ${maxGyroValue[0]}, Y: ${maxGyroValue[1]}, Z: ${maxGyroValue[2]}")
            Log.d("Location", "Longitude: $longitude, Latitude: $latitude")
            val jsonData = """
                                {
                                    "sessionID": "id",
                                    "gyro_x" : ${maxGyroValue[0]},
                                    "gyro_y" : ${maxGyroValue[1]},
                                    "gyro_z" : ${maxGyroValue[2]},
                                    "acc_acceleration" : ${maxAccValue[0]+maxAccValue[1]+maxAccValue[2]},
                                    "acc_x" : ${maxAccValue[0]},
                                    "acc_y" : ${maxAccValue[1]},
                                    "acc_z" : ${maxAccValue[2]},
                                    "longitude": $longitude,
                                    "latitude": $latitude
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

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE
            )
        } else {
            fetchLocation()
        }

        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                // Got last known location. In some rare situations this can be null.
                location?.let {
                    latitude = it.latitude
                    longitude = it.longitude
                }
            }

        handler.post(runnable)
    }

    override fun onDestroy() {
        super.onDestroy()
        sensorManager.unregisterListener(accelerationListener)
        sensorManager.unregisterListener(gyroscopeListener)
        handler.removeCallbacks(runnable)

        coroutineScope.cancel()  // Cancelling all launched coroutines
    }


    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                // Permission was granted, continue with fetching location
                fetchLocation()
            } else {
                // Permission was denied, handle appropriately
                Toast.makeText(this, "Location permission denied", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun fetchLocation() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                // Got last known location. In some rare situations this can be null.
                location?.let {
                    latitude = it.latitude
                    longitude = it.longitude
                }
            }
        }
    }


    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    private fun postJsonData(url: String, jsonData: String) {
        if (!isNetworkAvailable()) {
            Toast.makeText(this, "No internet connection", Toast.LENGTH_LONG).show()
            return
        }

        coroutineScope.launch {
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

    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkInfo = connectivityManager.activeNetworkInfo
        return networkInfo?.isConnectedOrConnecting ?: false
    }

}
