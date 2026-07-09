package com.aes.grammplayer.ui.features.chats

import androidx.fragment.app.Fragment
import com.aes.grammplayer.R
import com.aes.grammplayer.helper.NavigationExtras
import com.aes.grammplayer.ui.common.BaseHostActivity

/**
 * An activity that hosts the ChatsGridFragment to display Chats.
 */
class ChatsGridActivity : BaseHostActivity() {

    override val layoutId = R.layout.activity_chats_grid
    override val containerId = R.id.chats_grid_fragment_container

    override fun createFragment(): Fragment {
        val chatId = intent.getLongExtra(NavigationExtras.CHAT_ID, 0L)
        val chatTitle = intent.getStringExtra(NavigationExtras.CHAT_TITLE) ?: "Messages"
        return ChatsGridFragment.newInstance(chatId, chatTitle)
    }

    companion object {
        const val EXTRA_CHAT_ID = NavigationExtras.CHAT_ID
        const val EXTRA_CHAT_TITLE = NavigationExtras.CHAT_TITLE
    }
}
