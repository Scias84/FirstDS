package com.ejemplo.emulador

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.Color
import android.net.Uri
import android.opengl.GLSurfaceView
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.OpenableColumns
import android.view.MotionEvent
import android.view.View
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

class MainActivity : AppCompatActivity() {

    private var keyState: Int = 0x0FFF
    private val touchDigitizer = TouchDigitizer()
    private val audioEngine = AudioEngine(32828)
    private var gameLoop: GameLoop? = null
    private lateinit var prefs: SharedPreferences

    private var isPlaying = false
    private var isOptionsMenuOpen = false
    private var isVideoSettingsOpen = false
    private var isVirtualPadSettingsOpen = false

    private var hapticEnabled = true
    private var currentVPadAlpha = 0.7f

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
    private lateinit var layoutVirtualPadSettings: View
    private lateinit var btnContinue: TextView
    private lateinit var txtDriverSelected: TextView

    private val allControlIds = listOf(
        R.id.btn_a, R.id.btn_b, R.id.btn_x, R.id.btn_y,
        R.id.btn_l, R.id.btn_r, R.id.btn_start, R.id.btn_select,
        R.id.btn_up, R.id.btn_down, R.id.btn_left, R.id.btn_right
    )

    private val romPickerLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { cargarRomDesdeUri(it) }
    }

    private val driverPickerLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { instalarDriverDesdeZip(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences("FirstDS_Prefs", MODE_PRIVATE)
        hapticEnabled = prefs.getBoolean("vpad_haptic", true)
        currentVPadAlpha = prefs.getFloat("vpad_alpha", 0.7f)

        val systemDir = filesDir.absolutePath
        val libDir = applicationInfo.nativeLibraryDir
        NativeBridge.nativeInit(systemDir, libDir)

        bindViewsAndSurfaces()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        when {
            isPlaying -> mostrarMenu()
            isVirtualPadSettingsOpen -> ocultarAjustesVirtualPad()
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
                layoutVirtualPadSettings.visibility = View.GONE
            }
            isVirtualPadSettingsOpen -> {
                layoutEmulator.visibility = View.GONE
                layoutMainMenu.visibility = View.GONE
                layoutOptionsMenu.visibility = View.GONE
                layoutVideoSettings.visibility = View.GONE
                layoutVirtualPadSettings.visibility = View.VISIBLE
            }
            isVideoSettingsOpen -> {
                layoutEmulator.visibility = View.GONE
                layoutMainMenu.visibility = View.GONE
                layoutOptionsMenu.visibility = View.GONE
                layoutVideoSettings.visibility = View.VISIBLE
                layoutVirtualPadSettings.visibility = View.GONE
            }
            isOptionsMenuOpen -> {
                layoutEmulator.visibility = View.GONE
                layoutMainMenu.visibility = View.GONE
                layoutOptionsMenu.visibility = View.VISIBLE
                layoutVideoSettings.visibility = View.GONE
                layoutVirtualPadSettings.visibility = View.GONE
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
        layoutVirtualPadSettings = findViewById(R.id.layout_virtual_pad_settings)

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

        aplicarOpacidadControles(currentVPadAlpha)

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
        setupVirtualPadSettingsControls()
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
            if (lastPath != null && File(lastPath).exists()) iniciarEmulacion()
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

        btnExit.setOnClickListener { finish() }
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

        btnBack.setOnClickListener { ocultarOpciones() }
        optVideo.setOnClickListener { mostrarAjustesVideo() }
        optVirtualPad.setOnClickListener { mostrarAjustesVirtualPad() }

        optAudio.setOnClickListener { Toast.makeText(this, "Audio: 32.8 kHz Activo", Toast.LENGTH_SHORT).show() }
        optExternalPad.setOnClickListener { Toast.makeText(this, "Controlador externo: Mapeo", Toast.LENGTH_SHORT).show() }
        optGeneral.setOnClickListener { Toast.makeText(this, "General: Guardado e idioma", Toast.LENGTH_SHORT).show() }
        optSystem.setOnClickListener { Toast.makeText(this, "Sistema: melonDS Core v0.9.5", Toast.LENGTH_SHORT).show() }
        optAdvanced.setOnClickListener { Toast.makeText(this, "Avanzado: Hilos y sincronización", Toast.LENGTH_SHORT).show() }
    }
        private fun setupVideoSettingsControls() {
        val btnVideoBack = findViewById<TextView>(R.id.btn_video_back)
        val optBackend = findViewById<View>(R.id.opt_video_backend)
        val txtBackend = findViewById<TextView>(R.id.txt_backend_selected)
        val optDriver = findViewById<View>(R.id.opt_video_driver)
        txtDriverSelected = findViewById(R.id.txt_driver_selected)

        btnVideoBack.setOnClickListener { ocultarAjustesVideo() }

        optBackend.setOnClickListener {
            val backends = arrayOf("OpenGL ES 3.2 (Estándar recomendado)", "Vulkan (Renderizado de alta fidelidad)")
            AlertDialog.Builder(this)
                .setTitle("Seleccionar Motor Gráfico")
                .setItems(backends) { _, which ->
                    val selected = if (which == 0) "OpenGL ES 3.2" else "Vulkan"
                    txtBackend.text = selected
                    prefs.edit().putString("gfx_backend", selected).apply()
                }
                .show()
        }

        optDriver.setOnClickListener { mostrarDialogoSeleccionDrivers() }
    }

    private fun setupVirtualPadSettingsControls() {
        val btnVPadBack = findViewById<TextView>(R.id.btn_vpad_back)
        val optOpacity = findViewById<View>(R.id.opt_vpad_opacity)
        val txtOpacity = findViewById<TextView>(R.id.txt_vpad_opacity_val)
        val switchVib = findViewById<Switch>(R.id.switch_vpad_vibration)
        val optStyle = findViewById<View>(R.id.opt_vpad_style)
        val optLayoutEdit = findViewById<View>(R.id.opt_vpad_layout_edit)

        switchVib.isChecked = hapticEnabled
        switchVib.setOnCheckedChangeListener { _, isChecked ->
            hapticEnabled = isChecked
            prefs.edit().putBoolean("vpad_haptic", isChecked).apply()
        }

        btnVPadBack.setOnClickListener { ocultarAjustesVirtualPad() }

        optOpacity.setOnClickListener {
            val opacities = arrayOf("100% (Completamente visible)", "70% (Predeterminado)", "40% (Translúcido)", "15% (Muy transparente)")
            val alphas = floatArrayOf(1.0f, 0.7f, 0.4f, 0.15f)

            AlertDialog.Builder(this)
                .setTitle("Opacidad de los controles")
                .setItems(opacities) { _, which ->
                    currentVPadAlpha = alphas[which]
                    txtOpacity.text = opacities[which]
                    prefs.edit().putFloat("vpad_alpha", currentVPadAlpha).apply()
                    aplicarOpacidadControles(currentVPadAlpha)
                }
                .show()
        }

        optStyle.setOnClickListener {
            Toast.makeText(this, "Estilo de botones: Super Nintendo Activo", Toast.LENGTH_SHORT).show()
        }

        optLayoutEdit.setOnClickListener {
            Toast.makeText(this, "Arrastra botones en partida para moverlos", Toast.LENGTH_SHORT).show()
        }
    }

    private fun aplicarOpacidadControles(alpha: Float) {
        for (id in allControlIds) {
            findViewById<View>(id)?.alpha = alpha
        }
    }

    private fun ejecutarVibracionBoton() {
        if (!hapticEnabled) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
            } else {
                @Suppress("DEPRECATION")
                val v = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                @Suppress("DEPRECATION")
                v.vibrate(25)
            }
        } catch (e: Exception) {
        }
    }

    private fun mostrarDialogoSeleccionDrivers() {
        val driversDir = File(filesDir, "gpu_drivers")
        if (!driversDir.exists()) driversDir.mkdirs()

        val installedDrivers = driversDir.listFiles { f -> f.isDirectory }?.map { it.name }?.toMutableList() ?: mutableListOf()
        val optionsList = mutableListOf<String>()
        optionsList.add("Controlador del sistema (Predeterminado)")
        optionsList.addAll(installedDrivers)
        optionsList.add("➕ Instalar controlador desde archivo (.zip)...")

        AlertDialog.Builder(this)
            .setTitle("Controlador GPU (Vulkan / Adreno)")
            .setItems(optionsList.toTypedArray()) { _, which ->
                when (which) {
                    0 -> {
                        txtDriverSelected.text = "Controlador del sistema (Predeterminado)"
                        prefs.edit().putString("gpu_driver_name", "Controlador del sistema (Predeterminado)").putString("gpu_driver_path", "").apply()
                    }
                    optionsList.size - 1 -> {
                        driverPickerLauncher.launch(arrayOf("application/zip", "application/x-zip-compressed", "*/*"))
                    }
                    else -> {
                        val chosenName = optionsList[which]
                        val chosenDir = File(driversDir, chosenName)
                        txtDriverSelected.text = chosenName
                        prefs.edit().putString("gpu_driver_name", chosenName).putString("gpu_driver_path", chosenDir.absolutePath).apply()
                    }
                }
            }
            .show()
    }

    private fun instalarDriverDesdeZip(uri: Uri) {
        try {
            var zipName = "Custom_Driver"
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0 && cursor.moveToFirst()) zipName = cursor.getString(nameIndex).replace(".zip", "")
            }

            val targetDir = File(File(filesDir, "gpu_drivers"), zipName)
            if (targetDir.exists()) targetDir.deleteRecursively()
            targetDir.mkdirs()

            contentResolver.openInputStream(uri)?.use { stream ->
                ZipInputStream(stream).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        val file = File(targetDir, entry.name)
                        if (entry.isDirectory) {
                            file.mkdirs()
                        } else {
                            file.parentFile?.mkdirs()
                            FileOutputStream(file).use { out -> zis.copyTo(out) }
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
            }

            txtDriverSelected.text = zipName
            prefs.edit().putString("gpu_driver_name", zipName).putString("gpu_driver_path", targetDir.absolutePath).apply()
            Toast.makeText(this, "¡Driver '$zipName' instalado y activado!", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Error instalando driver ZIP: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun mostrarOpciones() {
        isOptionsMenuOpen = true
        isVideoSettingsOpen = false
        isVirtualPadSettingsOpen = false
        layoutMainMenu.visibility = View.GONE
        layoutOptionsMenu.visibility = View.VISIBLE
        layoutVideoSettings.visibility = View.GONE
        layoutVirtualPadSettings.visibility = View.GONE
    }

    private fun ocultarOpciones() {
        isOptionsMenuOpen = false
        isVideoSettingsOpen = false
        isVirtualPadSettingsOpen = false
        layoutOptionsMenu.visibility = View.GONE
        layoutVideoSettings.visibility = View.GONE
        layoutVirtualPadSettings.visibility = View.GONE
        layoutMainMenu.visibility = View.VISIBLE
    }

    private fun mostrarAjustesVideo() {
        isVideoSettingsOpen = true
        isVirtualPadSettingsOpen = false
        layoutOptionsMenu.visibility = View.GONE
        layoutVideoSettings.visibility = View.VISIBLE
    }

    private fun ocultarAjustesVideo() {
        isVideoSettingsOpen = false
        layoutVideoSettings.visibility = View.GONE
        layoutOptionsMenu.visibility = View.VISIBLE
    }

    private fun mostrarAjustesVirtualPad() {
        isVirtualPadSettingsOpen = true
        isVideoSettingsOpen = false
        layoutOptionsMenu.visibility = View.GONE
        layoutVirtualPadSettings.visibility = View.VISIBLE
    }

    private fun ocultarAjustesVirtualPad() {
        isVirtualPadSettingsOpen = false
        layoutVirtualPadSettings.visibility = View.GONE
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
        isVirtualPadSettingsOpen = false
        layoutMainMenu.visibility = View.GONE
        layoutOptionsMenu.visibility = View.GONE
        layoutVideoSettings.visibility = View.GONE
        layoutVirtualPadSettings.visibility = View.GONE
        layoutEmulator.visibility = View.VISIBLE
        audioEngine.start()
        gameLoop?.start()
    }

    private fun mostrarMenu() {
        isPlaying = false
        isOptionsMenuOpen = false
        isVideoSettingsOpen = false
        isVirtualPadSettingsOpen = false
        gameLoop?.stop()
        audioEngine.stop()
        layoutEmulator.visibility = View.GONE
        layoutOptionsMenu.visibility = View.GONE
        layoutVideoSettings.visibility = View.GONE
        layoutVirtualPadSettings.visibility = View.GONE
        layoutMainMenu.visibility = View.VISIBLE
        actualizarBotonContinuar()
    }

    private fun cargarRomDesdeUri(uri: Uri) {
        try {
            Toast.makeText(this, "Cargando cartucho NDS...", Toast.LENGTH_SHORT).show()
            val tempRom = File(filesDir, "current_game.nds")
            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempRom).use { output -> input.copyTo(output) }
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
                    v.alpha = (currentVPadAlpha * 0.6f).coerceAtLeast(0.2f)
                    ejecutarVibracionBoton()
                    keyState = keyState and keyMask.inv()
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.alpha = currentVPadAlpha
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
        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    v.alpha = (currentVPadAlpha * 0.6f).coerceAtLeast(0.2f)
                    ejecutarVibracionBoton()
                    keyState = keyState and keyMask.inv()
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.alpha = currentVPadAlpha
                    keyState = keyState or keyMask
                    true
                }
                else -> false
            }
        }
    }
}
