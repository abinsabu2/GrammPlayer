package com.aes.grammplayer.network.tmdb

import com.aes.grammplayer.BuildConfig
import com.aes.grammplayer.util.tdlib.ReleaseInfo

object PosterFetcher {
    private const val POSTER_BASE = "https://image.tmdb.org/t/p/w500"
    private const val BACKDROP_BASE = "https://image.tmdb.org/t/p/w780"
    private const val API_KEY = BuildConfig.TMDB_API_KEY

    private val searchCache = mutableMapOf<String, TmdbMovieResult?>()
    private val detailsCache = mutableMapOf<Int, TmdbMovieDetails?>()

    private val api: TmdbApi by lazy {
        TmdbClient.retrofit.create(TmdbApi::class.java)
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

    fun crewNames(details: TmdbMovieDetails, job: String, limit: Int = 2): List<String> =
        details.credits?.crew
            ?.filter { it.job.equals(job, ignoreCase = true) }
            ?.map { it.name }
            ?.distinct()
            ?.take(limit)
            ?: emptyList()

    fun trailerLabel(details: TmdbMovieDetails): String? =
        details.videos?.results
            ?.firstOrNull { it.site.equals("YouTube", ignoreCase = true) && it.type.equals("Trailer", ignoreCase = true) }
            ?.let { "Trailer available" }

    suspend fun fetchPosterUrl(info: ReleaseInfo): String? =
        posterUrl(fetchMovieData(info)?.poster_path)

    suspend fun fetchPosterUrl(title: String, year: Int? = null): String? =
        fetchPosterUrl(ReleaseInfo(null, title, year, null, null, null, null, null, null, null))

    suspend fun fetchMovieData(info: ReleaseInfo): TmdbMovieResult? {
        val cacheKey = "${info.title.lowercase()}_${info.year ?: 0}"
        if (searchCache.containsKey(cacheKey)) return searchCache[cacheKey]
        return try {
            val response = api.searchMovie(API_KEY, info.title, info.year)
            val movie = response.results.firstOrNull()
            searchCache[cacheKey] = movie
            movie
        } catch (e: Exception) {
            searchCache[cacheKey] = null
            null
        }
    }

    suspend fun fetchMovieDetails(movieId: Int): TmdbMovieDetails? {
        if (detailsCache.containsKey(movieId)) return detailsCache[movieId]
        return try {
            val details = api.getMovieDetails(movieId, API_KEY)
            detailsCache[movieId] = details
            details
        } catch (e: Exception) {
            detailsCache[movieId] = null
            null
        }
    }

    /** Search by parsed release title, then load full TMDB details (genres, cast, runtime, etc.). */
    suspend fun fetchDetailsForRelease(info: ReleaseInfo): TmdbMovieDetails? {
        val movie = fetchMovieData(info) ?: return null
        return fetchMovieDetails(movie.id)
    }
}