package com.aes.grammplayer

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

/**
 * A singleton object to globally track the state of media playback.
 * This allows different parts of the app (like a fragment and an activity)
 * to know if something is currently playing.
 */
object PlaybackStateManager {

    // Use LiveData to broadcast the playing state.
    // This is observable and lifecycle-aware.
    private val _isPlaying = MutableLiveData<Boolean>(false)
    val isPlaying: LiveData<Boolean> = _isPlaying

    // The ID of the file currently being played.
    // We use this to prevent re-launching the player for the same file.
    private var currentlyPlayingFileId: Int = 0

    /**
     * Call this when playback starts.
     */
    fun onPlaybackStarted(fileId: Int) {
        currentlyPlayingFileId = fileId
        _isPlaying.postValue(true)
    }

    /**
     * Call this when playback stops (e.g., when the PlayerActivity is closed).
     */
    fun onPlaybackStopped() {
        currentlyPlayingFileId = 0
        _isPlaying.postValue(false)
    }

    /**
     * Checks if a specific file is currently the one being played.
     */
    fun isCurrentlyPlaying(fileId: Int): Boolean {
        // Check if the player is active AND if it's playing the same file ID.
        return _isPlaying.value == true && currentlyPlayingFileId == fileId
    }
}
