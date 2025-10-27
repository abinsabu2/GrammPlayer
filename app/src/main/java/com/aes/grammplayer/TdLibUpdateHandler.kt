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

    private val _authError = MutableLiveData<TdApi.Error>()
    val authError: LiveData<TdApi.Error> = _authError

    private val _activeDownloads = MutableLiveData<List<TdApi.File>>(emptyList())
    val activeDownloads: LiveData<List<TdApi.File>> = _activeDownloads

    /**
     * This 'invoke' method is the entry point for all TDLib updates.
     * It categorizes the update and posts it to the appropriate LiveData stream.
     */
    override fun onResult(obj: TdApi.Object) {
        when (obj.constructor) {
            TdApi.UpdateAuthorizationState.CONSTRUCTOR -> {
                val authState = (obj as TdApi.UpdateAuthorizationState).authorizationState
                Log.d("TdLibUpdateHandler", "Global Auth state: ${authState.javaClass.simpleName}")
                _authorizationState.postValue(authState)
            }

            TdApi.UpdateFile.CONSTRUCTOR -> {
                val fileUpdate = obj as TdApi.UpdateFile
                // Post the file update. Any screen observing this will be notified.
                _fileUpdate.postValue(fileUpdate)

                // Update the active downloads list based on the file's download state
                val file = fileUpdate.file
                val currentDownloads = _activeDownloads.value ?: emptyList()
                if (file.local.isDownloadingActive) {
                    // Add or update the file in the list
                    val updatedList = if (currentDownloads.any { it.id == file.id }) {
                        currentDownloads.map { if (it.id == file.id) file else it }
                    } else {
                        currentDownloads + file
                    }
                    _activeDownloads.postValue(updatedList)
                } else {
                    // Remove the file if it's no longer downloading
                    _activeDownloads.postValue(currentDownloads.filter { it.id != file.id })
                }
            }

            TdApi.Error.CONSTRUCTOR -> {
                val authError = (obj as TdApi.Error)
                _authError.postValue(authError)
            }

            else -> {
                // You can uncomment this to see all unhandled updates for debugging.
                // Log.d("TdLibUpdateHandler", "Unhandled update: ${update?.javaClass?.simpleName}")
            }
        }
    }
}