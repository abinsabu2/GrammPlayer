package com.aes.grammplayer.network.tmdb

import android.net.Uri
import android.util.Log
import com.aes.grammplayer.BuildConfig
import com.aes.grammplayer.util.tdlib.ReleaseInfo
import com.aes.grammplayer.util.tdlib.ReleaseTitleParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import javax.net.ssl.SSLHandshakeException

object PosterFetcher {
    private const val TAG = "PosterFetcher"
    private const val POSTER_BASE = "https://image.tmdb.org/t/p/w500"
    private const val BACKDROP_BASE = "https://image.tmdb.org/t/p/w780"
    private val API_KEY = BuildConfig.TMDB_API_KEY.trim()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val apiSemaphore = Semaphore(permits = 4)

    private val searchCache = mutableMapOf<String, TmdbMovieResult>()
    private val tvSearchCache = mutableMapOf<String, TmdbTvResult>()
    private val detailsCache = mutableMapOf<Int, TmdbMovieDetails>()

    private val movieSearchMutex = Mutex()
    private val tvSearchMutex = Mutex()
    private val detailsMutex = Mutex()
    private val inFlightMovieSearches = mutableMapOf<String, Deferred<TmdbMovieResult?>>()
    private val inFlightTvSearches = mutableMapOf<String, Deferred<TmdbTvResult?>>()
    private val inFlightDetails = mutableMapOf<Int, Deferred<TmdbMovieDetails?>>()

    private val api: TmdbApi by lazy {
        TmdbClient.retrofit.create(TmdbApi::class.java)
    }

    fun isTrustedImageUrl(value: String): Boolean {
        if (!value.startsWith("http://", ignoreCase = true) &&
            !value.startsWith("https://", ignoreCase = true)
        ) {
            return false
        }

        val host = Uri.parse(value).host?.lowercase() ?: return false
        if (host == "example.com" || host.endsWith(".example.com")) return false
        return host.contains("tmdb.org") || host.contains("themoviedb.org")
    }

    fun posterUrl(posterPath: String?): String? =
        posterPath?.takeIf { it.isNotBlank() }?.let { "$POSTER_BASE$it" }

    fun backdropUrl(backdropPath: String?): String? =
        backdropPath?.takeIf { it.isNotBlank() }?.let { "$BACKDROP_BASE$it" }

    fun resolveBackdropPath(details: TmdbMovieDetails): String? =
        details.backdrop_path?.takeIf { it.isNotBlank() }
            ?: details.images?.backdrops?.firstOrNull()?.file_path

    fun bestImageUrl(details: TmdbMovieDetails): String? =
        posterUrl(details.poster_path) ?: backdropUrl(resolveBackdropPath(details))

    fun releaseYear(releaseDate: String?): Int? =
        releaseDate?.take(4)?.toIntOrNull()

    fun displayTitle(details: TmdbMovieDetails): String {
        val year = releaseYear(details.release_date)
        return year?.let { "${details.title} ($it)" } ?: details.title
    }

    fun trailerLabel(details: TmdbMovieDetails): String? =
        details.videos?.results
            ?.firstOrNull { it.site.equals("YouTube", ignoreCase = true) && it.type.equals("Trailer", ignoreCase = true) }
            ?.let { "Trailer available" }

    fun trailerKey(details: TmdbMovieDetails): String? =
        details.videos?.results
            ?.firstOrNull { it.site.equals("YouTube", ignoreCase = true) && it.type.equals("Trailer", ignoreCase = true) }
            ?.key

    fun trailerUrl(key: String): String = "https://www.youtube.com/watch?v=$key"

    suspend fun fetchPosterUrl(info: ReleaseInfo): String? =
        posterUrl(fetchMovieData(info)?.poster_path)

    suspend fun fetchPosterUrl(title: String, year: Int? = null): String? =
        fetchPosterUrl(ReleaseInfo(null, title, year, null, null, null, null, null, null, null))

    suspend fun fetchGridThumbnailUrl(info: ReleaseInfo, rawTitle: String = ""): String? {
        lookupThumbnail(info)?.let { return it }

        if (rawTitle.isNotBlank() && rawTitle != info.title) {
            lookupThumbnail(ReleaseTitleParser.parse(rawTitle))?.let { return it }
        }

        return null
    }

    private suspend fun lookupThumbnail(info: ReleaseInfo): String? {
        if (info.title.isBlank()) return null

        fetchMovieData(info)?.let { movie ->
            posterUrl(movie.poster_path)?.let { return it }
            fetchMovieDetails(movie.id)?.let { details ->
                bestImageUrl(details)?.let { return it }
            }
        }

        fetchTvData(info)?.let { show ->
            posterUrl(show.poster_path)?.let { return it }
        }

        return null
    }

    suspend fun fetchMovieData(info: ReleaseInfo): TmdbMovieResult? {
        val cacheKey = movieCacheKey(info.title, info.year)
        searchCache[cacheKey]?.let { return it }
        info.year?.let { searchCache[movieCacheKey(info.title, null)] }?.let { return it }

        val deferred = movieSearchMutex.withLock {
            inFlightMovieSearches[cacheKey] ?: scope.async {
                try {
                    resolveMovieSearch(info.title, info.year)
                } finally {
                    movieSearchMutex.withLock { inFlightMovieSearches.remove(cacheKey) }
                }
            }.also { inFlightMovieSearches[cacheKey] = it }
        }
        return deferred.await()
    }

    private suspend fun resolveMovieSearch(title: String, year: Int?): TmdbMovieResult? {
        val result = searchMovie(title, year) ?: year?.let { searchMovie(title, year = null) }
        if (result != null) {
            searchCache[movieCacheKey(title, year)] = result
            if (year != null) {
                searchCache[movieCacheKey(title, null)] = result
            }
        }
        return result
    }

    private suspend fun fetchTvData(info: ReleaseInfo): TmdbTvResult? {
        val cacheKey = movieCacheKey(info.title, info.year)
        tvSearchCache[cacheKey]?.let { return it }

        val deferred = tvSearchMutex.withLock {
            inFlightTvSearches[cacheKey] ?: scope.async {
                try {
                    val result = searchTv(info.title, info.year)
                        ?: info.year?.let { searchTv(info.title, year = null) }
                    if (result != null) {
                        tvSearchCache[cacheKey] = result
                    }
                    result
                } finally {
                    tvSearchMutex.withLock { inFlightTvSearches.remove(cacheKey) }
                }
            }.also { inFlightTvSearches[cacheKey] = it }
        }
        return deferred.await()
    }

    private suspend fun searchMovie(title: String, year: Int?): TmdbMovieResult? {
        if (!ensureApiKey()) return null
        return apiSemaphore.withPermit {
            try {
                pickMovie(api.searchMovie(API_KEY, title, year))
            } catch (e: Exception) {
                logLookupFailure("Movie search (retrofit)", title, year, e)
                pickMovie(TmdbHttpFallback.searchMovie(API_KEY, title, year)).also { result ->
                    if (result == null) Log.w(TAG, "Movie search fallback also failed for '$title'")
                }
            }
        }
    }

    private suspend fun searchTv(title: String, year: Int?): TmdbTvResult? {
        if (!ensureApiKey()) return null
        return apiSemaphore.withPermit {
            try {
                pickTv(api.searchTv(API_KEY, title, year))
            } catch (e: Exception) {
                logLookupFailure("TV search (retrofit)", title, year, e)
                pickTv(TmdbHttpFallback.searchTv(API_KEY, title, year)).also { result ->
                    if (result == null) Log.w(TAG, "TV search fallback also failed for '$title'")
                }
            }
        }
    }

    suspend fun fetchMovieDetails(movieId: Int): TmdbMovieDetails? {
        detailsCache[movieId]?.let { return it }

        val deferred = detailsMutex.withLock {
            inFlightDetails[movieId] ?: scope.async {
                try {
                    loadMovieDetails(movieId)
                } finally {
                    detailsMutex.withLock { inFlightDetails.remove(movieId) }
                }
            }.also { inFlightDetails[movieId] = it }
        }
        return deferred.await()
    }

    private suspend fun loadMovieDetails(movieId: Int): TmdbMovieDetails? {
        if (!ensureApiKey()) return null
        return apiSemaphore.withPermit {
            try {
                api.getMovieDetails(movieId, API_KEY).also { detailsCache[movieId] = it }
            } catch (e: Exception) {
                logLookupFailure("Movie details (retrofit)", movieId.toString(), null, e)
                TmdbHttpFallback.getMovieDetails(API_KEY, movieId)?.also { detailsCache[movieId] = it }
                    ?: run {
                        Log.w(TAG, "Movie details fallback also failed for id=$movieId")
                        null
                    }
            }
        }
    }

    suspend fun fetchDetailsForRelease(info: ReleaseInfo): TmdbMovieDetails? {
        val movie = fetchMovieData(info) ?: return null
        return fetchMovieDetails(movie.id)
    }

    suspend fun fetchBackdropUrl(info: ReleaseInfo): String? =
        fetchDetailsForRelease(info)?.let { details ->
            backdropUrl(resolveBackdropPath(details))
        }

    private fun pickMovie(response: TmdbSearchResponse?): TmdbMovieResult? =
        response?.results?.firstOrNull { !it.poster_path.isNullOrBlank() }
            ?: response?.results?.firstOrNull()

    private fun pickTv(response: TmdbTvSearchResponse?): TmdbTvResult? =
        response?.results?.firstOrNull { !it.poster_path.isNullOrBlank() }
            ?: response?.results?.firstOrNull()

    private fun movieCacheKey(title: String, year: Int?): String =
        "${title.lowercase()}_${year ?: 0}"

    private fun ensureApiKey(): Boolean {
        if (API_KEY.isNotBlank()) return true
        Log.e(TAG, "TMDB_API_KEY is missing — add tmbd_key to local.properties")
        return false
    }

    private fun logLookupFailure(kind: String, subject: String, year: Int?, error: Exception) {
        val yearSuffix = year?.let { " (year=$it)" }.orEmpty()
        val hint = when (error) {
            is SSLHandshakeException ->
                " — TLS handshake failed; retrying via platform HTTP"
            else -> ""
        }
        Log.w(TAG, "$kind failed for '$subject'$yearSuffix$hint", error)
    }
}