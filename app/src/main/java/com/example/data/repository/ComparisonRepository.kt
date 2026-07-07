package com.example.data.repository

import com.example.data.dao.ComparisonDao
import com.example.data.entity.ComparisonItem
import com.example.data.entity.ComparisonSession
import kotlinx.coroutines.flow.Flow

class ComparisonRepository(private val comparisonDao: ComparisonDao) {
    val allSessions: Flow<List<ComparisonSession>> = comparisonDao.getAllSessions()

    fun getItemsForSession(sessionId: Int): Flow<List<ComparisonItem>> =
        comparisonDao.getItemsForSession(sessionId)

    suspend fun getSessionById(id: Int): ComparisonSession? =
        comparisonDao.getSessionById(id)

    suspend fun insertSession(session: ComparisonSession): Long =
        comparisonDao.insertSession(session)

    suspend fun updateSession(session: ComparisonSession) =
        comparisonDao.updateSession(session)

    suspend fun insertItem(item: ComparisonItem): Long =
        comparisonDao.insertItem(item)

    suspend fun updateItem(item: ComparisonItem) =
        comparisonDao.updateItem(item)

    suspend fun deleteItem(item: ComparisonItem) =
        comparisonDao.deleteItem(item)

    suspend fun deleteSession(session: ComparisonSession) =
        comparisonDao.deleteSession(session)
}
