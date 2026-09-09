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
            webServer = SimpleWebServer(8080, selectedFiles)
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

class SimpleWebServer(port: Int, private var files: List<File>) : NanoHTTPD(port) {
    
    fun updateFiles(newFiles: List<File>) {
        files = newFiles
    }

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri
        
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
                return newFixedLengthResponse(Response.Status.OK, mime, fis, file.length())
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not Found")
    }

    private fun buildHtml(files: List<File>): String {
        val sb = StringBuilder()
        sb.append("<!DOCTYPE html><html><head><meta charset=\"UTF-8\">")
        sb.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">")
        sb.append("<title>Share Load Web Share</title>")
        sb.append("<style>body { font-family: sans-serif; padding: 20px; } a { display: block; padding: 10px; margin-bottom: 10px; background-color: #f0f0f0; text-decoration: none; color: #333; border-radius: 5px; } </style>")
        sb.append("</head><body>")
        sb.append("<h1>Shared Files</h1>")
        
        if (files.isEmpty()) {
            sb.append("<p>No files shared.</p>")
        } else {
            for (file in files) {
                sb.append("<a href=\"/").append(file.name).append("\">").append(file.name).append("</a>")
            }
        }
        
        sb.append("</body></html>")
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
