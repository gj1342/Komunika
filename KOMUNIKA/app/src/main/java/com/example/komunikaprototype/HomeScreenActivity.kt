package com.example.komunikaprototype

import android.Manifest
import android.app.AlertDialog
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.text.InputFilter
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.viewpager2.widget.ViewPager2
import de.hdodenhof.circleimageview.CircleImageView
import java.io.OutputStream

class HomeScreenActivity : AppCompatActivity() {

    private lateinit var viewPager: ViewPager2
    private lateinit var titleTextView: TextView
    private lateinit var descriptionTextView: TextView
    private lateinit var profileImageView: CircleImageView
    private lateinit var usernameTextView: TextView
    private lateinit var deviceIdTextView: TextView
    private lateinit var userTypeSpinner: Spinner
    private lateinit var editUsernameIcon: ImageView
    private lateinit var chatIcon: ImageView

    private lateinit var cameraLauncher: ActivityResultLauncher<Intent>

    private val sharedPreferences by lazy { getSharedPreferences("UserProfile", Context.MODE_PRIVATE) }

    private val cameraRequestCode = 101

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.home_screen)

        // Initialize UI components
        profileImageView = findViewById(R.id.profile_picture)
        usernameTextView = findViewById(R.id.username)
        deviceIdTextView = findViewById(R.id.device_id)
        userTypeSpinner = findViewById(R.id.user_type_spinner)
        editUsernameIcon = findViewById(R.id.imageView2)
        chatIcon = findViewById(R.id.chat_icon)
        viewPager = findViewById(R.id.viewPager)
        descriptionTextView = findViewById(R.id.descriptionTextView)

        chatIcon.setOnClickListener {
            val intent = Intent(this, FeedBackActivity::class.java)
            startActivity(intent)
            finish()
        }

        // Initialize the camera launcher
        cameraLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val bitmap = result.data?.extras?.get("data") as Bitmap
                profileImageView.setImageBitmap(bitmap)
                saveProfileImage(bitmap)
            }
        }

        // Load user profile data
        loadUserData()

        // Set up the carousel
        val titles = arrayOf("Singlephone", "Multiphone", "Vocabulary List")
        val descriptions = arrayOf(
            "Translate voice to FSL and text to voice on a single device.",
            "Connect multiple devices for group communication.",
            "Browse and learn common words, phrases, and questions in FSL."
        )

        val images = intArrayOf(R.drawable.singlephone, R.drawable.multiphone, R.drawable.vocabularylist)

        // Set up the carousel
        val adapter = ViewPagerAdapter(titles, images, this, viewPager) // 🔥 Pass ViewPager reference
        viewPager.adapter = adapter

        viewPager.setPageTransformer(DepthPageTransformer())
        viewPager.offscreenPageLimit = 3 // Smooth transitions

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                val actualPosition = position % descriptions.size
                descriptionTextView.text = descriptions[actualPosition] // ✅ Update description dynamically
            }
        })

        // Profile picture click listener to change the picture
        profileImageView.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
                openCamera()
            } else {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), cameraRequestCode)
            }
        }

        // Edit username listener
        editUsernameIcon.setOnClickListener {
            val usernameEditText = EditText(this)
            usernameEditText.setText(usernameTextView.text)
            usernameEditText.filters = arrayOf(InputFilter.LengthFilter(10))

            val dialog = AlertDialog.Builder(this)
                .setTitle("Edit Username")
                .setView(usernameEditText)
                .setPositiveButton("Save") { _, _ ->
                    val newUsername = usernameEditText.text.toString()
                    usernameTextView.text = newUsername
                    saveUserData(newUsername, userTypeSpinner.selectedItem.toString())
                }
                .setNegativeButton("Cancel", null)
                .create()
            dialog.show()
        }

        // Set the spinner to the correct user type
        userTypeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                saveUserData(usernameTextView.text.toString(), parent.getItemAtPosition(position).toString())
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }

    override fun onResume() {
        super.onResume()
        // Handle the data passed from StartingLobbyActivity
        intent?.let {
            val username = it.getStringExtra("username")
            val userType = it.getStringExtra("userType")
            val profileImageUri = it.getStringExtra("profileImageUri")

            // Update the UI with the received data
            username?.let { updatedUsername -> usernameTextView.text = updatedUsername }
            userType?.let { updatedUserType ->
                val userTypePosition = (userTypeSpinner.adapter as ArrayAdapter<String>).getPosition(updatedUserType)
                userTypeSpinner.setSelection(userTypePosition)
            }
            profileImageUri?.let { uri -> profileImageView.setImageURI(Uri.parse(uri)) }
        }
    }

    private fun loadUserData() {
        val username = sharedPreferences.getString("username", "Username")
        val userType = sharedPreferences.getString("userType", "Non mute") // Default userType
        val deviceId = "Device ID: ${android.os.Build.ID}"
        val profileImageUri = sharedPreferences.getString("profileImage", null)

        usernameTextView.text = username
        deviceIdTextView.text = deviceId

        // Custom ArrayAdapter to set text color to black and background color to white
        val userTypeAdapter = object : ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, resources.getStringArray(R.array.user_type_array)) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent)
                (view as TextView).setTextColor(Color.BLACK)
                return view
            }

            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getDropDownView(position, convertView, parent)
                (view as TextView).setTextColor(Color.BLACK)
                view.setBackgroundColor(Color.WHITE)  // Set background color to white
                return view
            }
        }
        userTypeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        userTypeSpinner.adapter = userTypeAdapter

        val userTypePosition = userTypeAdapter.getPosition(userType)
        userTypeSpinner.setSelection(userTypePosition)

        profileImageUri?.let {
            profileImageView.setImageURI(Uri.parse(it))
        }
    }

    private fun saveUserData(username: String, userType: String) {
        with(sharedPreferences.edit()) {
            putString("username", username)
            putString("userType", userType)
            apply()
        }
    }

    private fun openCamera() {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        cameraLauncher.launch(intent)
    }

    private fun saveProfileImage(bitmap: Bitmap) {
        val uri = saveBitmapToGallery(bitmap)
        with(sharedPreferences.edit()) {
            putString("profileImage", uri.toString())
            apply()
        }
    }

    private fun saveBitmapToGallery(bitmap: Bitmap): Uri? {
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "profile_image_${System.currentTimeMillis()}.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/ProfileImages")
        }

        val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        uri?.let {
            val outputStream: OutputStream? = contentResolver.openOutputStream(it)
            outputStream?.use { stream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
            }
        }
        return uri
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == cameraRequestCode) {
            if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                openCamera()
            } else {
                Toast.makeText(this, "Camera permission is required to take a profile picture.", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
