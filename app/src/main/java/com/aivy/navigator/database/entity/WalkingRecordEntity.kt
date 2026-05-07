package com.aivy.navigator.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "walking_records")
data class WalkingRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val totalDistance: Double,
    val totalTimeElapsed: Long,
    val paceStr: String,
    val calories: Int,
    val steps: Int,
    val averageSpeed: Double // km/h
)