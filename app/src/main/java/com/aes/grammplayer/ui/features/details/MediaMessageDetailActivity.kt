package com.aes.grammplayer.ui.features.details

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import com.aes.grammplayer.R
import com.aes.grammplayer.db.model.MediaMessage

class MediaMessageDetailActivity : Activity() {

    companion object {
        private const val EXTRA_MEDIA_MESSAGE = "extra_media_message"

        fun newIntent(context: Context, mediaMessage: MediaMessage): Intent =
            Intent(context, MediaMessageDetailActivity::class.java).apply {
                putExtra(EXTRA_MEDIA_MESSAGE, mediaMessage)
            }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_media_message_detail)

        val mediaMessage: MediaMessage? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getSerializableExtra(EXTRA_MEDIA_MESSAGE, MediaMessage::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getSerializableExtra(EXTRA_MEDIA_MESSAGE) as? MediaMessage
        }

        if (mediaMessage == null) {
            finish()
            return
        }

        if (savedInstanceState == null) {
            fragmentManager.beginTransaction()
                .replace(R.id.fragment_container, MediaMessageDetailFragment.newInstance(mediaMessage))
                .commit()
        }
    }
}