package com.aes.grammplayer

import android.app.Application
import android.content.Context
import android.util.Log
import com.aes.grammplayer.db.AppDatabase
import com.aes.grammplayer.db.DatabaseSeeder
import com.aes.grammplayer.network.tmdb.TlsHelper
import com.aes.grammplayer.network.tmdb.TmdbClient
import com.bumptech.glide.Glide
import com.bumptech.glide.integration.okhttp3.OkHttpUrlLoader
import com.bumptech.glide.load.model.GlideUrl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.InputStream

class GPlayerApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppContext = applicationContext
        TlsHelper.upgradeSecurityProvider(this)
        // ponytail: Glide default HttpUrlFetcher fails cert on emulator GMS; reuse TmdbClient OkHttp with MODERN_TLS
        try {
            Glide.get(this).registry.replace(
                GlideUrl::class.java,
                InputStream::class.java,
                OkHttpUrlLoader.Factory(TmdbClient.okHttpClient)
            )
            Log.i("GlideHelper", "Glide wired to OkHttp for TMDB images")
        } catch (e: Exception) {
            Log.w("GlideHelper", "Failed to wire Glide OkHttp", e)
        }
        CoroutineScope(Dispatchers.IO).launch {
            val db = AppDatabase.getDatabase(applicationContext)
            DatabaseSeeder.seed(db)
        }
    }

    companion object {
        lateinit var AppContext: Context
    }
}
