package com.aes.grammplayer

import android.app.Application
import android.content.Context
import com.aes.grammplayer.db.AppDatabase
import com.aes.grammplayer.db.DatabaseSeeder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class GPlayerApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppContext = applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            val db = AppDatabase.getDatabase(applicationContext)
            DatabaseSeeder.seed(db)
        }
    }

    companion object {
        lateinit var AppContext: Context
    }
}
