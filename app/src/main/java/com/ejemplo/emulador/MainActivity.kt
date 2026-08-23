package com.ejemplo.emulador

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import java.io.InputStream

class MainActivity : AppCompatActivity() {

    private lateinit var screenTop: TextView
    private lateinit var screenBottom: TextView

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.data?.let { uri ->
                parseNdsHeader(uri)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        screenTop = findViewById(R.id.screen_top)
        screenBottom = findViewById(R.id.screen_bottom)

        // Tocar pantalla superior para abrir ROMs
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

    // Lee los primeros 16 bytes de la cabecera del archivo .nds
    private fun parseNdsHeader(uri: Uri) {
        try {
            val inputStream: InputStream? = contentResolver.openInputStream(uri)
            val headerBuffer = ByteArray(16) // Los primeros 16 bytes contienen el Título y el ID
            inputStream?.use { stream ->
                stream.read(headerBuffer)
            }

            // Bytes 0x00 a 0x0B (12 bytes): Título interno del juego
            val gameTitle = String(headerBuffer, 0, 12, Charsets.US_ASCII).trim { it <= ' ' }
            
            // Bytes 0x0C a 0x0F (4 bytes): Código/ID del juego (ej: IRBO)
            val gameCode = String(headerBuffer, 12, 4, Charsets.US_ASCII).trim { it <= ' ' }

            // Mostrar la información analizada en las pantallas
            screenTop.text = "Título: $gameTitle"
            screenBottom.text = "ID del Cartucho: $gameCode\n(ROM Cargada Correctamente)"
            
            Toast.makeText(this, "¡ROM analizada con éxito!", Toast.LENGTH_SHORT).show()

        } catch (e: Exception) {
            Toast.makeText(this, "Error al leer la ROM: ${e.message}", Toast.LENGTH_LONG).show()
        }
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
