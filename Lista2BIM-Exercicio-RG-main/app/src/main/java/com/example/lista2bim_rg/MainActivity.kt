package com.example.lista2bim_rg

import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.apprglistbruno.mainPageActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val imageInit = findViewById<ImageView>(R.id.imageRg)

        imageInit.setOnClickListener{
            val intent = Intent(this, mainPageActivity::class.java)
            startActivity(intent)
        }
    }
}