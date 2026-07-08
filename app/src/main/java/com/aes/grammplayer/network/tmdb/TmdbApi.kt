package com.aes.grammplayer.network.tmdb

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query   // ✅ Only Retrofit's @Query

interface TmdbApi {

    @GET("search/movie")
    suspend fun searchMovie(
        @Query("api_key") apiKey: String,
        @Query("query") title: String,
        @Query("year") year: Int? = null
    ): TmdbSearchResponse

    // NEW: Get detailed movie info by TMDB ID
    @GET("movie/{movie_id}")
    suspend fun getMovieDetails(
        @Path("movie_id") movieId: Int,
        @Query("api_key") apiKey: String,
        @Query("append_to_response") appendToResponse: String = "credits,images,videos"  // extra data
    ): TmdbMovieDetails
}

data class TmdbSearchResponse(
    val results: List<TmdbMovieResult>
)

data class TmdbMovieResult(
    val id: Int,
    val title: String,
    val poster_path: String?,
    val release_date: String?
)

// Add these to the same file or separate

data class TmdbMovieDetails(
    val id: Int,
    val title: String,
    val overview: String?,
    val poster_path: String?,
    val backdrop_path: String?,
    val release_date: String?,
    val runtime: Int?,           // in minutes
    val vote_average: Double?,
    val genres: List<Genre>?,
    val credits: Credits? = null,
    val images: Images? = null,
    val videos: Videos? = null
)

data class Genre(val id: Int, val name: String)
data class Credits(val cast: List<CastMember>?, val crew: List<CrewMember>?)
data class CastMember(val name: String, val character: String?)
data class CrewMember(val name: String, val job: String?)

data class Images(val backdrops: List<Image>?)
data class Videos(val results: List<Video>?)
data class Image(val file_path: String)
data class Video(val key: String, val site: String, val type: String)