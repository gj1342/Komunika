package com.example.komunikaprototype

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.komunikaprototype.databinding.StartingLobbyBinding

class StartingLobbyActivity : AppCompatActivity() {

    private lateinit var binding: StartingLobbyBinding
    private val sharedPreferences by lazy { getSharedPreferences("UserProfile", Context.MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = StartingLobbyBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Load user data from shared preferences
        loadUserData()

        // Back Button: Return to previous activity when clicked
        val backButton: ImageView = findViewById(R.id.back_icon)
        backButton.setOnClickListener {
            finish()
        }

        // Set up click listener for the Start button
        binding.startButton.setOnClickListener {
            navigateToNextActivity()
        }
    }

    private fun loadUserData() {
        val username = sharedPreferences.getString("username", "Username")
        val userType = sharedPreferences.getString("userType", "Non mute")
        val deviceId = "Device ID: ${android.os.Build.ID}"
        val profileImageUri = sharedPreferences.getString("profileImage", null)

        binding.username.text = username
        binding.deviceId.text = deviceId

        // Set up user type spinner adapter
        val userTypeAdapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_item, resources.getStringArray(R.array.user_type_array)
        )
        userTypeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.userTypeSpinner.adapter = userTypeAdapter

        // Set spinner to correct position based on received value or saved value
        val userTypePosition = userTypeAdapter.getPosition(userType)
        binding.userTypeSpinner.setSelection(userTypePosition)

        profileImageUri?.let {
            binding.profilePicture.setImageURI(Uri.parse(it))
        }
    }

    private fun navigateToNextActivity() {
        val userType = binding.userTypeSpinner.selectedItem.toString()
        var serviceId = binding.serviceIdEditText.text.toString() // Get the Service ID from EditText
        val username = binding.username.text.toString()

        // Validate the Service ID
        if (!isValidServiceId(serviceId)) {
            Toast.makeText(this, "Invalid Service ID. Only alphanumeric, underscore, and hyphen characters are allowed. Max length is 30.", Toast.LENGTH_LONG).show()
            return
        }

        // Replace spaces with underscores
        serviceId = serviceId.replace(" ", "_")

        val intent = if (userType == "Deaf/Mute") {
            Intent(this, NonSignersToSignersActivity::class.java)
        } else {
            Intent(this, SignersToNonSignersActivity::class.java)
        }

        // Pass the Service ID to the next activity
        intent.putExtra("SERVICE_ID", serviceId)
        intent.putExtra("USERNAME", username) // Pass the username
        Log.d("StartingLobbyActivity", "Navigating to next activity for userType: $userType, serviceId: $serviceId, username: $username")
        startActivity(intent)
    }

    // Function to validate the Service ID
    private fun isValidServiceId(serviceId: String): Boolean {
        val regex = "^[a-zA-Z0-9_-]{1,30}$".toRegex() // Alphanumeric, underscore, hyphen, max length 30
        return serviceId.matches(regex)
    }
}
