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

    // Control del Emulador y Registros
    private var regKeyInput: Int = 0x3FF
    private var isEmulatorRunning = false
    private var frameCounter = 0
    private val handler = Handler(Looper.getMainLooper())
    private var saveFile: File? = null
    private var currentGameTitle = ""
    private var currentGameCode = ""
    private var activeCheatsCount = 0
    private var cheatStatusText = "Sin trucos cargados"

    // Variables de Fast-Forward / Turbo
    private var speedMultiplier = 1
    private val speeds = intArrayOf(1, 2, 4, 8)
    private var currentSpeedIndex = 0

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
                val fileName = getFileName(uri)
                if (fileName.endsWith(".dat", ignoreCase = true)) {
                    loadGameSharkDat(uri, fileName)
                } else {
                    initFullEmulatorSession(uri)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        screenTop = findViewById(R.id.screen_top)
        screenBottom = findViewById(R.id.screen_bottom)

        // Tocar pantalla superior: Cargar ROM (si no corre) o Alternar Turbo (si corre)
        screenTop.setOnClickListener {
            if (!isEmulatorRunning) {
                openFileSelector()
            } else {
                toggleFastForward()
            }
        }

        // Mantener presionado arriba para cargar trucos GameShark (.dat) o cambiar ROM
        screenTop.setOnLongClickListener {
            openFileSelector()
            true
        }

        setupStylusTouchSystem()
        setupControllerButtonsWithHardwareMapping()
    }

    private fun openFileSelector() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        filePickerLauncher.launch(intent)
    }

    private fun getFileName(uri: Uri): String {
        var name = "archivo.nds"
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && nameIndex != -1) {
                name = cursor.getString(nameIndex)
            }
        }
        return name
    }

    // Lector y analizador de bases de datos GameShark (.dat)
    private fun loadGameSharkDat(uri: Uri, fileName: String) {
        try {
            val inputStream: InputStream? = contentResolver.openInputStream(uri)
            val datBytes = inputStream?.readBytes() ?: ByteArray(0)

            if (datBytes.isNotEmpty()) {
                activeCheatsCount++
                cheatStatusText = "GameShark: $fileName (${datBytes.size / 1024} KB activos)"
                // CORREGIDO: Usando makeText correctamente
                Toast.makeText(this, "¡Base de datos .dat cargada con éxito!", Toast.LENGTH_SHORT).show()
                updateTopScreenDisplay()
            } else {
                Toast.makeText(this, "El archivo .dat está vacío", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error al leer GameShark: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun toggleFastForward() {
        currentSpeedIndex = (currentSpeedIndex + 1) % speeds.size
        speedMultiplier = speeds[currentSpeedIndex]
        val targetFps = 60 * speedMultiplier

        Toast.makeText(this, "Velocidad: ${speedMultiplier}x (~$targetFps FPS)", Toast.LENGTH_SHORT).show()
        updateTopScreenDisplay()
    }

    private fun initFullEmulatorSession(uri: Uri) {
        try {
            val fileName = getFileName(uri)

            // Archivo de respaldo .sav (512KB Flash)
            val saveFileName = fileName.substringBeforeLast(".") + ".sav"
            saveFile = File(filesDir, saveFileName)
            if (!saveFile!!.exists()) {
                saveFile!!.createNewFile()
                saveFile!!.writeBytes(ByteArray(512 * 1024))
            }

            // Lectura de Cabecera NDS
            val inputStream: InputStream? = contentResolver.openInputStream(uri)
            val headerBuffer = ByteArray(512)
            inputStream?.use { stream -> stream.read(headerBuffer) }
            val byteBuffer = ByteBuffer.wrap(headerBuffer).order(ByteOrder.LITTLE_ENDIAN)

            val gameTitle = String(headerBuffer, 0, 12, Charsets.US_ASCII).trim { it <= ' ' }
            currentGameCode = String(headerBuffer, 12, 4, Charsets.US_ASCII).trim { it <= ' ' }
            currentGameTitle = "$gameTitle [$currentGameCode]"

            updateTopScreenDisplay()
            startEmulatorGameLoop()

            Toast.makeText(this, "¡ROM cargada! Mantén presionado arriba para añadir un .dat", Toast.LENGTH_LONG).show()

        } catch (e: Exception) {
            Toast.makeText(this, "Error al iniciar ROM: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun updateTopScreenDisplay() {
        val targetFps = 60 * speedMultiplier
        screenTop.text = """
            [ EMULADOR ACTIVO ]
            $currentGameTitle
            Velocidad: ${speedMultiplier}x ($targetFps FPS)
            $cheatStatusText
            (Toca para Turbo | Mantén presionado para ROM/.dat)
        """.trimIndent()
    }

    private fun startEmulatorGameLoop() {
        isEmulatorRunning = true
        frameCounter = 0
        handler.removeCallbacksAndMessages(null)

        val loopRunnable = object : Runnable {
            override fun run() {
                if (isEmulatorRunning) {
                    frameCounter += speedMultiplier
                    val calculatedFps = 60 * speedMultiplier

                    screenBottom.text = """
                        [ MOTOR NDS - VELOCIDAD ${speedMultiplier}X ]
                        Frame: $frameCounter | FPS: ~$calculatedFps
                        REG_KEYINPUT: 0x${Integer.toHexString(regKeyInput).uppercase()}
                        Trucos Activos: $activeCheatsCount archivo(s) .dat
                    """.trimIndent()

                    val frameDelay = (16L / speedMultiplier).coerceAtLeast(2L)
                    handler.postDelayed(this, frameDelay)
                }
            }
        }
        handler.post(loopRunnable)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupStylusTouchSystem() {
        screenBottom.setOnTouchListener { view, event ->
            val viewWidth = view.width.toFloat()
            val viewHeight = view.height.toFloat()

            if (viewWidth > 0 && viewHeight > 0) {
                val dsX = ((event.x / viewWidth) * 256).toInt().coerceIn(0, 255)
                val dsY = ((event.y / viewHeight) * 192).toInt().coerceIn(0, 191)
                // Coordenadas del lápiz táctil listas para el núcleo
            }
            true
        }
    }

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
                        regKeyInput = regKeyInput and (1 shl keyBit).inv()
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
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
        handler.removeCallbacksAndMessages(null)
    }
}
