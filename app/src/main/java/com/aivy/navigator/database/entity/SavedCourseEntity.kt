package com.aivy.navigator.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "saved_courses")
data class SavedCourseEntity(
    @PrimaryKey val resId: Int,
    val name: String
)