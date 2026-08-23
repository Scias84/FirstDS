package com.ejemplo.emulador

import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val btnA = findViewById<Button>(R.id.btn_a)
        val btnB = findViewById<Button>(R.id.btn_b)

        btnA.setOnClickListener {
            Toast.makeText(this, "Botón A presionado", Toast.LENGTH_SHORT).show()
        }

        btnB.setOnClickListener {
            Toast.makeText(this, "Botón B presionado", Toast.LENGTH_SHORT).show()
        }
    }
}
