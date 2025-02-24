package com.example.komunikaprototype

import android.view.View
import androidx.viewpager2.widget.ViewPager2

class DepthPageTransformer : ViewPager2.PageTransformer {
    override fun transformPage(view: View, position: Float) {
        view.apply {
            when {
                position < -1 -> alpha = 0f // Page is way off-screen to the left
                position <= 0 -> {
                    alpha = 1f
                    scaleX = 1f
                    scaleY = 1f
                    translationX = 0f
                }
                position <= 1 -> {
                    alpha = 1 - position
                    scaleX = 1 - (0.25f * position)
                    scaleY = 1 - (0.25f * position)
                    translationX = width * -position
                }
                else -> alpha = 0f // Page is way off-screen to the right
            }
        }
    }
}
