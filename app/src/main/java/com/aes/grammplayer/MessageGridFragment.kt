package com.aes.grammplayer

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.leanback.app.VerticalGridSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.BaseGridView
import androidx.leanback.widget.VerticalGridPresenter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.drinkless.tdlib.TdApi
import androidx.leanback.widget.OnItemViewClickedListener
import androidx.leanback.widget.Presenter
import androidx.leanback.widget.Row // Make sure this import is correct
import androidx.leanback.widget.RowPresenter

/**
 * A fragment to display messages of a specific chat in a grid.
 */
class MessageGridFragment : VerticalGridSupportFragment() {

    private lateinit var gridAdapter: ArrayObjectAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = arguments?.getString(ARG_CHAT_TITLE) ?: "Messages"
        setupGrid()
        loadMessages()
        setupEventListeners()
    }

    private fun setupGrid() {
        // We need a custom presenter to access the grid view.
        val gridPresenter = object : VerticalGridPresenter() {
            override fun initializeGridViewHolder(vh: ViewHolder) {
                super.initializeGridViewHolder(vh)
            }
        }

        gridPresenter.numberOfColumns = 3 // You can adjust the number of columns here
        setGridPresenter(gridPresenter)

        // The rest of your code remains the same.
        gridAdapter = ArrayObjectAdapter(CardPresenter())
        adapter = gridAdapter
    }


    private fun setupEventListeners() {
        onItemViewClickedListener = object : OnItemViewClickedListener {
            override fun onItemClicked(
                itemViewHolder: Presenter.ViewHolder?,
                item: Any?,
                rowViewHolder: RowPresenter.ViewHolder?,
                row: Row?
            ) {
                if (item is MediaMessage) {
                    // --- NEW LOGIC: SHOW BOTTOM SHEET ---
                    // Create an instance of the bottom sheet with the clicked media message
                    val bottomSheet = MediaDetailsBottomSheetFragment.newInstance(item)

                    // Show the bottom sheet using the fragment manager
                    bottomSheet.show(parentFragmentManager, MediaDetailsBottomSheetFragment.TAG)
                }
            }
        }
    }


    private fun loadMessages() {
        val chatId = arguments?.getLong(ARG_CHAT_ID) ?: 0L
        if (chatId == 0L) {
            Log.e(TAG, "No Chat ID provided, cannot load messages.")
            return
        }

        // Use Coroutines to call the suspend function on the main thread.
        CoroutineScope(Dispatchers.Main).launch {
            try {
                // --- THIS IS THE FIX ---
                // We will collect all messages in this list.
                val allMessages = mutableListOf<TdApi.Message>()
                var fromMessageId: Long = 0 // Start from the most recent message

                // We will loop until we have enough messages or there are no more to load.
                while (allMessages.size < 10000) {
                    // Call the suspend function from the manager to get a chunk of messages.
                    val messagesChunk = TelegramClientManager.loadMessagesForChat(
                        chatId = chatId,
                        fromMessageId = fromMessageId, // Use the ID of the last message we received
                        limit = 1000 // It's okay to request a large chunk
                    )

                    // If TDLib returns an empty chunk, it means we've reached the beginning of the chat history.
                    if (messagesChunk.isEmpty()) {
                        break // Exit the loop
                    }

                    allMessages.addAll(messagesChunk)

                    // Set the 'fromMessageId' for the next loop iteration.
                    // This tells TDLib where to continue loading from.
                    fromMessageId = messagesChunk.last().id
                }

                // The rest of your code stays the same.
                val mediaMessages = allMessages.map { parseMessageContent(it.content) }

                if (mediaMessages.isEmpty()) {
                    return@launch
                }

                mediaMessages.forEach { message ->
                    if(message.isMedia){
                        gridAdapter.add(message)
                    }

                }

            } catch (e: Exception) {
                Log.e(TAG, "Error loading messages", e)
                // You could display an error to the user here.
            }
        }
    }

    private fun parseMessageContent(content: TdApi.MessageContent): MediaMessage {
        return when (content) {
            is TdApi.MessageVideo -> {
                val video = content.video
                val file = video.video
                val thumbnail = video.thumbnail
                val chatId = arguments?.getLong(ARG_CHAT_ID) ?: 0L

                MediaMessage(
                    // Core properties
                    id = file.id.toLong(),
                    title = video.fileName.ifEmpty { "Video" },
                    description = content.caption.text, // Use the video caption as the description
                    studio = "Telegram",
                    // Media file properties
                    chatId = chatId,
                    isMedia = true,
                    localPath = file.local.path.takeIf { it.isNotEmpty() },
                    fileId = file.id,
                    mimeType = video.mimeType,
                    // Dimensions and duration
                    width = video.width,
                    height = video.height,
                    duration = video.duration,
                    size = file.size,
                    // Thumbnail properties
                    thumbnailPath = thumbnail?.file?.local?.path?.takeIf { it.isNotEmpty() },
                    cardImageUrl = thumbnail?.file?.local?.path?.takeIf { it.isNotEmpty() } // Use thumbnail for card image
                )
            }

            is TdApi.MessageDocument -> {
                val document = content.document
                val file = document.document
                val thumbnail = document.thumbnail
                val chatId = arguments?.getLong(ARG_CHAT_ID) ?: 0L

                MediaMessage(
                    // Core properties
                    id = file.id.toLong(),
                    title = document.fileName.ifEmpty { "Document" },
                    description = content.caption.text, // Use the document caption
                    studio = "Telegram",

                    // Media file properties
                    isMedia = true, // A document can be a media file (e.g., mp4, mkv)
                    localPath = file.local.path.takeIf { it.isNotEmpty() },
                    fileId = file.id,
                    mimeType = document.mimeType,
                    chatId = chatId,
                    // Dimensions and duration (not applicable for all documents)
                    size = file.size,
                    // Thumbnail properties
                    thumbnailPath = thumbnail?.file?.local?.path?.takeIf { it.isNotEmpty() },
                    cardImageUrl = thumbnail?.file?.local?.path?.takeIf { it.isNotEmpty() }
                )
            }

            is TdApi.MessageText -> {
                // Handle plain text messages
                MediaMessage(
                    id = content.hashCode().toLong(), // Generate a stable ID for text messages
                    title = "Text Message",
                    description = content.text.text,
                    isMedia = false,
                    studio = "Telegram",
                    chatId = arguments?.getLong(ARG_CHAT_ID) ?: 0L
                )
            }

            else -> {
                // Default case for unhandled message types
                MediaMessage(
                    id = content.hashCode().toLong(),
                    title = "Unsupported Content",
                    description = "This message type is not currently supported.",
                    isMedia = false,
                    studio = "Telegram",
                    chatId = arguments?.getLong(ARG_CHAT_ID) ?: 0L
                )
            }
        }
    }

    companion object {
        private const val TAG = "MessageGridFragment"
        const val ARG_CHAT_ID = "chat_id"
        private const val ARG_CHAT_TITLE = "chat_title"

        /**
         * Factory method to create a new instance of this fragment with the required arguments.
         */
        fun newInstance(chatId: Long, chatTitle: String): MessageGridFragment {
            val fragment = MessageGridFragment()
            val args = Bundle()
            args.putLong(ARG_CHAT_ID, chatId)
            args.putString(ARG_CHAT_TITLE, chatTitle)
            fragment.arguments = args
            return fragment
        }
    }

}
