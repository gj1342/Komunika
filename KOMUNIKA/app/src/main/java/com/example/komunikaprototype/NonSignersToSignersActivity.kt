package com.example.komunikaprototype

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.example.komunikaprototype.databinding.NonsignersToSignersBinding
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class NonSignersToSignersActivity : AppCompatActivity(), PoseLandmarkerHelper.LandmarkerListener {

    private lateinit var viewBinding: NonsignersToSignersBinding
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var connectionsClient: ConnectionsClient
    private val connectedEndpoints = mutableMapOf<String, String>() // Map of endpointId -> username
    private var isConnected = false
    private var isActivityDestroyed = false
    private var wordList = listOf<String>()
    private var currentIndex = 0
    private lateinit var serviceId: String // Service ID received from StartingLobbyActivity
    private var hasControl = false // Flag to indicate if the device has control

    private lateinit var connectedUsernames: MutableList<String>
    private lateinit var adapter: ArrayAdapter<String>

    private val role = "Signers" // Role designation

    private var poseLandmarkerHelper: PoseLandmarkerHelper? = null

    private var controlSenderUsername: String? = null // Store the control sender's username

    private val activityResultLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        if (permissions.values.all { it }) {
            startCamera()
            startAdvertising()
        } else {
            Toast.makeText(this, "Permission request denied", Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        private const val TAG = "NonSignersToSignersActivity"
        const val SERVICE_ID = "com.example.komunikaprototype.SERVICE_ID"
        private val REQUIRED_PERMISSIONS = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_WIFI_STATE,
                Manifest.permission.CHANGE_WIFI_STATE,
                Manifest.permission.NEARBY_WIFI_DEVICES,
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_WIFI_STATE,
                Manifest.permission.CHANGE_WIFI_STATE,
                Manifest.permission.ACCESS_FINE_LOCATION,
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

        // A mapping of phrases to video resource IDs
        private val videoMap = hashMapOf(
            "again" to R.raw.again,
            "age" to R.raw.age,
            "edad" to R.raw.age,
            "aklat" to R.raw.book,
            "alam_ko" to R.raw.i_know,
            "alin" to R.raw.which,
            "ano" to R.raw.what,
            "anong" to R.raw.what,
            "april" to R.raw.april,
            "ate" to R.raw.sister,
            "august" to R.raw.august,
            "baby" to R.raw.baby,
            "bakit" to R.raw.why,
            "basahin" to R.raw.read,
            "birthday" to R.raw.birthday,
            "black" to R.raw.black,
            "blue" to R.raw.blue,
            "brown" to R.raw.brown,
            "bukas" to R.raw.tomorrow,
            "december" to R.raw.december,
            "do" to R.raw.do_,
            "doing" to R.raw.do_,
            "ginagawa" to R.raw.do_,
            "gawa" to R.raw.do_,
            "ginawa" to R.raw.do_,
            "draw" to R.raw.draw,
            "eat" to R.raw.eat,
            "eight" to R.raw.eight,
            "eighty" to R.raw.eighty,
            //"excuse_me" to R.raw.excuse_me,
            "february" to R.raw.february,
            "fifty" to R.raw.fifty,
            "fine" to R.raw.fine,
            "five" to R.raw.five,
            "forty" to R.raw.forty,
            "four" to R.raw.four,
            "friday" to R.raw.friday,
            "friend" to R.raw.friend,
            "gray" to R.raw.gray,
            "good" to R.raw.good,
            "green" to R.raw.green,
            "he" to R.raw.s_he,
            "hello" to R.raw.hi_hello,
            "hindi" to R.raw.no,
            "hindi_ko_alam" to R.raw.i_dont_know,
            "hindi_ko_maintindihan" to R.raw.i_dont_understand,
            "i_am" to R.raw.i_am,
            "ilan" to R.raw.how_many,
            "january" to R.raw.january,
            "july" to R.raw.july,
            "june" to R.raw.june,
            "kahapon" to R.raw.yesterday,
            "kailan" to R.raw.`when`,
            "klase" to R.raw.class_,
            "kuya" to R.raw.brother,
            "lecture" to R.raw.lecture,
            "let" to R.raw.let,
            "like" to R.raw.like,
            "gusto" to R.raw.like,
            "live" to R.raw.live,
            "lola" to R.raw.grandmother,
            "lolo" to R.raw.grandfather,
            "love" to R.raw.love,
            "mahal" to R.raw.love,
            "mabagal" to R.raw.slow,
            "mabilis" to R.raw.fast,
            "magandang_gabi" to R.raw.good_evening,
            "magandang_hapon" to R.raw.good_afternoon,
            "magandang_umaga" to R.raw.good_morning,
            "magkano" to R.raw.how_much,
            "mali" to R.raw.wrong,
            "mama" to R.raw.mother,
            "march" to R.raw.march,
            "may" to R.raw.may,
            "meet" to R.raw.meet,
            "makilala" to R.raw.meet,
            "miss" to R.raw.miss,
            "namimiss" to R.raw.miss,
            "monday" to R.raw.monday,
            "my" to R.raw.i_me,
            "me" to R.raw.i_me,
            "naiintindihan_ko" to R.raw.i_understand,
            "name" to R.raw.name,
            "nine" to R.raw.nine,
            "ninety" to R.raw.ninety,
            "november" to R.raw.november,
            "now" to R.raw.now,
            "nice" to R.raw.nice,
            "ikinagagalak" to R.raw.nice,
            "october" to R.raw.october,
            "one" to R.raw.one,
            "one_hundred" to R.raw.one_hundred,
            "oo" to R.raw.yes,
            "orange" to R.raw.orange,
            "paalam" to R.raw.goodbye,
            "paano" to R.raw.how,
            "paaralan" to R.raw.school,
            "pakiusap" to R.raw.please,
            "papa" to R.raw.papa,
            "paper" to R.raw.paper,
            "pasensya" to R.raw.sorry,
            "pencil" to R.raw.pencil,
            "pinsan" to R.raw.cousin,
            "red" to R.raw.red,
            "she" to R.raw.s_he,
            "saan" to R.raw.where,
            "salamat" to R.raw.thank_you,
            "saturday" to R.raw.saturday,
            "say" to R.raw.say,
            "see" to R.raw.see,
            "september" to R.raw.september,
            "seven" to R.raw.seven,
            "seventy" to R.raw.seventy,
            "sino" to R.raw.who,
            "six" to R.raw.six,
            "sixty" to R.raw.sixty,
            "sunday" to R.raw.sunday,
            "tama" to R.raw.correct,
            "teach" to R.raw.teach,
            "teacher" to R.raw.teacher,
            "ten" to R.raw.ten,
            "they" to R.raw.they,
            "thirty" to R.raw.thirty,
            "today" to R.raw.today,
            "three" to R.raw.three,
            "thursday" to R.raw.thursday,
            "tita" to R.raw.auntie,
            "tito" to R.raw.uncle,
            "tuesday" to R.raw.tuesday,
            "twenty" to R.raw.twenty,
            "two" to R.raw.two,
            "violet" to R.raw.violet_purple,
            "walang_anuman" to R.raw.your_welcome,
            "kami" to R.raw.we_kami,
            "tayo" to R.raw.we,
            "wednesday" to R.raw.wednesday,
            "white" to R.raw.white,
            "yellow" to R.raw.yellow,
            "you" to R.raw.you,
            "they"  to R.raw.they,
            "them"  to R.raw.they,

            "muli" to R.raw.again,
            "ulit" to R.raw.again,
            "book" to R.raw.book,
            "i_know" to R.raw.i_know,
            "which" to R.raw.which,
            "what" to R.raw.what,
            "abril" to R.raw.april,
            "sister" to R.raw.sister,
            "agosto" to R.raw.august,
            "bata" to R.raw.baby,
            "batang" to R.raw.baby,
            "why" to R.raw.why,
            "read" to R.raw.read,
            "reading" to R.raw.read,
            "kaarawan" to R.raw.birthday,
            "kaarawang" to R.raw.birthday,
            "tomorrow" to R.raw.tomorrow,
            "disyembre" to R.raw.december,
            "gumuhit" to R.raw.draw,
            "iguhit" to R.raw.draw,
            "kain" to R.raw.eat,
            "kumain" to R.raw.eat,
            "8" to R.raw.eight,
            "80" to R.raw.eighty,
            "makikiraan" to R.raw.excuse_me,
            "pebrero" to R.raw.february,
            "50" to R.raw.fifty,
            "5" to R.raw.five,
            "40" to R.raw.forty,
            "4" to R.raw.four,
            "biyernes" to R.raw.friday,
            "kaibigan" to R.raw.friend,
            "hi" to R.raw.hi_hello,
            "no" to R.raw.no,
            "hinding" to R.raw.no,
            "i_dont_know" to R.raw.i_dont_know,
            "i_dont_understand" to R.raw.i_dont_understand,
            "ako" to R.raw.i_am,
            "how_many" to R.raw.how_many,
            "enero" to R.raw.january,
            "hulyo" to R.raw.july,
            "hunyo" to R.raw.june,
            "yesterday" to R.raw.yesterday,
            "when" to R.raw.`when`,
            "class" to R.raw.class_,
            "klaseng" to R.raw.class_,
            "brother" to R.raw.brother,
            "kapatid" to R.raw.brother,
            "lektyur" to R.raw.lecture,
            "lets" to R.raw.let,
            "tara" to R.raw.let,
            "nakatira" to R.raw.live,
            "grandmother" to R.raw.grandmother,
            "grandfather" to R.raw.grandfather,
            "slow" to R.raw.slow,
            "fast" to R.raw.fast,
            "good_evening" to R.raw.good_evening,
            "good_afternoon" to R.raw.good_afternoon,
            "good_morning" to R.raw.good_morning,
            "how much" to R.raw.how_much,
            "wrong" to R.raw.wrong,
            "mother" to R.raw.mama,
            "incorrect" to R.raw.wrong,
            "marso" to R.raw.march,
            "mayo" to R.raw.may,
            "lunes" to R.raw.monday,
            "i_understand" to R.raw.i_understand,
            "pangalan" to R.raw.name,
            "9" to R.raw.nine,
            "90" to R.raw.ninety,
            "nobyembre" to R.raw.november,
            "oktubre" to R.raw.october,
            "1" to R.raw.one,
            "100" to R.raw.one_hundred,
            "yes" to R.raw.yes,
            "goodbye" to R.raw.goodbye,
            "bye" to R.raw.goodbye,
            "how" to R.raw.how,
            "school" to R.raw.school,
            "paaralang" to R.raw.school,
            "please" to R.raw.please,
            "father" to R.raw.father,
            "papel" to R.raw.paper,
            "sorry" to R.raw.sorry,
            "lapis" to R.raw.pencil,
            "cousin" to R.raw.cousin,
            "siya" to R.raw.s_he,
            "where" to R.raw.where,
            "thank_you" to R.raw.thank_you,
            "sabado" to R.raw.saturday,
            "sabi" to R.raw.say,
            "magkita" to R.raw.meet,
            "kita" to R.raw.see,
            "Setyembre" to R.raw.september,
            "7" to R.raw.seven,
            "70" to R.raw.seventy,
            "who" to R.raw.who,
            "6" to R.raw.six,
            "60" to R.raw.sixty,
            "linggo" to R.raw.sunday,
            "right" to R.raw.correct,
            "correct" to R.raw.correct,
            "turo" to R.raw.teach,
            "teaching" to R.raw.teach,
            "guro" to R.raw.teacher,
            "10" to R.raw.ten,
            "sila" to R.raw.they,
            "30" to R.raw.thirty,
            "3" to R.raw.three,
            "huwebes" to R.raw.thursday,
            "auntie" to R.raw.auntie,
            "uncle" to R.raw.uncle,
            "martes" to R.raw.tuesday,
            "20" to R.raw.twenty,
            "2" to R.raw.two,
            "purple" to R.raw.violet_purple,
            "youre_welcome" to R.raw.your_welcome,
            "we" to R.raw.we,
            "you_are_welcome" to R.raw.your_welcome,
            "miyerkules" to R.raw.wednesday,
            "ikaw" to R.raw.you,
            "kayo" to R.raw.you_kayo
        )

        // A mapping of alphabet letters to video resource IDs
        private val alphabetVideoMap = hashMapOf(
            "a" to R.raw.a,
            "b" to R.raw.b,
            "c" to R.raw.c,
            "d" to R.raw.d,
            "e" to R.raw.e,
            "f" to R.raw.f,
            "g" to R.raw.g,
            "h" to R.raw.h,
            "i" to R.raw.i,
            "j" to R.raw.j,
            "k" to R.raw.k,
            "l" to R.raw.l,
            "m" to R.raw.m,
            "n" to R.raw.n,
            "o" to R.raw.o,
            "p" to R.raw.p,
            "q" to R.raw.q,
            "r" to R.raw.r,
            "s" to R.raw.s,
            "t" to R.raw.t,
            "u" to R.raw.u,
            "v" to R.raw.v,
            "w" to R.raw.w,
            "x" to R.raw.x,
            "y" to R.raw.y,
            "z" to R.raw.z
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewBinding = NonsignersToSignersBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)

        // Back Button: Return to previous activity when clicked
        val backButton: ImageView = findViewById(R.id.back_icon)
        backButton.setOnClickListener {
            finish()
        }

        // Initialize PoseLandmarkerHelper
        poseLandmarkerHelper = PoseLandmarkerHelper(
            context = this,
            poseLandmarkerHelperListener = this // Pass the listener for results
        )

        // Retrieve Service ID from intent
        serviceId = intent.getStringExtra("SERVICE_ID") ?: SERVICE_ID

        // Initialize components
        connectionsClient = Nearby.getConnectionsClient(this)
        cameraExecutor = Executors.newSingleThreadExecutor()

        // Initialize components
        viewBinding.videoView.visibility = View.GONE // Hide VideoView by default

        // Initialize TextView
        viewBinding.textView.text = ""

        val participantCountTextView = findViewById<TextView>(R.id.participantCountTextView)
        participantCountTextView.text = "Nonsigner/s Participants: 0"

        // Request camera permissions
        if (allPermissionsGranted()) {
            startCamera()
            // Start advertising and discovery to establish a connection
            startAdvertising()
        } else {
            requestPermissions()
        }

        val wrongSignButton = findViewById<Button>(R.id.wrongSignButton)
        wrongSignButton.setOnClickListener {
            val controllingEndpointId = connectedEndpoints.entries.find { it.value == controlSenderUsername }?.key
            if (controllingEndpointId != null) {
                sendResetMessageToSender(controllingEndpointId)
            } else {
                Toast.makeText(this, "Control sender is not connected. Cannot send reset message.", Toast.LENGTH_SHORT).show()
            }
        }

        // Set VideoView completion listener to play the next video in sequence
        viewBinding.videoView.setOnCompletionListener {
            if (currentIndex < wordList.size) {
                playNextVideo()
            } else {
                // When all videos are finished, hide the VideoView
                viewBinding.videoView.visibility = View.GONE
                Log.d(TAG, "All videos finished. VideoView is now hidden.")

                // Ensure Wrong Sign Button remains visible and on top
                viewBinding.wrongSignButton.visibility = View.VISIBLE
                viewBinding.wrongSignButton.bringToFront()
            }
        }

        // Initialize Spinner and Adapter
        connectedUsernames = mutableListOf("None", "All")
        adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, connectedUsernames)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)

        val userSpinner = findViewById<Spinner>(R.id.userSpinner)
        userSpinner.adapter = adapter

        // Listener for Spinner item selection
        userSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                val selectedUser = connectedUsernames[position]
                handleUserSelection(selectedUser)
            }

            override fun onNothingSelected(parent: AdapterView<*>) {
                // Do nothing
            }
        }

        val upwardSpinner = findViewById<Spinner>(R.id.upwardSpinner)
        val categoriesWithIds = mapOf(
            "None" to 0,
            "A-D, F-M, U" to 1,
            "E, N-T, V-Z" to 2,
            "1-5" to 3,
            "6-10" to 18,
            "20-100" to 19,
            "Greetings" to 4,
            "Responses" to 5,
            "Family" to 6,
            "Colors" to 7,
            "Pronouns" to 8,
            "Nouns" to 9,
            //"Verbs" to 10,
            "School" to 11,
            //"Jan-June" to 12,
            //"July-Dec" to 13,
            "Weeks" to 14,
            //"Time" to 15,
            "Questions" to 16,
            "Phrases" to 17,
        )

        val spinnerAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            categoriesWithIds.keys.toList()
        )
        upwardSpinner.adapter = spinnerAdapter

        // Listener for Spinner item selection
        upwardSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                if (!hasControl) {
                    Toast.makeText(
                        this@NonSignersToSignersActivity,
                        "You do not have control to modify the model categories.",
                        Toast.LENGTH_SHORT
                    ).show()
                    return
                }

                val selectedCategory = parent.getItemAtPosition(position) as String
                val selectedId = categoriesWithIds[selectedCategory]

                if (selectedCategory == "None") {
                    sendMessageToSender("STOP_HAND_DETECTION")
                    Log.d(TAG, "Sent STOP_HAND_DETECTION to control sender.")
                } else if (selectedId != null) {
                    sendMessageToSender(selectedId.toString())
                    Log.d(TAG, "Spinner selected: $selectedCategory with ID: $selectedId, sent to control sender.")
                } else {
                    Log.e(TAG, "Invalid selection: $selectedCategory")
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>) {
                // Do nothing
            }
        }

    }

    // Add this helper method to send the reset message:
    private fun sendResetMessageToSender(endpointId: String) {
        val resetMessage = "RESET"
        connectionsClient.sendPayload(endpointId, Payload.fromBytes(resetMessage.toByteArray()))
            .addOnSuccessListener {
                Log.d(TAG, "Reset message sent successfully to control sender.")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to send reset message to control sender.", e)
            }
    }

    private fun sendMessageToSender(message: String) {
        val controllingEndpointId = connectedEndpoints.entries.find { it.value == controlSenderUsername }?.key
        if (controllingEndpointId != null) {
            connectionsClient.sendPayload(controllingEndpointId, Payload.fromBytes(message.toByteArray()))
                .addOnSuccessListener {
                    Log.d(TAG, "Message sent successfully to control sender: $message")
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed to send message to control sender: $message", e)
                }
        } else {
            Log.e(TAG, "No endpoint found for control sender. Message not sent.")
            Toast.makeText(this, "Control sender is not connected. Message not sent.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun addUserToSpinner(username: String) {
        runOnUiThread {
            val currentDeviceUsername = intent.getStringExtra("USERNAME") ?: "Unknown" // Retrieve current device username

            // Filter out system-specific prefixes and the current device's username
            if (username.startsWith("ALERT:") || username.startsWith("CONTROL:") || username.startsWith("USERNAME:") || username == currentDeviceUsername) {
                Log.d(TAG, "Filtered out username: $username")
                return@runOnUiThread
            }

            // Add only valid usernames to the spinner
            if (!connectedUsernames.contains(username)) {
                connectedUsernames.add(username)
                adapter.notifyDataSetChanged()
            }
        }
    }

    private fun removeUserFromSpinner(username: String) {
        runOnUiThread {
            connectedUsernames.remove(username)
            adapter.notifyDataSetChanged()
        }
    }

    private fun handleUserSelection(selectedUser: String) {
        when (selectedUser) {
            "None" -> {
                Toast.makeText(this, "No user selected for sending alerts.", Toast.LENGTH_SHORT).show()
            }
            "All" -> {
                sendAlertMessageToAll(intent.getStringExtra("USERNAME") ?: "Unknown") // Broadcast alert to all connected users
            }
            else -> {
                val senderUsername = intent.getStringExtra("USERNAME") ?: "Unknown"
                sendAlertMessageToUser(senderUsername, selectedUser) // Send alert to the selected user
            }
        }
    }

    private fun sendAlertMessageToUser(senderUsername: String, targetUsername: String) {
        val targetEndpointId = connectedEndpoints.entries.filter { it.value != "Signers" }.find { it.value == targetUsername }?.key // Filter out other Signers
        if (targetEndpointId != null) {
            val alertMessage = "ALERT:$senderUsername wants to communicate."
            connectionsClient.sendPayload(targetEndpointId, Payload.fromBytes(alertMessage.toByteArray()))
                .addOnSuccessListener {
                    Log.d(TAG, "Alert sent to $targetUsername.")
                    Toast.makeText(this, "Alert sent to $targetUsername.", Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed to send alert to $targetUsername", e)
                    Toast.makeText(this, "Failed to send alert to $targetUsername.", Toast.LENGTH_SHORT).show()
                }
        } else {
            Toast.makeText(this, "User $targetUsername not found.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun sendAlertMessageToAll(senderUsername: String) {
        if (connectedEndpoints.isNotEmpty()) {
            val alertMessage = "ALERT:All:$senderUsername wants to communicate."
            for (endpointId in connectedEndpoints.keys) {
                connectionsClient.sendPayload(endpointId, Payload.fromBytes(alertMessage.toByteArray()))
                    .addOnSuccessListener { Log.d(TAG, "Broadcast alert sent to $endpointId") }
                    .addOnFailureListener { e -> Log.e(TAG, "Failed to send alert to $endpointId", e) }
            }
        } else {
            Toast.makeText(this, "No connected devices to broadcast message.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateParticipantCount() {
        val count = connectedEndpoints.size
        runOnUiThread {
            val participantCountTextView = findViewById<TextView>(R.id.participantCountTextView)
            participantCountTextView.text = "Nonsigner/s Participants: $count"
        }
    }

    // Restrict model category change functionality
    private fun sendMessage(message: String) {
        Log.d(TAG, "Checking control status for message sending.")
        Log.d(TAG, "Connected Endpoints: $connectedEndpoints")

        // Allow only if this device has control
        if (!hasControl) {
            Toast.makeText(this, "You do not have control to modify the model categories.", Toast.LENGTH_SHORT).show()
            return
        }

        // Ensure there are connected devices
        if (connectedEndpoints.isEmpty()) {
            Toast.makeText(this, "No connected devices to send the message.", Toast.LENGTH_SHORT).show()
            return
        }

        val payloadMessage = "MESSAGE:$message"

        // Send message to all connected endpoints
        connectedEndpoints.keys.forEach { endpointId ->
            connectionsClient.sendPayload(endpointId, Payload.fromBytes(payloadMessage.toByteArray()))
                .addOnSuccessListener {
                    Log.d(TAG, "Message sent successfully to $endpointId: $payloadMessage")
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed to send message to $endpointId: $payloadMessage", e)
                    Toast.makeText(this, "Message delivery failed to some devices.", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun sendPayloadToEndpoint(endpointId: String, message: String) {
        val payload = Payload.fromBytes(message.toByteArray())
        connectionsClient.sendPayload(endpointId, payload)
            .addOnSuccessListener {
                Log.d(TAG, "Message sent successfully: $message to $endpointId")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to send message: $message to $endpointId", e)
            }
    }

    private fun startAdvertising() {
        val advertisingOptions = AdvertisingOptions.Builder().setStrategy(Strategy.P2P_CLUSTER).build()
        connectionsClient.startAdvertising(
            "User",
            serviceId,
            connectionLifecycleCallback,
            advertisingOptions
        ).addOnSuccessListener {
            Log.d(TAG, "Advertising started successfully.")
        }.addOnFailureListener { e ->
            Log.e(TAG, "Advertising failed: ${e.message}")
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
                val username = intent.getStringExtra("USERNAME") ?: "Unknown"

                // Add endpoint and username to the map
                connectedEndpoints[endpointId] = username
                addUserToSpinner(username)
                updateParticipantCount()

                // Send the username of this device to the connected endpoint
                val payload = Payload.fromBytes("ROLE:$role,USERNAME:$username".toByteArray())
                connectionsClient.sendPayload(endpointId, payload)

                Log.d(TAG, "Connected to $endpointId. Sent username: $username")
            } else {
                Log.e(TAG, "Connection failed to $endpointId")
            }
        }

        override fun onDisconnected(endpointId: String) {
            val username = connectedEndpoints.remove(endpointId)
            if (!username.isNullOrEmpty()) {
                removeUserFromSpinner(username)
            }
            updateParticipantCount()
            Log.d(TAG, "Disconnected from $endpointId. Username removed: $username")
        }
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            if (isActivityDestroyed) {
                Log.w(TAG, "Payload received but activity is destroyed. Ignoring payload.")
                return
            }

            Log.d(TAG, "Payload received from endpointId: $endpointId")

            if (payload.type == Payload.Type.BYTES) {
                val message = payload.asBytes()?.let { String(it) }
                if (message != null) {
                    val username = intent.getStringExtra("USERNAME") ?: "Unknown"

                    // Handle other cases for non-AR mode
                    when {
                        message.startsWith("BROADCAST_PREDICTION:") -> {
                            val parts = message.split(":", limit = 3)
                            if (parts.size == 3) {
                                val sender = parts[1] // Extract sender username
                                val prediction = parts[2] // Extract the predicted sign
                                displayPrediction(sender, prediction) // Use the same display logic
                            } else {
                                Log.e(TAG, "Invalid BROADCAST_PREDICTION message format: $message")
                            }
                        }
                        message.startsWith("PREDICTION:") -> {
                            val parts = message.split(":", limit = 3)
                            if (parts.size == 3) {
                                val sender = parts[1] // Correctly parses the sender username
                                val prediction = parts[2] // Correctly parses the predicted sign
                                displayPrediction(sender, prediction)
                            } else {
                                Log.e(TAG, "Invalid PREDICTION message format: $message")
                            }
                        }
                        message.startsWith("ROLE:") -> {
                            // Parse "ROLE:Non Signers,USERNAME: <username>"
                            val parts = message.split(",")
                            val role = parts.find { it.startsWith("ROLE:") }?.removePrefix("ROLE:")
                            val username = parts.find { it.startsWith("USERNAME:") }?.removePrefix("USERNAME:")

                            if (role == "Non Signers" && username != null) {
                                Log.d(TAG, "Parsed username: $username with role: $role from endpoint: $endpointId")
                                connectedEndpoints[endpointId] = username
                                addUserToSpinner(username)
                            } else {
                                Log.e(TAG, "Invalid payload format: $message")
                            }
                        }
                        message.startsWith("ALERT:") -> {
                            // Parse alert format: ALERT:<target>:<content>
                            val parts = message.split(":", limit = 3)
                            if (parts.size == 3) {
                                val targetUser = parts[1]
                                val alertContent = parts[2]

                                if (targetUser == username || targetUser == "All") {
                                    Log.d(TAG, "Received alert for this user: $alertContent")
                                    showAlertNotification(alertContent)
                                } else {
                                    Log.d(TAG, "Received alert not intended for this user: $message")
                                }
                            }
                        }
                        message.startsWith("BROADCAST:") -> {
                            // Broadcast message to all users
                            val broadcastMessage = message.removePrefix("BROADCAST:")
                            Log.d(TAG, "Received broadcast message: $broadcastMessage")
                            updateTextViewAndPlayVideo(broadcastMessage)
                        }
                        message.startsWith("TARGET:") -> {
                            // Targeted message for a specific user
                            val parts = message.split(":", limit = 3)
                            if (parts.size == 3) {
                                val targetUser = parts[1]
                                val targetMessage = parts[2]
                                val username = intent.getStringExtra("USERNAME") ?: "Unknown"

                                if (targetUser == username) {
                                    Log.d(TAG, "Received targeted message: $targetMessage")
                                    updateTextViewAndPlayVideo(targetMessage)
                                } else {
                                    Log.d(TAG, "Targeted message not for this user: $message")
                                }
                            }
                        }
                        message.startsWith("CONTROL:") -> {
                            val parts = message.split(",")
                            val controllingUser = parts[0].removePrefix("CONTROL:")
                            controlSenderUsername = parts.find { it.startsWith("USERNAME:") }?.removePrefix("USERNAME:")

                            val currentUsername = intent.getStringExtra("USERNAME") ?: "Unknown"
                            hasControl = (controllingUser == "All" || controllingUser == currentUsername)

                            runOnUiThread {
                                val statusMessage = if (hasControl) {
                                    "You now have control of model prediction, granted by $controlSenderUsername."
                                } else {
                                    "You do not have control of model prediction."
                                }
                                Toast.makeText(this@NonSignersToSignersActivity, statusMessage, Toast.LENGTH_SHORT).show()
                                Log.d(TAG, "Control status updated. Granted by: $controlSenderUsername. Current control: $hasControl")
                            }
                        }
                        else -> {
                            // Handle other messages
                            updateTextViewAndPlayVideo(message)
                        }
                    }
                }
            } else {
                Log.e(TAG, "Received payload of unexpected type from endpoint: $endpointId")
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            if (isActivityDestroyed) {
                Log.w(TAG, "Payload transfer update received but activity is destroyed. Ignoring update.")
                return
            }

            Log.d(TAG, "Payload transfer update from endpointId: $endpointId, status: ${update.status}")

            if (update.status == PayloadTransferUpdate.Status.SUCCESS) {
                Log.d(TAG, "Payload transfer successfully completed for endpoint: $endpointId")
            } else if (update.status == PayloadTransferUpdate.Status.FAILURE) {
                Log.e(TAG, "Payload transfer failed for endpoint: $endpointId")
            }
        }
    }

    private fun displayPrediction(sender: String, prediction: String) {
        runOnUiThread {
            val predictedSignTextView = findViewById<TextView>(R.id.predictedSignTextView)
            predictedSignTextView.text = "Predicted Sign: $prediction"
            Log.d(TAG, "Displayed prediction: $prediction")
        }
    }

    private fun showAlertNotification(alertContent: String) {
        runOnUiThread {
            Toast.makeText(this, alertContent, Toast.LENGTH_LONG).show()

            // Optional: Vibrate or play sound for alerts
            val vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                vibrator.vibrate(500)
            }
        }
    }

    // Update control logic
    private fun updateControl(controllingUser: String) {
        val deviceUsername = intent.getStringExtra("USERNAME") ?: "Unknown"
        hasControl = (controllingUser == "All" || controllingUser == deviceUsername)

        runOnUiThread {
            val statusMessage = if (hasControl) {
                "You now have control of model prediction."
            } else {
                "You do not have control of model prediction."
            }
            Toast.makeText(this, statusMessage, Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateTextViewAndPlayVideo(message: String) {
        Log.d(TAG, "Received message: $message")
        runOnUiThread {
            if (message.isBlank()) {
                viewBinding.videoView.visibility = View.GONE
                viewBinding.textView.visibility = View.GONE
                Log.d(TAG, "No message received. VideoView and TextView are now hidden.")
                return@runOnUiThread
            }

            try {
                val parts = message.split(":", limit = 3)
                when (parts.size) {
                    3 -> {
                        val senderUsername = parts[1]
                        val actualMessage = parts[2]

                        // Display the message with the sender's username
                        viewBinding.textView.visibility = View.VISIBLE
                        viewBinding.textView.text = "$senderUsername sends a message: $actualMessage"
                        Log.d(TAG, "Message from $senderUsername displayed: $actualMessage")

                        // Process the message to play corresponding videos
                        processMessageForVideo(actualMessage)
                    }
                    2 -> {
                        val senderUsername = parts[0]
                        val actualMessage = parts[1]

                        // Handle messages without the full structure
                        viewBinding.textView.visibility = View.VISIBLE
                        viewBinding.textView.text = "$senderUsername sends a message: $actualMessage"
                        Log.d(TAG, "Message from $senderUsername displayed: $actualMessage")

                        // Process the message to play corresponding videos
                        processMessageForVideo(actualMessage)
                    }
                    else -> throw IllegalArgumentException("Invalid message format: $message")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing message: $message", e)
                viewBinding.textView.text = "Error: Unable to display message."
                viewBinding.textView.visibility = View.VISIBLE
            }
        }
    }

    private fun processMessageForVideo(message: String) {
        val formattedInput = message.lowercase().replace(" ", "_")
        wordList = splitIntoKnownPhrases(formattedInput)
        currentIndex = 0

        if (wordList.isNotEmpty()) {
            playNextVideo()
        } else {
            viewBinding.videoView.visibility = View.GONE
        }
    }

    private fun playNextVideo() {
        while (currentIndex < wordList.size) {
            val phrase = wordList[currentIndex]
            val videoResId = videoMap[phrase] ?: getAlphabetVideos(phrase)

            if (videoResId != null) {
                try {
                    val videoUri = Uri.parse("android.resource://$packageName/$videoResId")
                    Log.d(TAG, "Playing video for phrase: $phrase, URI: $videoUri")
                    viewBinding.videoView.setVideoURI(videoUri)
                    viewBinding.videoView.visibility = View.VISIBLE
                    viewBinding.videoView.start()

                    // Ensure Wrong Sign Button remains visible and in front
                    viewBinding.wrongSignButton.visibility = View.VISIBLE
                    viewBinding.wrongSignButton.bringToFront()

                    // Ensure the VideoView is on top of PreviewView
                    viewBinding.videoView.bringToFront()
                    currentIndex++ // Move to the next item in the list
                    // Ensure Wrong Sign Button remains visible and in front
                    viewBinding.wrongSignButton.visibility = View.VISIBLE
                    viewBinding.wrongSignButton.bringToFront()
                    return // Exit the method to wait for video completion
                } catch (e: Exception) {
                    Log.e(TAG, "Error playing video: ${e.message}")
                }
            } else {
                Log.e(TAG, "No video resource found for phrase: $phrase")
            }

            currentIndex++ // Move to the next item
        }

        // All videos finished
        Log.d(TAG, "All videos finished.")
        viewBinding.videoView.visibility = View.GONE
    }

    private fun splitIntoKnownPhrases(input: String): List<String> {
        val result = mutableListOf<String>()
        var remainingInput = input

        while (remainingInput.isNotEmpty()) {
            // Check if the next part is numeric
            val numericMatch = Regex("^\\d+").find(remainingInput)
            if (numericMatch != null) {
                // Extract the full numeric part
                val numericPart = numericMatch.value.toIntOrNull()

                if (numericPart != null) {
                    // Handle tens and units
                    val tens = numericPart / 10 * 10 // e.g., 22 -> 20
                    val units = numericPart % 10    // e.g., 22 -> 2

                    if (tens > 0 && videoMap.containsKey(tens.toString())) {
                        result.add(tens.toString()) // e.g., "20" -> "twenty"
                    }

                    if (units > 0 && videoMap.containsKey(units.toString())) {
                        result.add(units.toString()) // e.g., "2" -> "two"
                    }

                    // Remove the numeric portion from the remaining input
                    remainingInput = remainingInput.drop(numericMatch.value.length).trimStart('_')
                    continue
                }
            }

            // Handle known phrases from videoMap (longest match first)
            val knownPhrases = videoMap.keys.sortedByDescending { it.length }
            var matched = false
            for (phrase in knownPhrases) {
                if (remainingInput.startsWith(phrase)) {
                    result.add(phrase)
                    remainingInput = remainingInput.removePrefix(phrase).trimStart('_')
                    matched = true
                    break
                }
            }

            // Handle unmatched characters
            if (!matched) {
                result.add(remainingInput.take(1))
                remainingInput = remainingInput.drop(1)
            }
        }

        return result
    }

    private fun getAlphabetVideos(phrase: String): Int? {
        return if (phrase.length == 1) {
            alphabetVideoMap[phrase]
        } else {
            null
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermissions() {
        activityResultLauncher.launch(REQUIRED_PERMISSIONS)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 10 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            Toast.makeText(this, "Camera permission is required to use the camera", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .setTargetRotation(viewBinding.previewView.display.rotation)
                .build().also {
                    it.setSurfaceProvider(viewBinding.previewView.surfaceProvider)
                }

            val imageAnalyzer = ImageAnalysis.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .setTargetRotation(viewBinding.previewView.display.rotation)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build().also { analyzer ->
                    analyzer.setAnalyzer(cameraExecutor) { imageProxy ->
                        try {
                            poseLandmarkerHelper?.detectLiveStream(imageProxy, isFrontCamera = false)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error analyzing image: ${e.message}")
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

    override fun onResults(resultBundle: PoseLandmarkerHelper.ResultBundle) {
        runOnUiThread {
            val layoutParams = viewBinding.videoView.layoutParams as FrameLayout.LayoutParams
            if (resultBundle.results.isNotEmpty()) {
                val firstPose = resultBundle.results[0]
                val landmarks = firstPose.landmarks()

                if (landmarks.isNotEmpty() && landmarks[0].isNotEmpty()) {
                    val rightShoulder = landmarks[0][12] // Right shoulder landmark

                    val screenX = (rightShoulder.x() * viewBinding.previewView.width).toInt()
                    val screenY = (rightShoulder.y() * viewBinding.previewView.height).toInt()

                    layoutParams.leftMargin = screenX - viewBinding.videoView.width / 2
                    layoutParams.topMargin = screenY - viewBinding.videoView.height / 2
                    viewBinding.videoView.layoutParams = layoutParams
                    viewBinding.videoView.visibility = View.VISIBLE
                } else {
                    // No landmarks detected, center video on the screen
                    centerVideoOnScreen(layoutParams)
                }
            } else {
                // No pose detected, center video on the screen
                centerVideoOnScreen(layoutParams)
            }
        }
    }

    private fun centerVideoOnScreen(layoutParams: FrameLayout.LayoutParams) {
        // Calculate center position based on parent (frameLayout in this case)
        val parentWidth = viewBinding.frameLayout.width
        val parentHeight = viewBinding.frameLayout.height

        if (parentWidth > 0 && parentHeight > 0) {
            layoutParams.leftMargin = (parentWidth - viewBinding.videoView.width) / 2
            layoutParams.topMargin = (parentHeight - viewBinding.videoView.height) / 2
            viewBinding.videoView.layoutParams = layoutParams
            viewBinding.videoView.visibility = View.VISIBLE
        } else {
            // Default to making the video visible if dimensions are not yet available
            viewBinding.videoView.visibility = View.VISIBLE
        }
    }

    override fun onError(error: String, errorCode: Int) {
        Log.e(TAG, "Pose detection error: $error")
        runOnUiThread {
            Toast.makeText(this, "Pose detection error: $error", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()
                cameraProvider.unbindAll()
            } catch (e: Exception) {
                Log.e(TAG, "Error while stopping the camera: ${e.message}")
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun stopAdvertising() {
        connectionsClient.stopAdvertising()
        Log.d(TAG, "Stopped advertising.")
    }

    private fun disconnectFromEndpoint() {
        if (connectedEndpoints.isNotEmpty()) {
            // Iterate through all connected endpoints and disconnect
            for (endpointId in connectedEndpoints.keys.toList()) {
                connectionsClient.disconnectFromEndpoint(endpointId)
                Log.d(TAG, "Disconnected from endpoint: $endpointId")
            }

            // Clear the connectedEndpoints map and update the UI
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
        stopAdvertising()
        disconnectFromEndpoint()

        // Allow the default back action (finish activity)
        super.onBackPressed()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return super.onKeyDown(keyCode, event)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopCamera()
        cameraExecutor.shutdown()
        isActivityDestroyed = true
    }
}
