package com.tans.tfiletransporter.ui.cleaner

import android.content.Context
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.tans.tfiletransporter.R
import com.tans.tfiletransporter.databinding.ActivityCleanerBinding
import com.tans.tfiletransporter.databinding.ItemCleanerCategoryBinding
import com.tans.tfiletransporter.databinding.ItemCleanerSubfileBinding
import com.tans.tfiletransporter.toSizeString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

class CleanerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCleanerBinding
    private val categories = mutableListOf<CleanerCategory>()
    private lateinit var categoryAdapter: CleanerCategoryAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCleanerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolBar.setNavigationOnClickListener {
            finish()
        }

        categoryAdapter = CleanerCategoryAdapter(categories) {
            updateTotalCleanableUI()
        }
        binding.rvCleanerCategories.apply {
            layoutManager = LinearLayoutManager(this@CleanerActivity)
            adapter = categoryAdapter
        }

        binding.cbSelectAll.setOnCheckedChangeListener { _, isChecked ->
            categories.forEach { cat ->
                cat.files.forEach { it.isSelected = isChecked }
            }
            categoryAdapter.notifyDataSetChanged()
            updateTotalCleanableUI()
        }

        binding.btnCleanNow.setOnClickListener {
            showCleanConfirmDialog()
        }

        startScanning()
    }

    private fun startScanning() {
        binding.pbScanning.visibility = View.VISIBLE
        binding.tvScanStatus.visibility = View.VISIBLE
        binding.tvScanStatus.text = getString(R.string.cleaner_scanning)
        binding.btnCleanNow.isEnabled = false

        lifecycleScope.launch(Dispatchers.IO) {
            val cacheFiles = mutableListOf<CleanerFile>()
            val apkFiles = mutableListOf<CleanerFile>()
            val emptyFolders = mutableListOf<CleanerFile>()
            val largeFiles = mutableListOf<CleanerFile>()

            // 1. App internal and external caches
            fun scanCacheDir(dir: File?) {
                if (dir == null || !dir.exists()) return
                dir.walkTopDown().maxDepth(3).forEach { f ->
                    if (f.isFile && f.length() > 0) {
                        cacheFiles.add(CleanerFile(f, f.name, f.length()))
                    }
                }
            }
            scanCacheDir(cacheDir)
            scanCacheDir(externalCacheDir)

            // 2. Storage scan
            val storageRoot = Environment.getExternalStorageDirectory()
            if (storageRoot != null && storageRoot.exists() && storageRoot.canRead()) {
                val targets = listOf(
                    File(storageRoot, "Download"),
                    File(storageRoot, "DCIM/.thumbnails"),
                    File(storageRoot, "Pictures/.thumbnails"),
                    File(storageRoot, "Movies/.thumbnails"),
                    File(storageRoot, "Android/data/${packageName}/cache"),
                    File(storageRoot, "Android/media/${packageName}")
                )

                for (target in targets) {
                    if (target.exists()) {
                        target.walkTopDown().maxDepth(4).forEach { f ->
                            val nameLower = f.name.lowercase(Locale.ROOT)
                            if (f.isDirectory) {
                                val children = f.listFiles()
                                if (children != null && children.isEmpty() && !f.name.startsWith(".")) {
                                    emptyFolders.add(CleanerFile(f, f.name, 0L))
                                }
                            } else if (f.isFile) {
                                if (nameLower.endsWith(".apk")) {
                                    apkFiles.add(CleanerFile(f, f.name, f.length()))
                                } else if (nameLower.endsWith(".tmp") || nameLower.endsWith(".temp") ||
                                    nameLower.endsWith(".log") || nameLower.endsWith(".bak") ||
                                    nameLower.contains(".thumb")
                                ) {
                                    cacheFiles.add(CleanerFile(f, f.name, f.length()))
                                } else if (f.length() > 50L * 1024 * 1024) {
                                    largeFiles.add(CleanerFile(f, f.name, f.length()))
                                }
                            }
                        }
                    }
                }
            }

            withContext(Dispatchers.Main) {
                categories.clear()
                if (cacheFiles.isNotEmpty()) {
                    categories.add(
                        CleanerCategory(
                            type = CleanerType.CACHE_TEMP,
                            title = getString(R.string.cleaner_cat_cache),
                            iconRes = R.drawable.ic_cleaner,
                            files = cacheFiles
                        )
                    )
                }
                if (apkFiles.isNotEmpty()) {
                    categories.add(
                        CleanerCategory(
                            type = CleanerType.APK_INSTALLERS,
                            title = getString(R.string.cleaner_cat_apk),
                            iconRes = R.drawable.ic_apk,
                            files = apkFiles
                        )
                    )
                }
                if (emptyFolders.isNotEmpty()) {
                    categories.add(
                        CleanerCategory(
                            type = CleanerType.EMPTY_FOLDERS,
                            title = getString(R.string.cleaner_cat_empty_dirs),
                            iconRes = R.drawable.folder_outline,
                            files = emptyFolders
                        )
                    )
                }
                if (largeFiles.isNotEmpty()) {
                    categories.add(
                        CleanerCategory(
                            type = CleanerType.LARGE_FILES,
                            title = getString(R.string.cleaner_cat_large_files),
                            iconRes = R.drawable.file_outline,
                            files = largeFiles
                        )
                    )
                }

                binding.pbScanning.visibility = View.GONE
                binding.tvScanStatus.visibility = View.GONE
                binding.btnCleanNow.isEnabled = true
                categoryAdapter.notifyDataSetChanged()
                updateTotalCleanableUI()
            }
        }
    }

    private fun updateTotalCleanableUI() {
        val totalSelectedBytes = categories.sumOf { it.totalSelectedSize }
        binding.tvCleanableSize.text = totalSelectedBytes.toSizeString()

        val allSelected = categories.isNotEmpty() && categories.all { it.isAllSelected }
        binding.cbSelectAll.setOnCheckedChangeListener(null)
        binding.cbSelectAll.isChecked = allSelected
        binding.cbSelectAll.setOnCheckedChangeListener { _, isChecked ->
            categories.forEach { cat ->
                cat.files.forEach { it.isSelected = isChecked }
            }
            categoryAdapter.notifyDataSetChanged()
            updateTotalCleanableUI()
        }

        binding.btnCleanNow.text = getString(R.string.cleaner_clean_now, totalSelectedBytes.toSizeString())
        binding.btnCleanNow.isEnabled = totalSelectedBytes > 0L || categories.any { it.files.any { f -> f.isSelected } }
    }

    private fun showCleanConfirmDialog() {
        val selectedCount = categories.sumOf { it.files.count { f -> f.isSelected } }
        val selectedBytes = categories.sumOf { it.totalSelectedSize }
        if (selectedCount == 0) {
            Toast.makeText(this, getString(R.string.cleaner_no_junk), Toast.LENGTH_SHORT).show()
            return
        }

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.cleaner_title))
            .setMessage(getString(R.string.cleaner_item_delete_warning) + "\n\n" + getString(R.string.cleaner_items_count, selectedCount, selectedBytes.toSizeString()))
            .setPositiveButton(R.string.dialog_positive) { _, _ ->
                performClean()
            }
            .setNegativeButton(R.string.dialog_negative, null)
            .show()
    }

    private fun performClean() {
        binding.pbScanning.visibility = View.VISIBLE
        binding.btnCleanNow.isEnabled = false

        lifecycleScope.launch(Dispatchers.IO) {
            var cleanedBytes = 0L
            var cleanedCount = 0

            for (cat in categories) {
                val iterator = cat.files.iterator()
                while (iterator.hasNext()) {
                    val cleanerFile = iterator.next()
                    if (cleanerFile.isSelected) {
                        val size = cleanerFile.size
                        val success = try {
                            if (cleanerFile.file.isDirectory) {
                                cleanerFile.file.deleteRecursively()
                            } else {
                                cleanerFile.file.delete()
                            }
                        } catch (e: Exception) {
                            false
                        }
                        if (success) {
                            cleanedBytes += size
                            cleanedCount++
                            iterator.remove()
                        }
                    }
                }
            }

            withContext(Dispatchers.Main) {
                binding.pbScanning.visibility = View.GONE
                categories.removeAll { it.files.isEmpty() }
                categoryAdapter.notifyDataSetChanged()
                updateTotalCleanableUI()

                AlertDialog.Builder(this@CleanerActivity)
                    .setTitle(getString(R.string.cleaner_title))
                    .setMessage(getString(R.string.cleaner_cleaned_success, cleanedBytes.toSizeString()))
                    .setIcon(R.drawable.ic_done_all)
                    .setPositiveButton(R.string.dialog_positive, null)
                    .show()
            }
        }
    }

    enum class CleanerType {
        CACHE_TEMP,
        APK_INSTALLERS,
        EMPTY_FOLDERS,
        LARGE_FILES,
        DUPLICATE_LOGS
    }

    data class CleanerFile(
        val file: File,
        val name: String,
        val size: Long,
        var isSelected: Boolean = true
    )

    data class CleanerCategory(
        val type: CleanerType,
        val title: String,
        val iconRes: Int,
        val files: MutableList<CleanerFile>,
        var isExpanded: Boolean = false
    ) {
        val totalSelectedSize: Long get() = files.filter { it.isSelected }.sumOf { it.size }
        val totalSize: Long get() = files.sumOf { it.size }
        val isAllSelected: Boolean get() = files.isNotEmpty() && files.all { it.isSelected }
    }

    inner class CleanerCategoryAdapter(
        private val list: List<CleanerCategory>,
        private val onSelectionChanged: () -> Unit
    ) : RecyclerView.Adapter<CleanerCategoryAdapter.CategoryViewHolder>() {

        inner class CategoryViewHolder(val binding: ItemCleanerCategoryBinding) :
            RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryViewHolder {
            val b = ItemCleanerCategoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return CategoryViewHolder(b)
        }

        override fun getItemCount(): Int = list.size

        override fun onBindViewHolder(holder: CategoryViewHolder, position: Int) {
            val item = list[position]
            val b = holder.binding

            b.ivCatIcon.setImageResource(item.iconRes)
            b.tvCatTitle.text = item.title
            b.tvCatSubtitle.text = getString(R.string.cleaner_items_count, item.files.size, item.totalSize.toSizeString())
            b.tvCatSize.text = item.totalSelectedSize.toSizeString()

            b.cbCatSelect.setOnCheckedChangeListener(null)
            b.cbCatSelect.isChecked = item.isAllSelected
            b.cbCatSelect.setOnCheckedChangeListener { _, isChecked ->
                item.files.forEach { it.isSelected = isChecked }
                notifyItemChanged(holder.bindingAdapterPosition)
                onSelectionChanged()
            }

            b.layoutCategoryHeader.setOnClickListener {
                item.isExpanded = !item.isExpanded
                b.rvSubfiles.visibility = if (item.isExpanded) View.VISIBLE else View.GONE
            }

            b.rvSubfiles.visibility = if (item.isExpanded) View.VISIBLE else View.GONE
            b.rvSubfiles.layoutManager = LinearLayoutManager(holder.itemView.context)
            b.rvSubfiles.adapter = CleanerSubFileAdapter(item.files) {
                b.tvCatSize.text = item.totalSelectedSize.toSizeString()
                b.cbCatSelect.setOnCheckedChangeListener(null)
                b.cbCatSelect.isChecked = item.isAllSelected
                b.cbCatSelect.setOnCheckedChangeListener { _, isChecked ->
                    item.files.forEach { it.isSelected = isChecked }
                    notifyItemChanged(holder.bindingAdapterPosition)
                    onSelectionChanged()
                }
                onSelectionChanged()
            }
        }
    }

    inner class CleanerSubFileAdapter(
        private val files: List<CleanerFile>,
        private val onItemSelectionChanged: () -> Unit
    ) : RecyclerView.Adapter<CleanerSubFileAdapter.SubFileViewHolder>() {

        inner class SubFileViewHolder(val binding: ItemCleanerSubfileBinding) :
            RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SubFileViewHolder {
            val b = ItemCleanerSubfileBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return SubFileViewHolder(b)
        }

        override fun getItemCount(): Int = files.size

        override fun onBindViewHolder(holder: SubFileViewHolder, position: Int) {
            val f = files[position]
            val b = holder.binding

            b.tvFileName.text = f.name
            b.tvFilePath.text = f.file.parent ?: ""
            b.tvFileSize.text = f.size.toSizeString()

            b.cbFileSelect.setOnCheckedChangeListener(null)
            b.cbFileSelect.isChecked = f.isSelected
            b.cbFileSelect.setOnCheckedChangeListener { _, isChecked ->
                f.isSelected = isChecked
                onItemSelectionChanged()
            }

            b.root.setOnClickListener {
                b.cbFileSelect.isChecked = !b.cbFileSelect.isChecked
            }
        }
    }
}
