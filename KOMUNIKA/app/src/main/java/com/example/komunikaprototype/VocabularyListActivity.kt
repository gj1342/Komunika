package com.example.komunikaprototype

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import de.hdodenhof.circleimageview.CircleImageView
import java.io.BufferedReader
import java.io.InputStreamReader

class VocabularyListActivity : AppCompatActivity() {

    private lateinit var profileImageView: CircleImageView
    private lateinit var usernameTextView: TextView
    private lateinit var deviceIdTextView: TextView
    private lateinit var userTypeTextView: TextView
    private lateinit var buttons: List<Button>

    // Video maps for each category
    private val alphabetsVideoMap = hashMapOf(
        "a" to R.raw.a, "b" to R.raw.b, "c" to R.raw.c, "d" to R.raw.d,
        "f" to R.raw.f, "g" to R.raw.g, "h" to R.raw.h, "i" to R.raw.i, "j" to R.raw.j,
        "k" to R.raw.k, "l" to R.raw.l, "m" to R.raw.m, "u" to R.raw.u,
        "e" to R.raw.e, "n" to R.raw.n, "o" to R.raw.o,
        "p" to R.raw.p, "q" to R.raw.q, "r" to R.raw.r, "s" to R.raw.s, "t" to R.raw.t,
        "v" to R.raw.v, "w" to R.raw.w, "x" to R.raw.x, "y" to R.raw.y,
        "z" to R.raw.z
    )

    private val calendarVideoMap = hashMapOf(
        "january" to R.raw.january, "february" to R.raw.february, "march" to R.raw.march,
        "april" to R.raw.april, "may" to R.raw.may, "june" to R.raw.june,
        "july" to R.raw.july, "august" to R.raw.august, "september" to R.raw.september, "october" to R.raw.october,
        "november" to R.raw.november, "december" to R.raw.december
    )

    private val numbersVideoMap = hashMapOf(
        "8" to R.raw.eight, "5" to R.raw.five, "4" to R.raw.four,
        "9" to R.raw.nine, "1" to R.raw.one, "7" to R.raw.seven,
        "6" to R.raw.six, "10" to R.raw.ten, "3" to R.raw.three,
        "20" to R.raw.twenty, "2" to R.raw.two,
        "30" to R.raw.thirty, "40" to R.raw.forty, "50" to R.raw.fifty,
        "60" to R.raw.sixty, "70" to R.raw.seventy, "80" to R.raw.eighty,
        "90" to R.raw.ninety, "100" to R.raw.one_hundred
    )

    private val greetingsVideoMap = hashMapOf(
        "hello" to R.raw.hi_hello,
        "good evening" to R.raw.good_evening, "good afternoon" to R.raw.good_afternoon,
        "good morning" to R.raw.good_morning
    )

    private val responsesVideoMap = hashMapOf(
        "correct" to R.raw.correct, "fast" to R.raw.fast, "fine" to R.raw.fine, "good" to R.raw.good,
        "no" to R.raw.no, "slow" to R.raw.slow, "wrong" to R.raw.wrong, "yes" to R.raw.yes
    )

    private val familyVideoMap = hashMapOf(
        "auntie" to R.raw.auntie, "baby" to R.raw.baby, "brother" to R.raw.brother, "cousin" to R.raw.cousin,
        "grandfather" to R.raw.grandfather,
        "grandmother" to R.raw.grandmother,
        "mama" to R.raw.mother, "papa" to R.raw.father,
        "sister" to R.raw.sister,
        "uncle" to R.raw.uncle
    )

    private val colorsVideoMap = hashMapOf(
        "black" to R.raw.black, "blue" to R.raw.blue, "brown" to R.raw.brown, "gray" to R.raw.gray,
        "green" to R.raw.green, "orange" to R.raw.orange, "red" to R.raw.red, "violet" to R.raw.violet_purple,
        "white" to R.raw.white, "yellow" to R.raw.yellow
    )

    private  val pronounsVideoMap = hashMapOf(
        "i" to R.raw.i_am, "you" to R.raw.you, "he/she" to R.raw.s_he, "they/you (kayo)" to R.raw.you_kayo,
        "we (tayo)" to R.raw.we, "we (kami)" to R.raw.we_kami
    )

    private val nounsVideoMap = hashMapOf(
        "name" to R.raw.name, "friend" to R.raw.friend, "birthday" to R.raw.birthday, "age" to R.raw.age
    )

    private val verbsVideoMap = hashMapOf(
        "again" to R.raw.again, "eat" to R.raw.eat, "let" to R.raw.let,
        "meet" to R.raw.meet, "live" to R.raw.live, "see" to R.raw.see, "say" to R.raw.say,
        "read" to R.raw.read, "teach" to R.raw.teach
    )

    private val schoolVideoMap = hashMapOf(
        "book" to R.raw.book, "class" to R.raw.class_, "lecture" to R.raw.lecture,
        "paper" to R.raw.paper, "pencil" to R.raw.pencil, "school" to R.raw.school,
        "teacher" to R.raw.teacher
    )

    private val weeksVideoMap = hashMapOf(
        "sunday" to R.raw.sunday, "monday" to R.raw.monday, "tuesday" to R.raw.tuesday,
        "wednesday" to R.raw.wednesday, "thursday" to R.raw.thursday, "friday" to R.raw.friday,
        "saturday" to R.raw.saturday
    )

    private val timeVideoMap = hashMapOf(
        "tomorrow" to R.raw.tomorrow, "now" to R.raw.now, "today" to R.raw.today,
        "yesterday" to R.raw.yesterday
    )

    private val questionsVideoMap = hashMapOf(
        "how" to R.raw.how, "how many" to R.raw.how_many, "how much" to R.raw.how_much, "what" to R.raw.what,
        "when" to R.raw.`when`, "where" to R.raw.where, "which" to R.raw.which, "who" to R.raw.who,
        "why" to R.raw.why
    )

    private val phrasesVideoMap = hashMapOf(
        "excuse me" to R.raw.excuse_me,
        "goodbye" to R.raw.goodbye,
        "i dont know" to R.raw.i_dont_know,
        "i dont understand" to R.raw.i_dont_understand, "i know" to R.raw.i_know,
        "i understand" to R.raw.i_understand,
        "please" to R.raw.please, "sorry" to R.raw.sorry, "thank you" to R.raw.thank_you,
        "youre welcome" to R.raw.your_welcome
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.vocabulary_list)

        // Back Button: Return to previous activity when clicked
        val backButton: ImageView = findViewById(R.id.back_icon)
        backButton.setOnClickListener {
            finish()
        }

        // Initialize UI components
        profileImageView = findViewById(R.id.profile_picture)
        usernameTextView = findViewById(R.id.username)
        deviceIdTextView = findViewById(R.id.device_id)
        userTypeTextView = findViewById(R.id.user_type)

        buttons = listOf(
            findViewById(R.id.button_1),
            findViewById(R.id.button_2),
            findViewById(R.id.button_3),
            findViewById(R.id.button_4),
            findViewById(R.id.button_5),
            findViewById(R.id.button_6),
            findViewById(R.id.button_7),
            findViewById(R.id.button_8),
            findViewById(R.id.button_9),
            findViewById(R.id.button_10),
            findViewById(R.id.button_11),
            findViewById(R.id.button_12),
            findViewById(R.id.button_13),
            findViewById(R.id.button_14),
            findViewById(R.id.button_15)
        )

        // Load user profile data
        loadUserData()

        // Set up click listeners for each button
        buttons.forEachIndexed { index, button ->
            button.setOnClickListener {
                navigateToListActivity(index)
            }
        }
    }

    private fun loadUserData() {
        val sharedPreferences = getSharedPreferences("UserProfile", Context.MODE_PRIVATE)
        val username = sharedPreferences.getString("username", "Username")
        val userType = sharedPreferences.getString("userType", "Non mute") // Default userType
        val deviceId = "Device ID: ${android.os.Build.ID}"
        val profileImageUri = sharedPreferences.getString("profileImage", null)

        usernameTextView.text = username
        deviceIdTextView.text = deviceId
        userTypeTextView.text = "User Type: $userType"

        profileImageUri?.let {
            profileImageView.setImageURI(Uri.parse(it))
        }
    }

    private fun navigateToListActivity(categoryIndex: Int) {
        val categoryData = getCategoryData(categoryIndex)
        val intent = Intent(this, ListActivity::class.java)
        intent.putExtra("TITLE", categoryData.first)
        intent.putStringArrayListExtra("ITEMS", ArrayList(categoryData.second))
        intent.putExtra("VIDEO_MAP", HashMap(categoryData.third))
        startActivity(intent)
    }

    private fun getCategoryData(index: Int): Triple<String, List<String>, Map<String, Int>> {
        val categories = listOf(
            "alphabets.txt",
            "numbers.txt",
            "greetings_labels.txt",
            "responses_labels.txt",
            "family_labels.txt",
            "colors_labels.txt",
            "pronouns.txt",
            "nouns_labels.txt",
            "verbs.txt",
            "school_labels.txt",
            "calendars.txt",
            "weeks_labels.txt",
            "time.txt",
            "questions_labels.txt",
            "phrases_labels.txt"
        )

        val categoryTitle = when (index) {
            0 -> "ALPHABETS"
            1 -> "NUMBERS"
            2 -> "GREETINGS"
            3 -> "RESPONSES"
            4 -> "FAMILY"
            5 -> "COLORS"
            6 -> "PRONOUNS"
            7 -> "NOUNS"
            8 -> "VERBS"
            9 -> "SCHOOL"
            10 -> "CALENDAR"
            11 -> "WEEKS"
            12 -> "TIME"
            13 -> "QUESTIONS"
            14 -> "PHRASES"
            else -> "Unknown"
        }

        val labels = readLabelsFromAssets(categories.getOrNull(index) ?: return Triple(categoryTitle, emptyList(), emptyMap()))
        val videoMap = when (index) {
            0 -> alphabetsVideoMap
            1 -> numbersVideoMap
            2 -> greetingsVideoMap
            3 -> responsesVideoMap
            4 -> familyVideoMap
            5 -> colorsVideoMap
            6 -> pronounsVideoMap
            7 -> nounsVideoMap
            8 -> verbsVideoMap
            9 -> schoolVideoMap
            10 -> calendarVideoMap
            11 -> weeksVideoMap
            12 -> timeVideoMap
            13 -> questionsVideoMap
            14 -> phrasesVideoMap
            else -> emptyMap()
        }
        return Triple(categoryTitle, labels, videoMap)
    }

    private fun readLabelsFromAssets(fileName: String): List<String> {
        val labels = mutableListOf<String>()
        try {
            val inputStream = assets.open(fileName)
            val reader = BufferedReader(InputStreamReader(inputStream))
            reader.use {
                it.forEachLine { line ->
                    labels.add(line.trim().lowercase()) // Convert each label to lowercase
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return labels
    }
}
