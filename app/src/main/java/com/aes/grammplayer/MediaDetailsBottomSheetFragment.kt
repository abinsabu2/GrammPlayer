package com.aes.grammplayer

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.content.FileProvider
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import org.drinkless.tdlib.TdApi
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MediaDetailsBottomSheetFragment : BottomSheetDialogFragment() {

    private var mediaMessage: MediaMessage? = null
    private var currentDownload: DownloadingFileInfo? = null
    private var hasAutoPlayed = false

    // UI Views
    private lateinit var downloadProgressContainer: LinearLayout
    private lateinit var downloadStatusText: TextView
    private lateinit var downloadProgressBar: ProgressBar
    private lateinit var downloadButton: Button
    private lateinit var playButton: Button
    // REMOVED: private lateinit var clearCacheButton: Button
    private lateinit var stopDownloadButton: Button
    private lateinit var availableStorageTextView: TextView
    private lateinit var logScrollView: ScrollView
    private lateinit var logTextView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mediaMessage = arguments?.getSerializable(ARG_MEDIA_MESSAGE) as? MediaMessage
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_media_details_bottom_sheet, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        expandBottomSheet()

        val message = mediaMessage ?: run { dismiss(); return }

        // 1. Find all views
        bindViews(view)

        // 2. Populate initial UI state
        populateInitialData(message)

        // 3. Set up all listeners
        setupEventListeners(message)

        // 4. Observe global updates
        TdLibUpdateHandler.fileUpdate.observe(viewLifecycleOwner) { update ->
            val id = update.file.remote.uniqueId
            val belongsToThisCard = (id == message.uniqueId)
            if (!belongsToThisCard) {
                logInfo("Debug log!: Update belongs to another card. Id: $id, Expected: ${message.uniqueId}")
                return@observe
            }
            handleFileUpdate(update)
        }

    }

    /**
     * Finds and assigns all UI view properties.
     */
    private fun bindViews(view: View) {
        val titleTextView: TextView = view.findViewById(R.id.detail_title)
        val descriptionTextView: TextView = view.findViewById(R.id.detail_description)
        val fileInfoTextView: TextView = view.findViewById(R.id.detail_file_info)
        val closeButton: ImageButton = view.findViewById(R.id.close_button)
        downloadButton = view.findViewById(R.id.detail_download_button)
        playButton = view.findViewById(R.id.detail_play_button)
        // REMOVED: clearCacheButton = view.findViewById(R.id.clear_cache_button)
        downloadProgressContainer = view.findViewById(R.id.download_progress_container)
        downloadStatusText = view.findViewById(R.id.download_status_text)
        downloadProgressBar = view.findViewById(R.id.download_progress_bar)
        stopDownloadButton = view.findViewById(R.id.stop_download_button)
        availableStorageTextView = view.findViewById(R.id.available_storage_text)
        //logScrollView = view.findViewById(R.id.log_scroll_view)
        logTextView = view.findViewById(R.id.log_text_view)
    }

    /**
     * Populates the static text fields and sets the initial button visibility.
     */
    private fun populateInitialData(message: MediaMessage) {
        view?.findViewById<TextView>(R.id.detail_title)?.text = message.title ?: "No Title"
        // Todo Api to fetch description from other website based on Movie details
        //view?.findViewById<TextView>(R.id.detail_description)?.text = message.description ?: "No Description"
        val fileSizeMb = if (message.size > 0) String.format("%.2f MB", message.size / 1024.0 / 1024.0) else "N/A"
        view?.findViewById<TextView>(R.id.detail_file_info)?.text = "File ID: ${message.fileId}\nSize: $fileSizeMb"

        updateAvailableStorageText()

        // Set initial button state based on whether the file is already downloaded
        val localFileExists = !message.localPath.isNullOrEmpty() && File(message.localPath).exists()
        resetButtonStates(showPlay = localFileExists, showDownload = !localFileExists, isDownloading = false)
    }

    /**
     * Sets up click listeners for all interactive elements.
     */
    private fun setupEventListeners(message: MediaMessage) {
        downloadButton.setOnClickListener {
            if (getAvailableInternalMemorySize() < message.size) {
                logError("Not enough storage. Available: ${String.format("%.2f GB", getAvailableInternalMemorySize() / 1024.0 / 1024.0 / 1024.0)}")
                return@setOnClickListener
            }
            if (message.fileId != 0) {
                logInfo("Download command sent for file ID: ${message.fileId}")
                resetButtonStates(isDownloading = true)
                TelegramClientManager.startFileDownload(message.fileId)
            }
        }

        playButton.setOnClickListener {
            val localPath = currentDownload?.localPath ?: mediaMessage?.localPath
            if (localPath.isNullOrEmpty() || !File(localPath).exists()) {
                logError("No local path available to play.")
                return@setOnClickListener
            }

            playWithVLC(requireContext(), localPath)
        }

        stopDownloadButton.setOnClickListener {
            mediaMessage?.fileId?.let { TelegramClientManager.cancelDownloadAndDelete(it) }
            mediaMessage?.localPath?.let { File(it).delete() }
            resetButtonStates(showDownload = true, showPlay = false, isDownloading = false)
            // Reset autoplay flag so it can trigger again on next download
            hasAutoPlayed = false
            logInfo("Download stopped and cancelled by user.")
            logInfo("Cache Cleared automatically!")
        }

        view?.findViewById<ImageButton>(R.id.close_button)?.setOnClickListener {
            dismiss()
            mediaMessage?.fileId?.let { TelegramClientManager.cancelDownloadAndDelete(it) }
            mediaMessage?.localPath?.let { File(it).delete() }
            resetButtonStates(showDownload = true, showPlay = false, isDownloading = false)
        }
    }

    private fun handleFileUpdate(update: TdApi.UpdateFile) {

        val file = update.file
        val downloaded = file.local.downloadedSize
        val expected = file.expectedSize
        val progress = if (expected > 0) (downloaded * 100 / expected).toInt() else 0
        val downloadedSize = file.local.downloadedSize.toFloat() / (1024 * 1024)
        val totalSize = file.expectedSize.toFloat() / (1024 * 1024)
        val fileId = file.id

        currentDownload = DownloadingFileInfo(file.id, downloadedSize,  totalSize, progress, file.local.path.takeIf { it.isNotEmpty() })

        activity?.runOnUiThread {
            downloadStatusText.text = String.format("Downloading: %d%% (%.2f/%.2f MB)", progress, downloadedSize, currentDownload?.totalSize)
            downloadProgressBar.progress = progress

            // Auto-play logic
            if(file.local.isDownloadingCompleted && !file.local.isDownloadingActive){
                currentDownload?.localPath = file.local.path
                resetButtonStates(showDownload = true, showPlay = true, isDownloading = false)
                appendLog("Download completed. Enjoy the Movie!")
            }

            if (mediaMessage?.localPath != null && progress > 30 && !hasAutoPlayed) {
                hasAutoPlayed = true
                resetButtonStates(showDownload = false, showPlay = true, isDownloading = true)
                playWithVLC(requireContext(), currentDownload?.localPath)
            }

        }
    }

    /**
     * Helper function to launch the internal player.
     */
    private fun playWithVLC(context: Context, localPath: String?) {
        val fileId = mediaMessage?.fileId ?: 0
        if (localPath.isNullOrEmpty() || fileId == 0) {
            return
        }

        if (PlaybackStateManager.isCurrentlyPlaying(fileId)) {
            return
        }
        val file = File(localPath)
        try {
            val contentUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",  // Must match manifest
                file
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, "video/*")
                setPackage("org.videolan.vlc") // Force open with VLC only
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            logError("Error while launching VLC Media Player!")
        }
    }

    /**
     * OPTIMIZED: Manages the visibility of all action buttons based on the current state.
     */
    private fun resetButtonStates(showPlay: Boolean = false, showDownload: Boolean = false, isDownloading: Boolean = false) {
        playButton.visibility = if (showPlay) View.VISIBLE else View.GONE
        downloadButton.visibility = if (showDownload) View.VISIBLE else View.GONE
        downloadProgressContainer.visibility = if (isDownloading) View.VISIBLE else View.GONE
        stopDownloadButton.visibility = if (isDownloading) View.VISIBLE else View.GONE
    }

    /**
     * OPTIMIZED: Calculates and updates the available storage text view.
     */
    private fun updateAvailableStorageText() {
        val availableSpaceBytes = getAvailableInternalMemorySize()
        availableStorageTextView.text = String.format("Available Storage: %.2f GB", availableSpaceBytes / 1024.0 / 1024.0 / 1024.0)
    }

    private fun expandBottomSheet() {
        view?.post {
            val dialog = dialog as? BottomSheetDialog
            val bottomSheet = dialog?.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)
            if (bottomSheet != null) {
                val behavior = BottomSheetBehavior.from(bottomSheet)
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
            }
        }
    }

    private fun getAvailableInternalMemorySize(): Long {
        val path = requireContext().filesDir.absolutePath
        val stat = android.os.StatFs(path)
        return stat.availableBlocksLong * stat.blockSizeLong
    }
    /**
     * Logs a message with an [ERROR] prefix.
     */
    private fun logError(message: String) {
        appendLog("[ERROR] $message")
    }
    /**
     * Logs a message with an [INFO] prefix.
     */
    private fun logInfo(message: String) {
        appendLog("[INFO] $message")
    }

    /**

     * The core function to append a message to the log TextView.
     * It makes the log area visible and auto-scrolls to the bottom.
     */
    @SuppressLint("SimpleDateFormat")
    private fun appendLog(message: String) {
        // Ensure UI updates are on the main thread.
        activity?.runOnUiThread {
            // Make the log area visible if it's hidden.
            if (logTextView.visibility == View.GONE) {
                logTextView.visibility = View.VISIBLE
            }
            val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            val currentLog = logTextView.text.toString()
            val newLog = if (currentLog.isEmpty()) {
                "[$timestamp] $message"
            } else {
                "[$timestamp] $message\n$currentLog"
            }
            logTextView.text = newLog

        }
    }

    companion object {
        const val TAG = "MediaDetailsBottomSheet"
        private const val ARG_MEDIA_MESSAGE = "media_message"
        fun newInstance(mediaMessage: MediaMessage): MediaDetailsBottomSheetFragment {
            val fragment = MediaDetailsBottomSheetFragment()
            val args = Bundle()
            args.putSerializable(ARG_MEDIA_MESSAGE, mediaMessage)
            fragment.arguments = args
            return fragment
        }
    }

    data class DownloadingFileInfo(
        val fileId: Int,
        val downloadedSize: Float,
        val totalSize: Float,
        val progress: Int,
        var localPath: String? = null
    )
}
