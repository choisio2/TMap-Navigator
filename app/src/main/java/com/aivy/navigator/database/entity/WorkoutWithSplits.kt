package com.aivy.navigator.database.entity

import androidx.room.Embedded
import androidx.room.Relation

// 운동 기록과 해당 운동의 모든 구간 기록을 한 번에 조회하기 위한 Relation 클래스

data class WorkoutWithSplits(
    @Embedded val workout: WorkoutRecordEntity,

    @Relation(
        parentColumn = "workoutId",
        entityColumn = "parentWorkoutId"
    )
    val splits: List<SplitRecordEntity>
)