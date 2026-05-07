package com.aivy.navigator.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

// 운동 전체 기록을 저장하는 테이블
@Entity(tableName = "workout_records")
data class WorkoutRecordEntity(
    @PrimaryKey(autoGenerate = true) val workoutId: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val totalDistance: Double,
    val totalTimeElapsed: Long,
    val calories: Int,
    val averagePaceStr: String
)