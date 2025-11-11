// HistoryManager.kt
import com.aes.grammplayer.MediaMessage
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

object HistoryManager {
    // Use a replay of 0 so new subscribers don't get old values, they only get new additions.
    private val _history = MutableSharedFlow<DownloadHistoryItem>(replay = 0)
    val history = _history.asSharedFlow()

    // A simple in-memory list to hold the history items
    private val historyList = mutableListOf<DownloadHistoryItem>()

    fun addHistoryItem(mediaMessage: MediaMessage) {
        val newItem = DownloadHistoryItem(mediaMessage)
        historyList.add(newItem)

        // Emit the new item to all observers.
        // Use GlobalScope if this needs to live for the app's entire lifecycle,
        // but a custom CoroutineScope tied to your app's lifecycle is even better.
        GlobalScope.launch {
            _history.emit(newItem)
        }
    }

    fun getHistory(): List<DownloadHistoryItem> {
        return historyList.toList()
    }
}
