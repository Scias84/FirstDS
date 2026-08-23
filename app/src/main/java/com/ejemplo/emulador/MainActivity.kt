package com.ejemplo.emulador

import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Lista de botones con sus nombres para probar pulsaciones
        val buttons = mapOf(
            R.id.btn_a to "A",
            R.id.btn_b to "B",
            R.id.btn_x to "X",
            R.id.btn_y to "Y",
            R.id.btn_l to "L",
            R.id.btn_r to "R",
            R.id.btn_up to "Arriba",
            R.id.btn_down to "Abajo",
            R.id.btn_left to "Izquierda",
            R.id.btn_right to "Derecha",
            R.id.btn_start to "Start",
            R.id.btn_select to "Select"
        )

        buttons.forEach { (id, name) ->
            findViewById<Button>(id)?.setOnClickListener {
                Toast.makeText(this, "Botón $name presionado", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
