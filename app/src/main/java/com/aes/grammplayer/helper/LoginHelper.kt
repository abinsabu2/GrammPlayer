package com.aes.grammplayer.helper

import com.aes.grammplayer.session.UserSession


object LoginHelper {
    fun appUserCheck(phoneNumber: String): Unit {
        UserSession.initialize(phoneNumber)
        UserSession.isTestUser()
    }

    fun dataHandleSelector(phoneNumber: String){

        this.appUserCheck(phoneNumber)



    }






}