package com.aes.grammplayer.config

// config/TestUserConfig.kt
object TestUserConfig {
    val TEST_PHONE_NUMBERS = setOf(
        "+10000000001",
        "+10000000002",
        "+10000000003"
    )

    fun isTestUser(phoneNumber: String): Boolean {
        return phoneNumber.trim() in TEST_PHONE_NUMBERS
    }
}