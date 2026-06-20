package com.meetloggerv2.data.local.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(items: List<HistoryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: HistoryEntity)

    @Query("SELECT * FROM history WHERE userId = :userId ORDER BY timestampMillis DESC")
    suspend fun getHistory(userId: String): List<HistoryEntity>

    @Query("SELECT * FROM history WHERE userId = :userId ORDER BY timestampMillis DESC")
    fun getHistoryFlow(userId: String): Flow<List<HistoryEntity>>

    @Query("DELETE FROM history WHERE userId = :userId AND fileName = :fileName")
    suspend fun deleteHistory(userId: String, fileName: String)

    @Query("DELETE FROM history WHERE userId = :userId")
    suspend fun clearHistory(userId: String)

    /**
     * Merges the server history into the local cache. The history track is
     * append-only server-side (never touched by rename/delete/copy), so we
     * upsert rather than clear+insert. This preserves optimistic local entries
     * (e.g. an upload that just started) until the authoritative server row
     * arrives and replaces it by primary key.
     */
    @Transaction
    suspend fun syncHistory(userId: String, items: List<HistoryEntity>) {
        insertHistory(items)
    }
}
