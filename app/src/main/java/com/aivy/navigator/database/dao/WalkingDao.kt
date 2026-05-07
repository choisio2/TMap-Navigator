package com.aivy.navigator.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.aivy.navigator.database.entity.WalkingRecordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WalkingDao {
    @Query("SELECT * FROM walking_records ORDER BY timestamp DESC")
    fun getAllWalks(): Flow<List<WalkingRecordEntity>>

    @Insert
    suspend fun insertWalk(record: WalkingRecordEntity)
}