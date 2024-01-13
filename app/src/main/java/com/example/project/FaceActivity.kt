package com.example.project
import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.project.R
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.io.IOException
import android.util.Log
import org.json.JSONObject
import java.lang.Math.abs
import java.util.concurrent.TimeUnit

class UnsignedByte(val value: UByte)

fun List<UnsignedByte>.toByteArray(): ByteArray {
    return ByteArray(size) { this[it].value.toByte() }
}

class FaceActivity : AppCompatActivity() {

    companion object {
        private const val CAMERA_PERMISSION_CODE = 1
        private const val CAMERA_REQUEST_CODE = 2
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_face)

        val takePhotoButton: Button = findViewById(R.id.capture_photo)
        takePhotoButton.setOnClickListener {
            if (checkCameraPermission()) {
                openCamera()
            } else {
                requestCameraPermission()
            }
        }
    }

    private fun checkCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestCameraPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.CAMERA),
            CAMERA_PERMISSION_CODE
        )
    }

    private fun openCamera() {
        val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        startActivityForResult(cameraIntent, CAMERA_REQUEST_CODE)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openCamera()
            } else {
                Toast.makeText(
                    this,
                    "Camera permission denied",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    fun compressFunction(diff: List<Int>): String {
        val compressed = StringBuilder()

        compressed.append(Integer.toBinaryString(diff[0]).padStart(8, '0'))

        var i = 1
        while (i < diff.size) {
            when {
                diff[i] > 30 || diff[i] < -30 -> {
                    compressed.append("10")
                    compressed.append(if (diff[i] > 0) "0" else "1")
                    compressed.append(Integer.toBinaryString(Math.abs(diff[i])).padStart(8, '0'))
                }
                diff[i] != 0 -> {
                    compressed.append("00")
                    when {
                        Math.abs(diff[i]) <= 2 -> {
                            compressed.append("00")
                            compressed.append(
                                if (diff[i] < 0) Integer.toBinaryString(diff[i] + 2).padStart(2, '0')
                                else Integer.toBinaryString(diff[i] + 1).padStart(2, '0')
                            )
                        }
                        Math.abs(diff[i]) <= 6 -> {
                            compressed.append("01")
                            compressed.append(
                                if (diff[i] < 0) Integer.toBinaryString(diff[i] + 6).padStart(3, '0')
                                else Integer.toBinaryString(diff[i] + 1).padStart(3, '0')
                            )
                        }
                        Math.abs(diff[i]) <= 14 -> {
                            compressed.append("10")
                            compressed.append(
                                if (diff[i] < 0) Integer.toBinaryString(diff[i] + 14).padStart(4, '0')
                                else Integer.toBinaryString(diff[i] + 1).padStart(4, '0')
                            )
                        }
                        Math.abs(diff[i]) <= 30 -> {
                            compressed.append("11")
                            compressed.append(
                                if (diff[i] < 0) Integer.toBinaryString(diff[i] + 30).padStart(5, '0')
                                else Integer.toBinaryString(diff[i] + 1).padStart(5, '0')
                            )
                        }
                    }
                }
                diff[i] == 0 -> {
                    compressed.append("01")
                    var counter = 0

                    while (counter < 8 && i + counter < diff.size && diff[i + counter] == 0) {
                        counter++
                    }

                    compressed.append(Integer.toBinaryString(counter - 1).padStart(3, '0'))
                    i += counter - 1
                }
            }
            i++
        }

        compressed.append("11")

        return compressed.toString()
    }






    private fun uploadPhoto(photo: Bitmap) {
        val byteArrayOutputStream = ByteArrayOutputStream()
        val sessionCookie: String? = retrieveSessionCookie()
        Log.d("Cookie", "$sessionCookie")
        photo.compress(Bitmap.CompressFormat.JPEG, 100, byteArrayOutputStream)

        // Extracting bytes from ByteArrayOutputStream
        val imageBytes = byteArrayOutputStream.toByteArray()

        // Creating a list to store unsigned bytes
        val unsignedBytesList = mutableListOf<UnsignedByte>()

        // Converting signed bytes to unsigned bytes
        for (byte in imageBytes) {
            unsignedBytesList.add(UnsignedByte(byte.toUByte()))
        }

        // Now, you can use unsignedBytesList as a representation of std::vector<unsigned char>
        // Log the size of the data format
        Log.d("Cookie", "${unsignedBytesList.size}")

        val uncompressed = mutableListOf<Int>()

        // Assuming you have a List<UnsignedByte> named 'input' already populated
        for (byte in unsignedBytesList) {
            uncompressed.add(byte.value.toInt())
        }

        Log.d("Cookie", "${uncompressed.size}")

        val diff = uncompressed.toMutableList()

        for (i in 1 until uncompressed.size) {
            diff[i] = uncompressed[i] - uncompressed[i - 1]
        }

        for (i in 0 until 5) {
            Log.d("Cookie", "${diff[i]}")
        }

        Log.d("Cookie", "${diff.size}")

        // Compression
        var compressed = compressFunction(diff)

        Log.d("Cookie", "${compressed.length}")

        // Update the RequestBody to send the compressed string
        val requestBody: RequestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("compressedData", compressed)
            .build()

        val request = Request.Builder()
            .url("http://192.168.0.25:3001/photos/")
            .post(requestBody)
            .header("Cookie", sessionCookie ?: "")
            .build()

        val client = OkHttpClient.Builder()
            .connectTimeout(0, TimeUnit.SECONDS)  // Infinite timeout for connection
            .readTimeout(0, TimeUnit.SECONDS)     // Infinite timeout for read
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                // Handle the failure case
                e.printStackTrace()
            }

            override fun onResponse(call: Call, response: Response) {
                // Handle the response
                Log.d("Cookie", response.message)
                if (response.isSuccessful) {
                    Log.d("Cookie", response.toString())
                    // Photo upload successful
                    // Handle the response here
                    val responseBody = response.body?.string()
                    val jsonObject = JSONObject(responseBody)
                    val testPassed = jsonObject.optBoolean("testPassed")

                    if (testPassed) {
                        val intent = Intent(this@FaceActivity, MainActivity::class.java)
                        startActivity(intent)
                        finish()
                    } else {
                        // Test failed, redirect to login activity
                        val intent = Intent(this@FaceActivity, LoginActivity::class.java)
                        startActivity(intent)
                        finish()
                    }
                } else {
                    // Photo upload failed
                    // Handle the response here
                    // Test failed, redirect to login activity
                    val intent = Intent(this@FaceActivity, LoginActivity::class.java)
                    startActivity(intent)
                    finish()
                }
            }
        })
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == CAMERA_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            val photo: Bitmap? = data?.extras?.get("data") as? Bitmap

            photo?.let {
                uploadPhoto(it)
            }
        }
    }

    private fun retrieveSessionCookie(): String? {
        val sharedPreferences: SharedPreferences = getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)
        return sharedPreferences.getString("sessionCookie", null)
    }
}