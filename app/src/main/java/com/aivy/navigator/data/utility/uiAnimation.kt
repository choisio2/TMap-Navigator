package com.aivy.navigator.data.utility

import android.view.View

// 뷰를 부드럽게 나타나게 하는 함수
fun View.fadeIn(duration: Long = 300L) {
    this.visibility = View.VISIBLE
    this.alpha = 0f
    this.animate()
        .alpha(1f)
        .setDuration(duration)
        .setListener(null)
}

// 뷰를 부드럽게 사라지게 하는 함수
fun View.fadeOut(duration: Long = 300L) {
    this.animate()
        .alpha(0f)
        .setDuration(duration)
        .withEndAction { this.visibility = View.GONE }
}
