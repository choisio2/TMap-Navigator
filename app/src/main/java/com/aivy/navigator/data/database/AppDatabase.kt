package com.aivy.navigator.data.database

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

// 데이터 모델 (Entities)

@Entity(tableName = "saved_courses")
data class SavedCourseEntity(
    @PrimaryKey val resId: Int,
    val name: String
)

@Entity(tableName = "workout_records")
data class WorkoutRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val distance: Double,
    val timeElapsed: Long,
    val calories: Int,
    val paceStr: String,
    val splitsCsv: String
)

// 데이터 접근 객체 (DAO)

@Dao
interface RunningDao {

    // 코스 관련
    @Query("SELECT * FROM saved_courses")
    fun getAllSavedCourses(): Flow<List<SavedCourseEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCourse(course: SavedCourseEntity)

    @Delete
    suspend fun deleteCourse(course: SavedCourseEntity)

    // 운동 기록 관련
    @Query("SELECT * FROM workout_records ORDER BY timestamp DESC")
    fun getAllWorkouts(): Flow<List<WorkoutRecordEntity>>

    @Insert
    suspend fun insertWorkout(record: WorkoutRecordEntity)

    @Query("SELECT * FROM workout_records ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLastWorkout(): WorkoutRecordEntity?

    @Delete
    suspend fun deleteWorkout(record: WorkoutRecordEntity)
}


// 데이터베이스 본체

@Database(entities = [SavedCourseEntity::class, WorkoutRecordEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun runningDao(): RunningDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "aivy_running_db"
                )
                    .fallbackToDestructiveMigration()
                    .build()

                INSTANCE = instance
                instance
            }
        }
    }
}