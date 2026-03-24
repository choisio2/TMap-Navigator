package com.aivy.navigator

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kotlin.jvm.java

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // 버튼 클릭 시 네이버맵으로 이동
//        val btnNaverStart = findViewById<Button>(R.id.btnNaverMaps)
//        btnNaverStart.setOnClickListener {
//            val intent = Intent(this, NaverMapsActivity::class.java)
//            startActivity(intent)
//        }

        // 티맵으로 이동
        val btnTmapStart = findViewById<Button>(R.id.btnTmap)
        btnTmapStart.setOnClickListener {
            val intent = Intent(this, TmapsActivity::class.java)
            startActivity(intent)
        }

    }
}