package com.example.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "comparison_sessions")
data class ComparisonSession(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val category: String, // "WEIGHT", "VOLUME", "COUNT"
    val referenceValue: Double, // e.g. 100.0, 1.0, etc.
    val referenceUnit: String, // e.g. "g", "ml", "개"
    val timestamp: Long = System.currentTimeMillis(),
    val isSaved: Boolean = false
)
