package com.aes.grammplayer.session

import com.aes.grammplayer.config.TestUserConfig
import com.aes.grammplayer.db.model.model.UserType

// session/UserSession.kt
object UserSession {
    var phoneNumber: String = ""
    var userType: UserType = UserType.REAL

    fun initialize(phone: String) {
        phoneNumber = phone
        userType = if (TestUserConfig.isTestUser(phone)) UserType.TEST else UserType.REAL
    }

    fun isTestUser() = userType == UserType.TEST
}