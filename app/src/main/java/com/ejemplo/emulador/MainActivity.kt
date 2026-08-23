package com.ejemplo.emulador

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var screenTop: TextView

    // Lanzador para abrir el explorador de archivos del teléfono
    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.data?.let { uri ->
                val romName = getFileName(uri)
                Toast.makeText(this, "Cargando: $romName", Toast.LENGTH_LONG).show()
                // Mostrar el nombre del juego en la pantalla superior provisionalmente
                screenTop.text = "Juego: $romName"
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        screenTop = findViewById(R.id.screen_top)

        // Al tocar la pantalla superior, se abre el selector de ROMs (.nds)
        screenTop.setOnClickListener {
            openRomSelector()
        }

        // Configuración de los botones de la consola
        setupControllerButtons()
    }

    private fun openRomSelector() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*" // Permitir archivos generales (luego filtramos por .nds)
        }
        filePickerLauncher.launch(intent)
    }

    private fun getFileName(uri: Uri): String {
        var name = "ROM_Desconocida.nds"
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && nameIndex != -1) {
                name = cursor.getString(nameIndex)
            }
        }
        return name
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
