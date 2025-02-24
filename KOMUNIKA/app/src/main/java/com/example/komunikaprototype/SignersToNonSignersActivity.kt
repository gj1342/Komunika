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

    private lateinit var connectedUsernames: MutableList<String>
    private val connectedEndpoints = mutableMapOf<String, String>() // Map of endpointId to username
    private lateinit var adapter: ArrayAdapter<String>

    private lateinit var participantCountTextView: TextView

    private val role = "Non Signers" // Role designation

    private lateinit var microphoneButton: ImageButton
    private var speechService: SpeechService? = null
    private var voskModel: Model? = null

    private lateinit var overlayView: OverlayView

    private lateinit var textToSpeech: TextToSpeech  // TTS Object
    private var isTTSInitialized = false

    private val predictionHistory = mutableListOf<String>() // To store individual predictions

    private val activityResultLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        if (permissions.values.all { it }) {
            startCamera()
            startDiscovering()
        } else {
            Toast.makeText(this, "Permission request denied", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewBinding = SignersToNonsignersBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)

        overlayView = findViewById(R.id.overlayView)

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

        // Initialize participant count TextView
        participantCountTextView = findViewById(R.id.participantCountTextView)

        // Initialize the connectedUsernames list and adapter first
        connectedUsernames = mutableListOf("None", "All")
        adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, connectedUsernames)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        viewBinding.userSpinner.adapter = adapter

        // Update participant count dynamically
        updateParticipantCount()

        // Add listener to update participant count on change
        adapter.registerDataSetObserver(object : DataSetObserver() {
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
            val selectedUser = viewBinding.userSpinner.selectedItem.toString()
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
        }

        // Add listener to the spinner (optional functionality for tracking selection)
        // Add listener to the spinner (to handle "None" selection and stop predictions)
        viewBinding.userSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                val selectedUser = connectedUsernames[position]
                Log.d(TAG, "User selected: $selectedUser")

                if (selectedUser == "None") {
                    // Stop hand detection and model predictions
                    stopHandDetection()
                    stopModelPrediction()
                    Toast.makeText(this@SignersToNonSignersActivity, "Hand detection and predictions stopped.", Toast.LENGTH_SHORT).show()
                } else {
                    // Handle control update
                    sendControlUpdate(selectedUser)
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>) {
                // Do nothing if no selection
            }
        }

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

                // Send the current device's username to the connected endpoint
                val currentDeviceUsername = intent.getStringExtra("USERNAME") ?: "Unknown"
                val payload = Payload.fromBytes("ROLE:$role,USERNAME:$currentDeviceUsername".toByteArray())
                connectionsClient.sendPayload(endpointId, payload)

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
                            // Extract role and username from the payload
                            val role = receivedData.substringAfter("ROLE:", "").substringBefore(",")
                            val username = receivedData.substringAfter("USERNAME:", "")

                            // Exclude devices with the same role (Non Signers)
                            if (role == this@SignersToNonSignersActivity.role) {
                                Log.d(TAG, "Filtered out device with the same role: $role")
                                return
                            }

                            // Add the device to connectedEndpoints and the spinner
                            if (username.isNotEmpty()) {
                                connectedEndpoints[endpointId] = username
                                addUserToSpinner(username)
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
                            // Ignore broadcast messages for other SignersToNonSignersActivity devices
                            val broadcastMessage = receivedData.removePrefix("BROADCAST:")
                            Log.d(TAG, "Received broadcast message: $broadcastMessage")
                        }
                        else -> {
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
                        if (!connectedUsernames.contains(normalizedMessage)) {
                            connectedUsernames.add(normalizedMessage)
                            adapter.notifyDataSetChanged()
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
            inferenceExecutor.submit {
                synchronized(this) {
                    try {
                        // Unload any previously loaded models
                        handLandmarkerHelper?.unloadModels()

                        // Initialize HandLandmarkerHelper if not already initialized
                        if (handLandmarkerHelper == null) {
                            handLandmarkerHelper = HandLandmarkerHelper(
                                context = this@SignersToNonSignersActivity,
                                runningMode = RunningMode.LIVE_STREAM,
                                handLandmarkerHelperListener = this@SignersToNonSignersActivity
                            )
                        }

                        // Load the appropriate model based on the message
                        // Remove known prefixes like "MESSAGE:" for processing
                        val strippedMessage = message.removePrefix("MESSAGE:")

                        // Process the message based on its stripped content
                        when (strippedMessage) {
                            "1" -> handLandmarkerHelper?.loadModelsAndLabels("A-D, F-M, U")
                            "2" -> handLandmarkerHelper?.loadModelsAndLabels("E, N-T, V-Z")
                            "3" -> handLandmarkerHelper?.loadModelsAndLabels("1-5")
                            "18" -> handLandmarkerHelper?.loadModelsAndLabels("6-10")
                            "19" -> handLandmarkerHelper?.loadModelsAndLabels("20-100")
                            "4" -> handLandmarkerHelper?.loadModelsAndLabels("greetings")
                            "5" -> handLandmarkerHelper?.loadModelsAndLabels("responses")
                            "6" -> handLandmarkerHelper?.loadModelsAndLabels("family")
                            "7" -> handLandmarkerHelper?.loadModelsAndLabels("colors")
                            "8" -> handLandmarkerHelper?.loadModelsAndLabels("pronouns")
                            "9" -> handLandmarkerHelper?.loadModelsAndLabels("nouns")
                            "10" -> handLandmarkerHelper?.loadModelsAndLabels("verbs")
                            "11" -> handLandmarkerHelper?.loadModelsAndLabels("school")
                            "12" -> handLandmarkerHelper?.loadModelsAndLabels("JanToJune")
                            "13" -> handLandmarkerHelper?.loadModelsAndLabels("JulyToDec")
                            "14" -> handLandmarkerHelper?.loadModelsAndLabels("weeks")
                            "15" -> handLandmarkerHelper?.loadModelsAndLabels("time")
                            "16" -> handLandmarkerHelper?.loadModelsAndLabels("questions")
                            "17" -> handLandmarkerHelper?.loadModelsAndLabels("phrases")
                            else -> Log.e(TAG, "Unknown model selection: $message")
                        }
                        modelsLoaded = true
                    } catch (e: Exception) {
                        Log.e(TAG, "Error loading models and labels: ${e.message}", e)
                        modelsLoaded = false
                    }
                }
            }
        }
    }

    // Add this helper method to clear the prediction:
    private fun clearPrediction() {
        runOnUiThread {
            if (predictionHistory.isNotEmpty()) {
                // Remove the last prediction group
                predictionHistory.removeAt(predictionHistory.size - 1)

                // Update the sentenceBuilder with the remaining predictions
                sentenceBuilder.clear()
                sentenceBuilder.append(predictionHistory.joinToString(" "))

                // Update the sentenceTextView to reflect the change
                sentenceTextView.text = "Prediction: ${sentenceBuilder.toString().trim()}"
                Toast.makeText(this, "Last predicted phrase removed.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "No predictions to clear.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun stopHandDetection() {
        runOnUiThread {
            if (handLandmarkerHelper != null) {
                handLandmarkerHelper?.unloadModels()
                handLandmarkerHelper = null
                modelsLoaded = false
                Toast.makeText(this, "Hand detection stopped.", Toast.LENGTH_SHORT).show()
                Log.d(TAG, "Hand detection models unloaded successfully.")
            }
        }
    }

    private fun addUserToSpinner(username: String) {
        runOnUiThread {

            // Exclude entries with "MESSAGE:" prefix
            if (username.startsWith("MESSAGE:")) {
                Log.d(TAG, "Skipping MESSAGE: entry, not adding to spinner: $username")
                return@runOnUiThread
            }

            // Retrieve the current device's username
            val currentDeviceUsername = intent.getStringExtra("USERNAME") ?: "Unknown"

            // Filter out invalid usernames and role-prefixed usernames
            if (username.startsWith("TARGET:") ||
                username.startsWith("CONTROL:") ||
                username.startsWith("MESSAGE:") ||
                username.startsWith("BROADCAST:") ||
                username.startsWith("ROLE:") ||  // Filter out usernames with "ROLE:" prefix
                username == "Unknown" ||
                username == currentDeviceUsername
            ) {
                Log.d(TAG, "Filtered out invalid username: $username")
                return@runOnUiThread
            }

            // Add the cleaned-up username to the spinner if it's not already present
            if (!connectedUsernames.contains(username)) {
                connectedUsernames.add(username)
                adapter.notifyDataSetChanged()
                Log.d(TAG, "Added username to spinner: $username")
            }
        }
    }

    private fun removeUserFromSpinner(username: String) {
        runOnUiThread {
            if (connectedUsernames.contains(username)) {
                connectedUsernames.remove(username)
                adapter.notifyDataSetChanged()
                Log.d(TAG, "Removed username from spinner: $username")
            }
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

        if (selectedUser == "All") {
            for ((endpointId, username) in connectedEndpoints) {
                connectionsClient.sendPayload(endpointId, Payload.fromBytes(payloadMessage.toByteArray()))
                    .addOnSuccessListener {
                        Log.d(TAG, "Message broadcasted to $username: $message")
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Failed to broadcast message to $username: $message", e)
                    }
            }
            messageSent = true
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

    private fun logEndpointState() {
        Log.d(TAG, "Connected endpoints: $connectedEndpoints")
        Log.d(TAG, "Spinner usernames: $connectedUsernames")
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(viewBinding.previewView.surfaceProvider)
            }

            val imageAnalyzer = ImageAnalysis.Builder().build().also {
                it.setAnalyzer(cameraExecutor) { imageProxy ->
                    try {
                        // Only detect when models are loaded and connected
                        if (modelsLoaded && handLandmarkerHelper != null && isConnected) {
                            handLandmarkerHelper?.detectLiveStream(imageProxy, isFrontCamera = false)
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
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
        return ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
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
        cameraExecutor.shutdown()
        inferenceExecutor.shutdown()
        handLandmarkerHelper?.clearHandLandmarker()
        handLandmarkerHelper = null

        if (::textToSpeech.isInitialized) {
            textToSpeech.stop()
            textToSpeech.shutdown()
        }
    }

    // Implement HandLandmarkerHelper.LandmarkerListener
    override fun onResults(resultBundle: HandLandmarkerHelper.ResultBundle) {
        runOnUiThread {
            if (isFinishing) return@runOnUiThread // Prevent updating the UI if activity is closing

            val handLandmarkerResult = resultBundle.results

            // Check if hands are detected
            if (handLandmarkerResult.landmarks().isNotEmpty()) {
                overlayView.visibility = View.VISIBLE
                overlayView.setResults(handLandmarkerResult) // Pass results to the OverlayView
            } else {
                overlayView.clear() // Clear the overlay when no hands are detected
                overlayView.visibility = View.INVISIBLE // Hide the OverlayView
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
                predictionHistory.add(prediction) // Add to the history
                sentenceBuilder.append("$prediction ")
                sentenceTextView.text = "Prediction: ${sentenceBuilder.toString().trim()}"

                // Speak the new predicted text
                speakText(prediction) // Speak only the latest word, not the entire sentence

                resetTimer()

                // Send the prediction to connected users
                val selectedUser = viewBinding.userSpinner.selectedItem.toString()
                sendPredictionToUser(prediction, selectedUser)
            } else {
                sentenceTextView.text = "Prediction Unavailable"
            }
        }
    }

    private fun speakText(text: String) {
        if (isTTSInitialized) {
            textToSpeech.stop() // Stop any current speech before speaking the new one
            textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
        }
    }

    private fun sendPredictionToUser(prediction: String, selectedUser: String) {
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
        val payloadMessage = if (selectedUser == "All") {
            "BROADCAST_PREDICTION:$currentDeviceUsername:$prediction"
        } else {
            "PREDICTION:$currentDeviceUsername:$prediction"
        }

        var predictionSent = false

        if (selectedUser == "All") {
            for ((endpointId, username) in connectedEndpoints) {
                connectionsClient.sendPayload(endpointId, Payload.fromBytes(payloadMessage.toByteArray()))
                    .addOnSuccessListener {
                        Log.d(TAG, "Prediction broadcasted to $username: $prediction")
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Failed to broadcast prediction to $username: $prediction", e)
                    }
            }
            predictionSent = true
        } else {
            val targetEndpointId = connectedEndpoints.filterValues { it == selectedUser }.keys.firstOrNull()
            if (targetEndpointId != null) {
                connectionsClient.sendPayload(targetEndpointId, Payload.fromBytes(payloadMessage.toByteArray()))
                    .addOnSuccessListener {
                        Log.d(TAG, "Prediction sent to $selectedUser: $prediction")
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Failed to send prediction to $selectedUser: $prediction", e)
                    }
                predictionSent = true
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
            connectedUsernames.clear()
            connectedUsernames.add("None")
            connectedUsernames.add("All")
            adapter.notifyDataSetChanged()
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
                Manifest.permission.ACCESS_FINE_LOCATION, // Ensure this is present
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO
            )
        }
    }
}
