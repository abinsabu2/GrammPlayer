package com.aes.grammplayer.ui.features.details

import android.app.Fragment
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import com.aes.grammplayer.R
import com.aes.grammplayer.db.model.MediaMessage
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions

class MediaMessageDetailFragment : Fragment() {

    companion object {
        private const val ARG_MEDIA_MESSAGE = "arg_media_message"

        fun newInstance(mediaMessage: MediaMessage): MediaMessageDetailFragment =
            MediaMessageDetailFragment().apply {
                arguments = Bundle().apply {
                    putSerializable(ARG_MEDIA_MESSAGE, mediaMessage)
                }
            }
    }

    // ── Views ────────────────────────────────────────────────────────────────

    private lateinit var ivBackground: ImageView
    private lateinit var ivThumbnail: ImageView
    private lateinit var ivCardImage: ImageView
    private lateinit var tvTitle: TextView
    private lateinit var tvStudio: TextView
    private lateinit var tvDescription: TextView
    private lateinit var tvDuration: TextView
    private lateinit var tvResolution: TextView
    private lateinit var tvSize: TextView
    private lateinit var tvMimeType: TextView
    private lateinit var tvLocalPath: TextView
    private lateinit var tvVideoUrl: TextView
    private lateinit var tvFileId: TextView
    private lateinit var tvChatId: TextView
    private lateinit var tvMessageId: TextView
    private lateinit var tvUniqueId: TextView
    private lateinit var chipIsMedia: TextView
    private lateinit var chipDownloaded: TextView
    private lateinit var chipDownloadActive: TextView
    private lateinit var btnPlay: Button
    private lateinit var btnDownload: Button
    private lateinit var btnBack: ImageButton

    // ── Lifecycle ────────────────────────────────────────────────────────────

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_media_message_detail, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bindViews(view)
        val message = getMediaMessage() ?: return
        bindMessage(message)
        setupListeners(message)
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun bindViews(v: View) {
        ivBackground       = v.findViewById(R.id.iv_background)
        ivThumbnail        = v.findViewById(R.id.iv_thumbnail)
        ivCardImage        = v.findViewById(R.id.iv_card_image)
        tvTitle            = v.findViewById(R.id.tv_title)
        tvStudio           = v.findViewById(R.id.tv_studio)
        tvDescription      = v.findViewById(R.id.tv_description)
        tvDuration         = v.findViewById(R.id.tv_duration)
        tvResolution       = v.findViewById(R.id.tv_resolution)
        tvSize             = v.findViewById(R.id.tv_size)
        tvMimeType         = v.findViewById(R.id.tv_mime_type)
        tvLocalPath        = v.findViewById(R.id.tv_local_path)
        tvVideoUrl         = v.findViewById(R.id.tv_video_url)
        tvFileId           = v.findViewById(R.id.tv_file_id)
        tvChatId           = v.findViewById(R.id.tv_chat_id)
        tvMessageId        = v.findViewById(R.id.tv_message_id)
        tvUniqueId         = v.findViewById(R.id.tv_unique_id)
        chipIsMedia        = v.findViewById(R.id.chip_is_media)
        chipDownloaded     = v.findViewById(R.id.chip_downloaded)
        chipDownloadActive = v.findViewById(R.id.chip_download_active)
        btnPlay            = v.findViewById(R.id.btn_play)
        btnDownload        = v.findViewById(R.id.btn_download)
        btnBack            = v.findViewById(R.id.btn_back)
    }

    private fun getMediaMessage(): MediaMessage? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arguments?.getSerializable(ARG_MEDIA_MESSAGE, MediaMessage::class.java)
        } else {
            @Suppress("DEPRECATION")
            arguments?.getSerializable(ARG_MEDIA_MESSAGE) as? MediaMessage
        }

    private fun setupListeners(m: MediaMessage) {
        btnBack.setOnClickListener {
            @Suppress("DEPRECATION")
            activity?.onBackPressed()
        }
        btnPlay.setOnClickListener {
            // TODO: launch player using m.videoUrl or m.localPath
        }
        btnDownload.setOnClickListener {
            // TODO: trigger download via ViewModel
        }
    }

    private fun bindMessage(m: MediaMessage) {
        loadImages(m)
        bindTextFields(m)
        bindBadges(m)
        bindButtons(m)
    }

    private fun loadImages(m: MediaMessage) {
        val placeholder = ColorDrawable(Color.parseColor("#1A1A2E"))

        Glide.with(activity)
            .load(m.backgroundImageUrl.ifBlank { m.thumbnailPath })
            .transition(DrawableTransitionOptions.withCrossFade())
            .centerCrop().placeholder(placeholder).error(placeholder)
            .into(ivBackground)

        Glide.with(activity)
            .load(m.thumbnailPath.ifBlank { m.cardImageUrl })
            .transition(DrawableTransitionOptions.withCrossFade())
            .centerCrop().placeholder(placeholder).error(placeholder)
            .into(ivThumbnail)

        Glide.with(activity)
            .load(m.cardImageUrl)
            .transition(DrawableTransitionOptions.withCrossFade())
            .centerCrop().placeholder(placeholder).error(placeholder)
            .into(ivCardImage)
    }

    private fun bindTextFields(m: MediaMessage) {
        tvTitle.text       = m.title.ifBlank { getString(R.string.label_untitled) }
        tvStudio.text      = m.studio.ifBlank { getString(R.string.label_unknown_studio) }
        tvDescription.text = m.description.ifBlank { getString(R.string.label_no_description) }
        tvDuration.text    = formatDuration(m.duration)
        tvResolution.text  = getString(R.string.format_resolution, m.width, m.height)
        tvSize.text        = Formatter.formatShortFileSize(activity, m.size.toLong())
        tvMimeType.text    = m.mimeType.ifBlank { getString(R.string.label_unknown) }
        tvUniqueId.text    = m.uniqueId.ifBlank { getString(R.string.label_unknown) }
        tvFileId.text      = getString(R.string.format_file_id, m.fileId)
        tvLocalPath.text   = m.localPath.ifBlank { getString(R.string.label_not_available) }
        tvVideoUrl.text    = m.videoUrl.ifBlank { getString(R.string.label_not_available) }
        tvChatId.text      = m.chat.toString()
        tvMessageId.text   = m.id.toString()
    }

    private fun bindBadges(m: MediaMessage) {
        chipIsMedia.text        = getString(if (m.isMedia) R.string.chip_media else R.string.chip_file)
        chipDownloaded.text     = getString(if (m.isDownloaded) R.string.chip_downloaded else R.string.chip_not_downloaded)
        chipDownloadActive.text = getString(if (m.isDownloadActive) R.string.chip_downloading else R.string.chip_idle)
    }

    private fun bindButtons(m: MediaMessage) {
        btnPlay.isEnabled = m.isDownloaded || m.videoUrl.isNotBlank()
        if (m.isDownloaded) {
            btnDownload.visibility = View.GONE
        } else {
            btnDownload.visibility = View.VISIBLE
            btnDownload.isEnabled  = !m.isDownloadActive
            btnDownload.text       = getString(if (m.isDownloadActive) R.string.btn_downloading else R.string.btn_download)
        }
    }

    private fun formatDuration(totalSeconds: Long): String {
        val h = totalSeconds / 3600
        val m = (totalSeconds % 3600) / 60
        val s = totalSeconds % 60
        return if (h > 0) String.format("%d:%02d:%02d", h, m, s)
        else String.format("%d:%02d", m, s)
    }
}