package com.aes.grammplayer.ui.features.messages

import androidx.fragment.app.Fragment
import com.aes.grammplayer.R
import com.aes.grammplayer.helper.NavigationExtras
import com.aes.grammplayer.ui.common.BaseHostActivity

/**
 * An activity that hosts the MessageGridFragment to display messages from a single chat.
 */
class MessageGridActivity : BaseHostActivity() {

    override val layoutId = R.layout.activity_message_grid
    override val containerId = R.id.message_grid_fragment_container

    override fun createFragment(): Fragment? {
        val chatId = intent.getLongExtra(NavigationExtras.CHAT_ID, 0L)
        if (chatId == 0L) return null
        val chatTitle = intent.getStringExtra(NavigationExtras.CHAT_TITLE) ?: "Messages"
        return MessageGridFragment.newInstance(chatId, chatTitle)
    }

    companion object {
        const val EXTRA_CHAT_ID = NavigationExtras.CHAT_ID
        const val EXTRA_CHAT_TITLE = NavigationExtras.CHAT_TITLE
    }
}
