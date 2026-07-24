package com.aes.grammplayer.helper

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.core.content.FileProvider
import com.aes.grammplayer.R
import java.io.File

object PlayerHelper {

    private const val TAG = "PlayerHelper"
    const val VLC_PACKAGE = "org.videolan.vlc"
    /** Public VIEW entry point — handles content/file/http video schemes. */
    private const val VLC_START_ACTIVITY = "org.videolan.vlc.StartActivity"
    /** Internal player; not registered for external ACTION_VIEW + content URIs. */
    private const val VLC_PLAYER_ACTIVITY = "org.videolan.vlc.gui.video.VideoPlayerActivity"
    private const val VLC_STOP_ACTION = "org.videolan.vlc.remote.StopPlayback"
    private const val PLAY_STORE_PACKAGE = "com.android.vending"
    private const val AMAZON_APPSTORE_PACKAGE = "com.amazon.venezia"
    private const val AMAZON_VLC_ASIN = "B00X4N8W2G"

    sealed class PlayResult {
        data class Started(val fileId: Int, val path: String) : PlayResult()
        data class Failed(val reason: String) : PlayResult()
    }

    fun isVlcInstalled(context: Context): Boolean {
        val pm = context.packageManager
        val installed = hasPackage(pm, VLC_PACKAGE) ||
            canResolveVlcView(pm) ||
            canResolveVlcComponent(pm, VLC_START_ACTIVITY) ||
            canResolveVlcComponent(pm, VLC_PLAYER_ACTIVITY)

        Log.i(TAG, "isVlcInstalled=$installed (package=${hasPackage(pm, VLC_PACKAGE)}, view=${canResolveVlcView(pm)})")
        return installed
    }

    private fun hasPackage(pm: PackageManager, packageName: String): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(packageName, 0)
            }
            true
        } catch (e: PackageManager.NameNotFoundException) {
            Log.d(TAG, "getPackageInfo($packageName) not found — check <queries> visibility", e)
            false
        } catch (e: Exception) {
            Log.w(TAG, "getPackageInfo($packageName) failed", e)
            false
        }
    }

    private fun canResolveVlcView(pm: PackageManager): Boolean {
        val probe = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse("content://org.videolan.vlc.probe/sample.mp4"), "video/*")
            setPackage(VLC_PACKAGE)
            addCategory(Intent.CATEGORY_DEFAULT)
        }
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.queryIntentActivities(
                    probe,
                    PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong())
                ).isNotEmpty()
            } else {
                @Suppress("DEPRECATION")
                pm.queryIntentActivities(probe, PackageManager.MATCH_DEFAULT_ONLY).isNotEmpty()
            } || probe.resolveActivity(pm) != null
        } catch (e: Exception) {
            Log.w(TAG, "canResolveVlcView failed", e)
            false
        }
    }

    private fun canResolveVlcComponent(pm: PackageManager, activityClass: String): Boolean {
        return try {
            val intent = Intent().setComponent(ComponentName(VLC_PACKAGE, activityClass))
            intent.resolveActivity(pm) != null
        } catch (e: Exception) {
            Log.w(TAG, "canResolveVlcComponent($activityClass) failed", e)
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
            val mimeType = mimeTypeFor(file)
            val intent = buildVlcPlayIntent(context, contentUri, mimeType)
            grantVlcUriPermission(context, contentUri)
            Log.i(TAG, "Launching VLC component=${intent.component} package=${intent.`package`} uri=$contentUri mime=$mimeType")
            context.startActivity(intent)
            PlayResult.Started(fileId, file.absolutePath)
        } catch (e: ActivityNotFoundException) {
            Log.e(TAG, "No activity to handle VLC play intent for ${file.absolutePath}", e)
            PlayResult.Failed("VLC Media Player is not installed or cannot open this file")
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException launching VLC for ${file.absolutePath}", e)
            PlayResult.Failed("Permission denied launching VLC: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Error launching VLC for ${file.absolutePath}", e)
            PlayResult.Failed(
                "Error while launching VLC Media Player for ${file.absolutePath}: ${e.message}"
            )
        }
    }

    private fun mimeTypeFor(file: File): String {
        val ext = file.extension.lowercase()
        if (ext.isNotEmpty()) {
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)?.let { return it }
        }
        return "video/*"
    }

    private fun grantVlcUriPermission(context: Context, contentUri: Uri) {
        try {
            context.grantUriPermission(
                VLC_PACKAGE,
                contentUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (e: Exception) {
            Log.w(TAG, "Could not pre-grant URI permission to VLC", e)
        }
    }

    /**
     * Prefer VLC's public StartActivity (registered for ACTION_VIEW + content/file).
     * VideoPlayerActivity is internal and only exposes Samsung REMOTE_ACTION on modern builds.
     */
    private fun buildVlcPlayIntent(context: Context, contentUri: Uri, mimeType: String): Intent {
        fun baseIntent(): Intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(contentUri, mimeType)
            clipData = ClipData.newRawUri("media", contentUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            addCategory(Intent.CATEGORY_DEFAULT)

            putExtra("title", "GrammPlayer")
            putExtra("from_start", true)
            putExtra("fullscreen", true)
            putExtra("start_paused", false)

            // Stable decoding on Realtek / low-end TV SoCs
            putExtra("hw", false)
            putExtra("avcodec-hw", "none")
            putExtra("android-mediacodec", false)
            putExtra("deinterlace", false)
            putExtra("vout", "android-opaque")
        }

        val pm = context.packageManager

        val startIntent = baseIntent().apply {
            component = ComponentName(VLC_PACKAGE, VLC_START_ACTIVITY)
        }
        if (startIntent.resolveActivity(pm) != null) {
            Log.d(TAG, "Using VLC StartActivity for VIEW")
            return startIntent
        }

        val packageIntent = baseIntent().apply {
            setPackage(VLC_PACKAGE)
        }
        if (packageIntent.resolveActivity(pm) != null) {
            Log.d(TAG, "Using package-scoped VIEW intent for VLC")
            return packageIntent
        }

        // Last resort: older VLC builds routed VIEW into VideoPlayerActivity
        Log.w(TAG, "Falling back to VideoPlayerActivity")
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
            browserIntent("https://www.videolan.org/vlc/download-android.html"),
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
