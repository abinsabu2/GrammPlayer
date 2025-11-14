package com.aes.grammplayer

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.content.FileProvider
import androidx.core.view.isGone
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.drinkless.tdlib.TdApi
import java.io.File
import java.io.Serializable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MediaDetailsBottomSheetFragment : BottomSheetDialogFragment(){

    private lateinit var gridAdapter: ArrayObjectAdapter
    private var mediaMessage: MediaMessage? = null
    private var currentDownload: DownloadingFileInfo? = null
    private var hasAutoPlayed = false

    private var hasDownloadStoped = true // Consider if this flag is still needed or if `currentDownload == null` suffices
    private var fileUpdateCollectorJob: Job? = null // To manage the collector lifecycle

    // UI Views
    private lateinit var downloadProgressContainer: LinearLayout
    private lateinit var downloadStatusText: TextView
    private lateinit var downloadProgressBar: ProgressBar
    private lateinit var downloadButton: Button
    private lateinit var playButton: Button
    private lateinit var stopDownloadButton: Button

    private lateinit var closeButton: ImageButton
    private lateinit var availableStorageTextView: TextView
    private lateinit var logTextView: TextView
    private lateinit var appliedSettingsTextView: TextView

    val activeDownloads = mutableSetOf<Int>()

    private lateinit var settingsDataStore: SettingsDataStore
    private var isAutoPlayEnabled: Boolean = false
    private var progressThreshold: Int = 30 // Default value
    private var bufferSizeThreshold: Int = 300 // Default value in MB

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settingsDataStore = SettingsDataStore(requireActivity())
        mediaMessage = arguments?.getSerializableCompat(ARG_MEDIA_MESSAGE, MediaMessage::class.java)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_media_details_bottom_sheet, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        expandBottomSheet()

        val message = mediaMessage ?: run { dismiss(); return }

        bindViews(view)
        populateInitialData(message)
        setupEventListeners(message)

        // Load settings values
        lifecycleScope.launch {
            isAutoPlayEnabled = settingsDataStore.autoPlay.first()
            progressThreshold = settingsDataStore.progressThreshold.first()
            bufferSizeThreshold = settingsDataStore.bufferSizeThreshold.first()
            appliedSettingsTextView.text = getString(R.string.applied_settings_placeholder_off)
            if (isAutoPlayEnabled){
                appliedSettingsTextView.text = getString(R.string.applied_settings_placeholder, isAutoPlayEnabled, progressThreshold, bufferSizeThreshold)
            }
        }

        // If a file was already being downloaded when this fragment was opened/recreated,
        // we should try to re-attach to its updates if it's the current media.
        // This is important for configuration changes or if the fragment is reused.
        if (activeDownloads.contains(message.fileId)) {
            startFileUpdateCollection()
            setDownloadButtonVisibility(false)
            setStopDownloadButtonVisibility(true)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Cancel the collector job when the view is destroyed to avoid leaks
        fileUpdateCollectorJob?.cancel()
    }

    private fun bindViews(view: View) {
        // ... (titleTextView, descriptionTextView, fileInfoTextView, closeButton are local vars, so no change needed)
        downloadButton = view.findViewById(R.id.detail_download_button)
        playButton = view.findViewById(R.id.detail_play_button)
        downloadProgressContainer = view.findViewById(R.id.download_progress_container)
        downloadStatusText = view.findViewById(R.id.download_status_text)
        downloadProgressBar = view.findViewById(R.id.download_progress_bar)
        stopDownloadButton = view.findViewById(R.id.stop_download_button)
        availableStorageTextView = view.findViewById(R.id.available_storage_text)
        logTextView = view.findViewById(R.id.log_text_view)
        closeButton = view.findViewById(R.id.close_button)
        appliedSettingsTextView = view.findViewById(R.id.applied_settings_text)
    }

    private fun populateInitialData(message: MediaMessage) {
        view?.findViewById<TextView>(R.id.detail_title)?.text = message.title ?: getString(R.string.media_title_placeholder)
        val fileSizeMb = if (message.size > 0) String.format(Locale.US, "%.2f MB", message.size / 1024.0 / 1024.0) else "N/A"
        view?.findViewById<TextView>(R.id.detail_file_info)?.text = getString(R.string.file_info_placeholder, message.fileId, fileSizeMb)

        updateAvailableStorageText()
        this.hasAutoPlayed = false // Reset hasAutoPlayed on initial setup

        // Initial check for play button based on *actual* local file
        val localFile = message.localPath?.let { File(it) }
        val downloadedSizeMb = localFile?.length()?.toFloat()?.div(1024 * 1024) ?: 0f
        val totalSizeMb = message.size.toFloat() / (1024 * 1024)
        val progress = if (totalSizeMb > 0) ((downloadedSizeMb / totalSizeMb) * 100).toInt() else 0
        val localFileExistsAndIsPlayable = localFile != null && localFile.exists()


        downloadStatusText.visibility  = View.GONE
        downloadProgressBar.visibility = View.GONE
        // If a download is currently active for this file, the buttons might already be in the correct state
        // Otherwise, determine initial visibility based on existing file.
        if (activeDownloads.contains(message.fileId)) {
            downloadStatusText.visibility  = View.VISIBLE
            downloadProgressBar.visibility = View.VISIBLE
            setCloseButtonVisibility(false)
            setDownloadButtonVisibility(false)
            setStopDownloadButtonVisibility(true)
            setPlayButtonVisibility(true) // Will be updated by handleFileUpdate if progress is enough
            // Consider if you want to initialize currentDownload from TelegramClientManager if a download is truly active
        } else if (localFileExistsAndIsPlayable) {
            downloadStatusText.visibility  = View.GONE
            downloadProgressBar.visibility = View.GONE
            setDownloadButtonVisibility(false)
            setStopDownloadButtonVisibility(false) // Not downloading anymore
            setPlayButtonVisibility(true)
            setCloseButtonVisibility(true)
            // Initialize currentDownload if the file is already downloaded
            currentDownload = DownloadingFileInfo(
                fileId = message.fileId,
                downloadedSize = downloadedSizeMb,
                totalSize = totalSizeMb,
                progress = progress,
                localPath = message.localPath
            )
            // For initial load, if auto-play is enabled and conditions met, trigger auto-play
            if (isAutoPlayEnabled && !hasAutoPlayed) {
                hasAutoPlayed = true
                playWithVLC(requireContext(), message.localPath)
            }
        } else {
            // No active download, no local playable file
            setDownloadButtonVisibility(true)
            setStopDownloadButtonVisibility(false)
            setPlayButtonVisibility(false)
            setCloseButtonVisibility(true)
            currentDownload = null // Ensure no stale download info
        }
    }

    private fun setupEventListeners(message: MediaMessage) {
        downloadButton.setOnClickListener {
            // Explicitly clear any previous download state before starting a new one
            cancelAndClearDownloadState()

            this.hasAutoPlayed = false // Reset hasAutoPlayed for new download
            if (getAvailableInternalMemorySize() < message.size) {
                logError("Not enough storage. Available: ${String.format(Locale.US, "%.2f GB", getAvailableInternalMemorySize() / 1024.0 / 1024.0 / 1024.0)}")
                return@setOnClickListener
            }
            if (message.fileId != 0) {
                activeDownloads.add(message.fileId)
                // *** ADD THIS LINE to save the MediaMessage to the history ***
                message.isDownloadActive = true
                HistoryManager.addHistoryItem(message)
                TelegramClientManager.startFileDownload(message.fileId)
                logInfo("Download command sent for file ID: ${message.fileId}")
                this.hasDownloadStoped = false // This flag might become redundant
                startFileUpdateCollection() // Start (or restart) collecting updates

                setDownloadButtonVisibility(false)
                setStopDownloadButtonVisibility(true)
                setPlayButtonVisibility(false) // Hide play button until playable progress
                setCloseButtonVisibility(false)
                downloadStatusText.visibility  = View.VISIBLE
                downloadProgressBar.visibility = View.VISIBLE
            }
        }

        playButton.setOnClickListener {
            val path = currentDownload?.localPath ?: mediaMessage?.localPath

            // Crucially, check if the physical file exists and is of sufficient size
            val localFile = path?.let { File(it) }
            if (localFile != null && localFile.exists()) {
                stopVLCPlayback() // Ensure previous playback is stopped
                this.hasAutoPlayed = true // Mark as played to prevent auto-play re-triggering
                playWithVLC(requireContext(), path)
            } else {
                logError("Cannot play: File not found or not large enough for playback. Path: $path, Exists: ${localFile?.exists()}, Size: ${localFile?.length()}")
                setPlayButtonVisibility(false) // Hide if it's not actually playable
            }
        }

        stopDownloadButton.setOnClickListener {
            cancelAndClearDownloadState()
            setDownloadButtonVisibility(true)
            setStopDownloadButtonVisibility(false)
            setCloseButtonVisibility(true)
            downloadStatusText.visibility  = View.GONE
            downloadProgressBar.visibility = View.GONE
            //dismiss() // Close the bottom sheet after cancellation
        }

        closeButton.setOnClickListener {
            cancelAndClearDownloadState()
            dismiss() // Close the bottom sheet
        }
    }

    /**
     * Helper to centralize download cancellation and state clearing.
     */
    private fun cancelAndClearDownloadState() {
        fileUpdateCollectorJob?.cancel() // Stop listening for updates
        this.hasDownloadStoped = true
        TelegramClientManager.cancelDownloadAndDelete(activeDownloads)
        activeDownloads.clear()
        mediaMessage?.localPath?.let { path ->
            val file = File(path)
            if (file.exists()) {
                if (file.delete()) {
                    logInfo("Successfully deleted local file: $path")
                } else {
                    logError("Failed to delete local file: $path")
                }
            }
        }
        stopVLCPlayback()
        setPlayButtonVisibility(false)
        setDownloadButtonVisibility(true)
        setStopDownloadButtonVisibility(false)
        logInfo("Download stopped and cancelled by user. Cache Cleared!")
        // IMPORTANT: Clear currentDownload to prevent stale data
        currentDownload = null
        // Reset mediaMessage or refresh its state if necessary
        // For now, we assume dismiss() will handle the fragment instance clean up
    }

    private fun startFileUpdateCollection() {
        // Cancel any existing job to ensure only one collector is active
        fileUpdateCollectorJob?.cancel()
        fileUpdateCollectorJob = lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                TdLibUpdateHandler.fileUpdate.collect { update ->
                    handleFileUpdate(update)
                }
            }
        }
    }

    fun stopVLCPlayback() {
        val stopIntent = Intent("org.videolan.vlc.remote.StopPlayback")
        stopIntent.setPackage("org.videolan.vlc")
        try {
            context?.sendBroadcast(stopIntent)
            logInfo("Player Playback Stoped!")
        } catch (e: Exception) {
            logError("Error while stopping VLC Playback: ${e.message}")
        }
        this.hasAutoPlayed = false
    }

    private fun handleFileUpdate(update: TdApi.UpdateFile) {
        val file = update.file
        val currentMediaFileId = mediaMessage?.fileId ?: 0

        // Filter out updates not for the current mediaMessage
        if (currentMediaFileId == 0 || file.id != currentMediaFileId) {
            Log.d(TAG, "Ignoring file update for ID ${file.id}. Expected ID: $currentMediaFileId")
            return
        }

        activity?.runOnUiThread {
            // Update UI elements related to download progress
            val downloaded = file.local.downloadedSize
            val expected = file.expectedSize
            val progress = if (expected > 0) (downloaded * 100 / expected).toInt() else 0
            val downloadedSizeMb = file.local.downloadedSize.toFloat() / (1024 * 1024)
            val totalSizeMb = file.expectedSize.toFloat() / (1024 * 1024)

            updateAvailableStorageText()

            // Update currentDownload info, ensuring localPath is set if available
            currentDownload = DownloadingFileInfo(
                fileId = file.id,
                downloadedSize = downloadedSizeMb,
                totalSize = totalSizeMb,
                progress = progress,
                localPath = file.local.path.takeIf { it.isNotEmpty() }
            )

            downloadStatusText.text = String.format("Downloading: %d%% (%.2f/%.2f MB)", progress, downloadedSizeMb, currentDownload?.totalSize)
            downloadProgressBar.progress = progress

            // --- Critical Check: Physical file existence and size for playing ---
            val localPathForPlay = currentDownload?.localPath
            val actualFile = localPathForPlay?.let { File(it) }

            // NEW CONDITION: Play button is visible if file exists AND (progress >= progressThreshold OR downloadedSizeMb >= bufferSizeThreshold)
            val isPlayableEnough = actualFile != null && actualFile.exists() && !this.hasAutoPlayed &&
                    (progress >= progressThreshold || downloadedSizeMb >= bufferSizeThreshold)

            if (isPlayableEnough && isAutoPlayEnabled) {
                setPlayButtonVisibility(true)
                hasAutoPlayed = true
                playWithVLC(requireContext(), localPathForPlay)
                logInfo("Auto-play triggered: Progress = $progress% (>= $progressThreshold%), Buffer = $downloadedSizeMb MB (>= $bufferSizeThreshold MB)")
            }

            // If download completes, ensure buttons reflect that
            if (file.local.isDownloadingCompleted && actualFile != null && actualFile.exists() &&
                (progress >= progressThreshold || downloadedSizeMb >= bufferSizeThreshold)) {
                mediaMessage?.isDownloaded = true
                setDownloadButtonVisibility(false)
                setStopDownloadButtonVisibility(false)
                setPlayButtonVisibility(true) // Should already be true from above check
                setCloseButtonVisibility(true)
                logInfo("Download completed for file ID: ${file.id}")
            } else if (file.local.isDownloadingCompleted && (actualFile == null || !actualFile.exists())) {
                // Edge case: TdLib says complete but file doesn't exist on disk (e.g. deleted externally)
                logError("TDLib reported download completed for ${file.id}, but physical file does not exist at ${localPathForPlay}. Resetting state.")
                // Trigger a reset of the UI state to allow re-download
                setDownloadButtonVisibility(true)
                setStopDownloadButtonVisibility(false)
                setPlayButtonVisibility(false)
                setCloseButtonVisibility(true)
                currentDownload = null
                TelegramClientManager.cancelDownloadAndDelete(activeDownloads) // Clean up TdLib state
                activeDownloads.clear()
            }
        }
    }

    /**
     * Helper function to launch the internal player.
     */
    private fun playWithVLC(context: Context, localPath: String?) {
        val fileId = mediaMessage?.fileId ?: 0
        val file = localPath?.let { File(it) }

        if (file == null || !file.exists() || fileId == 0) {
            logError("Cannot play: File path invalid, does not exist, or too small: $localPath")
            setPlayButtonVisibility(false) // Ensure button is hidden if conditions aren't met
            return
        }
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
            logInfo("Started VLC playback for file ID: $fileId, path: $localPath")
        } catch (e: Exception) {
            logError("Error while launching VLC Media Player for $localPath: ${e.message}")
        }
    }


    private fun setPlayButtonVisibility(isVisible: Boolean) {
        playButton.visibility = if (isVisible) View.VISIBLE else View.GONE
    }

    private fun setDownloadButtonVisibility(isVisible: Boolean) {
        downloadButton.visibility = if (isVisible) View.VISIBLE else View.GONE
    }

    private fun setStopDownloadButtonVisibility(isVisible: Boolean) {
        stopDownloadButton.visibility = if (isVisible) View.VISIBLE else View.GONE
    }

    private fun setCloseButtonVisibility(isVisible: Boolean) {
        closeButton.visibility = if (isVisible) View.VISIBLE else View.GONE
    }

    private fun updateAvailableStorageText() {
        val availableSpaceBytes = getAvailableInternalMemorySize()
        availableStorageTextView.text = getString(R.string.available_storage_placeholder, availableSpaceBytes / 1024.0 / 1024.0 / 1024.0)
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

    private fun logError(message: String) {
        appendLog("[ERROR] $message")
    }

    private fun logInfo(message: String) {
        appendLog("[INFO] $message")
    }

    @SuppressLint("SimpleDateFormat")
    private fun appendLog(message: String) {
        activity?.runOnUiThread {
            if (logTextView.isGone) {
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

@Suppress("DEPRECATION")
fun <T : Serializable?> Bundle.getSerializableCompat(key: String, clazz: Class<T>): T? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getSerializable(key, clazz)
    } else {
        @Suppress("UNCHECKED_CAST")
        getSerializable(key) as? T
    }
}