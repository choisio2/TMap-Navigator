package com.aivy.navigator.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey


// 각 1km 구간의 기록을 저장하는 테이블
// 부모 테이블(workout_records)과 foreign key로 연결
@Entity(
    tableName = "split_records",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutRecordEntity::class,
            parentColumns = ["workoutId"],
            childColumns = ["parentWorkoutId"],
            onDelete = ForeignKey.CASCADE // 부모 운동 기록이 삭제되면 구간 기록도 함께 삭제
        )
    ],
    indices = [Index("parentWorkoutId")] // 외래 키 검색 속도 향상을 위한 인덱싱
)
data class SplitRecordEntity(
    @PrimaryKey(autoGenerate = true) val splitId: Int = 0,
    val parentWorkoutId: Int = 0,
    val kmIndex: Int,
    val timeElapsedSec: Long,
    val cumulativeTimeSec: Long
)