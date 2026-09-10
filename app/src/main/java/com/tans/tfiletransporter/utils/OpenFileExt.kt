package com.tans.tfiletransporter.utils

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.core.content.FileProvider
import com.tans.tfiletransporter.R
import java.io.File

fun Context.openFile(file: File) {
    if (!file.exists()) {
        Toast.makeText(this, getString(R.string.history_file_not_found), Toast.LENGTH_SHORT).show()
        return
    }
    val ext = file.extension.lowercase()
    val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "*/*"
    val uri = FileProvider.getUriForFile(this, "${packageName}.provider", file)
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, mimeType)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    try {
        startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(this, getString(R.string.history_no_app_found), Toast.LENGTH_SHORT).show()
    }
}

fun Context.openUri(uri: android.net.Uri, mimeType: String) {
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, mimeType)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    try {
        startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(this, getString(R.string.history_no_app_found), Toast.LENGTH_SHORT).show()
    }
}
