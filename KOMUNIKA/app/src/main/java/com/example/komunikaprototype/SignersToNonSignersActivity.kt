package com.example.komunikaprototype

import android.Manifest
import android.content.pm.PackageManager
import android.database.DataSetObserver
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.komunikaprototype.databinding.SignersToNonsignersBinding
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.core.Preview
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.CameraSelector
import android.util.Log
import android.widget.EditText
import android.widget.ImageButton
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import com.google.mediapipe.tasks.vision.core.RunningMode
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.speech.tts.TextToSpeech
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView
import androidx.core.app.ActivityCompat
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import java.io.File
import java.io.IOException
import org.json.JSONObject
import java.util.Locale
import android.widget.ScrollView
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.content.Context

class SignersToNonSignersActivity : AppCompatActivity(), HandLandmarkerHelper.LandmarkerListener {

    private lateinit var viewBinding: SignersToNonsignersBinding
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var inferenceExecutor: ExecutorService
    private var handLandmarkerHelper: HandLandmarkerHelper? = null
    private lateinit var sendButton: ImageButton
    private lateinit var messageInput: EditText
    private lateinit var connectionsClient: ConnectionsClient
    private var isConnected = false
    @Volatile private var modelsLoaded = false
    private lateinit var serviceId: String // Service ID received from StartingLobbyActivity

    private lateinit var sentenceTextView: TextView
    private val sentenceBuilder = StringBuilder()
    private val handler = Handler(Looper.getMainLooper())
    private var resetRunnable: Runnable? = null

    private lateinit var connectedUsers: MutableList<UserWithImage>
    private lateinit var userSpinnerAdapter: UserSpinnerAdapter
    private val connectedEndpoints = mutableMapOf<String, String>() // Map of endpointId to username
    
    private lateinit var participantCountTextView: TextView

    private val role = "Non Signers" // Role designation

    private lateinit var microphoneButton: ImageButton
    private var speechService: SpeechService? = null
    private var voskModel: Model? = null

    private lateinit var overlayView: OverlayView

    private lateinit var textToSpeech: TextToSpeech  // TTS Object
    private var isTTSInitialized = false
    private var isTTSEnabled = true  // Track if TTS is enabled by user

    private val predictionHistory = mutableListOf<String>() // To store individual predictions

    private lateinit var messagesTextView: TextView
    private val messagesBuilder = StringBuilder()
    
    private var myProfileImageBase64: String? = null // Store current user's profile image

    private val activityResultLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        if (permissions.values.all { it }) {
            startCamera()
            startDiscovering()
        } else {
            Toast.makeText(this, "Permission request denied", Toast.LENGTH_SHORT).show()
        }
    }

    private var isDetectionActive = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewBinding = SignersToNonsignersBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)

        overlayView = findViewById(R.id.overlayView)

        // Load profile image from SharedPreferences
        loadProfileImage()

        // Back Button: Return to previous activity when clicked
        val backButton: ImageView = findViewById(R.id.back_icon)
        backButton.setOnClickListener {
            finish()
        }

        // Initialize the microphone button
        microphoneButton = findViewById(R.id.microphone_button)

        microphoneButton.setOnClickListener {
            if (speechService != null) {
                stopMicrophoneRecognition()
                microphoneButton.setBackgroundColor(ContextCompat.getColor(this, android.R.color.transparent)) // Reset to default
                Toast.makeText(this, "Microphone stopped", Toast.LENGTH_SHORT).show()
            } else {
                if (allPermissionsGranted()) {
                    startMicrophoneRecognition()
                    microphoneButton.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_red_light)) // Change color to indicate it's active
                    Toast.makeText(this, "Microphone is on. You can speak.", Toast.LENGTH_SHORT).show()
                } else {
                    requestPermissions()
                }
            }
        }

        // Copy the model files
        copyAssetsToInternalStorage("model-en-us", "model-en-us")

        // Initialize the Vosk model
        try {
            val modelPath = File(filesDir, "model-en-us").absolutePath
            voskModel = Model(modelPath)
            Log.d("Vosk", "Model loaded successfully from $modelPath")
        } catch (e: IOException) {
            Log.e("Vosk", "Failed to load the Vosk model", e)
        }

        // Initialize Text-to-Speech switch
        val ttsSwitch = findViewById<androidx.appcompat.widget.SwitchCompat>(R.id.ttsSwitch)
        ttsSwitch.setOnCheckedChangeListener { _, isChecked ->
            isTTSEnabled = isChecked
            if (isChecked) {
                Toast.makeText(this, "Text-to-Speech enabled", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Text-to-Speech disabled", Toast.LENGTH_SHORT).show()
                // Stop any ongoing speech
                if (::textToSpeech.isInitialized) {
                    textToSpeech.stop()
                }
            }
        }

        // Initialize participant count TextView
        participantCountTextView = findViewById(R.id.participantCountTextView)

        // Initialize the connectedUsernames list and adapter first
        connectedUsers = mutableListOf(
            UserWithImage("None"), 
            UserWithImage("All")
        )
        
        userSpinnerAdapter = UserSpinnerAdapter(
            this,
            R.layout.spinner_item_user,
            connectedUsers
        )
        
        viewBinding.userSpinner.adapter = userSpinnerAdapter

        // Update participant count dynamically
        updateParticipantCount()

        // Add listener to update participant count on change
        userSpinnerAdapter.registerDataSetObserver(object : DataSetObserver() {
            override fun onChanged() {
                updateParticipantCount()
            }
        })

        // Initialize the new sentence TextView
        sentenceTextView = findViewById(R.id.sentenceTextView)

        // Initialize TextToSpeech
        textToSpeech = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = textToSpeech.setLanguage(Locale.US)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Toast.makeText(this, "TTS Language not supported!", Toast.LENGTH_SHORT).show()
                } else {
                    isTTSInitialized = true
                }
            } else {
                Toast.makeText(this, "TTS Initialization Failed!", Toast.LENGTH_SHORT).show()
            }
        }

        // Retrieve Service ID from intent
        serviceId = intent.getStringExtra("SERVICE_ID") ?: SERVICE_ID

        // Initialize components
        connectionsClient = Nearby.getConnectionsClient(this)
        cameraExecutor = Executors.newSingleThreadExecutor()
        inferenceExecutor = Executors.newFixedThreadPool(2)
        sendButton = findViewById(R.id.send_button)
        messageInput = findViewById(R.id.message_input)

        // Add listener to the send button
        sendButton.setOnClickListener {

            // Dismiss the soft keyboard
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(messageInput.windowToken, 0)

            // Get the selected item position
            val selectedPosition = viewBinding.userSpinner.selectedItemPosition
            
            // Check if the position is valid
            if (selectedPosition >= 0 && selectedPosition < connectedUsers.size) {
                val selectedUserObj = connectedUsers[selectedPosition]
                val selectedUser = selectedUserObj.username
                val message = messageInput.text.toString().trim()

                if (message.isEmpty()) {
                    Toast.makeText(this@SignersToNonSignersActivity, "Message cannot be empty.", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                if (selectedUser == "None") {
                    Toast.makeText(this@SignersToNonSignersActivity, "No user selected.", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                if (!isConnected) {
                    Toast.makeText(this@SignersToNonSignersActivity, "Cannot send message. Devices are not connected.", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                // Send the message to the selected user or all users
                sendMessageToUser(message, selectedUser)

                // Optionally clear the EditText after sending the message
                messageInput.text.clear()
            } else {
                Toast.makeText(this@SignersToNonSignersActivity, "Invalid user selection.", Toast.LENGTH_SHORT).show()
            }
        }

        // Add listener to the spinner (to handle "None" selection and stop predictions)
        viewBinding.userSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                if (position < connectedUsers.size) {
                    val selectedUser = connectedUsers[position]
                    Log.d(TAG, "User selected: ${selectedUser.username}")

                    if (selectedUser.username == "None") {
                        // Stop hand detection and model predictions
                        stopHandDetection()
                        stopModelPrediction()
                        Toast.makeText(this@SignersToNonSignersActivity, "Hand detection and predictions stopped.", Toast.LENGTH_SHORT).show()
                    } else {
                        // Handle control update
                        sendControlUpdate(selectedUser.username)
                    }
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>) {
                // Do nothing if no selection
            }
        }

        // Initialize the messages TextView
        messagesTextView = findViewById(R.id.messagesTextView)

        // Request camera permissions
        if (allPermissionsGranted()) {
            startCamera()

            // Start advertising and discovery to establish connection
            startDiscovering()
        } else {
            requestPermissions()
        }
    }

    private fun stopModelPrediction() {
        runOnUiThread {
            if (speechService != null) {
                speechService?.stop()
                speechService = null
                microphoneButton.setBackgroundColor(ContextCompat.getColor(this, android.R.color.transparent)) // Reset button color
                Log.d(TAG, "Model predictions stopped.")
            } else {
                Log.d(TAG, "No active model predictions to stop.")
            }
        }
    }

    // Function to update participant count
    private fun updateParticipantCount() {
        val count = connectedEndpoints.size
        runOnUiThread {
            val participantCountTextView = findViewById<TextView>(R.id.participantCountTextView)
            participantCountTextView.text = "Signer/s Participants: $count"
        }
    }

    private fun sendControlUpdate(selectedUser: String) {
        if (connectedEndpoints.isEmpty()) {
            Log.e(TAG, "Cannot send control update: No connected endpoints.")
            Toast.makeText(this, "No device connected to assign control.", Toast.LENGTH_SHORT).show()
            return
        }

        if (selectedUser == "None") {
            Toast.makeText(this, "Prediction disabled for all users.", Toast.LENGTH_SHORT).show()
            return
        }

        val currentDeviceUsername = intent.getStringExtra("USERNAME") ?: "Unknown"
        val message = "CONTROL:$selectedUser,USERNAME:$currentDeviceUsername"

        for ((endpointId, username) in connectedEndpoints) {
            if (selectedUser == "All" || username == selectedUser) {
                connectionsClient.sendPayload(endpointId, Payload.fromBytes(message.toByteArray()))
                    .addOnSuccessListener {
                        Log.d(TAG, "Control update sent to $endpointId for user: $selectedUser")
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Failed to send control update to $endpointId", e)
                    }
            }
        }
    }

    private fun startDiscovering() {
        val discoveryOptions = DiscoveryOptions.Builder().setStrategy(Strategy.P2P_CLUSTER).build()
        connectionsClient.startDiscovery(
            serviceId,
            endpointDiscoveryCallback,
            discoveryOptions
        ).addOnSuccessListener {
            Log.d(TAG, "Discovery started successfully.")
        }.addOnFailureListener { e ->
            Log.e(TAG, "Discovery failed: ${e.message}")
        }
    }

    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            Log.d(TAG, "Endpoint found: ${info.endpointName}")
            connectionsClient.requestConnection("User", endpointId, connectionLifecycleCallback)
        }

        override fun onEndpointLost(endpointId: String) {
            Log.d(TAG, "Endpoint lost: $endpointId")
        }
    }

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, connectionInfo: ConnectionInfo) {
            Log.d(TAG, "Connection initiated with ${connectionInfo.endpointName}")
            connectionsClient.acceptConnection(endpointId, payloadCallback)
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            if (result.status.isSuccess) {
                isConnected = true

                Log.d(TAG, "Successfully connected to $endpointId")

                // Send the current device's username and profile image to the connected endpoint
                val currentDeviceUsername = intent.getStringExtra("USERNAME") ?: "Unknown"
                val profileData = if (myProfileImageBase64 != null) {
                    "ROLE:$role,USERNAME:$currentDeviceUsername,PROFILE_IMAGE:$myProfileImageBase64"
                } else {
                    "ROLE:$role,USERNAME:$currentDeviceUsername"
                }
                
                val payload = Payload.fromBytes(profileData.toByteArray())
                connectionsClient.sendPayload(endpointId, payload)
                Log.d(TAG, "Sent username: $currentDeviceUsername and profile image.")

            } else {
                Log.e(TAG, "Connection failed to $endpointId")
            }
        }

        override fun onDisconnected(endpointId: String) {
            isConnected = false

            // Remove username from the spinner and map
            val username = connectedEndpoints.remove(endpointId)
            if (!username.isNullOrEmpty()) {
                removeUserFromSpinner(username)
            }
            updateParticipantCount()
            Log.d(TAG, "Disconnected from $endpointId. Username removed: $username")

            modelsLoaded = false
            handLandmarkerHelper?.unloadModels() // Unload models if disconnected
        }
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            if (payload.type == Payload.Type.BYTES) {
                val receivedData = payload.asBytes()?.let { String(it) }
                if (!receivedData.isNullOrEmpty()) {
                    Log.d(TAG, "Payload received from endpoint: $endpointId, Data: $receivedData")

                    // Check if the received data is a username or a message
                    when {
                        receivedData == "STOP_HAND_DETECTION" -> {
                            Log.d(TAG, "Received STOP_HAND_DETECTION. Stopping hand detection.")
                            stopHandDetection()
                        }
                        receivedData == "RESET" -> {
                            Log.d(TAG, "Reset message received. Clearing sentenceTextView.")
                            clearPrediction()
                        }
                        receivedData.startsWith("ROLE:") -> {
                            // Parse message that includes role, username and potentially profile image
                            val parts = receivedData.split(",")
                            val role = parts.find { it.startsWith("ROLE:") }?.removePrefix("ROLE:")
                            val username = parts.find { it.startsWith("USERNAME:") }?.removePrefix("USERNAME:")
                            val profileImage = parts.find { it.startsWith("PROFILE_IMAGE:") }?.removePrefix("PROFILE_IMAGE:")

                            // Exclude devices with the same role (Non Signers)
                            if (role == this@SignersToNonSignersActivity.role) {
                                Log.d(TAG, "Filtered out device with the same role: $role")
                                return
                            }

                            // Add the device to connectedEndpoints and the spinner
                            if (username != null && !username.isNullOrEmpty()) {
                                connectedEndpoints[endpointId] = username
                                addUserToSpinner(username, endpointId, profileImage)
                                updateParticipantCount()
                                Log.d(TAG, "Added device with role $role and username $username")
                            } else {
                                Log.e(TAG, "Invalid username extracted from payload: $receivedData")
                            }
                        }
                        receivedData.startsWith("ALERT:") -> {
                            // Handle alert messages
                            Log.d(TAG, "Received alert: $receivedData")
                            showAlertNotification(receivedData.removePrefix("ALERT:"))
                        }
                        receivedData.startsWith("CONTROL:") -> {
                            // Ignore control messages in the spinner
                            Log.d(TAG, "Received CONTROL message: $receivedData")
                        }
                        receivedData.startsWith("BROADCAST:") -> {
                            // Display broadcast messages from deaf users in the messages TextView
                            val parts = receivedData.split(":", limit = 3)
                            if (parts.size == 3) {
                                val senderUsername = parts[1]
                                val message = parts[2]
                                displayDeafUserMessage(senderUsername, message, "BROADCAST")
                            } else {
                                Log.e(TAG, "Invalid BROADCAST message format: $receivedData")
                            }
                        }
                        receivedData.startsWith("TARGET:") -> {
                            // Display targeted messages from deaf users in the messages TextView
                            val parts = receivedData.split(":", limit = 4)
                            if (parts.size >= 3) { // Changed to >= 3 to be more flexible
                                try {
                                    val targetUser = parts[1]
                                    val senderUsername = parts[2]
                                    val message = if (parts.size == 4) parts[3] else "" // Handle empty message
                                    
                                    // Only display if this user is the target or "All"
                                    val currentUsername = intent.getStringExtra("USERNAME") ?: "Unknown"
                                    if (targetUser == currentUsername || targetUser == "All") {
                                        displayDeafUserMessage(senderUsername, message, "DIRECT")
                                    }
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error parsing TARGET message: $receivedData", e)
                                }
                            } else {
                                Log.e(TAG, "Invalid TARGET message format: $receivedData")
                            }
                        }
                        receivedData.startsWith("COMPLETE_TRANSLATION:") -> {
                            // Handle complete translation messages
                            val parts = receivedData.split(":", limit = 3)
                            if (parts.size == 3) {
                                val senderUsername = parts[1]
                                val completeSentence = parts[2]
                                
                                // Create a message in the expected format but without the timestamp
                                // The timestamp will be added in addToMessageHistory
                                val displayMessage = "📝 $senderUsername completed translation: $completeSentence"
                                
                                // Add to message history - addToMessageHistory will add the timestamp
                                addToMessageHistory(displayMessage)
                                
                                // If TTS is enabled, also speak the completed translation
                                if (isTTSInitialized && isTTSEnabled) {
                                    val textToSpeak = "$senderUsername says, $completeSentence"
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                                        textToSpeech.speak(textToSpeak, TextToSpeech.QUEUE_FLUSH, null, "complete_${System.currentTimeMillis()}")
                                    } else {
                                        @Suppress("DEPRECATION")
                                        textToSpeech.speak(textToSpeak, TextToSpeech.QUEUE_FLUSH, null)
                                    }
                                }
                                
                                Log.d(TAG, "Displayed complete translation from $senderUsername: $completeSentence")
                            } else {
                                Log.e(TAG, "Invalid COMPLETE_TRANSLATION message format: $receivedData")
                            }
                        }
                        else -> {
                            // For direct messages from NonSignersToSigners that contain a colon
                            if (receivedData.contains(":")) {
                                try {
                                    val parts = receivedData.split(":", limit = 2)
                                    if (parts.size == 2) {
                                        val senderUsername = parts[0]
                                        val message = parts[1].trim()
                                        
                                        if (message.isNotEmpty() && !senderUsername.contains("ROLE") && 
                                            !senderUsername.contains("MESSAGE") && !senderUsername.contains("USERNAME")) {
                                            // Display simple formatted messages (these can come from NonSignersToSigners)
                                            displayDeafUserMessage(senderUsername, message, "SIMPLE")
                                            Log.d(TAG, "Displayed simple message: $senderUsername: $message")
                                            return
                                        }
                                    }
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error parsing simple message: $receivedData", e)
                                }
                            }
                            
                            // Treat as a general message
                            Log.d(TAG, "General message received: $receivedData")
                            handleGeneralMessage(endpointId, receivedData)
                        }
                    }
                } else {
                    Log.e(TAG, "Received empty payload from endpoint: $endpointId")
                }
            } else {
                Log.e(TAG, "Received unexpected payload type from endpoint: $endpointId")
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            if (update.status == PayloadTransferUpdate.Status.SUCCESS) {
                Log.d(TAG, "Payload transfer successful for endpoint: $endpointId")
            } else {
                Log.e(TAG, "Payload transfer failed for endpoint: $endpointId, Status: ${update.status}")
            }
        }

        // Helper function to handle general messages
        private fun handleGeneralMessage(endpointId: String, message: String) {
            // Avoid processing numeric-only or redundant messages
            if (!message.matches(Regex("\\d+"))) {
                if (endpointId.isNotBlank()) {
                    runOnUiThread {
                        // Ensure the message is added to both connectedEndpoints and the spinner
                        val normalizedMessage = message

                        // Check if the message starts with "MESSAGE:", skip adding to spinner if true
                        if (normalizedMessage.startsWith("MESSAGE:")) {
                            Log.d(TAG, "Processing MESSAGE: entry for spinner: $normalizedMessage")
                            return@runOnUiThread
                        }

                        // Check if the endpoint is already linked
                        if (!connectedEndpoints.containsKey(endpointId)) {
                            connectedEndpoints[endpointId] = normalizedMessage
                            Log.d(TAG, "Added endpoint mapping: $endpointId -> $normalizedMessage")
                        }

                        // Ensure the username is in the spinner
                        if (!connectedUsers.any { it.username == normalizedMessage }) {
                            connectedUsers.add(UserWithImage(normalizedMessage))
                            userSpinnerAdapter.notifyDataSetChanged()
                            Log.d(TAG, "Added general message to spinner: $message")
                        } else {
                            Log.d(TAG, "Message already exists in spinner: $message")
                        }
                    }
                } else {
                    Log.e(TAG, "General message received without endpoint ID: $message")
                }
            }

            // Further processing for specific commands or data
            // Create a Boolean to track if we're already processing a model change
            val isProcessingModelChange = Object()
            var isCurrentlyProcessing = false

            inferenceExecutor.submit {
                synchronized(isProcessingModelChange) {
                    // Check if we're already processing a model change
                    if (isCurrentlyProcessing) {
                        Log.d(TAG, "Skipping model change request, already processing another change")
                        runOnUiThread {
                            Toast.makeText(this@SignersToNonSignersActivity, 
                                "Please wait until current category change completes", 
                                Toast.LENGTH_SHORT).show()
                        }
                        return@submit
                    }
                    isCurrentlyProcessing = true
                }

                try {
                    synchronized(this) {
                        try {
                            // Unload any previously loaded models
                            try {
                                handLandmarkerHelper?.unloadModels()
                            } catch (e: Exception) {
                                Log.e(TAG, "Error unloading models: ${e.message}")
                                // Continue and try to create a new instance
                            }

                            // Initialize HandLandmarkerHelper if not already initialized
                            if (handLandmarkerHelper == null) {
                                try {
                                    handLandmarkerHelper = HandLandmarkerHelper(
                                        context = this@SignersToNonSignersActivity,
                                        runningMode = RunningMode.LIVE_STREAM,
                                        handLandmarkerHelperListener = this@SignersToNonSignersActivity
                                    )
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error creating HandLandmarkerHelper: ${e.message}")
                                    // Notify user about the error
                                    runOnUiThread {
                                        Toast.makeText(this@SignersToNonSignersActivity, 
                                            "Failed to initialize hand detection. Try again.", 
                                            Toast.LENGTH_SHORT).show()
                                    }
                                    return@synchronized
                                }
                            }

                            // Load the appropriate model based on the message
                            // Remove known prefixes like "MESSAGE:" for processing
                            val strippedMessage = message.removePrefix("MESSAGE:")

                            // Process the message based on its stripped content
                            try {
                                // Add small delay to ensure previous model is fully unloaded
                                Thread.sleep(200)
                                
                                when (strippedMessage) {
                                    "1" -> { handLandmarkerHelper?.loadModelsAndLabels("alphabets"); isDetectionActive = true }
                                    "2" -> { handLandmarkerHelper?.loadModelsAndLabels("1-5"); isDetectionActive = true }
                                    "3" -> { handLandmarkerHelper?.loadModelsAndLabels("6-10"); isDetectionActive = true }
                                    "4" -> { handLandmarkerHelper?.loadModelsAndLabels("20-100"); isDetectionActive = true }
                                    "5" -> { handLandmarkerHelper?.loadModelsAndLabels("greetings"); isDetectionActive = true }
                                    "6" -> { handLandmarkerHelper?.loadModelsAndLabels("responses"); isDetectionActive = true }
                                    "7" -> { handLandmarkerHelper?.loadModelsAndLabels("family"); isDetectionActive = true }
                                    "8" -> { handLandmarkerHelper?.loadModelsAndLabels("colors"); isDetectionActive = true }
                                    "9" -> { handLandmarkerHelper?.loadModelsAndLabels("pronouns"); isDetectionActive = true }
                                    "10" -> { handLandmarkerHelper?.loadModelsAndLabels("nouns"); isDetectionActive = true }
                                    "11" -> { handLandmarkerHelper?.loadModelsAndLabels("verbs"); isDetectionActive = true }
                                    "12" -> { handLandmarkerHelper?.loadModelsAndLabels("school"); isDetectionActive = true }
                                    "13" -> { handLandmarkerHelper?.loadModelsAndLabels("weeks"); isDetectionActive = true }
                                    "14" -> { handLandmarkerHelper?.loadModelsAndLabels("time"); isDetectionActive = true }
                                    "15" -> { handLandmarkerHelper?.loadModelsAndLabels("questions"); isDetectionActive = true }
                                    "16" -> { handLandmarkerHelper?.loadModelsAndLabels("phrases"); isDetectionActive = true }
                                    "17" -> { handLandmarkerHelper?.loadModelsAndLabels("calendar"); isDetectionActive = true }
                                    else -> Log.e(TAG, "Unknown model selection: $message")
                                }
                                modelsLoaded = true
                                // Notify user about successful model change
                                runOnUiThread {
                                    Toast.makeText(this@SignersToNonSignersActivity, 
                                        "Category changed successfully", 
                                        Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Error loading models for category $strippedMessage: ${e.message}", e)
                                modelsLoaded = false
                                // Attempt to recover by reinitializing the HandLandmarkerHelper
                                try {
                                    handLandmarkerHelper?.clearHandLandmarker()
                                    handLandmarkerHelper = null
                                    
                                    // Wait a moment before recreating
                                    Thread.sleep(300)
                                    
                                    handLandmarkerHelper = HandLandmarkerHelper(
                                        context = this@SignersToNonSignersActivity,
                                        runningMode = RunningMode.LIVE_STREAM,
                                        handLandmarkerHelperListener = this@SignersToNonSignersActivity
                                    )
                                    
                                    // Notify user about recovery attempt
                                    runOnUiThread {
                                        Toast.makeText(this@SignersToNonSignersActivity, 
                                            "Recovered from error. Please try changing category again.", 
                                            Toast.LENGTH_SHORT).show()
                                    }
                                } catch (e2: Exception) {
                                    Log.e(TAG, "Failed recovery after model loading error: ${e2.message}", e2)
                                    // Notify user about the error
                                    runOnUiThread {
                                        Toast.makeText(this@SignersToNonSignersActivity, 
                                            "Failed to change category. Please restart the app.", 
                                            Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error in model loading process: ${e.message}", e)
                            modelsLoaded = false
                            // Notify user about the error
                            runOnUiThread {
                                Toast.makeText(this@SignersToNonSignersActivity, 
                                    "Error in sign detection setup. Please restart the application.", 
                                    Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                } finally {
                    // Always reset processing flag when done
                    synchronized(isProcessingModelChange) {
                        isCurrentlyProcessing = false
                    }
                }
            }
        }
    }

    // Add this helper method to clear the prediction:
    private fun clearPrediction() {
        runOnUiThread {
            if (predictionHistory.isNotEmpty()) {
                // Remove the last prediction
                predictionHistory.removeAt(predictionHistory.size - 1)
            }

            // Update the sentence builder with the remaining predictions
            sentenceBuilder.clear()
            sentenceBuilder.append(predictionHistory.joinToString(" "))

            // Update UI with new prediction (or empty if all removed)
            val updatedText = if (sentenceBuilder.isNotEmpty()) {
                "Prediction: ${sentenceBuilder.toString().trim()}"
            } else {
                "Prediction: " // Ensure UI reflects an empty prediction
            }
            sentenceTextView.text = updatedText

            // Notify the user
            Toast.makeText(this, "Last predicted phrase removed.", Toast.LENGTH_SHORT).show()

            // Send the updated prediction (or empty) to all connected endpoints
            // This ensures all devices are synchronized with the latest prediction state
            broadcastUpdatedPrediction()
        }
    }
    
    private fun broadcastUpdatedPrediction() {
        if (connectedEndpoints.isEmpty()) {
            Log.e(TAG, "Cannot broadcast updated prediction: No connected endpoints.")
            return
        }
        
        val currentDeviceUsername = intent.getStringExtra("USERNAME") ?: "Unknown"
        
        // Get only the latest prediction or an empty string if there are none
        val latestPrediction = if (predictionHistory.isNotEmpty()) {
            predictionHistory.last()
        } else {
            ""
        }
        
        // Create broadcast message with just the latest prediction
        val payloadMessage = "BROADCAST_PREDICTION:$currentDeviceUsername:$latestPrediction"
        
        Log.d(TAG, "Broadcasting updated prediction: $payloadMessage")
        
        // Send to all connected endpoints to ensure everyone is in sync
        for ((endpointId, username) in connectedEndpoints) {
            connectionsClient.sendPayload(endpointId, Payload.fromBytes(payloadMessage.toByteArray()))
                .addOnSuccessListener {
                    Log.d(TAG, "Updated prediction (latest only) broadcasted to $username: '$latestPrediction'")
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed to broadcast updated prediction (latest only) to $username: '$latestPrediction'", e)
                }
        }
    }

    private fun stopHandDetection() {
        runOnUiThread {
            isDetectionActive = false
            if (handLandmarkerHelper != null) {
                // Get the current sentence prediction text before stopping
                val currentSentence = sentenceBuilder.toString().trim()
                
                // Broadcast the full sentence as a final translation if there is content
                if (currentSentence.isNotEmpty()) {
                    val currentDeviceUsername = intent.getStringExtra("USERNAME") ?: "Unknown"
                    // Format a message with the complete sentence for the chat history
                    val completeSentenceMessage = "COMPLETE_TRANSLATION:$currentDeviceUsername:$currentSentence"
                    
                    // Send to all connected endpoints
                    for ((endpointId, username) in connectedEndpoints) {
                        connectionsClient.sendPayload(endpointId, Payload.fromBytes(completeSentenceMessage.toByteArray()))
                            .addOnSuccessListener {
                                Log.d(TAG, "Complete sentence broadcasted to $username: '$currentSentence'")
                            }
                            .addOnFailureListener { e ->
                                Log.e(TAG, "Failed to broadcast complete sentence to $username: '$currentSentence'", e)
                            }
                    }
                    
                    // Add to local chat history too
                    val timestamp = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault()).format(java.util.Date())
                    val displayMessage = "[$timestamp] 📝 You completed translation: $currentSentence"
                    addToMessageHistory(displayMessage)
                }
                
                // Now stop hand detection and unload models
                handLandmarkerHelper?.unloadModels()
                handLandmarkerHelper = null
                modelsLoaded = false
                
                // Clear the prediction display
                sentenceBuilder.clear()
                predictionHistory.clear()
                sentenceTextView.text = "Prediction: "

                Toast.makeText(this, "Hand detection stopped.", Toast.LENGTH_SHORT).show()
                Log.d(TAG, "Hand detection models unloaded successfully.")
            }
            // Always clear the overlay, even if helper is null
            overlayView.clear()
        }
    }

    private fun addUserToSpinner(username: String, endpointId: String, profileImageBase64: String? = null) {
        runOnUiThread {
            // Retrieve the current device's username
            val currentDeviceUsername = intent.getStringExtra("USERNAME") ?: "Unknown"

            // Filter out invalid usernames and role-prefixed usernames
            if (username.startsWith("TARGET:") ||
                username.startsWith("CONTROL:") ||
                username.startsWith("MESSAGE:") ||
                username.startsWith("BROADCAST:") ||
                username.startsWith("ROLE:") ||
                username == "Unknown" ||
                username == currentDeviceUsername
            ) {
                Log.d(TAG, "Filtered out invalid username: $username")
                return@runOnUiThread
            }

            // Check if user already exists in the list
            val existingUserIndex = connectedUsers.indexOfFirst { it.username == username && it.username != "None" && it.username != "All" }
            
            if (existingUserIndex != -1) {
                // Update existing user's profile image if needed
                if (profileImageBase64 != null) {
                    connectedUsers[existingUserIndex] = connectedUsers[existingUserIndex].copy(
                        profileImageBase64 = profileImageBase64
                    )
                }
            } else {
                // Add new user to the list
                connectedUsers.add(UserWithImage(
                    username = username,
                    profileImageBase64 = profileImageBase64,
                    endpointId = endpointId,
                    role = "Signers"
                ))
            }
            
            // Notify adapter of changes
            userSpinnerAdapter.notifyDataSetChanged()
            Log.d(TAG, "Added or updated username in spinner: $username")
        }
    }

    private fun removeUserFromSpinner(username: String) {
        runOnUiThread {
            connectedUsers.removeIf { it.username == username && it.username != "None" && it.username != "All" }
            userSpinnerAdapter.notifyDataSetChanged()
            Log.d(TAG, "Removed username from spinner: $username")
        }
    }

    // Function to display alert notification
    private fun showAlertNotification(message: String) {
        runOnUiThread {
            val alertContent = message.removePrefix("ALERT:")
            Toast.makeText(this, alertContent, Toast.LENGTH_LONG).show()

            // Optionally vibrate or play a sound to grab attention
            val vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                vibrator.vibrate(500)
            }
        }
    }

    private fun sendMessageToUser(message: String, selectedUser: String) {
        if (connectedEndpoints.isEmpty()) {
            Log.e(TAG, "Cannot send message: No connected endpoints.")
            Toast.makeText(this, "No device connected to send the message.", Toast.LENGTH_SHORT).show()
            return
        }

        if (selectedUser == "None") {
            Toast.makeText(this, "No user selected.", Toast.LENGTH_SHORT).show()
            return
        }

        val currentDeviceUsername = intent.getStringExtra("USERNAME") ?: "Unknown"
        val payloadMessage = if (selectedUser == "All") {
            "BROADCAST:$currentDeviceUsername:$message"
        } else {
            "TARGET:$selectedUser:$currentDeviceUsername:$message"
        }

        var messageSent = false

        // Also display the outgoing message in our own chat history
        val timestamp = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault()).format(java.util.Date())
        val displayMessage = if (selectedUser == "All") {
            "[$timestamp] 📤 $currentDeviceUsername → All: $message"
        } else {
            "[$timestamp] 📤 $currentDeviceUsername → $selectedUser: $message"
        }
        
        // Add to message history
        addToMessageHistory(displayMessage)

        if (selectedUser == "All") {
            for (endpointId in connectedEndpoints.keys) {
                connectionsClient.sendPayload(endpointId, Payload.fromBytes(payloadMessage.toByteArray()))
                    .addOnSuccessListener {
                        Log.d(TAG, "Message broadcasted to endpoint: $endpointId, message: $message")
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Failed to broadcast message to endpoint: $endpointId, error: ${e.message}")
                    }
            }
            messageSent = !connectedEndpoints.isEmpty()
        } else {
            val targetEndpointId = connectedEndpoints.filterValues { it == selectedUser }.keys.firstOrNull()
            if (targetEndpointId != null) {
                connectionsClient.sendPayload(targetEndpointId, Payload.fromBytes(payloadMessage.toByteArray()))
                    .addOnSuccessListener {
                        Log.d(TAG, "Message sent to $selectedUser: $message")
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Failed to send message to $selectedUser: $message", e)
                    }
                messageSent = true
            }
        }

        if (!messageSent) {
            Log.e(TAG, "No target found for selected user: $selectedUser")
            Toast.makeText(this, "User not connected: $selectedUser", Toast.LENGTH_SHORT).show()
        }
    }

    // Helper method to add messages to history and update UI
    private fun addToMessageHistory(message: String) {
        runOnUiThread {
            val currentUsername = intent.getStringExtra("USERNAME") ?: "Unknown"
            val timestamp = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault()).format(java.util.Date())
            
            // Format the message based on who sent it and message type
            val formattedMessage = when {
                // Messages sent by the current device user (outgoing messages)
                message.contains("$currentUsername → All:") -> {
                    val content = message.substringAfter("$currentUsername → All:")
                    "[$timestamp] <div style='text-align: right; color: #4B1F4E;'><b>You → All:</b>$content</div>"
                }
                message.contains("$currentUsername → ") -> {
                    val parts = message.substringAfter("$currentUsername → ").split(":", limit = 2)
                    val recipient = parts[0]
                    val content = if (parts.size > 1) parts[1] else ""
                    "[$timestamp] <div style='text-align: right; color: #4B1F4E;'><b>You → $recipient:</b>$content</div>"
                }
                // Completed translation messages
                message.contains("📝") && message.contains("completed translation:") -> {
                    // Preserve existing timestamp in the message to avoid duplicates
                    if (message.startsWith("[") && message.contains("]")) {
                        // Message already has a timestamp, use it as is
                        message
                    } else {
                        // Add timestamp if it doesn't have one
                        "[$timestamp] $message"
                    }
                }
                // Incoming messages from deaf users
                message.contains("📢") && message.contains(":") -> {
                    val sender = message.substringAfter("[").substringAfter("]").trim().substringAfter("📢").trim().substringBefore(":")
                    val content = message.substringAfter("$sender:")
                    "[$timestamp] <div style='text-align: left;'><b>$sender:</b>$content 📢</div>"
                }
                message.contains("💬") && message.contains(":") -> {
                    val sender = message.substringAfter("[").substringAfter("]").trim().substringAfter("💬").trim().substringBefore(":")
                    val content = message.substringAfter("$sender:")
                    "[$timestamp] <div style='text-align: left;'><b>$sender:</b>$content 💬</div>"
                }
                message.contains("📩") && message.contains(":") -> {
                    val sender = message.substringAfter("[").substringAfter("]").trim().substringAfter("📩").trim().substringBefore(":")
                    val content = message.substringAfter("$sender:")
                    "[$timestamp] <div style='text-align: left;'><b>$sender:</b>$content 📩</div>"
                }
                // System messages or other unrecognized formats - center alignment
                else -> {
                    "[$timestamp] <div style='text-align: center; color: #888888;'>$message</div>"
                }
            }
            
            // Append the new message with a line break
            if (messagesBuilder.isNotEmpty()) {
                messagesBuilder.append("\n")
            }
            messagesBuilder.append(formattedMessage)
            
            // Update the TextView with HTML formatting
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                messagesTextView.text = android.text.Html.fromHtml(messagesBuilder.toString(), android.text.Html.FROM_HTML_MODE_COMPACT)
            } else {
                @Suppress("DEPRECATION")
                messagesTextView.text = android.text.Html.fromHtml(messagesBuilder.toString())
            }
            
            // Enhanced auto-scrolling to the bottom
            val scrollView = findViewById<ScrollView>(R.id.messagesScrollView)
            
            // Clear any pending posts to ensure we don't have multiple scroll operations queued
            scrollView.removeCallbacks(null)
            
            // Immediate scroll attempt
            scrollView.post {
                // Force layout to calculate correct scroll height
                scrollView.fullScroll(ScrollView.FOCUS_DOWN)
                
                // Secondary scroll with slight delay to ensure layout is complete
                scrollView.postDelayed({
                    // Forcibly update scroll position to bottom
                    scrollView.smoothScrollTo(0, scrollView.getChildAt(0).height)
                    
                    // Final backup scroll attempt
                    scrollView.postDelayed({
                        scrollView.fullScroll(ScrollView.FOCUS_DOWN)
                        // Force invalidate to ensure UI is updated
                        scrollView.invalidate()
                    }, 150)
                }, 50)
            }
        }
    }

    private fun logEndpointState() {
        Log.d(TAG, "Connected endpoints: $connectedEndpoints")
        Log.d(TAG, "Spinner usernames: $connectedUsers")
    }

    private fun startCamera() {
        isDetectionActive = true
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(viewBinding.previewView.surfaceProvider)
            }

            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST) // Only process latest frame
                .build().also {
                it.setAnalyzer(cameraExecutor) { imageProxy ->
                    try {
                        // Only detect when models are loaded and connected
                        if (modelsLoaded && handLandmarkerHelper != null && isConnected) {
                            try {
                                handLandmarkerHelper?.detectLiveStream(imageProxy, isFrontCamera = false)
                            } catch (e: IllegalStateException) {
                                // This can happen when switching categories while collecting keypoints
                                Log.w(TAG, "HandLandmarker was in a bad state during detection: ${e.message}")
                                // Clear any existing model state
                                synchronized(this@SignersToNonSignersActivity) {
                                    try {
                                        handLandmarkerHelper?.clearHandLandmarker()
                                        
                                        // Force a delay to allow the system to stabilize before recreating
                                        Handler(Looper.getMainLooper()).postDelayed({
                                            if (!isFinishing) {
                                                // Try to recreate the helper with the current model
                                                try {
                                                    handLandmarkerHelper = HandLandmarkerHelper(
                                                        context = this@SignersToNonSignersActivity,
                                                        runningMode = RunningMode.LIVE_STREAM,
                                                        handLandmarkerHelperListener = this@SignersToNonSignersActivity
                                                    )
                                                    Log.d(TAG, "Successfully recreated HandLandmarkerHelper after error")
                                                } catch (e: Exception) {
                                                    Log.e(TAG, "Failed to recreate HandLandmarkerHelper: ${e.message}")
                                                    // If recreation fails multiple times, show a message to restart app
                                                    Handler(Looper.getMainLooper()).postDelayed({
                                                        if (!isFinishing) {
                                                            try {
                                                                handLandmarkerHelper = HandLandmarkerHelper(
                                                                    context = this@SignersToNonSignersActivity,
                                                                    runningMode = RunningMode.LIVE_STREAM,
                                                                    handLandmarkerHelperListener = this@SignersToNonSignersActivity
                                                                )
                                                                Log.d(TAG, "Second attempt to recreate HandLandmarkerHelper successful")
                                                            } catch (e: Exception) {
                                                                Log.e(TAG, "Second attempt to recreate HandLandmarkerHelper failed: ${e.message}")
                                                                Toast.makeText(this@SignersToNonSignersActivity, 
                                                                    "Hand detection issue detected. Please restart the app if problems persist.", 
                                                                    Toast.LENGTH_LONG).show()
                                                            }
                                                        }
                                                    }, 1000) // Try again after 1 second
                                                }
                                            }
                                        }, 500) // Half second delay
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Error during HandLandmarker recovery: ${e.message}", e)
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "General error during hand detection: ${e.message}", e)
                                // Do not recreate helper here, just log the error
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error analyzing image stream: ${e.message}", e)
                    } finally {
                        imageProxy.close()
                    }
                }
            }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalyzer)
            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun startMicrophoneRecognition() {
        if (speechService != null) {
            speechService?.stop()
            speechService = null
            microphoneButton.setBackgroundColor(ContextCompat.getColor(this, android.R.color.transparent)) // Reset button color
        } else {
            try {
                if (voskModel == null) {
                    voskModel = Model(filesDir.absolutePath + "/model-en-us") // Load Vosk model
                }
                val recognizer = Recognizer(voskModel, 16000.0f)
                speechService = SpeechService(recognizer, 16000.0f)
                speechService?.startListening(object : RecognitionListener {
                    override fun onResult(hypothesis: String?) {
                        hypothesis?.let {
                            val text = extractTextFromHypothesis(it)
                            appendTextToMessageInput(text)
                        }
                    }

                    override fun onPartialResult(hypothesis: String?) {
                        hypothesis?.let {
                            val text = extractTextFromHypothesis(it)
                            appendTextToMessageInput(text)
                        }
                    }

                    override fun onFinalResult(hypothesis: String?) {
                        hypothesis?.let {
                            val text = extractTextFromHypothesis(it)
                            appendTextToMessageInput(text)
                        }
                        stopMicrophoneRecognition()
                    }

                    override fun onError(e: Exception?) {
                        Toast.makeText(this@SignersToNonSignersActivity, "Error: ${e?.message}", Toast.LENGTH_SHORT).show()
                        stopMicrophoneRecognition()
                    }

                    override fun onTimeout() {
                        stopMicrophoneRecognition()
                    }
                })
            } catch (e: IOException) {
                Toast.makeText(this, "Failed to start microphone recognition: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun appendTextToMessageInput(text: String) {
        val currentText = messageInput.text.toString()
        if (text.isNotBlank()) {
            messageInput.setText("$currentText $text".trim()) // Append new text
            messageInput.setSelection(messageInput.text.length) // Move cursor to the end
        }
    }

    private fun extractTextFromHypothesis(hypothesis: String): String {
        return try {
            val jsonObject = JSONObject(hypothesis)
            jsonObject.getString("text") // Extract the value of the "text" key
        } catch (e: Exception) {
            Log.e("SpeechRecognition", "Failed to parse hypothesis: $hypothesis", e)
            ""
        }
    }

    private fun stopMicrophoneRecognition() {
        speechService?.stop()
        speechService = null
    }

    private fun copyAssetsToInternalStorage(assetFolder: String, destinationFolder: String) {
        val assetManager = assets
        val destinationDir = File(filesDir, destinationFolder)
        if (!destinationDir.exists()) {
            destinationDir.mkdirs()
        }

        try {
            val files = assetManager.list(assetFolder) ?: return
            for (file in files) {
                val assetPath = "$assetFolder/$file"
                val destinationPath = File(destinationDir, file).absolutePath

                if (assetManager.list(assetPath)?.isNotEmpty() == true) {
                    // Recursively copy subfolders
                    copyAssetsToInternalStorage(assetPath, "$destinationFolder/$file")
                } else {
                    // Copy individual file
                    val inputStream = assetManager.open(assetPath)
                    val outputFile = File(destinationPath)
                    val outputStream = outputFile.outputStream()
                    inputStream.copyTo(outputStream)
                    inputStream.close()
                    outputStream.close()
                    Log.d("Vosk", "Copied file: $file to $destinationPath")
                }
            }
        } catch (e: IOException) {
            Log.e("Vosk", "Error copying assets: ${e.message}", e)
        }
    }

    private fun requestPermissions() {
        activityResultLauncher.launch(REQUIRED_PERMISSIONS)
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 1)
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startMicrophoneRecognition()
        } else {
            Toast.makeText(this, "Permission denied to use the microphone.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        
        // Clean up the reset runnable if it exists
        resetRunnable?.let { handler.removeCallbacks(it) }
        
        // First, ensure we stop camera-related activities
        try {
            val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
            cameraProviderFuture.get()?.unbindAll()
        } catch (e: Exception) {
            Log.e(TAG, "Error unbinding camera: ${e.message}", e)
        }
        
        // Clear model resources
        try {
            synchronized(this) {
                handLandmarkerHelper?.clearHandLandmarker()
                handLandmarkerHelper?.unloadModels()
                handLandmarkerHelper = null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing hand landmarker: ${e.message}", e)
        }
        
        // Shut down executors
        try {
            cameraExecutor.shutdown()
            try {
                if (!cameraExecutor.awaitTermination(500, java.util.concurrent.TimeUnit.MILLISECONDS)) {
                    cameraExecutor.shutdownNow()
                }
            } catch (e: InterruptedException) {
                cameraExecutor.shutdownNow()
            }
            
            inferenceExecutor.shutdown()
            try {
                if (!inferenceExecutor.awaitTermination(500, java.util.concurrent.TimeUnit.MILLISECONDS)) {
                    inferenceExecutor.shutdownNow()
                }
            } catch (e: InterruptedException) {
                inferenceExecutor.shutdownNow()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error shutting down executors: ${e.message}", e)
        }

        // Clean up speech and TTS resources
        try {
            if (speechService != null) {
                speechService?.stop()
                speechService = null
            }
            
            if (::textToSpeech.isInitialized) {
                textToSpeech.stop()
                textToSpeech.shutdown()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up speech resources: ${e.message}", e)
        }
        
        // Disconnect from Nearby Connections
        try {
            stopDiscovering()
            disconnectFromEndpoint()
        } catch (e: Exception) {
            Log.e(TAG, "Error disconnecting from endpoints: ${e.message}", e)
        }
    }

    // Implement HandLandmarkerHelper.LandmarkerListener
    override fun onResults(resultBundle: HandLandmarkerHelper.ResultBundle) {
        runOnUiThread {
            if (!isDetectionActive) {
                overlayView.clear()
                return@runOnUiThread
            }
            val handLandmarkerResult = resultBundle.results
            if (handLandmarkerResult.landmarks().isNotEmpty()) {
                overlayView.setResults(handLandmarkerResult)
                overlayView.visibility = View.VISIBLE
            } else {
                overlayView.clear()
                overlayView.visibility = View.GONE
            }
        }
    }

    override fun onError(error: String, errorCode: Int) {
        Log.e(TAG, "Error: $error")
        runOnUiThread {
            if (isFinishing) return@runOnUiThread  // Prevent crashes if the activity is closing
            Toast.makeText(this, "Error: $error", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onPrediction(prediction: String) {
        runOnUiThread {
            if (isFinishing) return@runOnUiThread

            if (prediction.isNotBlank()) {
                predictionHistory.add(prediction) // Add the latest prediction to history
                sentenceBuilder.append(" $prediction ") // Append it to the sentence
                sentenceTextView.text = "Prediction: ${sentenceBuilder.toString().trim()}"

                // Speak only the latest prediction, not the full sentence
                speakText(prediction)

                resetTimer()

                // Send the updated sentence prediction to connected devices
                // Get the selected item position
                val selectedPosition = viewBinding.userSpinner.selectedItemPosition
                
                // Check if the position is valid
                if (selectedPosition >= 0 && selectedPosition < connectedUsers.size) {
                    val selectedUserObj = connectedUsers[selectedPosition]
                    val selectedUser = selectedUserObj.username
                    sendPredictionToUser(selectedUser)
                } else {
                    Log.e(TAG, "Invalid spinner position for prediction sending")
                    Toast.makeText(this, "Could not determine selected user for prediction", Toast.LENGTH_SHORT).show()
                }
            } else {
                sentenceTextView.text = "Prediction Unavailable"
            }
        }
    }

    private fun speakText(text: String) {
        if (isTTSInitialized) {
            textToSpeech.stop() // Stop any ongoing speech
            textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
        }
    }

    private fun sendPredictionToUser(selectedUser: String, forceSendEmpty: Boolean = false) {
        if (connectedEndpoints.isEmpty()) {
            Log.e(TAG, "Cannot send prediction: No connected endpoints.")
            Toast.makeText(this, "No device connected to send the prediction.", Toast.LENGTH_SHORT).show()
            return
        }

        if (selectedUser == "None") {
            Toast.makeText(this, "No user selected.", Toast.LENGTH_SHORT).show()
            return
        }

        val currentDeviceUsername = intent.getStringExtra("USERNAME") ?: "Unknown"

        // Get only the latest prediction, not the full sentence
        val latestPrediction = if (predictionHistory.isNotEmpty()) {
            predictionHistory.last()
        } else {
            ""
        }
        
        // Ensure empty predictions can be sent if forced
        if (latestPrediction.isBlank() && !forceSendEmpty) {
            Toast.makeText(this, "No prediction to send.", Toast.LENGTH_SHORT).show()
            return
        }

        // If forced, explicitly send an empty string
        val finalPrediction = if (forceSendEmpty) "" else latestPrediction

        val payloadMessage = if (selectedUser == "All") {
            "BROADCAST_PREDICTION:$currentDeviceUsername:$finalPrediction"
        } else {
            "PREDICTION:$currentDeviceUsername:$finalPrediction"
        }

        Log.d(TAG, "Sending prediction message: $payloadMessage")
        var predictionSent = false

        if (selectedUser == "All") {
            for ((endpointId, username) in connectedEndpoints) {
                connectionsClient.sendPayload(endpointId, Payload.fromBytes(payloadMessage.toByteArray()))
                    .addOnSuccessListener {
                        Log.d(TAG, "Latest prediction broadcasted to $username: '$finalPrediction'")
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Failed to broadcast latest prediction to $username: '$finalPrediction'", e)
                    }
            }
            predictionSent = !connectedEndpoints.isEmpty()
        } else {
            // Find the endpoint ID that corresponds to the selected username
            val targetEndpointIds = connectedEndpoints.entries
                .filter { it.value == selectedUser }
                .map { it.key }
                
            if (targetEndpointIds.isNotEmpty()) {
                for (endpointId in targetEndpointIds) {
                    connectionsClient.sendPayload(endpointId, Payload.fromBytes(payloadMessage.toByteArray()))
                        .addOnSuccessListener {
                            Log.d(TAG, "Latest prediction sent to $selectedUser (endpoint: $endpointId): '$finalPrediction'")
                        }
                        .addOnFailureListener { e ->
                            Log.e(TAG, "Failed to send latest prediction to $selectedUser (endpoint: $endpointId): '$finalPrediction'", e)
                        }
                }
                predictionSent = true
            } else {
                Log.e(TAG, "No endpoint found for selected user: $selectedUser")
            }
        }

        if (!predictionSent) {
            Log.e(TAG, "No target found for selected user: $selectedUser")
            Toast.makeText(this, "User not connected: $selectedUser", Toast.LENGTH_SHORT).show()
        }
    }

    private fun resetTimer() {
        // Cancel any existing reset action
        resetRunnable?.let { handler.removeCallbacks(it) }

        // Schedule a new reset action after 10 seconds
        resetRunnable = Runnable {
            sentenceBuilder.clear()
            predictionHistory.clear() // Clear the prediction history
            sentenceTextView.text = "Prediction: "
            Toast.makeText(this, "Prediction reset.", Toast.LENGTH_SHORT).show()
        }
        handler.postDelayed(resetRunnable!!, 10_000) // 10 seconds
    }

    private fun stopDiscovering() {
        connectionsClient.stopDiscovery()
        Log.d(TAG, "Stopped discovering.")
    }

    private fun disconnectFromEndpoint() {
        if (connectedEndpoints.isNotEmpty()) {
            // Disconnect from all endpoints
            for (endpointId in connectedEndpoints.keys.toList()) {
                connectionsClient.disconnectFromEndpoint(endpointId)
                Log.d(TAG, "Disconnected from endpoint: $endpointId")
            }

            // Clear the map and update the UI
            connectedEndpoints.clear()
            connectedUsers.clear()
            connectedUsers.add(UserWithImage("None"))
            connectedUsers.add(UserWithImage("All"))
            userSpinnerAdapter.notifyDataSetChanged()
            updateParticipantCount()
        } else {
            Log.d(TAG, "No active connections to disconnect.")
        }

        isConnected = false
    }

    override fun onBackPressed() {
        // Stop advertising, discovering, and disconnect from endpoint
        stopDiscovering()
        disconnectFromEndpoint()

        // Allow the default back action (finish activity)
        super.onBackPressed()
    }

    // Method to display messages from deaf users
    private fun displayDeafUserMessage(senderUsername: String, message: String, messageType: String) {
        val timestamp = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault()).format(java.util.Date())
        val formattedMessage = when (messageType) {
            "BROADCAST" -> "[$timestamp] 📢 $senderUsername: $message"
            "DIRECT" -> "[$timestamp] 💬 $senderUsername: $message"
            "SIMPLE" -> "[$timestamp] 📩 $senderUsername: $message"
            else -> "[$timestamp] $senderUsername: $message"
        }
        
        // Use the common helper method to add to history and update UI
        addToMessageHistory(formattedMessage)
        
        // Use text-to-speech to speak the message aloud if enabled by user
        if (isTTSInitialized && isTTSEnabled && message.isNotEmpty()) {
            val textToSpeak = "$senderUsername says, $message"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                textToSpeech.speak(textToSpeak, TextToSpeech.QUEUE_FLUSH, null, "msg_${System.currentTimeMillis()}")
            } else {
                @Suppress("DEPRECATION")
                textToSpeech.speak(textToSpeak, TextToSpeech.QUEUE_FLUSH, null)
            }
        }
        
        // Vibrate to notify about the new message
        val vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(100)
        }
    }

    // Load profile image from SharedPreferences
    private fun loadProfileImage() {
        val sharedPreferences = getSharedPreferences("UserProfile", Context.MODE_PRIVATE)
        val profileImageUri = sharedPreferences.getString("profileImage", null)
        
        if (profileImageUri != null) {
            try {
                // Convert URI to Bitmap
                val imageUri = Uri.parse(profileImageUri)
                val inputStream = contentResolver.openInputStream(imageUri)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()
                
                // Convert bitmap to base64 for transmission
                myProfileImageBase64 = UserWithImage.bitmapToBase64(bitmap)
                Log.d(TAG, "Profile image loaded and converted to base64")
            } catch (e: Exception) {
                Log.e(TAG, "Error loading profile image: ${e.message}")
                myProfileImageBase64 = null
            }
        }
    }

    companion object {
        private const val TAG = "SignersToNonSignersActivity"
        const val SERVICE_ID = "com.example.komunikaprototype.SERVICE_ID"
        private val REQUIRED_PERMISSIONS = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_WIFI_STATE,
                Manifest.permission.CHANGE_WIFI_STATE,
                Manifest.permission.NEARBY_WIFI_DEVICES,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO
            )
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_WIFI_STATE,
                Manifest.permission.CHANGE_WIFI_STATE,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO
            )
            else -> arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_WIFI_STATE,
                Manifest.permission.CHANGE_WIFI_STATE,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO
            )
        }
    }
}
