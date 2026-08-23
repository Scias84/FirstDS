package com.ejemplo.emulador

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.dsemu.drastic.DraSticBridge

class MainActivity : AppCompatActivity() {

    // Registro de botones activos (Máscara de bits de Nintendo DS)
    private var keyState: Int = 0x0FFF // 0x0FFF = Todos los botones liberados

    // Constantes de botones DS (Lógica activa en nivel bajo: 0 = presionado)
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

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 1. Vincular pantallas visuales
        screenTop = findViewById(R.id.screen_top)
        screenBottom = findViewById(R.id.screen_bottom)

        // 2. Intentar inicializar el núcleo nativo de forma segura
        try {
            val storagePath = filesDir.absolutePath
            DraSticBridge.initCore(storagePath)
            screenTop.text = "[ MOTOR DS CONECTADO ]\nEsperando ROM..."
        } catch (e: Throwable) {
            screenTop.text = "[ MODO INTERFAZ ]\nNúcleo en espera de enlace nativo"
        }

        // 3. Configurar eventos táctiles de los botones
        setupButton(R.id.btn_a, KEY_A, "A")
        setupButton(R.id.btn_b, KEY_B, "B")
        setupButton(R.id.btn_x, KEY_X, "X")
        setupButton(R.id.btn_y, KEY_Y, "Y")
        setupButton(R.id.btn_l, KEY_L, "L")
        setupButton(R.id.btn_r, KEY_R, "R")
        setupButton(R.id.btn_start, KEY_START, "START")
        setupButton(R.id.btn_select, KEY_SELECT, "SELECT")

        // 4. Configurar controles de dirección (Cruceta)
        setupDirectionButton(R.id.btn_up, KEY_UP, "ARRIBA")
        setupDirectionButton(R.id.btn_down, KEY_DOWN, "ABAJO")
        setupDirectionButton(R.id.btn_left, KEY_LEFT, "IZQUIERDA")
        setupDirectionButton(R.id.btn_right, KEY_RIGHT, "DERECHA")
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupButton(viewId: Int, keyMask: Int, name: String) {
        val view = findViewById<View>(viewId) ?: return
        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    v.alpha = 0.5f
                    keyState = keyState and keyMask.inv() // Marca el bit como presionado (0)
                    updateStatus("Botón Presionado: $name")
                    sendInputToCore()
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.alpha = 1.0f
                    keyState = keyState or keyMask // Restaura el bit a liberado (1)
                    updateStatus("Botón Liberado: $name")
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
                    updateStatus("Dirección: $direction")
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
        screenBottom.text = "[ ENTRADA TÁCTIL ]\n$info\nRegistro HEX: 0x${Integer.toHexString(keyState).uppercase()}"
    }

    private fun sendInputToCore() {
        try {
            DraSticBridge.updateFrame(keyState)
        } catch (_: Throwable) {
            // Se ignora si aún no se está ejecutando el ciclo nativo
        }
    }
}
