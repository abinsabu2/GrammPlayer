package com.aes.grammplayer.network.tmdb

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface TmdbApi {

    @GET("search/movie")
    suspend fun searchMovie(
        @Query("api_key") apiKey: String,
        @Query("query") title: String,
        @Query("year") year: Int? = null
    ): TmdbSearchResponse

    @GET("search/tv")
    suspend fun searchTv(
        @Query("api_key") apiKey: String,
        @Query("query") title: String,
        @Query("first_air_date_year") year: Int? = null
    ): TmdbTvSearchResponse

    @GET("movie/{movie_id}")
    suspend fun getMovieDetails(
        @Path("movie_id") movieId: Int,
        @Query("api_key") apiKey: String,
        @Query("append_to_response") appendToResponse: String = "credits,images,videos"
    ): TmdbMovieDetails
}

data class TmdbSearchResponse(
    val results: List<TmdbMovieResult>
)

data class TmdbTvSearchResponse(
    val results: List<TmdbTvResult>
)

data class TmdbMovieResult(
    val id: Int,
    val title: String,
    val poster_path: String?,
    val release_date: String?
)

data class TmdbTvResult(
    val id: Int,
    val name: String,
    val poster_path: String?,
    val first_air_date: String?
)

data class TmdbMovieDetails(
    val id: Int,
    val title: String,
    val original_title: String? = null,
    val tagline: String? = null,
    val overview: String?,
    val poster_path: String?,
    val backdrop_path: String?,
    val release_date: String?,
    val runtime: Int?,
    val vote_average: Double?,
    val status: String? = null,
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