package com.aes.grammplayer

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
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
 * allowing collection on specific dispatchers and better handling of backpressure/emissions.
 */
object TdLibUpdateHandler : Client.ResultHandler {

    // Coroutine scope for handling emissions (SupervisorJob to avoid cancellation on errors)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    // Use StateFlow for state-holding updates like authorization (emits latest value to new collectors)
    private val _authorizationState = MutableStateFlow<TdApi.AuthorizationState?>(null)
    val authorizationState = _authorizationState.asStateFlow()

    // Use SharedFlow for event-like updates like file updates (replay=1 to cache the last emission)
    private val _fileUpdate = MutableSharedFlow<TdApi.UpdateFile>(replay = 1)
    val fileUpdate = _fileUpdate.asSharedFlow()

    // SharedFlow for errors
    private val _authError = MutableSharedFlow<TdApi.Error>(replay = 1)
    val authError = _authError.asSharedFlow()

    /**
     * This 'onResult' method is the entry point for all TDLib updates.
     * It categorizes the object and emits it to the appropriate Flow.
     */
    override fun onResult(obj: TdApi.Object) {
        when (obj.constructor) {
            TdApi.UpdateAuthorizationState.CONSTRUCTOR -> {
                val authState = (obj as TdApi.UpdateAuthorizationState).authorizationState
                Log.d("TdLibUpdateHandler", "Global Auth state: ${authState.javaClass.simpleName}")
                _authorizationState.value = authState  // Direct set for StateFlow (main thread safe)
            }

            TdApi.UpdateFile.CONSTRUCTOR -> {
                val fileUpdate = obj as TdApi.UpdateFile
                scope.launch {
                    _fileUpdate.emit(fileUpdate)  // Emit asynchronously if needed, but fast
                }
            }

            TdApi.Error.CONSTRUCTOR -> {
                val authError = (obj as TdApi.Error)
                scope.launch {
                    _authError.emit(authError)
                }
            }

            else -> {
                // You can uncomment this to see all unhandled updates for debugging.
                // Log.d("TdLibUpdateHandler", "Unhandled object: ${obj.javaClass.simpleName}")
            }
        }
    }
}