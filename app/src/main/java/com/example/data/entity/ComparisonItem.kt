package com.example.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "comparison_items",
    foreignKeys = [
        ForeignKey(
            entity = ComparisonSession::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("sessionId")]
)
data class ComparisonItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val sessionId: Int,
    val name: String,
    val price: Double,         // Price in KRW
    val capacity: Double,      // Capacity value (e.g. 500, 2)
    val unit: String,          // e.g. "g", "kg", "ml", "L", "개"
    val bundleCount: Int = 1,  // Pack size or bundle count
    val timestamp: Long = System.currentTimeMillis()
)
