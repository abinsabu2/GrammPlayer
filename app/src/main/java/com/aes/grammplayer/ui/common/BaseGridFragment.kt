package com.aes.grammplayer.ui.common

import android.os.Bundle
import androidx.leanback.app.VerticalGridSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.Presenter
import androidx.leanback.widget.VerticalGridPresenter
import com.aes.grammplayer.helper.NavigationExtras

abstract class BaseGridFragment : VerticalGridSupportFragment() {

    protected lateinit var gridAdapter: ArrayObjectAdapter

    protected open val gridColumnCount: Int = 3

    protected abstract fun createItemPresenter(): Presenter

    protected fun setupGrid() {
        val gridPresenter = VerticalGridPresenter()
        gridPresenter.numberOfColumns = gridColumnCount
        setGridPresenter(gridPresenter)
        gridAdapter = ArrayObjectAdapter(createItemPresenter())
        adapter = gridAdapter
    }

    protected fun refreshAllCards() {
        if (::gridAdapter.isInitialized && gridAdapter.size() > 0) {
            gridAdapter.notifyItemRangeChanged(0, gridAdapter.size())
        }
    }

    protected val chatId: Long
        get() = arguments?.getLong(NavigationExtras.CHAT_ID) ?: 0L

    protected val chatTitle: String
        get() = arguments?.getString(NavigationExtras.CHAT_TITLE) ?: ""

    companion object {
        fun buildChatArgs(chatId: Long, chatTitle: String): Bundle =
            Bundle().apply {
                putLong(NavigationExtras.CHAT_ID, chatId)
                putString(NavigationExtras.CHAT_TITLE, chatTitle)
            }
    }
}