package com.example.komunikaprototype

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.komunikaprototype.databinding.VocabListItemsBinding

class ListActivity : AppCompatActivity() {

    private lateinit var binding: VocabListItemsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Use ViewBinding to inflate the layout
        binding = VocabListItemsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Get the title and data from the intent
        val title = intent.getStringExtra("TITLE") ?: "Vocabulary"
        val items = intent.getStringArrayListExtra("ITEMS") ?: arrayListOf()
        val videoMap = intent.getSerializableExtra("VIDEO_MAP") as? Map<String, Int>

        // Set the title
        binding.headerTitle.text = title

        // Set up the RecyclerView with the custom adapter
        val adapter = VideoListAdapter(this, items, videoMap)
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter

        // Handle back button click
        binding.backIcon.setOnClickListener {
            finish() // Close the activity and go back
        }
    }
}
