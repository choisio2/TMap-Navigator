package com.aivy.navigator.walking

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.aivy.navigator.MainActivity
import com.aivy.navigator.database.AppDatabase
import com.aivy.navigator.database.entity.DailyStepEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class PedometerService : Service(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var stepSensor: Sensor? = null
    private lateinit var notificationManager: NotificationManager
    // 팝업 스와이프해도 주기적으로 다시 띄우기, DB 작업도 처리할 코루틴 스코프
    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())

    companion object {
        private const val CHANNEL_ID = "PedometerChannel"
        private const val NOTIFICATION_ID = 9988
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

        stepSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val prefs = getSharedPreferences("pedometer_prefs", Context.MODE_PRIVATE)
        val todaySteps = prefs.getInt("today_total_steps", 0)
        startForeground(NOTIFICATION_ID, createNotification(todaySteps))
        startPeriodicNotificationUpdate()

        return START_STICKY
    }

    // 1분마다 팝업 강제로 다시 띄우는 함수
    private fun startPeriodicNotificationUpdate() {
        serviceScope.launch {
            while (isActive) {
                delay(60 * 1000L)

                // 최신 걸음 수를 가져와서 알림 다시 쏘기
                val prefs = getSharedPreferences("pedometer_prefs", Context.MODE_PRIVATE)
                val todaySteps = prefs.getInt("today_total_steps", 0)
                notificationManager.notify(NOTIFICATION_ID, createNotification(todaySteps))
            }
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_STEP_COUNTER) {
            val currentSensorSteps = event.values[0].toInt()
            val todaySteps = calculateAndUpdateSteps(currentSensorSteps)
            notificationManager.notify(NOTIFICATION_ID, createNotification(todaySteps))
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    // 걸음 수 변화량을 계산하고 누적 (DB 동기화 포함)
    private fun calculateAndUpdateSteps(currentSensorSteps: Int): Int {
        val prefs = getSharedPreferences("pedometer_prefs", Context.MODE_PRIVATE)
        val todayDateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val savedDateStr = prefs.getString("saved_date", "")

        var startOfDaySteps = prefs.getInt("start_of_day_steps", currentSensorSteps)

        // 날짜가 바뀌었거나 기기가 재부팅되어 센서 값이 0부터 다시 시작하는 경우 방어 로직
        if (todayDateStr != savedDateStr || currentSensorSteps < startOfDaySteps) {
            startOfDaySteps = currentSensorSteps
            prefs.edit()
                .putString("saved_date", todayDateStr)
                .putInt("start_of_day_steps", startOfDaySteps)
                .putInt("today_total_steps", 0)
                .apply()
        }

        val todaySteps = currentSensorSteps - startOfDaySteps
        val previousTodaySteps = prefs.getInt("today_total_steps", 0)

        // 이전 저장값 대비 순수하게 증가한 걸음 수 계산
        val delta = todaySteps - previousTodaySteps

        if (delta > 0) {
            // SharedPreferences 업데이트 (UI 및 실시간 팝업용)
            prefs.edit()
                .putInt("today_total_steps", todaySteps)
                .apply()

            // Room DB 업데이트
            serviceScope.launch {
                val cal = Calendar.getInstance()
                val y = cal.get(Calendar.YEAR)
                val m = cal.get(Calendar.MONTH) + 1
                val d = cal.get(Calendar.DAY_OF_MONTH)

                val dao = AppDatabase.getDatabase(applicationContext).dailyStepDao()

                val updatedCount = dao.updateSteps(y, m, d, todaySteps)

                if (updatedCount == 0) {
                    dao.insertDailyStep(DailyStepEntity(year = y, month = m, day = d, steps = todaySteps))
                }
            }
        }

        return todaySteps
    }

    private fun createNotification(steps: Int): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("오늘의 걷기")
            .setContentText("현재 ${String.format("%,d", steps)}걸음 걸었습니다.")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        sensorManager.unregisterListener(this)
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "만보기 상태", NotificationManager.IMPORTANCE_LOW
            )
            channel.setSound(null, null)
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }
}