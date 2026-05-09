package com.aes.grammplayer.helper

import com.aes.grammplayer.db.model.model.UserType
import com.aes.grammplayer.session.UserSession
import com.aes.grammplayer.util.tdlib.TelegramClientManager

object LoginHelper {
    fun appUserCheck(phoneNumber: String): Unit {
        UserSession.initialize(phoneNumber)
    }

    fun sendPhoneNumber(phoneNumber: String) {
        appUserCheck(phoneNumber)
        when (UserSession.userType) {
            UserType.TEST -> {

            }
            UserType.REAL -> {
                TelegramClientManager.sendPhoneNumber(phoneNumber)
            }
        }

    }


}