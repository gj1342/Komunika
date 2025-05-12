package com.example.komunikaprototype

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.MediaStore
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.komunikaprototype.databinding.NonsignersToSignersBinding
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class NonSignersToSignersActivity : AppCompatActivity() {

    private lateinit var viewBinding: NonsignersToSignersBinding
    private lateinit var connectionsClient: ConnectionsClient
    private val connectedEndpoints = mutableMapOf<String, String>() // Map of endpointId -> username
    private var isConnected = false
    private var isActivityDestroyed = false
    private var wordList = listOf<String>()
    private var currentIndex = 0
    private lateinit var serviceId: String // Service ID received from StartingLobbyActivity
    private var hasControl = false // Flag to indicate if the device has control

    private lateinit var connectedUsers: MutableList<UserWithImage>
    private lateinit var userSpinnerAdapter: UserSpinnerAdapter

    private val role = "Signers" // Role designation

    private var controlSenderUsername: String? = null // Store the control sender's username
    private var myProfileImageBase64: String? = null // Store current user's profile image

    private val activityResultLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        if (permissions.values.all { it }) {
            startAdvertising()
        } else {
            Toast.makeText(this, "Permission request denied", Toast.LENGTH_SHORT).show()
        }
    }

    // Add a new instance variable to keep track of messages
    private val messageHistory = StringBuilder()

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
                Manifest.permission.ACCESS_FINE_LOCATION
            )
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_WIFI_STATE,
                Manifest.permission.CHANGE_WIFI_STATE,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
            else -> arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_WIFI_STATE,
                Manifest.permission.CHANGE_WIFI_STATE,
                Manifest.permission.ACCESS_FINE_LOCATION, // Ensure this is present
                Manifest.permission.ACCESS_COARSE_LOCATION
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

        // Retrieve Service ID from intent
        serviceId = intent.getStringExtra("SERVICE_ID") ?: SERVICE_ID

        // Load profile image from SharedPreferences
        loadProfileImage()

        // Initialize components
        connectionsClient = Nearby.getConnectionsClient(this)

        // Hide the camera preview view since we're not using the camera
        viewBinding.previewView.visibility = View.GONE

        // Initialize components
        viewBinding.videoView.visibility = View.GONE // Hide VideoView by default

        // Initialize TextView
        viewBinding.textView.text = ""

        val participantCountTextView = findViewById<TextView>(R.id.participantCountTextView)
        participantCountTextView.text = "Nonsigner/s Participants: 0"
        participantCountTextView.visibility = View.VISIBLE
        
        // Make sure the participant count is always accessible/visible
        val spacer = findViewById<View>(R.id.spacer)
        spacer.visibility = View.VISIBLE

        // Initialize prediction text view
        val predictedSignTextView = findViewById<TextView>(R.id.predictedSignTextView)
        predictedSignTextView.text = "Waiting for sign detection..."

        // Set up message sending functionality
        val messageEditText = findViewById<EditText>(R.id.messageEditText)
        val sendButton = findViewById<Button>(R.id.sendButton)
        
        sendButton.setOnClickListener {
            val message = messageEditText.text.toString().trim()
            if (message.isNotEmpty()) {
                val selectedPosition = findViewById<Spinner>(R.id.userSpinner).selectedItemPosition
                if (selectedPosition >= 0 && selectedPosition < connectedUsers.size) {
                    val selectedUser = connectedUsers[selectedPosition]
                    // Check if it's a valid selection (not the current user)
                    if (selectedUser.username == "None") {
                        Toast.makeText(this, "Please select a user to send the message to", Toast.LENGTH_SHORT).show()
                    } else {
                        sendMessageToNonDeafUser(message, selectedUser.username)
                        messageEditText.text.clear()
                    }
                } else {
                    Toast.makeText(this, "Invalid user selection", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "Please enter a message", Toast.LENGTH_SHORT).show()
            }
        }
        
        // Handle Enter key press in EditText
        messageEditText.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_ENTER) {
                sendButton.performClick()
                return@setOnKeyListener true
            }
            false
        }

        // Request permissions and start advertising
        if (allPermissionsGranted()) {
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

        // Set VideoView properties for playback with automatic chaining of videos
        viewBinding.videoView.setOnCompletionListener {
            // When a video completes, play the next one in the sequence
            Log.d(TAG, "Video playback completed")
            
            // Play the next video in the sequence if available
            playNextVideo()
            
            // Make sure the Wrong Sign Button remains visible
            viewBinding.wrongSignButton.visibility = View.VISIBLE
            viewBinding.wrongSignButton.bringToFront()
        }

        // Initialize user list with "None" and "All" options
        connectedUsers = mutableListOf(
            UserWithImage("None"), 
            UserWithImage("All")
        )
        
        // Initialize the custom spinner adapter
        userSpinnerAdapter = UserSpinnerAdapter(
            this,
            R.layout.spinner_item_user,
            connectedUsers
        )

        val userSpinner = findViewById<Spinner>(R.id.userSpinner)
        userSpinner.adapter = userSpinnerAdapter

        // Listener for Spinner item selection
        userSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                if (position < connectedUsers.size) {
                    val selectedUser = connectedUsers[position]
                    handleUserSelection(selectedUser.username)
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>) {
                // Do nothing
            }
        }

        val upwardSpinner = findViewById<Spinner>(R.id.upwardSpinner)
        val categoriesWithIds = mapOf(
            "None" to 0,
            "Alphabets" to 1,
            "1-5" to 2,
            "6-10" to 3,
            "20-100" to 4,
            "Greetings" to 5,
            "Responses" to 6,
            "Family" to 7,
            "Colors" to 8,
            "Pronouns" to 9,
            "Nouns" to 10,
            "Verbs" to 11,
            "School" to 12,
            "Weeks" to 13,
            "Time" to 14,
            "Questions" to 15,
            "Phrases" to 16,
            "Calendar" to 17
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
        
        // Set up the Stop Prediction button
        val stopPredictionButton = findViewById<Button>(R.id.stopPredictionButton)
        stopPredictionButton.setOnClickListener {
            if (!hasControl) {
                Toast.makeText(
                    this@NonSignersToSignersActivity,
                    "You do not have control to stop predictions.",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }
            
            // Get the current prediction text
            val predictedSignTextView = findViewById<TextView>(R.id.predictedSignTextView)
            val currentPrediction = predictedSignTextView.text.toString()
            
            // Set spinner selection to "None" (which is at position 0)
            upwardSpinner.setSelection(0)
            
            // Send STOP_HAND_DETECTION message directly
            sendMessageToSender("STOP_HAND_DETECTION")
            
            Toast.makeText(
                this@NonSignersToSignersActivity,
                "AI prediction stopped",
                Toast.LENGTH_SHORT
            ).show()
            
            Log.d(TAG, "Stop Prediction button clicked. Sent STOP_HAND_DETECTION to control sender.")
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

    private fun addUserToSpinner(username: String, endpointId: String, profileImageBase64: String? = null) {
        runOnUiThread {
            val currentDeviceUsername = intent.getStringExtra("USERNAME") ?: "Unknown" // Retrieve current device username

            // Filter out system-specific prefixes and the current device's username
            if (username.startsWith("ALERT:") || username.startsWith("CONTROL:") || username.startsWith("USERNAME:") || username == currentDeviceUsername) {
                Log.d(TAG, "Filtered out username: $username")
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
                    role = "Non Signers"
                ))
            }
            
            // Notify adapter of changes
            userSpinnerAdapter.notifyDataSetChanged()
        }
    }

    private fun removeUserFromSpinner(username: String) {
        runOnUiThread {
            connectedUsers.removeIf { it.username == username && it.username != "None" && it.username != "All" }
            userSpinnerAdapter.notifyDataSetChanged()
        }
    }

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

    private fun playNextVideo() {
        // Check if there's nothing to play
        if (wordList.isEmpty() || currentIndex >= wordList.size) {
            Log.d(TAG, "No videos to play or reached the end of the list")
            viewBinding.videoView.visibility = View.GONE
            
            // Reset prediction text
            val predictedSignTextView = findViewById<TextView>(R.id.predictedSignTextView)
            predictedSignTextView.text = "Waiting for sign detection..."
            return
        }
        
        while (currentIndex < wordList.size) {
            val phrase = wordList[currentIndex]
            val videoResId = videoMap[phrase] ?: getAlphabetVideos(phrase)

            if (videoResId != null) {
                try {
                    // Stop any currently playing video
                    if (viewBinding.videoView.isPlaying) {
                        viewBinding.videoView.stopPlayback()
                    }
                    
                    val videoUri = Uri.parse("android.resource://$packageName/$videoResId")
                    Log.d(TAG, "Playing video for phrase: $phrase, URI: $videoUri")
                    viewBinding.videoView.setVideoURI(videoUri)
                    viewBinding.videoView.visibility = View.VISIBLE
                    viewBinding.videoView.start()

                    // Update prediction text
                    val predictedSignTextView = findViewById<TextView>(R.id.predictedSignTextView)
                    predictedSignTextView.text = "Current sign: $phrase"
                    
                    currentIndex++ // Move to the next item in the list
                    return // Exit the method to wait for video completion
                } catch (e: Exception) {
                    Log.e(TAG, "Error playing video: ${e.message}")
                    currentIndex++ // Skip to the next item if there's an error
                }
            } else {
                Log.e(TAG, "No video resource found for phrase: $phrase")
                currentIndex++ // Skip to the next item
            }
        }

        // All videos finished
        Log.d(TAG, "All videos finished.")
        viewBinding.videoView.visibility = View.GONE
        
        // Reset prediction text
        val predictedSignTextView = findViewById<TextView>(R.id.predictedSignTextView)
        predictedSignTextView.text = "Waiting for sign detection..."
    }

    private fun centerVideoOnScreen() {
        // Just ensure the video is visible
        viewBinding.videoView.visibility = View.VISIBLE
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
            startAdvertising()
        } else {
            Toast.makeText(this, "Permission is required to continue", Toast.LENGTH_SHORT).show()
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

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, connectionInfo: ConnectionInfo) {
            Log.d(TAG, "Connection initiated with ${connectionInfo.endpointName}")
            connectionsClient.acceptConnection(endpointId, payloadCallback)
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            if (result.status.isSuccess) {
                isConnected = true
                val username = intent.getStringExtra("USERNAME") ?: "Unknown"

                // Add endpoint and username to the map (but don't add to UI yet - we'll wait for ROLE message)
                Log.d(TAG, "Connection successful to endpoint: $endpointId")

                // Send user information including profile image
                val profileData = if (myProfileImageBase64 != null) {
                    "ROLE:$role,USERNAME:$username,PROFILE_IMAGE:$myProfileImageBase64"
                } else {
                    "ROLE:$role,USERNAME:$username"
                }
                
                val payload = Payload.fromBytes(profileData.toByteArray())
                connectionsClient.sendPayload(endpointId, payload)
                    .addOnSuccessListener {
                        Log.d(TAG, "Sent profile data successfully to $endpointId")
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Failed to send profile data to $endpointId: ${e.message}")
                    }

                Log.d(TAG, "Connected to $endpointId. Sent username: $username and profile image.")
            } else {
                Log.e(TAG, "Connection failed to $endpointId with status: ${result.status}")
            }
        }

        override fun onDisconnected(endpointId: String) {
            val username = connectedEndpoints.remove(endpointId)
            if (!username.isNullOrEmpty()) {
                removeUserFromSpinner(username)
            }
            updateParticipantCount()
            
            if (connectedEndpoints.isEmpty()) {
                isConnected = false
                Log.d(TAG, "No more connected endpoints. Setting isConnected to false.")
            }
            
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
                    // Handle messages
                    when {
                        message.startsWith("ROLE:") -> {
                            // Parse message that includes role, username and potentially profile image
                            val parts = message.split(",")
                            val role = parts.find { it.startsWith("ROLE:") }?.removePrefix("ROLE:")
                            val username = parts.find { it.startsWith("USERNAME:") }?.removePrefix("USERNAME:")
                            val profileImage = parts.find { it.startsWith("PROFILE_IMAGE:") }?.removePrefix("PROFILE_IMAGE:")

                            if (role == "Non Signers" && username != null) {
                                Log.d(TAG, "Parsed username: $username with role: $role from endpoint: $endpointId")
                                // Add to connected endpoints map and update UI
                                connectedEndpoints[endpointId] = username
                                addUserToSpinner(username, endpointId, profileImage)
                                updateParticipantCount()
                                
                                // Set the isConnected flag to true if we have at least one connection
                                if (!isConnected && connectedEndpoints.isNotEmpty()) {
                                    isConnected = true
                                    Log.d(TAG, "At least one endpoint connected. Setting isConnected to true.")
                                }
                            } else {
                                Log.e(TAG, "Invalid payload format or incompatible role: $message")
                            }
                        }
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
                        message.startsWith("COMPLETE_TRANSLATION:") -> {
                            val parts = message.split(":", limit = 3)
                            if (parts.size == 3) {
                                val sender = parts[1] 
                                val completeSentence = parts[2]
                                
                                // Skip displaying messages from the current user
                                val currentUsername = intent.getStringExtra("USERNAME") ?: "Unknown"
                                if (sender == currentUsername) {
                                    Log.d(TAG, "Skipped displaying own completed translation")
                                    return@onPayloadReceived
                                }
                                
                                // Format the message for display in chat
                                val timestamp = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date())
                                val formattedMessage = "[$timestamp] 📝 $sender completed translation: $completeSentence"
                                
                                // Add prediction to chat history
                                updateTextViewAndPlayVideo(formattedMessage)
                                
                                Log.d(TAG, "Displayed complete translation from $sender: $completeSentence")
                            } else {
                                Log.e(TAG, "Invalid COMPLETE_TRANSLATION message format: $message")
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
                        message.startsWith("ALERT:") -> {
                            // Parse alert format: ALERT:<target>:<content>
                            val parts = message.split(":", limit = 3)
                            if (parts.size == 3) {
                                val targetUser = parts[1]
                                val alertContent = parts[2]
                                val username = intent.getStringExtra("USERNAME") ?: "Unknown"

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
                        message.contains("📝") && message.contains("completed translation:") -> {
                            // Only pass through completed translation messages that are from other users
                            val sender = message.substringAfter("[").substringAfter("]").trim().substringAfter("📝").trim().substringBefore("completed")
                            val currentUsername = intent.getStringExtra("USERNAME") ?: "Unknown"
                            
                            if (sender.trim() == currentUsername) {
                                Log.d(TAG, "Skipped displaying own completed translation from message handler")
                                return@onPayloadReceived
                            } else {
                                // For other users' messages, pass to the general handler
                                updateTextViewAndPlayVideo(message)
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

            if (update.status == PayloadTransferUpdate.Status.SUCCESS) {
                Log.d(TAG, "Payload transfer successfully completed for endpoint: $endpointId")
            } else if (update.status == PayloadTransferUpdate.Status.FAILURE) {
                Log.e(TAG, "Payload transfer failed for endpoint: $endpointId")
                
                // Check if this failure means we should consider the endpoint disconnected
                if (connectedEndpoints.containsKey(endpointId)) {
                    Log.d(TAG, "Endpoint $endpointId is no longer connected after payload failure")
                    val username = connectedEndpoints.remove(endpointId)
                    if (username != null) {
                        removeUserFromSpinner(username)
                        updateParticipantCount()
                    }
                    
                    // Update isConnected flag if needed
                    if (connectedEndpoints.isEmpty()) {
                        isConnected = false
                        Log.d(TAG, "No more connected endpoints after payload failure. Setting isConnected to false.")
                    }
                }
            }
        }
    }

    private fun displayPrediction(sender: String, prediction: String) {
        runOnUiThread {
            val predictedSignTextView = findViewById<TextView>(R.id.predictedSignTextView)
            
            // Check if the prediction is empty (after a RESET)
            if (prediction.isBlank()) {
                predictedSignTextView.text = "Waiting for sign detection..."
                
                // Hide the video view since there's nothing to display
                viewBinding.videoView.visibility = View.GONE
                
                // Make sure wrong sign button is still visible for next prediction
                viewBinding.wrongSignButton.visibility = View.VISIBLE
                viewBinding.wrongSignButton.bringToFront()
                
                Log.d(TAG, "Cleared prediction display after receiving empty prediction from $sender")
                return@runOnUiThread
            }
            
            // Format with the sender information if available
            val formattedPrediction = if (sender.isNotEmpty() && sender != "Unknown") {
                "Current sign: $prediction (from $sender)"
            } else {
                "Current sign: $prediction"
            }
            
            predictedSignTextView.text = formattedPrediction
            
            // Make sure the TextView is visible
            predictedSignTextView.visibility = View.VISIBLE
            
            // Animate the text slightly to draw attention
            predictedSignTextView.alpha = 0.7f
            predictedSignTextView.animate().alpha(1.0f).setDuration(300).start()
            
            Log.d(TAG, "Displayed prediction from $sender: $prediction")
            
            // Play video corresponding to the prediction immediately
            try {
                // Always stop any currently playing video
                if (viewBinding.videoView.isPlaying) {
                    viewBinding.videoView.stopPlayback()
                }
                
                // Format the prediction for video lookup
                val formattedPrediction = prediction.lowercase().trim().replace(" ", "_")
                
                // Find the appropriate video for this prediction
                val phrases = splitIntoKnownPhrases(formattedPrediction)
                
                if (phrases.isNotEmpty()) {
                    // Get just the first phrase to show immediately
                    val firstPhrase = phrases[0]
                    val videoResId = videoMap[firstPhrase] ?: getAlphabetVideos(firstPhrase)
                    
                    if (videoResId != null) {
                        // Play the video immediately
                        val videoUri = Uri.parse("android.resource://$packageName/$videoResId")
                        Log.d(TAG, "IMMEDIATELY playing video for phrase: $firstPhrase, URI: $videoUri")
                        
                        viewBinding.videoView.setVideoURI(videoUri)
                        viewBinding.videoView.visibility = View.VISIBLE
                        viewBinding.videoView.start()
                        
                        // Store the full phrase list in case we need it later
                        wordList = phrases
                        currentIndex = 1  // We've played the first one already
                    } else {
                        Log.e(TAG, "No video resource found for phrase: $firstPhrase")
                        viewBinding.videoView.visibility = View.GONE
                    }
                } else {
                    Log.d(TAG, "No phrases found for prediction: $prediction")
                    viewBinding.videoView.visibility = View.GONE
                }
                
                // Make sure wrong sign button is visible
                viewBinding.wrongSignButton.visibility = View.VISIBLE
                viewBinding.wrongSignButton.bringToFront()
            } catch (e: Exception) {
                Log.e(TAG, "Error processing prediction for video: ${e.message}", e)
                viewBinding.videoView.visibility = View.GONE
            }
            
            // Optional vibration feedback
            try {
                val vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    vibrator.vibrate(100)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error with vibration: ${e.message}")
            }
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
                // Format message with timestamp
                val timestamp = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date())
                val displayMessage: String
                val actualMessage: String
                val formattedMessage: String
                val currentUsername = intent.getStringExtra("USERNAME") ?: "Unknown"

                // Parse the message based on its format
                when {
                    message.contains("📝") && message.contains("completed translation:") -> {
                        // Special format for translation completions
                        val sender = message.substringAfter("[").substringAfter("]").trim().substringAfter("📝").trim().substringBefore("completed")
                        val content = message.substringAfter("completed translation:").trim()
                        
                        // Only show completed translations from other users, not from current user
                        if (sender.trim() == currentUsername) {
                            // Skip displaying own messages
                            return@runOnUiThread
                        }
                        
                        displayMessage = "[$timestamp] <div style='text-align: left; color: #006400;'><b>$sender completed translation:</b> $content 📝</div>"
                        actualMessage = content
                        formattedMessage = "$sender completed translation: $content 📝"
                    }
                    message.startsWith("BROADCAST:") -> {
                        val parts = message.removePrefix("BROADCAST:").split(":", limit = 2)
                        if (parts.size == 2) {
                            val sender = parts[0]
                            actualMessage = parts[1]
                            // Check if this is a message from the current user
                            if (sender == currentUsername) {
                                displayMessage = "[$timestamp] <div style='text-align: right; color: #4B1F4E;'><b>You:</b> $actualMessage 📢</div>"
                            } else {
                                displayMessage = "[$timestamp] <div style='text-align: left;'><b>$sender:</b> $actualMessage 📢</div>"
                            }
                            formattedMessage = "$sender: $actualMessage 📢"
                        } else {
                            throw IllegalArgumentException("Invalid broadcast message format: $message")
                        }
                    }
                    message.contains("→") -> {
                        val parts = message.split("→", limit = 2)
                        val sender = parts[0].trim()
                        val recipientMessage = parts[1].trim()
                        val recipientParts = recipientMessage.split(":", limit = 2)
                        val recipient = recipientParts[0].trim()
                        actualMessage = recipientParts.getOrElse(1) { "" }.trim()
                        
                        // Check if this is a message from the current user
                        if (sender == currentUsername) {
                            displayMessage = "[$timestamp] <div style='text-align: right; color: #4B1F4E;'><b>You → $recipient:</b> $actualMessage 🔹</div>"
                        } else {
                            displayMessage = "[$timestamp] <div style='text-align: left;'><b>$sender → $recipient:</b> $actualMessage 🔹</div>"
                        }
                        formattedMessage = "$sender → $recipient: $actualMessage 🔹"
                    }
                    message.contains(":") -> {
                        val parts = message.split(":", limit = 2)
                        val sender = parts[0].trim()
                        actualMessage = parts[1].trim()
                        
                        // Check if this is a message from the current user
                        if (sender == currentUsername) {
                            displayMessage = "[$timestamp] <div style='text-align: right; color: #4B1F4E;'><b>You:</b> $actualMessage 💬</div>"
                        } else {
                            displayMessage = "[$timestamp] <div style='text-align: left;'><b>$sender:</b> $actualMessage 💬</div>"
                        }
                        formattedMessage = "$sender: $actualMessage 💬"
                    }
                    else -> {
                        // System messages or other formats
                        displayMessage = "[$timestamp] <div style='text-align: center; color: #888888;'>$message</div>"
                        actualMessage = message
                        formattedMessage = message
                    }
                }

                // Add message to history with line breaks
                if (messageHistory.isNotEmpty()) {
                    messageHistory.append("\n")
                }
                messageHistory.append(displayMessage)

                // Update TextView with the full message history
                        viewBinding.textView.visibility = View.VISIBLE
                
                // Use HTML formatting to support text alignment
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                    viewBinding.textView.text = android.text.Html.fromHtml(messageHistory.toString(), android.text.Html.FROM_HTML_MODE_COMPACT)
                } else {
                    @Suppress("DEPRECATION")
                    viewBinding.textView.text = android.text.Html.fromHtml(messageHistory.toString())
                }

                // Enhanced scrolling to ensure the latest messages are always visible
                val scrollView = findViewById<ScrollView>(R.id.messageScrollView)
                
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

                // Stop any currently playing video before processing the new message
                if (viewBinding.videoView.isPlaying) {
                    viewBinding.videoView.stopPlayback()
                }
                
                // Clear any pending videos
                wordList = emptyList()
                currentIndex = 0
                
                // Process the message for immediate playback
                try {
                    // Get just the first phrase to show immediately
                    val formattedInput = actualMessage.lowercase().replace(" ", "_")
                    val phrases = splitIntoKnownPhrases(formattedInput)
                    
                    if (phrases.isNotEmpty()) {
                        val firstPhrase = phrases[0]
                        val videoResId = videoMap[firstPhrase] ?: getAlphabetVideos(firstPhrase)
                        
                        if (videoResId != null) {
                            // Play the video immediately
                            val videoUri = Uri.parse("android.resource://$packageName/$videoResId")
                            Log.d(TAG, "IMMEDIATELY playing video for phrase: $firstPhrase, URI: $videoUri")
                            
                            viewBinding.videoView.setVideoURI(videoUri)
                            viewBinding.videoView.visibility = View.VISIBLE
                            viewBinding.videoView.start()
                            
                            // Store the full phrase list
                            wordList = phrases
                            currentIndex = 1  // We've played the first one already
                        } else {
                            Log.e(TAG, "No video resource found for phrase: $firstPhrase")
                            viewBinding.videoView.visibility = View.GONE
                        }
                    } else {
                        Log.d(TAG, "No phrases found for message: $actualMessage")
                        viewBinding.videoView.visibility = View.GONE
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error playing video for message: ${e.message}", e)
                    // Traditional fallback method in case of error
                    processMessageForVideo(actualMessage)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing message: $message", e)
                viewBinding.textView.text = "Error: Unable to display message."
                viewBinding.textView.visibility = View.VISIBLE
            }
        }
    }

    private fun processMessageForVideo(message: String) {
        // Stop any currently playing video
        if (viewBinding.videoView.isPlaying) {
            viewBinding.videoView.stopPlayback()
        }
        
        val formattedInput = message.lowercase().replace(" ", "_")
        wordList = splitIntoKnownPhrases(formattedInput)
        currentIndex = 0

        if (wordList.isNotEmpty()) {
            playNextVideo()
        } else {
            viewBinding.videoView.visibility = View.GONE
        }
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
        isActivityDestroyed = true
    }

    private fun sendMessageToNonDeafUser(message: String, targetUser: String) {
        // Check if we have any connections first
        if (connectedEndpoints.isEmpty()) {
            Toast.makeText(this, "No connected users to send messages to", Toast.LENGTH_SHORT).show()
            return
        }
        
        when (targetUser) {
            "None" -> {
                Toast.makeText(this, "Please select a user to send the message to", Toast.LENGTH_SHORT).show()
            }
            "All" -> {
                // Send to all connected users
                val username = intent.getStringExtra("USERNAME") ?: "Unknown"
                val formattedMessage = "BROADCAST:$username:$message"
                var sent = false
                
                for (endpointId in connectedEndpoints.keys) {
                    connectionsClient.sendPayload(endpointId, Payload.fromBytes(formattedMessage.toByteArray()))
                        .addOnSuccessListener { 
                            Log.d(TAG, "Message sent to all: $message")
                            sent = true
                        }
                        .addOnFailureListener { e -> 
                            Log.e(TAG, "Failed to send message to $endpointId", e) 
                        }
                }
                
                // Update local display regardless of success to show what we tried to send
                updateTextViewAndPlayVideo("$username:$message")
                
                if (connectedEndpoints.isEmpty()) {
                    Toast.makeText(this, "No connected users to broadcast to", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Message sent to all participants", Toast.LENGTH_SHORT).show()
                }
            }
            else -> {
                // Send to specific user
                val username = intent.getStringExtra("USERNAME") ?: "Unknown"
                // Find the endpoint ID for the target user
                val targetEndpointId = connectedEndpoints.entries.find { it.value == targetUser }?.key
                
                if (targetEndpointId != null) {
                    // Using TARGET: format for compatibility with both activities
                    val formattedMessage = "TARGET:$targetUser:$username:$message"
                    connectionsClient.sendPayload(targetEndpointId, Payload.fromBytes(formattedMessage.toByteArray()))
                        .addOnSuccessListener { 
                            Log.d(TAG, "Message sent to $targetUser: $message") 
                            // Update local display
                            updateTextViewAndPlayVideo("$username → $targetUser: $message")
                            Toast.makeText(this, "Message sent to $targetUser", Toast.LENGTH_SHORT).show()
                        }
                        .addOnFailureListener { e -> 
                            Log.e(TAG, "Failed to send message to $targetUser", e)
                            // Still update the local display to show what we tried to send 
                            updateTextViewAndPlayVideo("$username → $targetUser: $message (failed to send)")
                            Toast.makeText(this, "Failed to send message to $targetUser", Toast.LENGTH_SHORT).show()
                        }
                } else {
                    Log.e(TAG, "User $targetUser not found in connected endpoints: $connectedEndpoints")
                    // Update with a failure message
                    updateTextViewAndPlayVideo("$username → $targetUser: $message (user not found)")
                    Toast.makeText(this, "User $targetUser not found", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
