package com.aes.grammplayer

import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import org.drinkless.tdlib.TdApi
import java.io.File

class MediaDetailsBottomSheetFragment : BottomSheetDialogFragment() {

    private var mediaMessage: MediaMessage? = null
    private var currentDownload: DownloadingFileInfo? = null

    // --- NEW: Add a state flag to ensure auto-play only triggers once ---
    private var hasAutoPlayed = false

    private lateinit var downloadProgressContainer: LinearLayout
    private lateinit var downloadStatusText: TextView
    private lateinit var downloadProgressBar: ProgressBar
    private lateinit var downloadButton: Button
    private lateinit var playButton: Button
    private lateinit var clearCacheButton: Button

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

        // Find views
        val titleTextView: TextView = view.findViewById(R.id.detail_title)
        val descriptionTextView: TextView = view.findViewById(R.id.detail_description)
        val fileInfoTextView: TextView = view.findViewById(R.id.detail_file_info)
        val closeButton: ImageButton = view.findViewById(R.id.close_button)
        downloadButton = view.findViewById(R.id.detail_download_button)
        playButton = view.findViewById(R.id.detail_play_button)
        clearCacheButton = view.findViewById(R.id.clear_cache_button)
        downloadProgressContainer = view.findViewById(R.id.download_progress_container)
        downloadStatusText = view.findViewById(R.id.download_status_text)
        downloadProgressBar = view.findViewById(R.id.download_progress_bar)
        val availableStorageTextView: TextView = view.findViewById(R.id.available_storage_text)

        // Populate initial text data
        titleTextView.text = message.title ?: "No Title"
        descriptionTextView.text = message.description ?: "No Description"
        val fileSizeMb = if (message.size > 0) String.format("%.2f MB", message.size / 1024.0 / 1024.0) else "N/A"
        fileInfoTextView.text = "File ID: ${message.fileId}\nSize: $fileSizeMb"
        val availableSpaceBytes = getAvailableInternalMemorySize()
        availableStorageTextView.text = String.format("Available Storage: %.2f GB", availableSpaceBytes / 1024.0 / 1024.0 / 1024.0)

        // Initial button state
        if (!message.localPath.isNullOrEmpty() && File(message.localPath).exists()) {
            showPlayButton(message.localPath)
        }

        // Observe global file updates
        TdLibUpdateHandler.fileUpdate.observe(viewLifecycleOwner) { update ->
            if (update.file.id == message.fileId) {
                handleFileUpdate(update)
            }
        }

        // Set listeners
        downloadButton.setOnClickListener {
            if (message.fileId != 0) {
                if (message.size > availableSpaceBytes) {
                    Toast.makeText(requireContext(), "Not enough storage.", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                downloadButton.visibility = View.GONE
                downloadProgressContainer.visibility = View.VISIBLE
                TelegramClientManager.startFileDownload(message.fileId)
            } else {
                Toast.makeText(requireContext(), "No file ID to download.", Toast.LENGTH_SHORT).show()
            }
        }

        playButton.setOnClickListener {
            val localPath = currentDownload?.localPath ?: mediaMessage?.localPath
            val fileId = mediaMessage?.fileId ?: 0
            if (!localPath.isNullOrEmpty() && fileId != 0) {
                val intent = Intent(requireActivity(), PlayerActivity::class.java).apply {
                    putExtra(PlayerActivity.EXTRA_FILE_PATH, localPath)
                    putExtra(PlayerActivity.EXTRA_FILE_ID, fileId)
                }
                startActivity(intent)
            } else {
                Toast.makeText(requireContext(), "File path or ID not available.", Toast.LENGTH_SHORT).show()
            }
        }

        clearCacheButton.setOnClickListener {
            val deletedCount = TelegramClientManager.clearDownloadedFiles()
            Toast.makeText(requireContext(), "Deleted $deletedCount downloaded files.", Toast.LENGTH_LONG).show()
            val newAvailableBytes = getAvailableInternalMemorySize()
            availableStorageTextView.text = String.format("Available Storage: %.2f GB", newAvailableBytes / 1024.0 / 1024.0 / 1024.0)
        }

        closeButton.setOnClickListener { dismiss() }
    }

    private fun handleFileUpdate(update: TdApi.UpdateFile) {
        val file = update.file
        val progress = if (file.expectedSize > 0) (file.local.downloadedSize * 100 / file.expectedSize).toInt() else 0
        val downloadedMb = file.local.downloadedSize.toFloat() / (1024 * 1024)
        val totalMb = file.expectedSize.toFloat() / (1024 * 1024)

        currentDownload = DownloadingFileInfo(file.id, downloadedMb, totalMb, progress, file.local.path.takeIf { it.isNotEmpty() })

        activity?.runOnUiThread {
            val status = String.format("Downloading: %d%% (%.2f/%.2f MB)", progress, downloadedMb, totalMb)
            downloadStatusText.text = status
            downloadProgressBar.progress = progress

            // --- REVISED AUTO-PLAY LOGIC ---
            val isDownloadedEnough = downloadedMb > 50

            // Check if we have downloaded enough AND we haven't already tried to auto-play.
            if (isDownloadedEnough && !hasAutoPlayed) {
                // Set the flag to true to prevent this block from ever running again for this session.
                hasAutoPlayed = true

                val localPath = currentDownload?.localPath
                val fileId = currentDownload?.fileId

                if (!localPath.isNullOrEmpty() && fileId != null) {
                    val intent = Intent(requireActivity(), PlayerActivity::class.java).apply {
                        putExtra(PlayerActivity.EXTRA_FILE_PATH, localPath)
                        putExtra(PlayerActivity.EXTRA_FILE_ID, fileId)
                    }
                    startActivity(intent)
                }
            }
            // --- END OF REVISED LOGIC ---

            if (file.local.isDownloadingCompleted) {
                downloadStatusText.text = "Download Complete!"
                downloadProgressBar.progress = 100
                mediaMessage?.localPath = file.local.path
                showPlayButton(file.local.path)
            }
        }
    }

    private fun showPlayButton(filePath: String?) {
        if (filePath == null) return
        downloadProgressContainer.visibility = View.GONE
        downloadButton.visibility = View.GONE
        playButton.visibility = View.VISIBLE
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
