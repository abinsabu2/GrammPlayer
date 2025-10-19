package com.aes.grammplayer

import java.io.Serializable

/**
 * MediaMessage class represents a media entity from a chat,
 * including details about the file, its location, and thumbnails.
 */
data class MediaMessage(
    // Core properties
    val id: Long = 0,
    val title: String? = null,
    val chatId: Long, // Add this property
    val description: String? = null,
    val studio: String? = null, // Can represent the source, e.g., "Telegram"

    // Media file properties
    val isMedia: Boolean = false,
    var localPath: String? = null,
    val fileId: Int = 0,
    val mimeType: String? = null,
    val videoUrl: String? = null, // Main content URL

    // Dimensions and duration
    val width: Int = 0,
    val height: Int = 0,
    val duration: Int = 0,
    val size: Long = 0,

    // Thumbnail properties
    val thumbnailPath: String? = null,
    val cardImageUrl: String? = null, // Main thumbnail for cards
    val backgroundImageUrl: String? = null // Background image for details screen
) : Serializable {

    override fun toString(): String {
        return "MediaMessage{" +
                "id=$id, " +
                "title='$title', " +
                "isMedia=$isMedia, " +
                "description='$description', " +
                "localPath='$localPath', " +
                "fileId=$fileId, " +
                "mimeType='$mimeType'" +
                '}'
    }

    companion object {
        // It's good practice to update the serialVersionUID when the class structure changes.
        internal const val serialVersionUID = 727566175075960653L
    }
}
