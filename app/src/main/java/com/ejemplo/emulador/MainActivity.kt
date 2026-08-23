package com.ejemplo.emulador

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var screenTop: TextView
    private lateinit var screenBottom: TextView

    // Cargar la librería nativa de C++ al iniciar la app
    companion object {
        init {
            System.loadLibrary("emulatorkernel")
        }
    }

    // Declaración del método nativo escrito en C++
    external fun initEmulatorCore(romPath: String): String

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.data?.let { uri ->
                val path = uri.path ?: "rom_virtual.nds"
                
                // Llamamos al núcleo en C++ pasando la ruta del juego
                val kernelResponse = initEmulatorCore(path)
                
                screenTop.text = "¡Juego Enlazado al Motor C++!"
                screenBottom.text = kernelResponse
                Toast.makeText(this, "Motor NDK activado con éxito", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        screenTop = findViewById(R.id.screen_top)
        screenBottom = findViewById(R.id.screen_bottom)

        screenTop.setOnClickListener {
            openRomSelector()
        }

        setupControllerButtons()
    }

    private fun openRomSelector() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        filePickerLauncher.launch(intent)
    }

    private fun setupControllerButtons() {
        val buttons = mapOf(
            R.id.btn_a to "A",
            R.id.btn_b to "B",
            R.id.btn_x to "X",
            R.id.btn_y to "Y",
            R.id.btn_l to "L",
            R.id.btn_r to "R",
            R.id.btn_start to "START",
            R.id.btn_select to "SELECT",
            R.id.btn_up to "Arriba",
            R.id.btn_down to "Abajo",
            R.id.btn_left to "Izquierda",
            R.id.btn_right to "Derecha"
        )

        buttons.forEach { (id, name) ->
            findViewById<View>(id)?.setOnClickListener {
                Toast.makeText(this, "Botón $name presionado", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
