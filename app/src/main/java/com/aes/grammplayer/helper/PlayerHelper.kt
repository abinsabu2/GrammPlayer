package com.aes.grammplayer.helper

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider

object PlayerHelper {

    private const val TAG = "PlayerHelper"
    const val VLC_PACKAGE = "org.videolan.vlc"
    private const val VLC_STOP_ACTION = "org.videolan.vlc.remote.StopPlayback"

    sealed class PlayResult {
        data class Started(val fileId: Int, val path: String) : PlayResult()
        data class Failed(val reason: String) : PlayResult()
    }

    fun isVlcInstalled(context: Context): Boolean =
        context.packageManager.getLaunchIntentForPackage(VLC_PACKAGE) != null

    fun play(context: Context, filePath: String?, fileId: Int = 0): PlayResult {
        if (!isVlcInstalled(context)) {
            return PlayResult.Failed("VLC Media Player is not installed")
        }

        val file = MediaFileHelper.resolveFile(filePath)
        if (file == null || fileId == 0) {
            val path = filePath ?: "N/A"
            return PlayResult.Failed(
                "Cannot play: File path invalid, does not exist, or too small: $path"
            )
        }

        return try {
            val contentUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                file
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, "video/*")
                setPackage(VLC_PACKAGE)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                putExtra("title", "GrammPlayer")
                putExtra("from_start", true)
            }
            context.startActivity(intent)
            PlayResult.Started(fileId, file.absolutePath)
        } catch (e: Exception) {
            Log.e(TAG, "Error launching VLC for ${file.absolutePath}", e)
            PlayResult.Failed(
                "Error while launching VLC Media Player for ${file.absolutePath}: ${e.message}"
            )
        }
    }

    fun stop(context: Context) {
        if (!isVlcInstalled(context)) return
        val stopIntent = Intent(VLC_STOP_ACTION).apply {
            setPackage(VLC_PACKAGE)
        }
        try {
            context.sendBroadcast(stopIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping VLC playback", e)
        }
    }
}