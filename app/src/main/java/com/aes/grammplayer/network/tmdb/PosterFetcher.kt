package com.aes.grammplayer.network.tmdb

import com.aes.grammplayer.BuildConfig
import com.aes.grammplayer.util.tdlib.ReleaseInfo

object PosterFetcher {
    private const val IMAGE_BASE = "https://image.tmdb.org/t/p/w500"
    private const val API_KEY = BuildConfig.TMDB_API_KEY // store in local.properties / BuildConfig, not hardcoded

    // Simple in-memory cache: key = "title_year", value = poster URL
    private val cache = mutableMapOf<String, TmdbMovieResult?>()

    private val api: TmdbApi by lazy {
        // build via your existing Retrofit instance, base url = https://api.themoviedb.org/3/
        TmdbClient.retrofit.create(TmdbApi::class.java)
    }

    suspend fun fetchMovieData(info: ReleaseInfo): TmdbMovieResult? {
        val cacheKey = "${info.title.lowercase()}_${info.year ?: 0}"
        if (cache.containsKey(cacheKey)) return cache[cacheKey] as String? as TmdbMovieResult?
        return try {
            val response = api.searchMovie(API_KEY, info.title, info.year)
            val movie = response.results.firstOrNull()
            cache[cacheKey] = movie
            movie
        } catch (e: Exception) {
            null
        }
    }

    suspend fun fetchMovieDetails(movieId: Int): TmdbMovieDetails? {
        return try {
            api.getMovieDetails(movieId, BuildConfig.TMDB_API_KEY)
        } catch (e: Exception) {
            null
        }
    }
}