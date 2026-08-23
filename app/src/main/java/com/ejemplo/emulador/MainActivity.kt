package com.ejemplo.emulador

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.dsemu.drastic.DraSticBridge
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {

    private var keyState: Int = 0x0FFF

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

    private lateinit var screenTop: TextView
    private lateinit var screenBottom: TextView

    // Selector de archivos del sistema
    private val romPickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { cargarRomSeleccionada(it) }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        screenTop = findViewById(R.id.screen_top)
        screenBottom = findViewById(R.id.screen_bottom)

        // Inicializar almacenamiento interno para el motor
        try {
            val storagePath = filesDir.absolutePath
            DraSticBridge.initCore(storagePath)
            screenTop.text = "[ MOTOR LISTO ]\nToca aquí para cargar una ROM (.nds)"
        } catch (e: Throwable) {
            screenTop.text = "[ MODO INTERFAZ ]\nToca aquí para elegir una ROM"
        }

        // Tocar la pantalla superior abre el explorador de archivos
        screenTop.setOnClickListener {
            romPickerLauncher.launch(arrayOf("*/*"))
        }

        // Configuración de botones
        setupButton(R.id.btn_a, KEY_A, "A")
        setupButton(R.id.btn_b, KEY_B, "B")
        setupButton(R.id.btn_x, KEY_X, "X")
        setupButton(R.id.btn_y, KEY_Y, "Y")
        setupButton(R.id.btn_l, KEY_L, "L")
        setupButton(R.id.btn_r, KEY_R, "R")
        setupButton(R.id.btn_start, KEY_START, "START")
        setupButton(R.id.btn_select, KEY_SELECT, "SELECT")

        // Configuración de cruceta
        setupDirectionButton(R.id.btn_up, KEY_UP, "ARRIBA")
        setupDirectionButton(R.id.btn_down, KEY_DOWN, "ABAJO")
        setupDirectionButton(R.id.btn_left, KEY_LEFT, "IZQUIERDA")
        setupDirectionButton(R.id.btn_right, KEY_RIGHT, "DERECHA")
    }

    private fun cargarRomSeleccionada(uri: Uri) {
        try {
            screenTop.text = "Copiando ROM a la memoria de trabajo..."
            
            // Copiar el archivo seleccionado a la memoria interna de la app
            val tempRomFile = File(cacheDir, "game.nds")
            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempRomFile).use { output ->
                    input.copyTo(output)
                }
            }

            // Notificar al motor nativo
            val exito = try {
                DraSticBridge.loadRom(tempRomFile.absolutePath)
            } catch (e: Throwable) {
                false
            }

            if (exito) {
                screenTop.text = "[ ROM CARGADA CON ÉXITO ]\n${tempRomFile.name} (${tempRomFile.length() / (1024 * 1024)} MB)"
            } else {
                screenTop.text = "[ ARCHIVO LISTO EN CACHE ]\n${tempRomFile.name}\nEsperando bucle gráfico"
            }
        } catch (e: Exception) {
            screenTop.text = "Error al leer ROM: ${e.message}"
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupButton(viewId: Int, keyMask: Int, name: String) {
        val view = findViewById<View>(viewId) ?: return
        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    v.alpha = 0.5f
                    keyState = keyState and keyMask.inv()
                    updateStatus("Botón: $name")
                    sendInputToCore()
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.alpha = 1.0f
                    keyState = keyState or keyMask
                    updateStatus("Liberado: $name")
                    sendInputToCore()
                    true
                }
                else -> false
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupDirectionButton(viewId: Int, keyMask: Int, direction: String) {
        val view = findViewById<View>(viewId) ?: return
        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    keyState = keyState and keyMask.inv()
                    updateStatus("Cruceta: $direction")
                    sendInputToCore()
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    keyState = keyState or keyMask
                    sendInputToCore()
                    true
                }
                else -> false
            }
        }
    }

    private fun updateStatus(info: String) {
        screenBottom.text = "[ ENTRADA TÁCTIL ]\n$info\nMáscara: 0x${Integer.toHexString(keyState).uppercase()}"
    }

    private fun sendInputToCore() {
        try {
            DraSticBridge.updateFrame(keyState)
        } catch (_: Throwable) { }
    }
}
