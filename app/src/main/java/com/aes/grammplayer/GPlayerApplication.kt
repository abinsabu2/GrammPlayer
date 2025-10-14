package com.aes.grammplayer

import android.app.Application
import android.content.Context

class GPlayerApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppContext = applicationContext
    }

    companion object {
        lateinit var AppContext: Context
    }
}
