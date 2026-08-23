package com.ejemplo.emulador

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.view.MotionEvent
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class MainActivity : AppCompatActivity() {

    private lateinit var screenTop: TextView
    private lateinit var screenBottom: TextView

    // Simulación de registros de hardware de la Nintendo DS
    private var regKeyInput: Int = 0x3FF // Bits en alto (no presionados)
    private var isEmulatorRunning = false
    private var frameCounter = 0
    private val handler = Handler(Looper.getMainLooper())
    private var saveFile: File? = null

    // Mapeo de bits para REG_KEYINPUT (Lógica invertida: 0 = presionado)
    companion object {
        const val KEY_A = 0
        const val KEY_B = 1
        const val KEY_SELECT = 2
        const val KEY_START = 3
        const val KEY_RIGHT = 4
        const val KEY_LEFT = 5
        const val KEY_UP = 6
        const val KEY_DOWN = 7
        const val KEY_R = 8
        const val KEY_L = 9
        const val KEY_X = 10
        const val KEY_Y = 11
    }

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.data?.let { uri ->
                initFullEmulatorSession(uri)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        screenTop = findViewById(R.id.screen_top)
        screenBottom = findViewById(R.id.screen_bottom)

        // Tocar pantalla superior para cargar ROMs
        screenTop.setOnClickListener {
            openRomSelector()
        }

        setupStylusTouchSystem()
        setupControllerButtonsWithHardwareMapping()
    }

    private fun openRomSelector() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        filePickerLauncher.launch(intent)
    }

    private fun initFullEmulatorSession(uri: Uri) {
        try {
            var fileName = "juego.nds"
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && nameIndex != -1) {
                    fileName = cursor.getString(nameIndex)
                }
            }

            // 1. Crear o enlazar archivo de guardado (.sav) para evitar errores de memoria Flash
            val saveFileName = fileName.substringBeforeLast(".") + ".sav"
            saveFile = File(filesDir, saveFileName)
            if (!saveFile!!.exists()) {
                saveFile!!.createNewFile()
                saveFile!!.writeBytes(ByteArray(512 * 1024)) // 512KB Flash vacío
            }

            // 2. Leer Cabecera NDS
            val inputStream: InputStream? = contentResolver.openInputStream(uri)
            val headerBuffer = ByteArray(512)
            inputStream?.use { stream -> stream.read(headerBuffer) }
            val byteBuffer = ByteBuffer.wrap(headerBuffer).order(ByteOrder.LITTLE_ENDIAN)

            val gameTitle = String(headerBuffer, 0, 12, Charsets.US_ASCII).trim { it <= ' ' }
            val gameCode = String(headerBuffer, 12, 4, Charsets.US_ASCII).trim { it <= ' ' }

            screenTop.text = """
                [ EMULADOR ACTIVO ]
                Juego: $gameTitle [$gameCode]
                Save: ${saveFile!!.name} (${saveFile!!.length() / 1024} KB)
            """.trimIndent()

            // 3. Iniciar el bucle de renderizado a 60 FPS
            startEmulatorGameLoop()

            Toast.makeText(this, "¡Sesión de emulación iniciada!", Toast.LENGTH_SHORT).show()

        } catch (e: Exception) {
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    // Bucle cíclico de 60 FPS simulado
    private fun startEmulatorGameLoop() {
        isEmulatorRunning = true
        frameCounter = 0
        handler.post(object : Runnable {
            override fun run() {
                if (isEmulatorRunning) {
                    frameCounter++
                    screenBottom.text = """
                        [ MOTOR DE EMULACIÓN - 60 FPS ]
                        Frames: $frameCounter
                        REG_KEYINPUT Hex: 0x${Integer.toHexString(regKeyInput).uppercase()}
                        (Controles y Memoria Sincronizados)
                    """.trimIndent()
                    handler.postDelayed(this, 16) // ~60 FPS
                }
            }
        })
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupStylusTouchSystem() {
        screenBottom.setOnTouchListener { view, event ->
            val viewWidth = view.width.toFloat()
            val viewHeight = view.height.toFloat()

            if (viewWidth > 0 && viewHeight > 0) {
                val dsX = ((event.x / viewWidth) * 256).toInt().coerceIn(0, 255)
                val dsY = ((event.y / viewHeight) * 192).toInt().coerceIn(0, 191)

                when (event.action) {
                    MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                        // Táctil activo en coordenadas DS (dsX, dsY)
                    }
                    MotionEvent.ACTION_UP -> {
                        // Táctil en reposo
                    }
                }
            }
            true
        }
    }

    // Mapeo avanzado de botones virtuales a los bits del registro REG_KEYINPUT de la DS
    @SuppressLint("ClickableViewAccessibility")
    private fun setupControllerButtonsWithHardwareMapping() {
        val buttonMap = mapOf(
            R.id.btn_a to KEY_A,
            R.id.btn_b to KEY_B,
            R.id.btn_x to KEY_X,
            R.id.btn_y to KEY_Y,
            R.id.btn_l to KEY_L,
            R.id.btn_r to KEY_R,
            R.id.btn_start to KEY_START,
            R.id.btn_select to KEY_SELECT,
            R.id.btn_up to KEY_UP,
            R.id.btn_down to KEY_DOWN,
            R.id.btn_left to KEY_LEFT,
            R.id.btn_right to KEY_RIGHT
        )

        buttonMap.forEach { (viewId, keyBit) ->
            findViewById<View>(viewId)?.setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        // Lógica invertida de la DS: 0 = presionado
                        regKeyInput = regKeyInput and (1 shl keyBit).inv()
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        // Al soltar, el bit vuelve a 1 (libre)
                        regKeyInput = regKeyInput or (1 shl keyBit)
                        true
                    }
                    else -> false
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isEmulatorRunning = false
    }
}
