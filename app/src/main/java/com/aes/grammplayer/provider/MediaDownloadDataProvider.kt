package com.aes.grammplayer.provider

import android.annotation.SuppressLint
import android.util.Log
import com.aes.grammplayer.GPlayerApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.aes.grammplayer.db.model.MediaMessage
import com.aes.grammplayer.util.tdlib.TelegramClientManager
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

object MediaDownloadDataProvider {

    // OkHttp with logging interceptor (helps debug 403 errors)
    private val httpClient = OkHttpClient.Builder()
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        })
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    // Multiple reliable public test video URLs (fallback on 403/any failure)
    private val TEST_VIDEO_URLS = listOf(
        "https://filesamples.com/samples/video/mp4/sample_1280x720.mp4"
    )

    @SuppressLint("LongLogTag")
    suspend fun downloadMedia(
        mode: Boolean = true,
        mediaMessage: MediaMessage,
        onProgress: (progress: Int) -> Unit = {}
    ): MediaMessage? {
        val context = GPlayerApplication.AppContext

        return withContext(Dispatchers.IO) {
            try {
                val updatedMessage = if (mode) {
                    downloadVideoFromTestServer(mediaMessage, context, onProgress)
                } else {
                    TelegramClientManager.startFileDownload(fileId = mediaMessage.fileId)
                }
                updatedMessage

            } catch (e: Exception) {
                Log.e("MediaDownloadDataProvider", "Error downloading video: ${e.message}", e)
                null
            } as MediaMessage?
        }
    }

    /**
     * Downloads video from public test servers with fallback + progress
     */
    @SuppressLint("LongLogTag")
    private suspend fun downloadVideoFromTestServer(
        original: MediaMessage,
        context: android.content.Context,
        onProgress: (Int) -> Unit
    ): MediaMessage {
        val cacheDir = File(context.cacheDir, "media_videos")
        if (!cacheDir.exists()) cacheDir.mkdirs()

        val fileName = "test_video_${System.currentTimeMillis()}.mp4"
        val destinationFile = File(cacheDir, fileName)

        var lastException: Exception? = null

        for (url in TEST_VIDEO_URLS) {
            try {
                Log.d("MediaDownloadDataProvider", "Trying test video URL: $url")

                val request = Request.Builder().url(url).build()

                httpClient.newCall(request).execute().use { response: Response ->
                    if (!response.isSuccessful) {
                        Log.w("MediaDownloadDataProvider", "HTTP ${response.code} from $url")
                        if (response.code == 403) {
                            lastException = IOException("HTTP 403")
                            return@use // Try next URL
                        }
                        throw IOException("HTTP ${response.code}")
                    }

                    val body: ResponseBody = response.body ?: throw IOException("Empty body")
                    val contentLength = body.contentLength().coerceAtLeast(1L)

                    FileOutputStream(destinationFile).use { fos ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        var totalBytesRead = 0L

                        body.byteStream().use { inputStream ->
                            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                                fos.write(buffer, 0, bytesRead)
                                totalBytesRead += bytesRead

                                val progress = ((totalBytesRead * 100) / contentLength).toInt()
                                onProgress(progress.coerceAtMost(100))
                            }
                        }
                    }
                }

                // Success
                onProgress(100)
                Log.d("MediaDownloadDataProvider", "✅ Test video downloaded: ${destinationFile.absolutePath}")

                return original.copy(
                    localPath = destinationFile.absolutePath,
                    isDownloaded = true,
                    mimeType = "video/mp4"
                )

            } catch (e: Exception) {
                lastException = e
                Log.w("MediaDownloadDataProvider", "Failed with $url: ${e.message}")
            }
        }

        // All URLs failed
        throw lastException ?: IOException("All test servers failed")
    }

    private suspend fun downloadLocally(mediaMessage: MediaMessage): MediaMessage {
        return mediaMessage.copy(isDownloaded = true)
    }
}