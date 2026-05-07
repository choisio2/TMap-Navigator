package com.aivy.navigator

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Button
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.aivy.navigator.running.RunningReadyActivity
import com.aivy.navigator.walking.PedometerService
import com.aivy.navigator.walking.WalkingReadyActivity

class MainActivity : AppCompatActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        startPedometerService()
    }

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

        // 워킹 대시보드 화면으로 이동
        val btnWalking= findViewById<Button>(R.id.btnWalking)
        btnWalking.setOnClickListener {
            val intent = Intent(this, WalkingReadyActivity::class.java)
            startActivity(intent)
        }

        // 앱이 켜질 때 권한 확인 후 만보기 켜기 실행
        checkAndRequestPermissions()
    }

    // 안드로이드 버전에 맞춰 필요한 권한을 묻는 함수
    private fun checkAndRequestPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        // 만보기 센서 권한 (안드로이드 10 이상)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            permissionsToRequest.add(Manifest.permission.ACTIVITY_RECOGNITION)
        }

        // 상단 알림 권한 (안드로이드 13 이상)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        if (permissionsToRequest.isNotEmpty()) {
            requestPermissionLauncher.launch(permissionsToRequest.toTypedArray())
        } else {
           startPedometerService()
        }
    }

    // 만보기 백그라운드 서비스 시작 함수
    private fun startPedometerService() {
        val serviceIntent = Intent(this, PedometerService::class.java)
        ContextCompat.startForegroundService(this, serviceIntent)
    }
}