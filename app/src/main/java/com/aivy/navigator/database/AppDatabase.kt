package com.aivy.navigator.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.aivy.navigator.database.dao.RunningDao
import com.aivy.navigator.database.entity.SavedCourseEntity
import com.aivy.navigator.database.entity.SplitRecordEntity
import com.aivy.navigator.database.entity.WorkoutRecordEntity
import com.aivy.navigator.database.dao.WalkingDao
import com.aivy.navigator.database.entity.WalkingRecordEntity

@Database(
    entities = [
        SavedCourseEntity::class,
        WorkoutRecordEntity::class,
        SplitRecordEntity::class,
        WalkingRecordEntity::class
    ],
    version = 3,
    exportSchema = false
)

abstract class AppDatabase : RoomDatabase() {

    abstract fun runningDao(): RunningDao
    abstract fun walkingDao(): WalkingDao

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