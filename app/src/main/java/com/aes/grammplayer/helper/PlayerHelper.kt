package com.aes.grammplayer.helper

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.core.content.FileProvider
import com.aes.grammplayer.R
import java.io.File
import java.io.FileInputStream
import java.io.IOException

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

    /** VLC intent: when true, start at 0; when false, honor [EXTRA_START_TIME]. */
    const val EXTRA_FROM_START = "from_start"
    /** VLC intent: start position in milliseconds (PLAY_EXTRA_START_TIME). */
    const val EXTRA_START_TIME = "position"
    /** VLC intent: media Uri (PLAY_EXTRA_ITEM_LOCATION). */
    const val EXTRA_ITEM_LOCATION = "item_location"
    /**
     * VLC VideoPlayerActivity extra. Presence disables HW decode
     * (`PLAY_DISABLE_HARDWARE`). `"hw"` / `"vout"` extras are ignored.
     */
    const val EXTRA_DISABLE_HARDWARE = "disable_hardware"
    /** Tells VLC this launch came from another app so it won't reopen VLC's own UI on exit. */
    const val EXTRA_FROM_EXTERNAL = "from_external"
    /** Returned by VLC VideoPlayerActivity on exit. */
    const val RESULT_EXTRA_POSITION = "extra_position"
    const val RESULT_EXTRA_DURATION = "extra_duration"
    const val RESULT_EXTRA_URI = "extra_uri"
    private const val FILE_READY_TIMEOUT_MS = 800L
    private const val FILE_READY_RETRY_MS = 80L

    sealed class PlayResult {
        data class Started(val fileId: Int, val path: String) : PlayResult()
        data class Failed(val reason: String) : PlayResult()
        /** Intent ready for [androidx.activity.result.ActivityResultLauncher]; caller starts it. */
        data class Ready(val intent: Intent, val fileId: Int, val path: String) : PlayResult()
    }

    data class PlaybackExitPosition(
        val positionMs: Long,
        val durationMs: Long,
        val uri: String?
    )

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

    /**
     * Builds a VLC play intent without starting it.
     * Use with Activity Result API so VLC can return [RESULT_EXTRA_POSITION].
     *
     * @param startPositionMs when > 0, resume at that offset; otherwise start from beginning.
     * @param forActivityResult when true, omits FLAG_ACTIVITY_NEW_TASK so setResult is delivered.
     */
    fun preparePlay(
        context: Context,
        filePath: String?,
        fileId: Int = 0,
        startPositionMs: Long = 0L,
        forActivityResult: Boolean = true
    ): PlayResult {
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
        if (!waitUntilReadable(file)) {
            Log.w(TAG, "File not readable yet: ${file.absolutePath} len=${file.length()}")
        }

        return try {
            val contentUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                file
            )
            val mimeType = mimeTypeFor(file)
            val intent = buildVlcPlayIntent(
                context,
                contentUri,
                mimeType,
                startPositionMs = startPositionMs,
                forActivityResult = forActivityResult
            )
            grantVlcUriPermission(context, contentUri)
            Log.i(
                TAG,
                "Prepared VLC component=${intent.component} package=${intent.`package`} " +
                    "uri=$contentUri mime=$mimeType startMs=$startPositionMs forResult=$forActivityResult"
            )
            PlayResult.Ready(intent, fileId, file.absolutePath)
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "FileProvider failed for ${file.absolutePath}", e)
            PlayResult.Failed("Share failed: Failed to find configured root for ${file.absolutePath}")
        } catch (e: ActivityNotFoundException) {
            Log.e(TAG, "No activity to handle VLC play intent for ${file.absolutePath}", e)
            PlayResult.Failed("VLC Media Player is not installed or cannot open this file")
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException preparing VLC for ${file.absolutePath}", e)
            PlayResult.Failed("Permission denied launching VLC: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Error preparing VLC for ${file.absolutePath}", e)
            PlayResult.Failed(
                "Error while launching VLC Media Player for ${file.absolutePath}: ${e.message}"
            )
        }
    }

    fun play(
        context: Context,
        filePath: String?,
        fileId: Int = 0,
        startPositionMs: Long = 0L
    ): PlayResult {
        return when (val prepared = preparePlay(
            context,
            filePath,
            fileId,
            startPositionMs = startPositionMs,
            forActivityResult = false
        )) {
            is PlayResult.Ready -> {
                try {
                    context.startActivity(prepared.intent)
                    PlayResult.Started(prepared.fileId, prepared.path)
                } catch (e: ActivityNotFoundException) {
                    Log.e(TAG, "No activity to handle VLC play intent for ${prepared.path}", e)
                    PlayResult.Failed("VLC Media Player is not installed or cannot open this file")
                } catch (e: SecurityException) {
                    Log.e(TAG, "SecurityException launching VLC for ${prepared.path}", e)
                    PlayResult.Failed("Permission denied launching VLC: ${e.message}")
                } catch (e: Exception) {
                    Log.e(TAG, "Error launching VLC for ${prepared.path}", e)
                    PlayResult.Failed(
                        "Error while launching VLC Media Player for ${prepared.path}: ${e.message}"
                    )
                }
            }
            else -> prepared
        }
    }

    fun parseExitPosition(data: Intent?): PlaybackExitPosition? {
        if (data == null) return null
        if (!data.hasExtra(RESULT_EXTRA_POSITION)) return null
        val positionMs = data.getLongExtra(RESULT_EXTRA_POSITION, -1L)
        if (positionMs < 0L) return null
        val durationMs = data.getLongExtra(RESULT_EXTRA_DURATION, 0L).coerceAtLeast(0L)
        val uri = data.getStringExtra(RESULT_EXTRA_URI)
        return PlaybackExitPosition(positionMs = positionMs, durationMs = durationMs, uri = uri)
    }

    /**
     * MIME must start with `video/` so VLC StartActivity routes to VideoPlayerActivity.
     * A non-video type (or null mkv mapping) makes StartActivity call finish() immediately.
     */
    private fun mimeTypeFor(file: File): String {
        val ext = file.extension.lowercase()
        val mapped = when (ext) {
            "mkv", "mk3d" -> "video/x-matroska"
            "webm" -> "video/webm"
            "mp4", "m4v", "mov" -> "video/mp4"
            "avi" -> "video/avi"
            "ts", "m2ts", "mts" -> "video/mp2t"
            else -> if (ext.isNotEmpty()) {
                MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
            } else {
                null
            }
        }
        return mapped?.takeIf { it.startsWith("video/") || it.startsWith("audio/") } ?: "video/*"
    }

    /**
     * TDLib may still be renaming/flushing the file when `isDownloadingCompleted` flips.
     * VLC StartActivity probes the URI on the main thread; a closed/missing fd makes it
     * finish() immediately — the "starts then stops" race that debugger stepping hides.
     */
    private fun waitUntilReadable(file: File): Boolean {
        val deadline = SystemClock.elapsedRealtime() + FILE_READY_TIMEOUT_MS
        var lastError: IOException? = null
        while (true) {
            try {
                FileInputStream(file).use { stream ->
                    val ignored = ByteArray(1)
                    stream.read(ignored)
                }
                return true
            } catch (e: IOException) {
                lastError = e
                if (SystemClock.elapsedRealtime() >= deadline) break
                try {
                    Thread.sleep(FILE_READY_RETRY_MS)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    break
                }
            }
        }
        Log.w(TAG, "waitUntilReadable timed out for ${file.absolutePath}", lastError)
        return false
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
     *
     * @param startPositionMs VLC start offset in ms; 0 starts from beginning.
     * @param forActivityResult omit NEW_TASK so the caller receives VLC exit extras.
     */
    private fun buildVlcPlayIntent(
        context: Context,
        contentUri: Uri,
        mimeType: String,
        startPositionMs: Long = 0L,
        forActivityResult: Boolean = false
    ): Intent {
        val resumeFrom = startPositionMs > 0L
        fun baseIntent(): Intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(contentUri, mimeType)
            clipData = ClipData.newRawUri("media", contentUri)
            var flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            if (!forActivityResult) {
                // VideoPlayerActivity is singleTask + finishOnTaskLaunch. On TV it
                // finish()es in onStop, so it must not share our task: for-result
                // without NEW_TASK brings us back to front and kills playback.
                flags = flags or Intent.FLAG_ACTIVITY_NEW_TASK
            }
            addFlags(flags)
            addCategory(Intent.CATEGORY_DEFAULT)

            putExtra("title", "GrammPlayer")
            putExtra(EXTRA_ITEM_LOCATION, contentUri)
            putExtra(EXTRA_FROM_START, !resumeFrom)
            if (resumeFrom) {
                putExtra(EXTRA_START_TIME, startPositionMs)
            }
            putExtra(EXTRA_FROM_EXTERNAL, true)

            putExtra("fullscreen", true)
            putExtra("start_paused", false)
            // VLC 3.x only honors PLAY_DISABLE_HARDWARE ("disable_hardware"), not "hw"/"vout".
            // Realtek TV SoCs abort instantly on HW decode of complete mkv.
            if (needsSoftwareDecode(context)) {
                putExtra(EXTRA_DISABLE_HARDWARE, true)
            }
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

    fun needsSoftwareDecode(context: Context): Boolean {
        val pm = context.packageManager
        return isFireTv() ||
            pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
            pm.hasSystemFeature(PackageManager.FEATURE_TELEVISION)
    }

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
