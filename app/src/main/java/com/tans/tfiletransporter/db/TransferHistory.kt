package com.tans.tfiletransporter.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transfer_history")
data class TransferHistory(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val fileName: String,
    val fileSize: Long,
    val timestamp: Long,
    val remoteDevice: String,
    val isSend: Boolean,
    val filePath: String? = null
)
