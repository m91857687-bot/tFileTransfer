package com.tans.tfiletransporter.ui.filetransport

import android.annotation.SuppressLint
import android.view.View
import com.tans.tfiletransporter.R
import com.tans.tfiletransporter.databinding.ReadingWritingFilesDialogLayoutBinding
import com.tans.tfiletransporter.logs.AndroidLog
import com.tans.tfiletransporter.toSizeString
import com.tans.tfiletransporter.transferproto.fileexplore.model.FileExploreFile
import com.tans.tfiletransporter.transferproto.filetransfer.FileSender
import com.tans.tfiletransporter.transferproto.filetransfer.FileTransferObserver
import com.tans.tfiletransporter.transferproto.filetransfer.FileTransferState
import com.tans.tfiletransporter.transferproto.filetransfer.SpeedCalculator
import com.tans.tfiletransporter.transferproto.filetransfer.model.SenderFile
import com.tans.tuiutils.dialog.BaseSimpleCoroutineResultForceDialogFragment
import com.tans.tuiutils.view.clicks
import kotlinx.coroutines.launch
import java.net.InetAddress
import java.util.Optional
import java.util.concurrent.atomic.AtomicReference
import kotlin.jvm.optionals.getOrNull

class FileSenderDialog : BaseSimpleCoroutineResultForceDialogFragment<FileTransferDialogState, FileTransferResult> {

    private val bindAddress: InetAddress?
    private val files: List<SenderFile>?

    private val sender: AtomicReference<FileSender?> by lazy {
        AtomicReference(null)
    }

    private val speedCalculator: AtomicReference<SpeedCalculator?> by lazy {
        AtomicReference(null)
    }

    override val layoutId: Int = R.layout.reading_writing_files_dialog_layout

    constructor() : super(FileTransferDialogState()) {
        this.bindAddress = null
        this.files = null
    }

    constructor(bindAddress: InetAddress, files: List<SenderFile>) : super(FileTransferDialogState()) {
        this.bindAddress = bindAddress
        this.files = files
    }

    override fun firstLaunchInitData() {
        val files = this.files ?: return
        val bindAddress = this.bindAddress ?: return
        launch {
            val sender = FileSender(
                files = files,
                bindAddress = bindAddress,
                log = AndroidLog
            )
            this@FileSenderDialog.sender.get()?.cancel()
            this@FileSenderDialog.sender.set(sender)
            val speedCalculator = SpeedCalculator()
            var peakSpeedInBytes = 0L
            speedCalculator.addObserver(object : SpeedCalculator.Companion.SpeedObserver {
                override fun onSpeedUpdated(speedInBytes: Long, speedInString: String) {
                    if (speedInBytes > peakSpeedInBytes) {
                        peakSpeedInBytes = speedInBytes
                    }
                    val isSuper = speedInBytes >= 5 * 1024 * 1024
                    updateState {
                        val file = it.transferFile.getOrNull()
                        val process = it.process
                        var etaStr = ""
                        if (file != null && speedInBytes > 0) {
                            val remainBytes = file.size - process
                            val remainSeconds = (remainBytes / speedInBytes).coerceAtLeast(0)
                            val m = remainSeconds / 60
                            val s = remainSeconds % 60
                            etaStr = String.format(java.util.Locale.ROOT, "%02d:%02d", m, s)
                        }
                        it.copy(
                            speedString = speedInString,
                            peakSpeedString = peakSpeedInBytes.toSizeString() + "/s",
                            etaString = if (etaStr.isNotEmpty()) "ETA: $etaStr" else "",
                            isSuperSpeed = isSuper
                        )
                    }
                }
            })
            this@FileSenderDialog.speedCalculator.set(speedCalculator)
            sender.addObserver(object : FileTransferObserver {
                override fun onNewState(s: FileTransferState) {
                    when (s) {
                        FileTransferState.NotExecute -> {}
                        FileTransferState.Started -> {
                            speedCalculator.start()
                        }
                        FileTransferState.Canceled -> {
                            onResult(FileTransferResult.Cancel)
                        }
                        FileTransferState.Finished -> {
                            speedCalculator.stop()
                            try {
                                val vibrator = activity?.getSystemService(android.content.Context.VIBRATOR_SERVICE) as? android.os.Vibrator
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                    vibrator?.vibrate(android.os.VibrationEffect.createOneShot(100, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                                } else {
                                    @Suppress("DEPRECATION")
                                    vibrator?.vibrate(100)
                                }
                            } catch (_: Exception) {}
                            onResult(FileTransferResult.Finished)
                        }
                        is FileTransferState.Error -> {
                            speedCalculator.stop()
                            onResult(FileTransferResult.Error(s.msg))
                        }
                        is FileTransferState.RemoteError -> {
                            speedCalculator.stop()
                            onResult(FileTransferResult.Error("Remote error: ${s.msg}"))
                        }
                    }
                }

                override fun onStartFile(file: FileExploreFile) {
                    speedCalculator.reset()
                    updateState {
                        it.copy(
                            transferFile = Optional.of(file),
                            process = 0L,
                            speedString = ""
                        )
                    }
                }

                override fun onProgressUpdate(file: FileExploreFile, progress: Long) {
                    speedCalculator.updateCurrentSize(progress)
                    updateState { oldState ->
                        oldState.copy(process = progress)
                    }
                }

                override fun onEndFile(file: FileExploreFile) {
                    updateState { oldState -> oldState.copy(finishedFiles = oldState.finishedFiles + file) }
                }

            })
            sender.start()
        }
    }

    @SuppressLint("SetTextI18n")
    override fun bindContentView(view: View) {
        val files = this.files ?: return

        val viewBinding = ReadingWritingFilesDialogLayoutBinding.bind(view)

        val exploreFiles = files.map { it.exploreFile }.toList()
        renderStateNewCoroutine({ it.transferFile }) {
            val file = it.getOrNull()
            if (file != null) {
                viewBinding.titleTv.text = requireContext().getString(
                    R.string.sending_files_dialog_title,
                    exploreFiles.indexOf(file) + 1, exploreFiles.size
                )
                viewBinding.fileNameTv.text = file.name
            } else {
                viewBinding.titleTv.text = ""
                viewBinding.fileNameTv.text = ""
            }
        }

        renderStateNewCoroutine({ it.transferFile to it.process }) {
            val file = it.first.getOrNull()
            val process = it.second
            if (file != null) {
                val processInPercent = if (file.size > 0) (process * 100L / file.size) else 0L
                viewBinding.filePb.progress = processInPercent.toInt()
                viewBinding.fileDealSizeTv.text = "${process.toSizeString()} / ${file.size.toSizeString()} ($processInPercent%)"
            } else {
                viewBinding.filePb.progress = 0
                viewBinding.fileDealSizeTv.text = ""
            }
        }

        renderStateNewCoroutine({ it.speedString }) {
            viewBinding.speedTv.text = it
        }

        renderStateNewCoroutine({ it.peakSpeedString }) {
            viewBinding.tvPeakSpeed.text = if (it.isNotEmpty()) "Peak: $it" else ""
        }

        renderStateNewCoroutine({ it.etaString }) {
            viewBinding.tvEta.text = it
        }

        renderStateNewCoroutine({ it.isSuperSpeed }) {
            viewBinding.tvSuperSpeedBadge.visibility = if (it) View.VISIBLE else View.GONE
        }

        viewBinding.cancelButton.clicks(this, 1000L) {
            onResult(FileTransferResult.Cancel)
        }

    }

    override fun onDestroy() {
        super.onDestroy()
        sender.get()?.cancel()
        speedCalculator.get()?.stop()
    }
}

sealed class FileTransferResult {
    data class Error(val msg: String) : FileTransferResult()
    data object Cancel : FileTransferResult()
    data object Finished : FileTransferResult()
}

data class FileTransferDialogState(
    val transferFile: Optional<FileExploreFile> = Optional.empty(),
    val process: Long = 0L,
    val speedString: String = "",
    val finishedFiles: List<FileExploreFile> = emptyList(),
    val peakSpeedString: String = "",
    val etaString: String = "",
    val isSuperSpeed: Boolean = false
)