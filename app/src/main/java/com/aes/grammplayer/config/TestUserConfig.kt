package com.aes.grammplayer.config

// config/TestUserConfig.kt
object TestUserConfig {
    val TEST_PHONE_NUMBERS = setOf(
        "+123456789",
        "+987654321",
        "+555555555",
        "+111111111",
        "+100"
    )

    fun isTestUser(phoneNumber: String): Boolean {
        return phoneNumber.trim() in TEST_PHONE_NUMBERS
    }
}