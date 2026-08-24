package com.ejemplo.emulador

import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.Color
import android.net.Uri
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {

    private var keyState: Int = 0x0FFF
    private val touchDigitizer = TouchDigitizer()
    private val audioEngine = AudioEngine(32828)
    private var gameLoop: GameLoop? = null
    private lateinit var prefs: SharedPreferences

    private var isPlaying = false

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
    private lateinit var layoutEmulator: View
    private lateinit var layoutMainMenu: View
    private lateinit var btnContinue: TextView

    private val romPickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { cargarRomDesdeUri(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences("FirstDS_Prefs", MODE_PRIVATE)

        val nativeDir = applicationInfo.nativeLibraryDir
        NativeBridge.nativeInit(nativeDir)

        bindViewsAndSurfaces()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isPlaying) {
                    mostrarMenu()
                } else {
                    finish()
                }
            }
        })
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        setContentView(R.layout.activity_main)
        bindViewsAndSurfaces()
        if (isPlaying) {
            layoutEmulator.visibility = View.VISIBLE
            layoutMainMenu.visibility = View.GONE
        } else {
            mostrarMenu()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun bindViewsAndSurfaces() {
        layoutEmulator = findViewById(R.id.layout_emulator)
        layoutMainMenu = findViewById(R.id.layout_main_menu)

        glScreenTop = findViewById(R.id.gl_screen_top)
        glScreenBottom = findViewById(R.id.gl_screen_bottom)

        glScreenTop.setEGLContextClientVersion(2)
        glScreenTop.setRenderer(EmulatorRenderer(isTopScreen = true))
        glScreenTop.renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY

        glScreenBottom.setEGLContextClientVersion(2)
        glScreenBottom.setRenderer(EmulatorRenderer(isTopScreen = false))
        glScreenBottom.renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY

        if (gameLoop == null) {
            gameLoop = GameLoop(
                glTop = glScreenTop,
                glBottom = glScreenBottom,
                audioEngine = audioEngine,
                getKeyMask = { keyState },
                getTouchState = { touchDigitizer }
            )
        } else {
            gameLoop?.updateSurfaces(glScreenTop, glScreenBottom)
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

        setupMenuControls()
    }

    private fun setupMenuControls() {
        btnContinue = findViewById(R.id.btn_menu_continue)
        val btnNewGame = findViewById<TextView>(R.id.btn_menu_new_game)
        val btnOptions = findViewById<TextView>(R.id.btn_menu_options)
        val btnExit = findViewById<TextView>(R.id.btn_menu_exit)
        val btnHelp = findViewById<TextView?>(R.id.btn_top_help)

        actualizarBotonContinuar()

        btnContinue.setOnClickListener {
            val lastPath = prefs.getString("last_rom_path", null)
            if (lastPath != null && File(lastPath).exists()) {
                iniciarEmulacion()
            }
        }

        btnNewGame.setOnClickListener {
            romPickerLauncher.launch(arrayOf("*/*"))
        }

        btnOptions.setOnClickListener {
            Toast.makeText(this, "Opciones próximamente", Toast.LENGTH_SHORT).show()
        }

        btnHelp?.setOnClickListener {
            Toast.makeText(this, "FirstDS - melonDS Core", Toast.LENGTH_LONG).show()
        }

        btnExit.setOnClickListener {
            finish()
        }
    }

    private fun actualizarBotonContinuar() {
        val lastPath = prefs.getString("last_rom_path", null)
        if (lastPath != null && File(lastPath).exists()) {
            btnContinue.setTextColor(Color.parseColor("#4F357E"))
            btnContinue.isClickable = true
        } else {
            btnContinue.setTextColor(Color.parseColor("#C2BFCC"))
            btnContinue.isClickable = false
        }
    }

    private fun iniciarEmulacion() {
        isPlaying = true
        layoutMainMenu.visibility = View.GONE
        layoutEmulator.visibility = View.VISIBLE
        audioEngine.start()
        gameLoop?.start()
    }

    private fun mostrarMenu() {
        isPlaying = false
        gameLoop?.stop()
        audioEngine.stop()
        layoutEmulator.visibility = View.GONE
        layoutMainMenu.visibility = View.VISIBLE
        actualizarBotonContinuar()
    }

    private fun cargarRomDesdeUri(uri: Uri) {
        try {
            Toast.makeText(this, "Cargando cartucho NDS...", Toast.LENGTH_SHORT).show()
            val tempRom = File(filesDir, "current_game.nds")
            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempRom).use { output ->
                    input.copyTo(output)
                }
            }

            prefs.edit().putString("last_rom_path", tempRom.absolutePath).apply()

            val romResult = NativeBridge.nativeLoadRom(tempRom.absolutePath)
            Toast.makeText(this, romResult, Toast.LENGTH_SHORT).show()

            iniciarEmulacion()
        } catch (e: Exception) {
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onResume() {
        super.onResume()
        glScreenTop.onResume()
        glScreenBottom.onResume()
        if (isPlaying) {
            audioEngine.start()
            gameLoop?.start()
        }
    }

    override fun onPause() {
        super.onPause()
        gameLoop?.stop()
        audioEngine.stop()
        glScreenTop.onPause()
        glScreenBottom.onPause()
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
