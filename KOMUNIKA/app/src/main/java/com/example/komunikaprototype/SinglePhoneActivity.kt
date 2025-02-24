package com.example.komunikaprototype

import android.net.Uri
import android.os.Bundle
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.VideoView
import androidx.appcompat.app.AppCompatActivity

class SinglePhoneActivity : AppCompatActivity() {

    private lateinit var backIcon: ImageView
    private lateinit var videoView: VideoView
    private lateinit var inputText: EditText
    private lateinit var playButton: ImageButton
    private lateinit var displayTextView: TextView // Display the current message
    private lateinit var chatHistoryTextView: TextView // Display the chat history

    private val chatHistory = StringBuilder()

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

    private var wordList = listOf<String>()
    private var currentIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.single_phone)

        // Initialize UI components
        backIcon = findViewById(R.id.back_icon)
        videoView = findViewById(R.id.videoView)
        inputText = findViewById(R.id.message_input)
        playButton = findViewById(R.id.sendButton)
        displayTextView = findViewById(R.id.textView)
        chatHistoryTextView = findViewById(R.id.chatHistoryTextView)

        // Set backIcon click listener to simply finish the activity
        backIcon.setOnClickListener {
            finish()
        }

        // Set playButton click listener
        playButton.setOnClickListener {
            val input = inputText.text.toString().trim()
            if (input.isNotBlank()) {
                // Append to chat history
                chatHistory.append(input).append("\n")
                chatHistoryTextView.text = chatHistory.toString()

                // Display the input text in the TextView
                displayTextView.text = input
                inputText.text.clear()

                // Split the input into words, replacing spaces with underscores
                val formattedInput = input.lowercase().replace(" ", "_")
                wordList = splitIntoKnownPhrases(formattedInput)
                currentIndex = 0
                playNextVideo()
            }
        }

        // Set videoView completion listener to play next video
        videoView.setOnCompletionListener { playNextVideo() }
    }

    private fun playNextVideo() {
        while (currentIndex < wordList.size) {
            val phrase = wordList[currentIndex]

            // Check both videoMap and alphabetVideoMap
            val videoResId = videoMap[phrase] ?: getAlphabetVideos(phrase)

            if (videoResId != null) {
                try {
                    val videoUri = Uri.parse("android.resource://$packageName/$videoResId")
                    videoView.setVideoURI(videoUri)
                    videoView.start()
                    currentIndex++ // Increment only if a video is found
                    return // Exit the method to wait for video completion
                } catch (e: Exception) {
                    // Log error and move to the next video
                    currentIndex++
                }
            } else {
                // Log if no video resource is found
                currentIndex++
            }
        }

        // If all videos are finished
        if (currentIndex >= wordList.size) {
            videoView.stopPlayback() // Stop video playback
        }
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
}
