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
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
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
    private var isOptionsMenuOpen = false
    private var isVideoSettingsOpen = false

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
    private lateinit var layoutOptionsMenu: View
    private lateinit var layoutVideoSettings: View
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

        val systemDir = filesDir.absolutePath
        val libDir = applicationInfo.nativeLibraryDir
        NativeBridge.nativeInit(systemDir, libDir)

        bindViewsAndSurfaces()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        when {
            isPlaying -> mostrarMenu()
            isVideoSettingsOpen -> ocultarAjustesVideo()
            isOptionsMenuOpen -> ocultarOpciones()
            else -> super.onBackPressed()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        setContentView(R.layout.activity_main)
        bindViewsAndSurfaces()

        when {
            isPlaying -> {
                layoutEmulator.visibility = View.VISIBLE
                layoutMainMenu.visibility = View.GONE
                layoutOptionsMenu.visibility = View.GONE
                layoutVideoSettings.visibility = View.GONE
            }
            isVideoSettingsOpen -> {
                layoutEmulator.visibility = View.GONE
                layoutMainMenu.visibility = View.GONE
                layoutOptionsMenu.visibility = View.GONE
                layoutVideoSettings.visibility = View.VISIBLE
            }
            isOptionsMenuOpen -> {
                layoutEmulator.visibility = View.GONE
                layoutMainMenu.visibility = View.GONE
                layoutOptionsMenu.visibility = View.VISIBLE
                layoutVideoSettings.visibility = View.GONE
            }
            else -> mostrarMenu()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun bindViewsAndSurfaces() {
        layoutEmulator = findViewById(R.id.layout_emulator)
        layoutMainMenu = findViewById(R.id.layout_main_menu)
        layoutOptionsMenu = findViewById(R.id.layout_options_menu)
        layoutVideoSettings = findViewById(R.id.layout_video_settings)

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
        setupOptionsControls()
        setupVideoSettingsControls()
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
            mostrarOpciones()
        }

        btnHelp?.setOnClickListener {
            Toast.makeText(this, "FirstDS - melonDS Core", Toast.LENGTH_LONG).show()
        }

        btnExit.setOnClickListener {
            finish()
        }
    }

    private fun setupOptionsControls() {
        val btnBack = findViewById<TextView>(R.id.btn_options_back)
        val optVideo = findViewById<TextView>(R.id.opt_video)
        val optAudio = findViewById<TextView>(R.id.opt_audio)
        val optVirtualPad = findViewById<TextView>(R.id.opt_virtual_pad)
        val optExternalPad = findViewById<TextView>(R.id.opt_external_pad)
        val optGeneral = findViewById<TextView>(R.id.opt_general)
        val optSystem = findViewById<TextView>(R.id.opt_system)
        val optAdvanced = findViewById<TextView>(R.id.opt_advanced)

        btnBack.setOnClickListener {
            ocultarOpciones()
        }

        optVideo.setOnClickListener {
            mostrarAjustesVideo()
        }

        optAudio.setOnClickListener {
            Toast.makeText(this, "Audio: 32.8 kHz Activo", Toast.LENGTH_SHORT).show()
        }

        optVirtualPad.setOnClickListener {
            Toast.makeText(this, "Mando virtual: Opacidad y disposición", Toast.LENGTH_SHORT).show()
        }

        optExternalPad.setOnClickListener {
            Toast.makeText(this, "Controlador externo: Mapeo de botones", Toast.LENGTH_SHORT).show()
        }

        optGeneral.setOnClickListener {
            Toast.makeText(this, "General: Guardado automático e idioma", Toast.LENGTH_SHORT).show()
        }

        optSystem.setOnClickListener {
            Toast.makeText(this, "Sistema: FreeBIOS / melonDS v0.9.5", Toast.LENGTH_SHORT).show()
        }

        optAdvanced.setOnClickListener {
            Toast.makeText(this, "Avanzado: Hilos y sincronización", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupVideoSettingsControls() {
        val btnVideoBack = findViewById<TextView>(R.id.btn_video_back)
        val optBackend = findViewById<View>(R.id.opt_video_backend)
        val txtBackend = findViewById<TextView>(R.id.txt_backend_selected)
        val optDriver = findViewById<View>(R.id.opt_video_driver)
        val txtDriver = findViewById<TextView>(R.id.txt_driver_selected)

        btnVideoBack.setOnClickListener {
            ocultarAjustesVideo()
        }

        optBackend.setOnClickListener {
            val backends = arrayOf("OpenGL ES 3.2 (Estándar recomendado)", "Vulkan (Renderizado de alta fidelidad)")
            AlertDialog.Builder(this)
                .setTitle("Seleccionar Motor Gráfico")
                .setItems(backends) { _, which ->
                    val selected = if (which == 0) "OpenGL ES 3.2" else "Vulkan"
                    txtBackend.text = selected
                    prefs.edit().putString("gfx_backend", selected).apply()
                    Toast.makeText(this, "Motor: $selected", Toast.LENGTH_SHORT).show()
                }
                .show()
        }

        optDriver.setOnClickListener {
            val drivers = arrayOf(
                "Controlador del sistema (Adreno oficial)",
                "Driver Turnip Mesa (Optimizado Snapdragon)",
                "Driver Qualcomm v615+ Propietario"
            )
            AlertDialog.Builder(this)
                .setTitle("Seleccionar Controlador GPU")
                .setItems(drivers) { _, which ->
                    txtDriver.text = drivers[which]
                    prefs.edit().putString("gpu_driver", drivers[which]).apply()
                    Toast.makeText(this, "Driver asignado: ${drivers[which]}", Toast.LENGTH_SHORT).show()
                }
                .show()
        }
    }

    private fun mostrarOpciones() {
        isOptionsMenuOpen = true
        isVideoSettingsOpen = false
        layoutMainMenu.visibility = View.GONE
        layoutOptionsMenu.visibility = View.VISIBLE
        layoutVideoSettings.visibility = View.GONE
    }

    private fun ocultarOpciones() {
        isOptionsMenuOpen = false
        isVideoSettingsOpen = false
        layoutOptionsMenu.visibility = View.GONE
        layoutVideoSettings.visibility = View.GONE
        layoutMainMenu.visibility = View.VISIBLE
    }

    private fun mostrarAjustesVideo() {
        isVideoSettingsOpen = true
        layoutOptionsMenu.visibility = View.GONE
        layoutVideoSettings.visibility = View.VISIBLE
    }

    private fun ocultarAjustesVideo() {
        isVideoSettingsOpen = false
        layoutVideoSettings.visibility = View.GONE
        layoutOptionsMenu.visibility = View.VISIBLE
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
        isOptionsMenuOpen = false
        isVideoSettingsOpen = false
        layoutMainMenu.visibility = View.GONE
        layoutOptionsMenu.visibility = View.GONE
        layoutVideoSettings.visibility = View.GONE
        layoutEmulator.visibility = View.VISIBLE
        audioEngine.start()
        gameLoop?.start()
    }

    private fun mostrarMenu() {
        isPlaying = false
        isOptionsMenuOpen = false
        isVideoSettingsOpen = false
        gameLoop?.stop()
        audioEngine.stop()
        layoutEmulator.visibility = View.GONE
        layoutOptionsMenu.visibility = View.GONE
        layoutVideoSettings.visibility = View.GONE
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
