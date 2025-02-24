package com.example.komunikaprototype

import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2

class ViewPagerAdapter(
    private val titles: Array<String>,
    private val images: IntArray,
    private val context: Context,
    private val viewPager: ViewPager2 // ✅ Pass ViewPager reference to track the centered item
) : RecyclerView.Adapter<ViewPagerAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.carousel_item, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val fixedPosition = position % titles.size // ✅ Keeps index within bounds

        holder.itemTitle.text = titles[fixedPosition]
        holder.itemImage.setImageResource(images[fixedPosition])

        // 🔥 Function to handle clicks (for both item and image)
        val handleClick: (View) -> Unit = {
            val currentItem = viewPager.currentItem // ✅ Get the currently centered item
            Log.d("ViewPagerAdapter", "Clicked item position: $position, Current centered item: $currentItem")

            if (position == currentItem) { // ✅ Ensures only the centered item is clickable
                val intent = when (fixedPosition) {
                    0 -> Intent(context, SinglePhoneActivity::class.java)
                    1 -> Intent(context, StartingLobbyActivity::class.java)
                    2 -> Intent(context, VocabularyListActivity::class.java)
                    else -> null
                }
                intent?.let {
                    Log.d("ViewPagerAdapter", "Opening activity for position: $fixedPosition")
                    context.startActivity(it)
                }
            } else {
                Log.d("ViewPagerAdapter", "Click ignored: Item not in the center")
            }
        }

        // ✅ Make the entire item clickable
        holder.itemView.setOnClickListener(handleClick)

        // ✅ Make the image clickable separately
        holder.itemImage.setOnClickListener(handleClick)
    }

    override fun getItemCount(): Int = titles.size // ✅ Ensure only 4 items

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val itemImage: ImageView = itemView.findViewById(R.id.item_image)
        val itemTitle: TextView = itemView.findViewById(R.id.item_title)
    }
}
