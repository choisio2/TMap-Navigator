package com.aivy.navigator.database.dao

import androidx.room.*
import com.aivy.navigator.database.entity.SavedCourseEntity
import com.aivy.navigator.database.entity.SplitRecordEntity
import com.aivy.navigator.database.entity.WorkoutRecordEntity
import com.aivy.navigator.database.entity.WorkoutWithSplits
import kotlinx.coroutines.flow.Flow

@Dao
interface RunningDao {

    // --- 코스 관련 ---
    @Query("SELECT * FROM saved_courses")
    fun getAllSavedCourses(): Flow<List<SavedCourseEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCourse(course: SavedCourseEntity)

    @Delete
    suspend fun deleteCourse(course: SavedCourseEntity)


    // --- 운동 기록 관련 ---
    @Insert
    suspend fun insertWorkoutOnly(record: WorkoutRecordEntity): Long

    @Insert
    suspend fun insertSplits(splits: List<SplitRecordEntity>)


     // 운동 기록을 먼저 저장한 후 발급된 ID를 구간 기록들에 넣어준 뒤 함께 저장
    @Transaction
    suspend fun insertWorkoutWithSplits(workout: WorkoutRecordEntity, splits: List<SplitRecordEntity>) {
        val newWorkoutId = insertWorkoutOnly(workout).toInt()

        val splitsWithParentId = splits.map {
            it.copy(parentWorkoutId = newWorkoutId)
        }

        insertSplits(splitsWithParentId)
    }

    @Transaction
    @Query("SELECT * FROM workout_records ORDER BY timestamp DESC")
    fun getAllWorkoutsWithSplits(): Flow<List<WorkoutWithSplits>>

    @Delete
    suspend fun deleteWorkout(record: WorkoutRecordEntity)
}