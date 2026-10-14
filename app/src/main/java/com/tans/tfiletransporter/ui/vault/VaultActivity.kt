package com.tans.tfiletransporter.ui.vault

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.OpenableColumns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.tans.tfiletransporter.R
import com.tans.tfiletransporter.databinding.ActivityVaultBinding
import com.tans.tfiletransporter.databinding.ItemVaultFileBinding
import com.tans.tfiletransporter.toSizeString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class VaultItem(
    val id: String,
    val originalName: String,
    val originalPath: String,
    val vaultFileName: String,
    val size: Long,
    val dateAdded: Long,
    val mimeType: String
)

class VaultActivity : AppCompatActivity() {

    private lateinit var binding: ActivityVaultBinding
    private val vaultItems = mutableListOf<VaultItem>()
    private val displayedItems = mutableListOf<VaultItem>()
    private lateinit var vaultAdapter: VaultAdapter

    private var currentPinStep = PinStep.UNLOCK
    private var tempPinToConfirm = ""
    private var enteredPin = ""

    private var selectedTab = TabType.ALL

    enum class PinStep {
        UNLOCK,
        SETUP_FIRST,
        CONFIRM_NEW
    }

    enum class TabType {
        ALL, PHOTOS, VIDEOS, DOCS
    }

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri>? ->
        if (!uris.isNullOrEmpty()) {
            importUrisToVault(uris)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVaultBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolBar.setNavigationOnClickListener {
            finish()
        }

        setupPinKeypad()
        setupVaultUI()

        checkPinState()
    }

    private fun checkPinState() {
        val prefs = getSharedPreferences(PREFS_VAULT, Context.MODE_PRIVATE)
        val savedPin = prefs.getString(KEY_PIN, null)
        if (savedPin.isNullOrEmpty()) {
            currentPinStep = PinStep.SETUP_FIRST
            binding.tvPinPrompt.text = getString(R.string.vault_setup_pin)
            binding.tvPinSubprompt.text = "Create a 4-digit security PIN for your vault"
        } else {
            currentPinStep = PinStep.UNLOCK
            binding.tvPinPrompt.text = getString(R.string.vault_enter_pin)
            binding.tvPinSubprompt.text = "Protect your private photos and sensitive files"
        }
        resetPinEntry()
    }

    private fun setupPinKeypad() {
        val numButtons = listOf(
            binding.btnKey0 to "0",
            binding.btnKey1 to "1",
            binding.btnKey2 to "2",
            binding.btnKey3 to "3",
            binding.btnKey4 to "4",
            binding.btnKey5 to "5",
            binding.btnKey6 to "6",
            binding.btnKey7 to "7",
            binding.btnKey8 to "8",
            binding.btnKey9 to "9"
        )

        numButtons.forEach { (btn, digit) ->
            btn.setOnClickListener {
                if (enteredPin.length < 4) {
                    enteredPin += digit
                    updatePinDots()
                    if (enteredPin.length == 4) {
                        handlePinCompleted(enteredPin)
                    }
                }
            }
        }

        binding.btnKeyClear.setOnClickListener {
            resetPinEntry()
        }

        binding.btnKeyBackspace.setOnClickListener {
            if (enteredPin.isNotEmpty()) {
                enteredPin = enteredPin.dropLast(1)
                updatePinDots()
            }
        }
    }

    private fun updatePinDots() {
        val dots = listOf(binding.dot1, binding.dot2, binding.dot3, binding.dot4)
        val filledColor = getColor(R.color.teal_200)
        val emptyBg = getDrawable(R.drawable.common_line_button_color_bg)

        dots.forEachIndexed { index, dot ->
            if (index < enteredPin.length) {
                dot.setBackgroundColor(filledColor)
            } else {
                dot.background = emptyBg
            }
        }
    }

    private fun resetPinEntry() {
        enteredPin = ""
        updatePinDots()
    }

    private fun handlePinCompleted(pin: String) {
        val prefs = getSharedPreferences(PREFS_VAULT, Context.MODE_PRIVATE)
        when (currentPinStep) {
            PinStep.UNLOCK -> {
                val savedPin = prefs.getString(KEY_PIN, "")
                if (pin == savedPin) {
                    unlockVault()
                } else {
                    Toast.makeText(this, getString(R.string.vault_pin_wrong), Toast.LENGTH_SHORT).show()
                    resetPinEntry()
                }
            }
            PinStep.SETUP_FIRST -> {
                tempPinToConfirm = pin
                currentPinStep = PinStep.CONFIRM_NEW
                binding.tvPinPrompt.text = getString(R.string.vault_confirm_pin)
                binding.tvPinSubprompt.text = "Please enter the same 4-digit PIN again"
                resetPinEntry()
            }
            PinStep.CONFIRM_NEW -> {
                if (pin == tempPinToConfirm) {
                    prefs.edit().putString(KEY_PIN, pin).apply()
                    Toast.makeText(this, getString(R.string.vault_pin_set_success), Toast.LENGTH_SHORT).show()
                    unlockVault()
                } else {
                    Toast.makeText(this, getString(R.string.vault_pin_mismatch), Toast.LENGTH_SHORT).show()
                    currentPinStep = PinStep.SETUP_FIRST
                    binding.tvPinPrompt.text = getString(R.string.vault_setup_pin)
                    resetPinEntry()
                }
            }
        }
    }

    private fun unlockVault() {
        binding.layoutPin.visibility = View.GONE
        binding.layoutVaultContent.visibility = View.VISIBLE
        loadVaultFiles()
    }

    private fun setupVaultUI() {
        vaultAdapter = VaultAdapter(
            items = displayedItems,
            onItemClick = { openVaultFile(it) },
            onRestoreClick = { restoreVaultFile(it) },
            onDeleteClick = { confirmDeleteVaultFile(it) }
        )

        binding.rvVaultFiles.apply {
            layoutManager = LinearLayoutManager(this@VaultActivity)
            adapter = vaultAdapter
        }

        binding.tabAll.setOnClickListener { setTab(TabType.ALL) }
        binding.tabPhotos.setOnClickListener { setTab(TabType.PHOTOS) }
        binding.tabVideos.setOnClickListener { setTab(TabType.VIDEOS) }
        binding.tabDocs.setOnClickListener { setTab(TabType.DOCS) }

        binding.btnAddToVault.setOnClickListener {
            try {
                filePickerLauncher.launch(arrayOf("*/*"))
            } catch (e: Exception) {
                Toast.makeText(this, "Cannot open file picker: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setTab(tab: TabType) {
        selectedTab = tab
        val activeColor = getColor(R.color.teal_200)
        val normalColor = getColor(R.color.black_1)

        binding.tabAll.setTextColor(if (tab == TabType.ALL) activeColor else normalColor)
        binding.tabPhotos.setTextColor(if (tab == TabType.PHOTOS) activeColor else normalColor)
        binding.tabVideos.setTextColor(if (tab == TabType.VIDEOS) activeColor else normalColor)
        binding.tabDocs.setTextColor(if (tab == TabType.DOCS) activeColor else normalColor)

        filterDisplayedItems()
    }

    private fun getVaultDirectory(): File {
        val dir = File(filesDir, "secure_vault")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun getIndexFile(): File {
        return File(getVaultDirectory(), "vault_index.json")
    }

    private fun loadVaultFiles() {
        lifecycleScope.launch(Dispatchers.IO) {
            val list = mutableListOf<VaultItem>()
            val indexFile = getIndexFile()
            if (indexFile.exists()) {
                try {
                    val jsonStr = indexFile.readText()
                    val array = JSONArray(jsonStr)
                    for (i in 0 until array.length()) {
                        val obj = array.getJSONObject(i)
                        list.add(
                            VaultItem(
                                id = obj.optString("id", UUID.randomUUID().toString()),
                                originalName = obj.optString("originalName", "File"),
                                originalPath = obj.optString("originalPath", ""),
                                vaultFileName = obj.optString("vaultFileName", ""),
                                size = obj.optLong("size", 0L),
                                dateAdded = obj.optLong("dateAdded", System.currentTimeMillis()),
                                mimeType = obj.optString("mimeType", "*/*")
                            )
                        )
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            // Verify file presence on disk
            val vaultDir = getVaultDirectory()
            val validList = list.filter {
                File(vaultDir, it.vaultFileName).exists()
            }

            withContext(Dispatchers.Main) {
                vaultItems.clear()
                vaultItems.addAll(validList)
                filterDisplayedItems()
            }
        }
    }

    private fun saveVaultIndex() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val array = JSONArray()
                vaultItems.forEach {
                    val obj = JSONObject().apply {
                        put("id", it.id)
                        put("originalName", it.originalName)
                        put("originalPath", it.originalPath)
                        put("vaultFileName", it.vaultFileName)
                        put("size", it.size)
                        put("dateAdded", it.dateAdded)
                        put("mimeType", it.mimeType)
                    }
                    array.put(obj)
                }
                getIndexFile().writeText(array.toString())
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun filterDisplayedItems() {
        displayedItems.clear()
        when (selectedTab) {
            TabType.ALL -> displayedItems.addAll(vaultItems)
            TabType.PHOTOS -> displayedItems.addAll(vaultItems.filter { it.mimeType.startsWith("image/") || it.originalName.lowercase().let { n -> n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png") || n.endsWith(".webp") } })
            TabType.VIDEOS -> displayedItems.addAll(vaultItems.filter { it.mimeType.startsWith("video/") || it.originalName.lowercase().let { n -> n.endsWith(".mp4") || n.endsWith(".mkv") || n.endsWith(".3gp") } })
            TabType.DOCS -> displayedItems.addAll(vaultItems.filter {
                val n = it.originalName.lowercase()
                n.endsWith(".pdf") || n.endsWith(".doc") || n.endsWith(".docx") || n.endsWith(".xls") || n.endsWith(".xlsx") || n.endsWith(".txt") || n.endsWith(".apk") || n.endsWith(".zip")
            })
        }
        vaultAdapter.notifyDataSetChanged()

        if (displayedItems.isEmpty()) {
            binding.layoutVaultEmpty.visibility = View.VISIBLE
            binding.rvVaultFiles.visibility = View.GONE
        } else {
            binding.layoutVaultEmpty.visibility = View.GONE
            binding.rvVaultFiles.visibility = View.VISIBLE
        }
    }

    private fun importUrisToVault(uris: List<Uri>) {
        lifecycleScope.launch(Dispatchers.IO) {
            var importedCount = 0
            val vaultDir = getVaultDirectory()

            uris.forEach { uri ->
                try {
                    var displayName = "file_${System.currentTimeMillis()}"
                    var size = 0L
                    contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (cursor.moveToFirst()) {
                            if (nameIndex != -1) displayName = cursor.getString(nameIndex)
                            if (sizeIndex != -1) size = cursor.getLong(sizeIndex)
                        }
                    }

                    val mimeType = contentResolver.getType(uri) ?: "*/*"
                    val uniqueFileName = "${UUID.randomUUID()}_vault.dat"
                    val targetFile = File(vaultDir, uniqueFileName)

                    contentResolver.openInputStream(uri)?.use { input ->
                        targetFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }

                    if (targetFile.exists() && targetFile.length() > 0L) {
                        val item = VaultItem(
                            id = UUID.randomUUID().toString(),
                            originalName = displayName,
                            originalPath = uri.toString(),
                            vaultFileName = uniqueFileName,
                            size = targetFile.length(),
                            dateAdded = System.currentTimeMillis(),
                            mimeType = mimeType
                        )
                        vaultItems.add(0, item)
                        importedCount++
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            saveVaultIndex()

            withContext(Dispatchers.Main) {
                if (importedCount > 0) {
                    Toast.makeText(this@VaultActivity, getString(R.string.vault_locked_success, importedCount), Toast.LENGTH_SHORT).show()
                    filterDisplayedItems()
                }
            }
        }
    }

    private fun openVaultFile(item: VaultItem) {
        val file = File(getVaultDirectory(), item.vaultFileName)
        if (!file.exists()) return

        try {
            // Create a temp file with original name for viewing
            val tempCacheDir = File(cacheDir, "vault_preview")
            if (!tempCacheDir.exists()) tempCacheDir.mkdirs()
            val tempFile = File(tempCacheDir, item.originalName)
            file.copyTo(tempFile, overwrite = true)

            val uri = FileProvider.getUriForFile(this, "${packageName}.provider", tempFile)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, if (item.mimeType.isNotEmpty()) item.mimeType else "*/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Cannot open file: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun restoreVaultFile(item: VaultItem) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val sourceFile = File(getVaultDirectory(), item.vaultFileName)
                val targetDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "ShareLoad/Restored_Vault")
                if (!targetDir.exists()) targetDir.mkdirs()
                val targetFile = File(targetDir, item.originalName)

                sourceFile.copyTo(targetFile, overwrite = true)
                sourceFile.delete()

                vaultItems.remove(item)
                saveVaultIndex()

                withContext(Dispatchers.Main) {
                    Toast.makeText(this@VaultActivity, getString(R.string.vault_restored_success), Toast.LENGTH_SHORT).show()
                    filterDisplayedItems()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@VaultActivity, "Restore failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun confirmDeleteVaultFile(item: VaultItem) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.vault_title))
            .setMessage(getString(R.string.vault_delete_confirm))
            .setPositiveButton(getString(R.string.action_delete)) { _, _ ->
                val sourceFile = File(getVaultDirectory(), item.vaultFileName)
                sourceFile.delete()
                vaultItems.remove(item)
                saveVaultIndex()
                filterDisplayedItems()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    companion object {
        private const val PREFS_VAULT = "prefs_vault"
        private const val KEY_PIN = "key_pin"
    }

    class VaultAdapter(
        private val items: List<VaultItem>,
        private val onItemClick: (VaultItem) -> Unit,
        private val onRestoreClick: (VaultItem) -> Unit,
        private val onDeleteClick: (VaultItem) -> Unit
    ) : RecyclerView.Adapter<VaultAdapter.ViewHolder>() {

        class ViewHolder(val binding: ItemVaultFileBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemVaultFileBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            val binding = holder.binding

            binding.tvFileName.text = item.originalName
            val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
            binding.tvFileMeta.text = "${item.size.toSizeString()} • ${dateFormat.format(Date(item.dateAdded))}"

            // Icon / Thumbnail
            val isImage = item.mimeType.startsWith("image/") || item.originalName.lowercase().let { n -> n.endsWith(".jpg") || n.endsWith(".png") || n.endsWith(".jpeg") }
            val isVideo = item.mimeType.startsWith("video/") || item.originalName.lowercase().endsWith(".mp4")
            val isAudio = item.mimeType.startsWith("audio/") || item.originalName.lowercase().endsWith(".mp3")

            when {
                isImage || isVideo -> {
                    val file = File(holder.itemView.context.filesDir, "secure_vault/${item.vaultFileName}")
                    Glide.with(holder.itemView.context)
                        .load(file)
                        .placeholder(if (isImage) R.drawable.ic_image else R.drawable.ic_movie)
                        .into(binding.ivFileThumb)
                }
                isAudio -> binding.ivFileThumb.setImageResource(R.drawable.ic_audio)
                item.originalName.lowercase().endsWith(".apk") -> binding.ivFileThumb.setImageResource(R.drawable.ic_apk)
                else -> binding.ivFileThumb.setImageResource(R.drawable.ic_document)
            }

            binding.root.setOnClickListener { onItemClick(item) }
            binding.btnRestoreFile.setOnClickListener { onRestoreClick(item) }
            binding.btnDeleteFile.setOnClickListener { onDeleteClick(item) }
        }

        override fun getItemCount(): Int = items.size
    }
}
