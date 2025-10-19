package com.aes.grammplayer

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
/**
 * A singleton object that acts as the single, global result handler for all TDLib updates.
 * It uses LiveData to broadcast updates to any interested part of the UI.
 */
object TdLibUpdateHandler : Client.ResultHandler {

    private val _authorizationState = MutableLiveData<TdApi.AuthorizationState>()
    val authorizationState: LiveData<TdApi.AuthorizationState> = _authorizationState

    private val _fileUpdate = MutableLiveData<TdApi.UpdateFile>()
    val fileUpdate: LiveData<TdApi.UpdateFile> = _fileUpdate

    /**
     * This 'invoke' method is the entry point for all TDLib updates.
     * It categorizes the update and posts it to the appropriate LiveData stream.
     */
    override fun onResult(obj: TdApi.Object) {
        when (obj?.constructor) {
            TdApi.UpdateAuthorizationState.CONSTRUCTOR -> {
                val authState = (obj as TdApi.UpdateAuthorizationState).authorizationState
                Log.d("TdLibUpdateHandler", "Global Auth state: ${authState.javaClass.simpleName}")
                _authorizationState.postValue(authState)
            }

            TdApi.UpdateFile.CONSTRUCTOR -> {
                val fileUpdate = obj as TdApi.UpdateFile
                // Post the file update. Any screen observing this will be notified.
                _fileUpdate.postValue(fileUpdate)
            }

            TdApi.Error.CONSTRUCTOR -> {
                val error = obj as TdApi.Error
                Log.e("TdLibUpdateHandler", "Global Error: [${error.code}] ${error.message}")
            }

            else -> {
                // You can uncomment this to see all unhandled updates for debugging.
                // Log.d("TdLibUpdateHandler", "Unhandled update: ${update?.javaClass?.simpleName}")
            }
        }
    }
}
