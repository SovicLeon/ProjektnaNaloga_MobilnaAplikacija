package com.example.project

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
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
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
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

            // Update the location TextView with the current latitude and longitude values
            val locationTextView = findViewById<TextView>(R.id.textViewLocation)
            val locationText = "Location\nLatitude: $latitude\nLongitude: $longitude"
            locationTextView.text = locationText

            // Update the accelerometer TextView with the current sensor values
            val accTextView = findViewById<TextView>(R.id.textViewAcc)
            val accText = "Accelerometer\nX: ${maxAccValue[0]}\nY: ${maxAccValue[1]}\nZ: ${maxAccValue[2]}"
            accTextView.text = accText

            // Update the gyroscope TextView with the current sensor values
            val gyroTextView = findViewById<TextView>(R.id.textViewGyro)
            val gyroText = "Gyroscope\nX: ${maxGyroValue[0]}\nY: ${maxGyroValue[1]}\nZ: ${maxGyroValue[2]}"
            gyroTextView.text = gyroText

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
            if (latitude  != 0.0 && longitude != 0.0) {
                postJsonData("http://192.168.1.6:3001/vehicleData/",jsonData)
            }
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
                gravityV[1] = alpha * gravityV[1] + (1 - alpha) * event.values[1]
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

    private var startTime: Long = 0
    private var endTime: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val startButton = findViewById<Button>(R.id.startButton)
        val stopButton = findViewById<Button>(R.id.stopButton)

        startButton.setOnClickListener {
            startTime = System.currentTimeMillis()
            handler.post(runnable)
        }

        stopButton.setOnClickListener {
            endTime = System.currentTimeMillis()
            handler.removeCallbacks(runnable)
            processVehicleData()
        }

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
            requestLocationUpdates()
        }
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
                requestLocationUpdates()
            } else {
                // Permission was denied, handle appropriately
                Toast.makeText(this, "Location permission denied", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun requestLocationUpdates() {
        val locationRequest = LocationRequest.create().apply {
            interval = 1000 // Update interval in milliseconds
            fastestInterval = 500 // Fastest update interval in milliseconds
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }

        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult?) {
                locationResult?.lastLocation?.let { location ->
                    latitude = location.latitude
                    longitude = location.longitude
                }
            }
        }

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ),
                LOCATION_PERMISSION_REQUEST_CODE
            )
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

            val sessionCookie: String? = retrieveSessionCookie()
            val request = Request.Builder()
                .url(url)
                .post(body)
                .header("Cookie", sessionCookie ?: "") // Use an empty string if the sessionCookie is null
                .build()

            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("Unexpected code $response")
                    val responseBody = response.body?.string()
                    println(responseBody)

                    runOnUiThread {
                        val debugTextView = findViewById<TextView>(R.id.debugTextView)
                        debugTextView.text = "Data sending successfully!"
                        //debugTextView.text = "Data sent successfully!\nResponse: $responseBody"
                    }
                }
            } catch (e: IOException) {
                // Handle the exception here
                e.printStackTrace()
                runOnUiThread {
                    val debugTextView = findViewById<TextView>(R.id.debugTextView)
                    debugTextView.text = e.message
                }
            } finally {
                if (url == "http://192.168.0.120:3001/vehicleData/process") {
                    runOnUiThread {
                        val debugTextView = findViewById<TextView>(R.id.debugTextView)
                        debugTextView.text = "Not sending data."
                    }
                }
            }
        }
    }

    private fun processVehicleData() {
        val jsonData = """
        {
            "start": $startTime,
            "end": $endTime
        }
        """
        postJsonData("http://192.168.0.120:3001/vehicleData/process", jsonData)
    }

    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkInfo = connectivityManager.activeNetworkInfo
        return networkInfo?.isConnectedOrConnecting ?: false
    }

    private fun retrieveSessionCookie(): String? {
        val sharedPreferences: SharedPreferences = getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)
        return sharedPreferences.getString("sessionCookie", null)
    }
}
