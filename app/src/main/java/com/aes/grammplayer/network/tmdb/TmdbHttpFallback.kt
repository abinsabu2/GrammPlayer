package com.aes.grammplayer.network.tmdb

import android.util.Log
import com.google.gson.Gson
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Platform [HttpURLConnection] fallback for TMDB when OkHttp/Retrofit TLS fails
 * on some devices/emulators (OCSP validation errors).
 */
internal object TmdbHttpFallback {

    private const val TAG = "TmdbHttpFallback"
    private const val BASE_URL = "https://api.themoviedb.org/3/"
    private val gson = Gson()

    fun searchMovie(apiKey: String, title: String, year: Int?): TmdbSearchResponse? =
        get(
            path = buildString {
                append("search/movie?api_key=").append(apiKey)
                append("&query=").append(encode(title))
                year?.let { append("&year=").append(it) }
            },
            type = TmdbSearchResponse::class.java
        )

    fun searchTv(apiKey: String, title: String, year: Int?): TmdbTvSearchResponse? =
        get(
            path = buildString {
                append("search/tv?api_key=").append(apiKey)
                append("&query=").append(encode(title))
                year?.let { append("&first_air_date_year=").append(it) }
            },
            type = TmdbTvSearchResponse::class.java
        )

    fun getMovieDetails(apiKey: String, movieId: Int): TmdbMovieDetails? =
        get(
            path = "movie/$movieId?api_key=$apiKey&append_to_response=credits,images,videos",
            type = TmdbMovieDetails::class.java
        )

    private fun <T> get(path: String, type: Class<T>): T? {
        val connection = (URL(BASE_URL + path).openConnection() as HttpURLConnection)
        return try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            connection.requestMethod = "GET"
            val code = connection.responseCode
            if (code !in 200..299) {
                Log.w(TAG, "TMDB HTTP $code for $path")
                return null
            }
            connection.inputStream.bufferedReader().use { gson.fromJson(it, type) }
        } catch (e: Exception) {
            Log.w(TAG, "TMDB fallback request failed for $path", e)
            null
        } finally {
            connection.disconnect()
        }
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, Charsets.UTF_8.name())
}