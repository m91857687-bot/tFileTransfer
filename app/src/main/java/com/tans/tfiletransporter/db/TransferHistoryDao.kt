package com.tans.tfiletransporter.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TransferHistoryDao {
    @Insert
    fun insert(history: TransferHistory): Long

    @Query("SELECT * FROM transfer_history ORDER BY timestamp DESC")
    fun getAllHistory(): Flow<List<TransferHistory>>

    @Query("DELETE FROM transfer_history")
    fun clearAll()
}
