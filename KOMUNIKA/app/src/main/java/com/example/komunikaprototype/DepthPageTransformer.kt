package com.example.komunikaprototype

import android.view.View
import androidx.viewpager2.widget.ViewPager2
import kotlin.math.abs

class DepthPageTransformer : ViewPager2.PageTransformer {
    override fun transformPage(view: View, position: Float) {
        // Set the translationZ so that pages closer to the center are drawn on top.
        view.translationZ = 1 - abs(position)

        view.apply {
            when {
                position < -1 -> {
                    // Page is way off-screen to the left
                    alpha = 0f
                }
                position <= 0 -> {
                    // Page is moving in from the left to center.
                    alpha = 1f
                    scaleX = 1f
                    scaleY = 1f
                    translationX = 0f
                }
                position <= 1 -> {
                    // Page is moving from center to right.
                    alpha = 1 - position
                    scaleX = 1 - (0.25f * position)
                    scaleY = 1 - (0.25f * position)
                    translationX = width * -position
                }
                else -> {
                    // Page is way off-screen to the right.
                    alpha = 0f
                }
            }
        }
    }
}
