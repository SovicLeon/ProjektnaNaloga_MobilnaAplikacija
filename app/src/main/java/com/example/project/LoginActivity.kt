package com.example.project

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

class LoginActivity : AppCompatActivity() {

    private lateinit var usernameEditText: EditText
    private lateinit var passwordEditText: EditText
    private lateinit var loginButton: Button
    private lateinit var errorTextView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        usernameEditText = findViewById(R.id.usernameEditText)
        passwordEditText = findViewById(R.id.passwordEditText)
        loginButton = findViewById(R.id.loginButton)
        errorTextView = findViewById(R.id.errorTextView)

        loginButton.setOnClickListener {
            val username = usernameEditText.text.toString()
            val password = passwordEditText.text.toString()
            performLogin(username, password)
        }
    }

    private fun performLogin(username: String, password: String) {
        val url = "http://86.58.50.249:3001/users/login" // Replace with your actual login API URL

        val jsonData = """
        {
            "username": "$username",
            "password": "$password"
        }
        """.trimIndent()

        val client = OkHttpClient()
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val body = jsonData.toRequestBody(mediaType)

        val request = Request.Builder()
            .url(url)
            .post(body)
            .build()

        GlobalScope.launch(Dispatchers.IO) {
            try {
                val response = client.newCall(request).execute()
                val responseBody = response.body?.string()
                if (response.isSuccessful && responseBody != null) {
                    // Successful login, navigate to the main activity
                    val intent = Intent(this@LoginActivity, FaceActivity::class.java)
                    startActivity(intent)
                    finish()
                    // Save the session cookie if available
                    val cookies = response.headers.values("Set-Cookie")
                    if (cookies.isNotEmpty()) {
                        val sessionCookie = cookies[0]

                        // Save the sessionCookie to shared preferences
                        saveSessionCookie(sessionCookie)
                    }
                } else {
                    // Invalid username or password, display error message
                    runOnUiThread {
                        errorTextView.text = "Invalid username or password"
                    }
                }
            } catch (e: IOException) {
                e.printStackTrace()
                // Handle network or server errors
                runOnUiThread {
                    errorTextView.text = "Error logging in"
                }
            }
        }
    }

    private fun saveSessionCookie(sessionCookie: String) {
        val sharedPreferences: SharedPreferences = getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)
        val editor: SharedPreferences.Editor = sharedPreferences.edit()
        editor.putString("sessionCookie", sessionCookie)
        editor.apply()
    }
}
