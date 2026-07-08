package com.aes.grammplayer.helper

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider

object PlayerHelper {

    private const val TAG = "PlayerHelper"
    const val VLC_PACKAGE = "org.videolan.vlc"
    private const val VLC_PLAYER_ACTIVITY = "org.videolan.vlc.gui.video.VideoPlayerActivity"
    private const val VLC_STOP_ACTION = "org.videolan.vlc.remote.StopPlayback"

    sealed class PlayResult {
        data class Started(val fileId: Int, val path: String) : PlayResult()
        data class Failed(val reason: String) : PlayResult()
    }

    /**
     * Checks whether the VLC app package is present. Uses [PackageManager.getPackageInfo]
     * because [PackageManager.getLaunchIntentForPackage] often returns null on TV devices
     * even when VLC is installed.
     */
    fun isVlcInstalled(context: Context): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    VLC_PACKAGE,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(VLC_PACKAGE, 0)
            }
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    fun play(context: Context, filePath: String?, fileId: Int = 0): PlayResult {
        if (!isVlcInstalled(context)) {
            return PlayResult.Failed("VLC Media Player is not installed")
        }

        val file = MediaFileHelper.resolveFile(filePath)
        if (file == null) {
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
            val intent = buildVlcPlayIntent(context, contentUri)
            context.startActivity(intent)
            PlayResult.Started(fileId, file.absolutePath)
        } catch (e: Exception) {
            Log.e(TAG, "Error launching VLC for ${file.absolutePath}", e)
            PlayResult.Failed(
                "Error while launching VLC Media Player for ${file.absolutePath}: ${e.message}"
            )
        }
    }

    private fun buildVlcPlayIntent(context: Context, contentUri: Uri): Intent {
        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(contentUri, "video/*")
            setPackage(VLC_PACKAGE)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            putExtra("title", "GrammPlayer")
            putExtra("from_start", true)
        }
        if (viewIntent.resolveActivity(context.packageManager) != null) {
            return viewIntent
        }

        Log.d(TAG, "VLC VIEW intent not resolved; using explicit VideoPlayerActivity")
        return Intent(Intent.ACTION_VIEW).apply {
            component = ComponentName(VLC_PACKAGE, VLC_PLAYER_ACTIVITY)
            setDataAndType(contentUri, "video/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            putExtra("title", "GrammPlayer")
            putExtra("from_start", true)
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