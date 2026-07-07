package com.example.data.dao

import androidx.room.*
import com.example.data.entity.ComparisonItem
import com.example.data.entity.ComparisonSession
import kotlinx.coroutines.flow.Flow

@Dao
interface ComparisonDao {
    @Query("SELECT * FROM comparison_sessions ORDER BY timestamp DESC")
    fun getAllSessions(): Flow<List<ComparisonSession>>

    @Query("SELECT * FROM comparison_sessions WHERE id = :id")
    suspend fun getSessionById(id: Int): ComparisonSession?

    @Query("SELECT * FROM comparison_items WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun getItemsForSession(sessionId: Int): Flow<List<ComparisonItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: ComparisonSession): Long

    @Update
    suspend fun updateSession(session: ComparisonSession)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: ComparisonItem): Long

    @Update
    suspend fun updateItem(item: ComparisonItem)

    @Delete
    suspend fun deleteItem(item: ComparisonItem)

    @Query("DELETE FROM comparison_items WHERE sessionId = :sessionId")
    suspend fun deleteItemsForSession(sessionId: Int)

    @Delete
    suspend fun deleteSession(session: ComparisonSession)
}
