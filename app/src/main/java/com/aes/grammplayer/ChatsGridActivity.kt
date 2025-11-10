package com.aes.grammplayer

import android.os.Bundle
import androidx.fragment.app.FragmentActivity

/**
 * An activity that hosts the ChatsGridFragment to display Chats.
 */
class ChatsGridActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chats_grid) // A simple layout to host the fragment

        if (savedInstanceState == null) {
            val chatId = intent.getLongExtra(EXTRA_CHAT_ID, 0L)
            val chatTitle = intent.getStringExtra(EXTRA_CHAT_TITLE) ?: "Messages"
            // Create a new ChatsGridFragment and pass the chat info to it.
            val fragment = ChatsGridFragment.newInstance(chatId, chatTitle)
            supportFragmentManager.beginTransaction()
                .replace(R.id.chats_grid_fragment_container, fragment)
                .commitNow()

        }
    }

    companion object {
        const val EXTRA_CHAT_ID = "chat_id"
        const val EXTRA_CHAT_TITLE = "chat_title"
    }
}
