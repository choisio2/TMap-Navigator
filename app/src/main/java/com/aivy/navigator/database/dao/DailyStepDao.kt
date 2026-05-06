package com.aivy.navigator.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.aivy.navigator.database.entity.DailyStepEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyStepDao {
    @Query("SELECT * FROM daily_steps")
    fun getAllDailySteps(): Flow<List<DailyStepEntity>>

    @Query("SELECT * FROM daily_steps WHERE year = :year AND month = :month AND day = :day LIMIT 1")
    suspend fun getDailyStep(year: Int, month: Int, day: Int): DailyStepEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDailyStep(dailyStep: DailyStepEntity)

    // 이미 존재하는 날짜면 걸음 수만 덮어쓰기 위한 쿼리
    @Query("UPDATE daily_steps SET steps = :steps WHERE year = :year AND month = :month AND day = :day")
    suspend fun updateSteps(year: Int, month: Int, day: Int, steps: Int): Int
}