package com.aes.grammplayer

import android.os.Bundle
import androidx.fragment.app.FragmentActivity

/**
 * An activity that hosts the MessageGridFragment to display messages from a single chat.
 */
class MessageGridActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_message_grid) // A simple layout to host the fragment

        if (savedInstanceState == null) {
            val chatId = intent.getLongExtra(EXTRA_CHAT_ID, 0L)
            val chatTitle = intent.getStringExtra(EXTRA_CHAT_TITLE) ?: "Messages"

            if (chatId != 0L) {
                // Create a new MessageGridFragment and pass the chat info to it.
                val fragment = MessageGridFragment.newInstance(chatId, chatTitle)
                supportFragmentManager.beginTransaction()
                    .replace(R.id.message_grid_fragment_container, fragment)
                    .commitNow()
            }

        }
    }

    companion object {
        const val EXTRA_CHAT_ID = "chat_id"
        const val EXTRA_CHAT_TITLE = "chat_title"
    }
}
