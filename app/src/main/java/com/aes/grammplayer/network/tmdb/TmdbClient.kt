package com.aes.grammplayer.network.tmdb

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object TmdbClient {

    private const val BASE_URL = "https://api.themoviedb.org/3/"

    val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    val api: TmdbApi by lazy {
        retrofit.create(TmdbApi::class.java)
    }
}