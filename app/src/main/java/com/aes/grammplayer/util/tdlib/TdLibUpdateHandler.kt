package com.aes.grammplayer.util.tdlib

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi

/**
 * A singleton object that acts as the single, global result handler for all TDLib updates.
 * It uses Flows for broadcasting updates, which can be more responsive and flexible than LiveData,
 * allowing a collection on specific dispatchers and better handling of backpressure/emissions.
 *
 * Coverage:
 *  - Authorization state (StateFlow)
 *  - Connection state (StateFlow)
 *  - File updates / download progress (SharedFlow, replay = 1)
 *  - New / edited / deleted messages (SharedFlow) — used to keep Room in sync incrementally
 *  - Chat metadata changes: new chat, title, last message, position, read state (SharedFlow)
 *  - User / basic group / supergroup metadata (SharedFlow)
 *  - Options (SharedFlow) — e.g. "my_id", rate limits, etc.
 *  - Errors (SharedFlow)
 *  - A generic catch-all flow for anything not explicitly modeled, so nothing is silently dropped
 */
object TdLibUpdateHandler : Client.ResultHandler {

    // Coroutine scope for handling emissions (SupervisorJob to avoid cancellation on errors)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    // ---------------------------------------------------------------------
    // Authorization
    // ---------------------------------------------------------------------
    private val _authorizationState = MutableStateFlow<TdApi.AuthorizationState?>(null)
    val authorizationState = _authorizationState.asStateFlow()

    // ---------------------------------------------------------------------
    // Connection state (useful for showing "connecting..." / offline banners)
    // ---------------------------------------------------------------------
    private val _connectionState = MutableStateFlow<TdApi.ConnectionState?>(null)
    val connectionState = _connectionState.asStateFlow()

    // ---------------------------------------------------------------------
    // Files / downloads
    // ---------------------------------------------------------------------
    private val _fileUpdate = MutableSharedFlow<TdApi.UpdateFile>(replay = 1)
    val fileUpdate = _fileUpdate.asSharedFlow()

    // ---------------------------------------------------------------------
    // Messages (new / content changed / deleted / send succeeded-failed)
    // ---------------------------------------------------------------------
    private val _newMessage = MutableSharedFlow<TdApi.UpdateNewMessage>(extraBufferCapacity = 64)
    val newMessage = _newMessage.asSharedFlow()

    private val _messageContent = MutableSharedFlow<TdApi.UpdateMessageContent>(extraBufferCapacity = 64)
    val messageContent = _messageContent.asSharedFlow()

    private val _messageEdited = MutableSharedFlow<TdApi.UpdateMessageEdited>(extraBufferCapacity = 64)
    val messageEdited = _messageEdited.asSharedFlow()

    private val _deleteMessages = MutableSharedFlow<TdApi.UpdateDeleteMessages>(extraBufferCapacity = 64)
    val deleteMessages = _deleteMessages.asSharedFlow()

    private val _messageSendSucceeded = MutableSharedFlow<TdApi.UpdateMessageSendSucceeded>(extraBufferCapacity = 16)
    val messageSendSucceeded = _messageSendSucceeded.asSharedFlow()

    private val _messageSendFailed = MutableSharedFlow<TdApi.UpdateMessageSendFailed>(extraBufferCapacity = 16)
    val messageSendFailed = _messageSendFailed.asSharedFlow()

    // ---------------------------------------------------------------------
    // Chats
    // ---------------------------------------------------------------------
    private val _newChat = MutableSharedFlow<TdApi.UpdateNewChat>(extraBufferCapacity = 64)
    val newChat = _newChat.asSharedFlow()

    private val _chatTitle = MutableSharedFlow<TdApi.UpdateChatTitle>(extraBufferCapacity = 32)
    val chatTitle = _chatTitle.asSharedFlow()

    private val _chatPhoto = MutableSharedFlow<TdApi.UpdateChatPhoto>(extraBufferCapacity = 32)
    val chatPhoto = _chatPhoto.asSharedFlow()

    private val _chatLastMessage = MutableSharedFlow<TdApi.UpdateChatLastMessage>(extraBufferCapacity = 64)
    val chatLastMessage = _chatLastMessage.asSharedFlow()

    private val _chatPosition = MutableSharedFlow<TdApi.UpdateChatPosition>(extraBufferCapacity = 64)
    val chatPosition = _chatPosition.asSharedFlow()

    private val _chatReadInbox = MutableSharedFlow<TdApi.UpdateChatReadInbox>(extraBufferCapacity = 64)
    val chatReadInbox = _chatReadInbox.asSharedFlow()

    private val _chatReadOutbox = MutableSharedFlow<TdApi.UpdateChatReadOutbox>(extraBufferCapacity = 64)
    val chatReadOutbox = _chatReadOutbox.asSharedFlow()

    // ---------------------------------------------------------------------
    // Users / groups metadata
    // ---------------------------------------------------------------------
    private val _user = MutableSharedFlow<TdApi.UpdateUser>(extraBufferCapacity = 32)
    val user = _user.asSharedFlow()

    private val _basicGroup = MutableSharedFlow<TdApi.UpdateBasicGroup>(extraBufferCapacity = 32)
    val basicGroup = _basicGroup.asSharedFlow()

    private val _supergroup = MutableSharedFlow<TdApi.UpdateSupergroup>(extraBufferCapacity = 32)
    val supergroup = _supergroup.asSharedFlow()

    // ---------------------------------------------------------------------
    // Options (e.g. "my_id" arrives here after login)
    // ---------------------------------------------------------------------
    private val _option = MutableSharedFlow<TdApi.UpdateOption>(extraBufferCapacity = 32)
    val option = _option.asSharedFlow()

    // ---------------------------------------------------------------------
    // Errors
    // ---------------------------------------------------------------------
    private val _authError = MutableSharedFlow<TdApi.Error>(replay = 1)
    val authError = _authError.asSharedFlow()

    // ---------------------------------------------------------------------
    // Catch-all for anything not explicitly modeled above.
    // Prevents silent drops as TDLib's update surface evolves.
    // ---------------------------------------------------------------------
    private val _unhandledUpdate = MutableSharedFlow<TdApi.Object>(extraBufferCapacity = 64)
    val unhandledUpdate = _unhandledUpdate.asSharedFlow()

    /**
     * This 'onResult' method is the entry point for all TDLib updates.
     * It categorizes the object and emits it to the appropriate Flow.
     *
     * Note: onResult is called from TDLib's internal threads (not necessarily main),
     * so StateFlow.value assignment is safe (atomic), but we still route everything
     * through the scope for SharedFlow emits to keep ordering predictable on Main.immediate.
     */
    override fun onResult(obj: TdApi.Object) {
        when (obj.constructor) {

            // ---------------- Authorization ----------------
            TdApi.UpdateAuthorizationState.CONSTRUCTOR -> {
                val authState = (obj as TdApi.UpdateAuthorizationState).authorizationState
                Log.d("TdLibUpdateHandler", "Auth state: ${authState.javaClass.simpleName}")
                _authorizationState.value = authState
            }

            // ---------------- Connection ----------------
            TdApi.UpdateConnectionState.CONSTRUCTOR -> {
                val state = (obj as TdApi.UpdateConnectionState).state
                Log.d("TdLibUpdateHandler", "Connection state: ${state.javaClass.simpleName}")
                _connectionState.value = state
            }

            // ---------------- Files ----------------
            TdApi.UpdateFile.CONSTRUCTOR -> {
                val update = obj as TdApi.UpdateFile
                scope.launch { _fileUpdate.emit(update) }
            }

            // ---------------- Messages ----------------
            TdApi.UpdateNewMessage.CONSTRUCTOR -> {
                val update = obj as TdApi.UpdateNewMessage
                scope.launch { _newMessage.emit(update) }
            }

            TdApi.UpdateMessageContent.CONSTRUCTOR -> {
                val update = obj as TdApi.UpdateMessageContent
                scope.launch { _messageContent.emit(update) }
            }

            TdApi.UpdateMessageEdited.CONSTRUCTOR -> {
                val update = obj as TdApi.UpdateMessageEdited
                scope.launch { _messageEdited.emit(update) }
            }

            TdApi.UpdateDeleteMessages.CONSTRUCTOR -> {
                val update = obj as TdApi.UpdateDeleteMessages
                scope.launch { _deleteMessages.emit(update) }
            }

            TdApi.UpdateMessageSendSucceeded.CONSTRUCTOR -> {
                val update = obj as TdApi.UpdateMessageSendSucceeded
                scope.launch { _messageSendSucceeded.emit(update) }
            }

            TdApi.UpdateMessageSendFailed.CONSTRUCTOR -> {
                val update = obj as TdApi.UpdateMessageSendFailed
                Log.w("TdLibUpdateHandler", "Message send failed: ${update.error?.message}")
                scope.launch { _messageSendFailed.emit(update) }
            }

            // ---------------- Chats ----------------
            TdApi.UpdateNewChat.CONSTRUCTOR -> {
                val update = obj as TdApi.UpdateNewChat
                scope.launch { _newChat.emit(update) }
            }

            TdApi.UpdateChatTitle.CONSTRUCTOR -> {
                val update = obj as TdApi.UpdateChatTitle
                scope.launch { _chatTitle.emit(update) }
            }

            TdApi.UpdateChatPhoto.CONSTRUCTOR -> {
                val update = obj as TdApi.UpdateChatPhoto
                scope.launch { _chatPhoto.emit(update) }
            }

            TdApi.UpdateChatLastMessage.CONSTRUCTOR -> {
                val update = obj as TdApi.UpdateChatLastMessage
                scope.launch { _chatLastMessage.emit(update) }
            }

            TdApi.UpdateChatPosition.CONSTRUCTOR -> {
                val update = obj as TdApi.UpdateChatPosition
                scope.launch { _chatPosition.emit(update) }
            }

            TdApi.UpdateChatReadInbox.CONSTRUCTOR -> {
                val update = obj as TdApi.UpdateChatReadInbox
                scope.launch { _chatReadInbox.emit(update) }
            }

            TdApi.UpdateChatReadOutbox.CONSTRUCTOR -> {
                val update = obj as TdApi.UpdateChatReadOutbox
                scope.launch { _chatReadOutbox.emit(update) }
            }

            // ---------------- Users / Groups ----------------
            TdApi.UpdateUser.CONSTRUCTOR -> {
                val update = obj as TdApi.UpdateUser
                scope.launch { _user.emit(update) }
            }

            TdApi.UpdateBasicGroup.CONSTRUCTOR -> {
                val update = obj as TdApi.UpdateBasicGroup
                scope.launch { _basicGroup.emit(update) }
            }

            TdApi.UpdateSupergroup.CONSTRUCTOR -> {
                val update = obj as TdApi.UpdateSupergroup
                scope.launch { _supergroup.emit(update) }
            }

            // ---------------- Options ----------------
            TdApi.UpdateOption.CONSTRUCTOR -> {
                val update = obj as TdApi.UpdateOption
                Log.d("TdLibUpdateHandler", "Option changed: ${update.name}")
                scope.launch { _option.emit(update) }
            }

            // ---------------- Errors ----------------
            TdApi.Error.CONSTRUCTOR -> {
                val error = obj as TdApi.Error
                Log.e("TdLibUpdateHandler", "TDLib error: ${error.code} ${error.message}")
                scope.launch { _authError.emit(error) }
            }

            // ---------------- Everything else ----------------
            else -> {
                // Catch-all so future/uncommon updates (UpdateChatFilters, UpdateStickerSet,
                // UpdateUnreadChatCount, UpdateChatNotificationSettings, etc.) aren't dropped
                // silently. Collect this flow anywhere you need to debug or add handling later.
                scope.launch { _unhandledUpdate.emit(obj) }
            }
        }
    }
}