package com.example.komunikaprototype

import android.content.Context
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.VideoView
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView

class VideoListAdapter(
    private val context: Context,
    private val items: List<String>,
    private val videoMap: Map<String, Int>?
) : RecyclerView.Adapter<VideoListAdapter.VideoViewHolder>() {

    class VideoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val itemText: TextView = itemView.findViewById(R.id.item_text)
        val itemVideo: VideoView = itemView.findViewById(R.id.item_video)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VideoViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.list_item_with_video, parent, false)
        return VideoViewHolder(view)
    }

    override fun onBindViewHolder(holder: VideoViewHolder, position: Int) {
        val item = items[position]
        holder.itemText.text = item

        val videoResId = videoMap?.get(item)
        if (videoResId != null) {
            val videoUri = Uri.parse("android.resource://${context.packageName}/$videoResId")
            holder.itemVideo.setVideoURI(videoUri)

            // Hide the media controller (play button)
            holder.itemVideo.setMediaController(null)

            // Set video to loop
            holder.itemVideo.setOnCompletionListener { holder.itemVideo.start() }

            // Automatically start playback when the item is clicked
            holder.itemView.setOnClickListener {
                if (!holder.itemVideo.isVisible) {
                    holder.itemVideo.visibility = View.VISIBLE
                    holder.itemVideo.start()
                } else {
                    holder.itemVideo.visibility = View.GONE
                    holder.itemVideo.stopPlayback()
                }
            }
        } else {
            // Hide VideoView if no video is available for the item
            holder.itemVideo.visibility = View.GONE
        }
    }

    override fun getItemCount(): Int = items.size
}
