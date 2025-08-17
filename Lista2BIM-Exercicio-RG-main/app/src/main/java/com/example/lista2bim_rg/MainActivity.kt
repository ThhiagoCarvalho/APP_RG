package com.example.lista2bim_rg

import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import com.example.apprglistbruno.mainPageActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Mantemos o mesmo layout file para evitar mexer no Manifest
        setContentView(R.layout.activity_main)

        val imageInit = findViewById<ImageView>(R.id.imageRg)

        // Toque na imagem para abrir a próxima tela com uma transição suave
        imageInit.setOnClickListener {
            val intent = Intent(this, mainPageActivity::class.java)
            startActivity(intent)
            // pequena transição para diferenciar do original
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }
    }
}