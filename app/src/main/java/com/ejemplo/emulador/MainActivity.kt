package com.ejemplo.emulador

import android.annotation.SuppressLint
import android.net.Uri
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {

    private var keyState: Int = 0x0FFF
    private val touchDigitizer = TouchDigitizer()
    private var gameLoop: GameLoop? = null

    companion object {
        const val KEY_A = 1 shl 0
        const val KEY_B = 1 shl 1
        const val KEY_SELECT = 1 shl 2
        const val KEY_START = 1 shl 3
        const val KEY_RIGHT = 1 shl 4
        const val KEY_LEFT = 1 shl 5
        const val KEY_UP = 1 shl 6
        const val KEY_DOWN = 1 shl 7
        const val KEY_R = 1 shl 8
        const val KEY_L = 1 shl 9
        const val KEY_X = 1 shl 10
        const val KEY_Y = 1 shl 11
    }

    private lateinit var glScreenTop: GLSurfaceView
    private lateinit var glScreenBottom: GLSurfaceView

    private val romPickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { cargarRom(it) }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        NativeBridge.nativeInit(filesDir.absolutePath)

        glScreenTop = findViewById(R.id.gl_screen_top)
        glScreenBottom = findViewById(R.id.gl_screen_bottom)

        glScreenTop.setEGLContextClientVersion(2)
        glScreenTop.setRenderer(EmulatorRenderer(isTopScreen = true))
        glScreenTop.renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY

        glScreenBottom.setEGLContextClientVersion(2)
        glScreenBottom.setRenderer(EmulatorRenderer(isTopScreen = false))
        glScreenBottom.renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY

        gameLoop = GameLoop(
            glTop = glScreenTop,
            glBottom = glScreenBottom,
            getKeyMask = { keyState },
            getTouchState = { touchDigitizer }
        )

        glScreenTop.setOnClickListener {
            romPickerLauncher.launch(arrayOf("*/*"))
        }

        glScreenBottom.setOnTouchListener { v, event ->
            touchDigitizer.handleTouchEvent(v, event)
        }

        setupButton(R.id.btn_a, KEY_A)
        setupButton(R.id.btn_b, KEY_B)
        setupButton(R.id.btn_x, KEY_X)
        setupButton(R.id.btn_y, KEY_Y)
        setupButton(R.id.btn_l, KEY_L)
        setupButton(R.id.btn_r, KEY_R)
        setupButton(R.id.btn_start, KEY_START)
        setupButton(R.id.btn_select, KEY_SELECT)

        setupDirectionButton(R.id.btn_up, KEY_UP)
        setupDirectionButton(R.id.btn_down, KEY_DOWN)
        setupDirectionButton(R.id.btn_left, KEY_LEFT)
        setupDirectionButton(R.id.btn_right, KEY_RIGHT)
    }

    override fun onResume() {
        super.onResume()
        glScreenTop.onResume()
        glScreenBottom.onResume()
        gameLoop?.start()
    }

    override fun onPause() {
        super.onPause()
        gameLoop?.stop()
        glScreenTop.onPause()
        glScreenBottom.onPause()
    }

    private fun cargarRom(uri: Uri) {
        try {
            Toast.makeText(this, "Leyendo cartucho NDS...", Toast.LENGTH_SHORT).show()
            val tempRom = File(cacheDir, "game.nds")
            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempRom).use { output ->
                    input.copyTo(output)
                }
            }

            val romResult = NativeBridge.nativeLoadRom(tempRom.absolutePath)
            Toast.makeText(this, romResult, Toast.LENGTH_LONG).show()

            gameLoop?.start()
        } catch (e: Exception) {
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupButton(viewId: Int, keyMask: Int) {
        val view = findViewById<View>(viewId) ?: return
        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    v.alpha = 0.5f
                    keyState = keyState and keyMask.inv()
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.alpha = 1.0f
                    keyState = keyState or keyMask
                    true
                }
                else -> false
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupDirectionButton(viewId: Int, keyMask: Int) {
        val view = findViewById<View>(viewId) ?: return
        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    keyState = keyState and keyMask.inv()
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    keyState = keyState or keyMask
                    true
                }
                else -> false
            }
        }
    }
}
