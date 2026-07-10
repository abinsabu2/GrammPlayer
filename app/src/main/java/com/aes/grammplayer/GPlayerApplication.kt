package com.aes.grammplayer

import android.app.Application
import android.content.Context
import com.aes.grammplayer.db.AppDatabase
import com.aes.grammplayer.db.DatabaseSeeder
import com.aes.grammplayer.network.tmdb.TlsHelper
import com.aes.grammplayer.util.analytics.AnalyticsHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class GPlayerApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppContext = applicationContext
        TlsHelper.upgradeSecurityProvider(this)
        AnalyticsHelper.initialize(this)
        CoroutineScope(Dispatchers.IO).launch {
            val db = AppDatabase.getDatabase(applicationContext)
            DatabaseSeeder.seed(db)
        }
    }

    companion object {
        lateinit var AppContext: Context
    }
}
