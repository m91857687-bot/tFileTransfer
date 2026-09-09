package com.tans.tfiletransporter.ui.history

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.ViewGroup
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.tans.tfiletransporter.R
import com.tans.tfiletransporter.databinding.ActivityHistoryBinding
import com.tans.tfiletransporter.databinding.ItemHistoryBinding
import com.tans.tfiletransporter.db.AppDatabase
import com.tans.tfiletransporter.db.TransferHistory
import com.tans.tfiletransporter.toSizeString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class HistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHistoryBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.title = getString(R.string.history_title)
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }

        val adapter = HistoryAdapter(this) { item ->
            openFile(item)
        }
        binding.rvHistory.layoutManager = LinearLayoutManager(this)
        binding.rvHistory.adapter = adapter

        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(this@HistoryActivity)
            db.transferHistoryDao().getAllHistory().collectLatest { history ->
                withContext(Dispatchers.Main) {
                    adapter.submitList(history)
                }
            }
        }
    }

    private fun openFile(item: TransferHistory) {
        if (item.isSend) return
        
        val file = File(item.filePath ?: return)
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
}

class HistoryAdapter(private val context: android.content.Context, private val onItemClick: (TransferHistory) -> Unit) : RecyclerView.Adapter<HistoryAdapter.HistoryViewHolder>() {
    private var items = emptyList<TransferHistory>()

    fun submitList(newItems: List<TransferHistory>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val binding = ItemHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return HistoryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        val item = items[position]
        holder.binding.tvFileName.text = item.fileName
        holder.binding.tvDevice.text = if (item.isSend) context.getString(R.string.history_sent_to, item.remoteDevice) else context.getString(R.string.history_received_from, item.remoteDevice)
        val dateString = DateFormat.format("yyyy-MM-dd HH:mm", item.timestamp).toString()
        holder.binding.tvSizeTime.text = "${item.fileSize.toSizeString()} • $dateString"
        
        if (item.isSend) {
            holder.binding.ivType.setImageResource(R.drawable.share_variant_outline)
        } else {
            holder.binding.ivType.setImageResource(R.drawable.download_outline)
        }

        holder.itemView.setOnClickListener {
            onItemClick(item)
        }
    }

    override fun getItemCount(): Int = items.size
    
    class HistoryViewHolder(val binding: ItemHistoryBinding) : RecyclerView.ViewHolder(binding.root)
}
