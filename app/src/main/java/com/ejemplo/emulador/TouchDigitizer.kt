package com.ejemplo.emulador

import android.view.MotionEvent
import android.view.View

class TouchDigitizer {

    var isTouching: Boolean = false
        private set
    var dsX: Int = 0
        private set
    var dsY: Int = 0
        private set

    fun handleTouchEvent(view: View, event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                val width = view.width.toFloat()
                val height = view.height.toFloat()

                if (width > 0 && height > 0) {
                    // Escalar a coordenadas nativas NDS (0-255 horizontal, 0-191 vertical)
                    val clampedX = event.x.coerceIn(0f, width)
                    val clampedY = event.y.coerceIn(0f, height)

                    dsX = ((clampedX / width) * 255).toInt()
                    dsY = ((clampedY / height) * 191).toInt()
                    isTouching = true
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isTouching = false
                return true
            }
        }
        return false
    }
}
