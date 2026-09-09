package com.tans.tfiletransporter.ui.webshare

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.tans.tfiletransporter.R
import com.tans.tfiletransporter.databinding.ActivityWebShareBinding
import com.tans.tfiletransporter.databinding.ItemHistoryBinding
import com.tans.tfiletransporter.toSizeString
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.math.BigInteger
import java.net.InetAddress
import java.nio.ByteOrder
import fi.iki.elonen.NanoHTTPD

class WebShareActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWebShareBinding
    private var webServer: SimpleWebServer? = null
    private val selectedFiles = mutableListOf<File>()
    private lateinit var adapter: SharedFileAdapter

    private val selectFilesLauncher = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) {
            handleSelectedUris(uris)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWebShareBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener {
            finish()
        }

        adapter = SharedFileAdapter(selectedFiles)
        binding.rvFiles.layoutManager = LinearLayoutManager(this)
        binding.rvFiles.adapter = adapter

        binding.btnSelectFiles.setOnClickListener {
            selectFilesLauncher.launch("*/*")
        }

        binding.btnToggleServer.setOnClickListener {
            if (webServer == null) {
                startServer()
            } else {
                stopServer()
            }
        }
    }

    private fun handleSelectedUris(uris: List<Uri>) {
        val files = uris.mapNotNull { uri ->
            try {
                val contentResolver = contentResolver
                val cursor = contentResolver.query(uri, null, null, null, null)
                cursor?.use {
                    if (it.moveToFirst()) {
                        val displayName = it.getString(it.getColumnIndexOrThrow(android.provider.OpenableColumns.DISPLAY_NAME))
                        
                        val file = File(cacheDir, displayName)
                        contentResolver.openInputStream(uri)?.use { inputStream ->
                            file.outputStream().use { outputStream ->
                                inputStream.copyTo(outputStream)
                            }
                        }
                        return@mapNotNull file
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            null
        }
        
        selectedFiles.clear()
        selectedFiles.addAll(files)
        adapter.notifyDataSetChanged()
        
        if (selectedFiles.isNotEmpty() && webServer != null) {
            webServer?.updateFiles(selectedFiles)
        }
    }

    private fun startServer() {
        if (selectedFiles.isEmpty()) {
            Toast.makeText(this, R.string.web_share_select_files, Toast.LENGTH_SHORT).show()
            return
        }

        val ipAddress = getLocalIpAddress()
        if (ipAddress == null) {
            Toast.makeText(this, R.string.web_share_connect_wifi, Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val downloadDir = File(com.tans.tfiletransporter.Settings.getDownloadDir())
            webServer = SimpleWebServer(8080, selectedFiles, downloadDir)
            webServer?.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
            
            binding.tvAddress.text = "http://$ipAddress:8080"
            binding.btnToggleServer.text = getString(R.string.web_share_stop)
            Toast.makeText(this, R.string.web_share_running, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, R.string.web_share_failed, Toast.LENGTH_SHORT).show()
            e.printStackTrace()
            webServer = null
        }
    }

    private fun stopServer() {
        webServer?.stop()
        webServer = null
        binding.tvAddress.text = ""
        binding.btnToggleServer.text = getString(R.string.web_share_start)
    }

    private fun getLocalIpAddress(): String? {
        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val wifiInfo = wifiManager.connectionInfo
        val ipInt = wifiInfo.ipAddress
        if (ipInt == 0) return null

        val ipAddress = if (ByteOrder.nativeOrder().equals(ByteOrder.LITTLE_ENDIAN)) {
            Integer.reverseBytes(ipInt)
        } else {
            ipInt
        }
        val ipByteArray = BigInteger.valueOf(ipAddress.toLong()).toByteArray()
        val ipAddressStr = try {
            InetAddress.getByAddress(ipByteArray).hostAddress
        } catch (e: Exception) {
            null
        }
        return ipAddressStr
    }

    override fun onDestroy() {
        super.onDestroy()
        stopServer()
    }
}

class SimpleWebServer(port: Int, private var files: List<File>, private val downloadDir: File) : NanoHTTPD(port) {
    
    fun updateFiles(newFiles: List<File>) {
        files = newFiles
    }

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri
        val method = session.method
        
        if (Method.POST == method && uri == "/upload") {
            try {
                val filesMap = HashMap<String, String>()
                session.parseBody(filesMap)
                
                val params = session.parameters
                val fileNames = params["file"] // NanoHTTPD gives original filename in parameters or through part header, it's a bit tricky.
                
                // For a robust upload, nanohttpd handles it by putting temp file path in filesMap.
                // We'll iterate over filesMap to move temp files to our download directory.
                for ((key, tempFilePath) in filesMap) {
                    val originalFileName = params[key]?.firstOrNull() ?: "uploaded_file_${System.currentTimeMillis()}"
                    val tempFile = File(tempFilePath)
                    if (tempFile.exists()) {
                        val destFile = File(downloadDir, originalFileName)
                        tempFile.copyTo(destFile, overwrite = true)
                    }
                }
                
                return newFixedLengthResponse(Response.Status.REDIRECT, "text/plain", "").apply {
                    addHeader("Location", "/")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", "Upload failed: ${e.message}")
            }
        }
        
        if (uri == "/") {
            val html = buildHtml(files)
            return newFixedLengthResponse(Response.Status.OK, "text/html", html)
        }
        
        val fileName = uri.substring(1) // Remove leading slash
        val file = files.find { it.name == fileName }
        
        if (file != null && file.exists()) {
            try {
                val fis = FileInputStream(file)
                val mime = getMimeTypeForFile(uri)
                return newFixedLengthResponse(Response.Status.OK, mime, fis, file.length()).apply {
                    addHeader("Content-Disposition", "attachment; filename=\"${file.name}\"")
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not Found")
    }

    private fun buildHtml(files: List<File>): String {
        val sb = StringBuilder()
        sb.append("""
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Share Load - Web Share</title>
                <link href="https://fonts.googleapis.com/css2?family=Poppins:wght@400;600&display=swap" rel="stylesheet">
                <style>
                    body { font-family: 'Poppins', sans-serif; background-color: #f4f7f6; color: #333; margin: 0; padding: 0; }
                    .container { max-width: 800px; margin: 40px auto; background: white; border-radius: 12px; box-shadow: 0 4px 15px rgba(0,0,0,0.05); overflow: hidden; padding: 30px; }
                    h1 { text-align: center; color: #2c3e50; font-weight: 600; margin-bottom: 30px; }
                    
                    .upload-section { background: #f8faff; border: 2px dashed #4a90e2; padding: 30px; border-radius: 12px; text-align: center; margin-bottom: 30px; transition: 0.3s; }
                    .upload-section:hover { background: #f1f5ff; border-color: #357abd; }
                    .upload-section input[type="file"] { display: none; }
                    .upload-label { display: inline-block; padding: 12px 25px; background: #4a90e2; color: white; border-radius: 8px; cursor: pointer; font-weight: 600; transition: 0.3s; }
                    .upload-label:hover { background: #357abd; }
                    .submit-btn { margin-top: 15px; padding: 10px 20px; border: none; background: #2ecc71; color: white; border-radius: 8px; cursor: pointer; font-weight: 600; display: none; }
                    .submit-btn:hover { background: #27ae60; }
                    
                    .file-list { display: grid; grid-template-columns: repeat(auto-fill, minmax(200px, 1fr)); gap: 15px; }
                    .file-card { background: #fff; border: 1px solid #e1e8ed; border-radius: 10px; padding: 15px; text-align: center; transition: transform 0.2s, box-shadow 0.2s; text-decoration: none; color: inherit; display: flex; flex-direction: column; align-items: center; }
                    .file-card:hover { transform: translateY(-5px); box-shadow: 0 5px 15px rgba(0,0,0,0.1); border-color: #4a90e2; }
                    .file-icon { font-size: 40px; margin-bottom: 10px; color: #4a90e2; }
                    .file-name { font-size: 14px; font-weight: 600; word-break: break-all; margin-bottom: 5px; }
                    .file-size { font-size: 12px; color: #7f8c8d; }
                    
                    .empty-state { text-align: center; color: #7f8c8d; padding: 40px; font-style: italic; }
                </style>
                <script>
                    function handleFileSelect(input) {
                        if(input.files.length > 0) {
                            document.getElementById('submitBtn').style.display = 'inline-block';
                            document.getElementById('uploadLabel').innerText = input.files.length + ' file(s) selected';
                        }
                    }
                </script>
            </head>
            <body>
                <div class="container">
                    <h1>🚀 Share Load - Web Share</h1>
                    
                    <div class="upload-section">
                        <h3>Upload Files to Phone</h3>
                        <form action="/upload" method="post" enctype="multipart/form-data">
                            <label for="fileUpload" class="upload-label" id="uploadLabel">Choose Files</label>
                            <input type="file" id="fileUpload" name="file" multiple onchange="handleFileSelect(this)">
                            <br>
                            <button type="submit" class="submit-btn" id="submitBtn">Send to Phone</button>
                        </form>
                    </div>

                    <h3>Files available to Download</h3>
        """.trimIndent())
        
        sb.append("<div class=\"file-list\">")
        if (files.isEmpty()) {
            sb.append("</div><div class=\"empty-state\">No files shared from phone yet.</div>")
        } else {
            for (file in files) {
                val size = file.length() / 1024
                val sizeStr = if (size > 1024) "${size / 1024} MB" else "$size KB"
                val icon = if (file.name.endsWith(".jpg") || file.name.endsWith(".png")) "🖼️" 
                           else if (file.name.endsWith(".mp4")) "🎬"
                           else if (file.name.endsWith(".mp3")) "🎵"
                           else "📄"
                           
                sb.append("""
                    <a href="/${file.name}" class="file-card">
                        <div class="file-icon">$icon</div>
                        <div class="file-name">${file.name}</div>
                        <div class="file-size">$sizeStr</div>
                    </a>
                """.trimIndent())
            }
            sb.append("</div>")
        }
        
        sb.append("""
                </div>
            </body>
            </html>
        """.trimIndent())
        return sb.toString()
    }
}

class SharedFileAdapter(private val items: List<File>) : RecyclerView.Adapter<SharedFileAdapter.FileViewHolder>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
        val binding = ItemHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return FileViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
        val file = items[position]
        holder.binding.tvFileName.text = file.name
        holder.binding.tvDevice.text = file.absolutePath
        holder.binding.tvSizeTime.text = file.length().toSizeString()
        holder.binding.ivType.setImageResource(R.drawable.share_variant_outline)
    }

    override fun getItemCount(): Int = items.size
    
    class FileViewHolder(val binding: ItemHistoryBinding) : RecyclerView.ViewHolder(binding.root)
}
