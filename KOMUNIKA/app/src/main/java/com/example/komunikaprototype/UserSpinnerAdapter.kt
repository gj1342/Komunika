package com.example.komunikaprototype

import android.content.Context
import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat

/**
 * Custom adapter for displaying users with profile images in a spinner
 */
class UserSpinnerAdapter(
    context: Context,
    resource: Int,
    private val users: List<UserWithImage>
) : ArrayAdapter<UserWithImage>(context, resource, users) {

    private val inflater: LayoutInflater = LayoutInflater.from(context)
    
    override fun getCount(): Int = users.size
    
    override fun getItem(position: Int): UserWithImage = users[position]
    
    // This view is shown in the spinner dropdown
    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
        return createItemView(position, convertView, parent)
    }
    
    // This view is shown when the spinner is collapsed
    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        return createItemView(position, convertView, parent)
    }
    
    private fun createItemView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: inflater.inflate(R.layout.spinner_item_user, parent, false)
        
        val userImage = view.findViewById<ImageView>(R.id.userImageView)
        val userName = view.findViewById<TextView>(R.id.userNameTextView)
        
        val user = users[position]
        
        // Set username
        userName.text = user.username
        
        try {
            // Set profile image if available, otherwise use default
            val profileBitmap = user.getProfileBitmap()
            if (profileBitmap != null) {
                userImage.setImageBitmap(profileBitmap)
            } else {
                // Default profile image
                ContextCompat.getDrawable(context, R.drawable.profile)?.let {
                    userImage.setImageDrawable(it)
                } ?: run {
                    // Fallback to a colored background if drawable not found
                    userImage.setBackgroundColor(ContextCompat.getColor(context, android.R.color.darker_gray))
                }
            }
        } catch (e: Exception) {
            // Handle any errors loading images
            android.util.Log.e("UserSpinnerAdapter", "Error setting profile image: ${e.message}")
            // Fallback to a colored background
            userImage.setBackgroundColor(ContextCompat.getColor(context, android.R.color.darker_gray))
        }
        
        return view
    }
} 