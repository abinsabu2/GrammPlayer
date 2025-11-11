// You can place this in a new file, e.g., DownloadHistoryItem.kt
import com.aes.grammplayer.MediaMessage
import java.util.Date

data class DownloadHistoryItem(
    val mediaMessage: MediaMessage,
    val timestamp: Date = Date()
)
