package com.ejemplo.emulador

import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.FileOutputStream

class MenuActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var btnContinue: TextView

    private val romPicker = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { launchEmulatorWithRom(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_menu)

        prefs = getSharedPreferences("FirstDS_Prefs", MODE_PRIVATE)

        btnContinue = findViewById(R.id.btn_menu_continue)
        val btnNewGame = findViewById<TextView>(R.id.btn_menu_new_game)
        val btnOptions = findViewById<TextView>(R.id.btn_menu_options)
        val btnExit = findViewById<TextView>(R.id.btn_menu_exit)
        val btnHelp = findViewById<TextView>(R.id.btn_top_help)

        updateContinueButtonState()

        btnContinue.setOnClickListener {
            val lastRomPath = prefs.getString("last_rom_path", null)
            if (lastRomPath != null && File(lastRomPath).exists()) {
                val intent = Intent(this, EmulatorActivity::class.java).apply {
                    putExtra("ROM_PATH", lastRomPath)
                }
                startActivity(intent)
            } else {
                Toast.makeText(this, "No hay juego previo para continuar", Toast.LENGTH_SHORT).show()
            }
        }

        btnNewGame.setOnClickListener {
            romPicker.launch(arrayOf("*/*"))
        }

        btnOptions.setOnClickListener {
            Toast.makeText(this, "Opciones próximamente", Toast.LENGTH_SHORT).show()
        }

        btnHelp.setOnClickListener {
            Toast.makeText(this, "FirstDS - melonDS Core", Toast.LENGTH_LONG).show()
        }

        btnExit.setOnClickListener {
            finishAffinity()
        }
    }

    override fun onResume() {
        super.onResume()
        updateContinueButtonState()
    }

    private fun updateContinueButtonState() {
        val lastRom = prefs.getString("last_rom_path", null)
        if (lastRom != null && File(lastRom).exists()) {
            btnContinue.setTextColor(Color.parseColor("#4F357E"))
            btnContinue.isClickable = true
        } else {
            btnContinue.setTextColor(Color.parseColor("#C2BFCC"))
            btnContinue.isClickable = false
        }
    }

    private fun launchEmulatorWithRom(uri: Uri) {
        try {
            Toast.makeText(this, "Cargando juego...", Toast.LENGTH_SHORT).show()
            val persistentRom = File(filesDir, "current_game.nds")
            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(persistentRom).use { output ->
                    input.copyTo(output)
                }
            }

            prefs.edit().putString("last_rom_path", persistentRom.absolutePath).apply()

            val intent = Intent(this, EmulatorActivity::class.java).apply {
                putExtra("ROM_PATH", persistentRom.absolutePath)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Error al abrir ROM: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
