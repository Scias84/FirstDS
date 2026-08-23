package com.ejemplo.emulador

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.MotionEvent
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class MainActivity : AppCompatActivity() {

    private lateinit var screenTop: TextView
    private lateinit var screenBottom: TextView

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.data?.let { uri ->
                parseFullNdsHeader(uri)
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
        setupControllerButtons()
    }

    private fun openRomSelector() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        filePickerLauncher.launch(intent)
    }

    // Mapeador de coordenadas de la pantalla táctil DS (256 x 192)
    @SuppressLint("ClickableViewAccessibility")
    private fun setupStylusTouchSystem() {
        screenBottom.setOnTouchListener { view, event ->
            val viewWidth = view.width.toFloat()
            val viewHeight = view.height.toFloat()

            if (viewWidth > 0 && viewHeight > 0) {
                // Normalización de coordenadas al hardware de Nintendo DS
                val dsX = ((event.x / viewWidth) * 256).toInt().coerceIn(0, 255)
                val dsY = ((event.y / viewHeight) * 192).toInt().coerceIn(0, 191)

                when (event.action) {
                    MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                        screenBottom.text = "[ Stylus Táctil ]\nPresionado en DS: X = $dsX | Y = $dsY\n(Estado: Touch Activo)"
                    }
                    MotionEvent.ACTION_UP -> {
                        screenBottom.text = "[ Stylus Táctil ]\nÚltima posición: X = $dsX | Y = $dsY\n(Estado: Reposo)"
                    }
                }
            }
            true
        }
    }

    // Lectura de los 512 bytes de la cabecera NDS y localización de ejecutables
    private fun parseFullNdsHeader(uri: Uri) {
        try {
            var fileName = "juego.nds"
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && nameIndex != -1) {
                    fileName = cursor.getString(nameIndex)
                }
            }

            val inputStream: InputStream? = contentResolver.openInputStream(uri)
            val headerBuffer = ByteArray(512)
            inputStream?.use { stream ->
                stream.read(headerBuffer)
            }

            val byteBuffer = ByteBuffer.wrap(headerBuffer).order(ByteOrder.LITTLE_ENDIAN)

            // Título e ID
            val gameTitle = String(headerBuffer, 0, 12, Charsets.US_ASCII).trim { it <= ' ' }
            val gameCode = String(headerBuffer, 12, 4, Charsets.US_ASCII).trim { it <= ' ' }

            // Datos de ejecución ARM9 (Procesador Principal)
            val arm9RomOffset = byteBuffer.getInt(0x020)
            val arm9EntryAddress = byteBuffer.getInt(0x024)
            val arm9RamAddress = byteBuffer.getInt(0x028)
            val arm9Size = byteBuffer.getInt(0x02C)

            // Datos de ejecución ARM7 (Sub-procesador de Audio/E/S)
            val arm7RomOffset = byteBuffer.getInt(0x030)
            val arm7EntryAddress = byteBuffer.getInt(0x034)
            val arm7RamAddress = byteBuffer.getInt(0x038)
            val arm7Size = byteBuffer.getInt(0x03C)

            screenTop.text = """
                Título: $gameTitle [$gameCode]
                Archivo: $fileName
                ─────────────────────
                ARM9 RAM Target: 0x${Integer.toHexString(arm9RamAddress).uppercase()} (Size: ${arm9Size / 1024} KB)
                ARM7 RAM Target: 0x${Integer.toHexString(arm7RamAddress).uppercase()} (Size: ${arm7Size / 1024} KB)
            """.trimIndent()

            screenBottom.text = "[ Pantalla Táctil ]\nArrastra el dedo aquí para probar el Stylus DS."
            Toast.makeText(this, "Estructura binaria mapeada", Toast.LENGTH_SHORT).show()

        } catch (e: Exception) {
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
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
