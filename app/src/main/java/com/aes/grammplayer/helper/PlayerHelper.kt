package com.aes.grammplayer.helper

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import com.aes.grammplayer.R
import com.aes.grammplayer.ui.features.playback.InAppPlaybackActivity
import com.aes.grammplayer.util.analytics.AnalyticsHelper

object PlayerHelper {

    private const val TAG = "PlayerHelper"
    const val VLC_PACKAGE = "org.videolan.vlc"
    private const val VLC_PLAYER_ACTIVITY = "org.videolan.vlc.gui.video.VideoPlayerActivity"
    private const val VLC_STOP_ACTION = "org.videolan.vlc.remote.StopPlayback"
    private const val PLAY_STORE_PACKAGE = "com.android.vending"
    private const val AMAZON_APPSTORE_PACKAGE = "com.amazon.venezia"
    private const val AMAZON_VLC_ASIN = "B00X4N8W2G"

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

    /** Opens full-screen playback inside the app (bundled libVLC). */
    fun playInApp(context: Context, filePath: String?, fileId: Int = 0): PlayResult {
        val file = MediaFileHelper.resolveFile(filePath)
        if (file == null) {
            val path = filePath ?: "N/A"
            return PlayResult.Failed(
                "Cannot play: File path invalid, does not exist, or too small: $path"
            )
        }

        return try {
            val intent = Intent(context, InAppPlaybackActivity::class.java).apply {
                putExtra(InAppPlaybackActivity.EXTRA_FILE_PATH, file.absolutePath)
                if (context !is Activity) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            context.startActivity(intent)
            AnalyticsHelper.logMediaPlay(fileId, "in_app")
            PlayResult.Started(fileId, file.absolutePath)
        } catch (e: Exception) {
            Log.e(TAG, "Error launching in-app player for ${file.absolutePath}", e)
            PlayResult.Failed("Error starting in-app playback: ${e.message}")
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
            AnalyticsHelper.logMediaPlay(fileId, "vlc")
            PlayResult.Started(fileId, file.absolutePath)
        } catch (e: Exception) {
            Log.e(TAG, "Error launching VLC for ${file.absolutePath}", e)
            PlayResult.Failed(
                "Error while launching VLC Media Player for ${file.absolutePath}: ${e.message}"
            )
        }
    }

    private fun buildVlcPlayIntent(context: Context, contentUri: Uri): Intent {
        fun baseIntent(): Intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(contentUri, "video/*")
            clipData = ClipData.newRawUri("media", contentUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            putExtra("title", "GrammPlayer")
            putExtra("from_start", true)
        }

        val viewIntent = baseIntent().apply { setPackage(VLC_PACKAGE) }
        if (viewIntent.resolveActivity(context.packageManager) != null) {
            return viewIntent
        }

        Log.d(TAG, "VLC VIEW intent not resolved; using explicit VideoPlayerActivity")
        return baseIntent().apply {
            component = ComponentName(VLC_PACKAGE, VLC_PLAYER_ACTIVITY)
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

    /** True on Fire TV / Fire OS devices (Amazon hardware). */
    fun isFireTv(): Boolean =
        Build.MANUFACTURER.equals("Amazon", ignoreCase = true) ||
            Build.MODEL.startsWith("AFT", ignoreCase = true)

    fun vlcRequiredMessage(context: Context): String =
        if (isFireTv()) {
            context.getString(R.string.playback_vlc_required_message_fire)
        } else {
            context.getString(R.string.playback_vlc_required_message)
        }

    fun vlcInstallButtonLabel(context: Context): String =
        if (isFireTv()) {
            context.getString(R.string.playback_vlc_install_fire)
        } else {
            context.getString(R.string.playback_vlc_install)
        }

    /**
     * Opens the best VLC install option for the current device.
     * Fire TV → Amazon Appstore, then VLC download page. Other TVs → Play Store fallback.
     */
    fun openVlcInstallPage(context: Context) {
        for (intent in vlcInstallIntents()) {
            if (intent.resolveActivity(context.packageManager) == null) continue
            try {
                context.startActivity(intent)
                return
            } catch (e: ActivityNotFoundException) {
                Log.w(TAG, "VLC install intent not handled: ${intent.data}", e)
            }
        }
        Toast.makeText(context, R.string.playback_vlc_install_failed, Toast.LENGTH_LONG).show()
    }

    private fun vlcInstallIntents(): List<Intent> {
        if (isFireTv()) {
            return listOf(
                amazonAppStoreIntent("amzn://apps/android?asin=$AMAZON_VLC_ASIN"),
                amazonAppStoreIntent("amzn://apps/android?p=$VLC_PACKAGE"),
                browserIntent("https://www.amazon.com/gp/mas/dl/android?p=$VLC_PACKAGE"),
                browserIntent("https://www.videolan.org/vlc/download-android.html"),
            )
        }
        return listOf(
            playStoreIntent("market://details?id=$VLC_PACKAGE"),
            browserIntent("https://play.google.com/store/apps/details?id=$VLC_PACKAGE"),
        )
    }

    private fun amazonAppStoreIntent(uri: String): Intent =
        Intent(Intent.ACTION_VIEW, Uri.parse(uri)).apply {
            setPackage(AMAZON_APPSTORE_PACKAGE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    private fun playStoreIntent(uri: String): Intent =
        Intent(Intent.ACTION_VIEW, Uri.parse(uri)).apply {
            setPackage(PLAY_STORE_PACKAGE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    private fun browserIntent(uri: String): Intent =
        Intent(Intent.ACTION_VIEW, Uri.parse(uri)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
}