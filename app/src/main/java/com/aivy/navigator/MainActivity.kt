package com.aivy.navigator

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.aivy.navigator.running.RunningReadyActivity
import com.aivy.navigator.walking.WalkingActivity
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

        // 티맵으로 이동
        val btnTmapStart = findViewById<Button>(R.id.btnTmap)
        btnTmapStart.setOnClickListener {
            val intent = Intent(this, TmapsActivity::class.java)
            startActivity(intent)
        }

        // 러닝 대시보드 화면으로 이동
        val btnRunning= findViewById<Button>(R.id.btnRunning)
        btnRunning.setOnClickListener {
            val intent = Intent(this, RunningReadyActivity::class.java)
            startActivity(intent)
        }

        val btnWalking= findViewById<Button>(R.id.btnWalking)
        btnWalking.setOnClickListener {
            val intent = Intent(this, WalkingActivity::class.java)
            startActivity(intent)
        }

    }
}