package com.aes.grammplayer.config

// config/TestUserConfig.kt
object TestUserConfig {

    /** Amazon Appstore review login — country code +1, phone 00, code 12345. */
    const val AMAZON_REVIEW_COUNTRY_CODE = "1"
    const val AMAZON_REVIEW_PHONE_SUFFIX = "00"
    const val AMAZON_REVIEW_FULL_PHONE = "+$AMAZON_REVIEW_COUNTRY_CODE$AMAZON_REVIEW_PHONE_SUFFIX"
    const val AMAZON_REVIEW_CODE = "12345"

    val TEST_PHONE_NUMBERS = setOf(
        "+123456789",
        "+987654321",
        "+555555555",
        "+111111111",
        AMAZON_REVIEW_FULL_PHONE
    )

    fun isTestUser(phoneNumber: String): Boolean {
        return phoneNumber.trim() in TEST_PHONE_NUMBERS
    }
}