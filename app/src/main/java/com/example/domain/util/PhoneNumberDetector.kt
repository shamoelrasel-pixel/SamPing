package com.example.domain.util

import android.util.Patterns
import java.util.regex.Pattern

object PhoneNumberDetector {

    // Regex matching standard phone numbers including international and local formats
    private val PHONE_PATTERN: Pattern = Pattern.compile(
        "(\\+?\\d{1,4}[\\s-]?)?(\\(?\\d{2,5}\\)?[\\s-]?)?\\d{3,4}[\\s-]?\\d{3,4}"
    )

    data class PhoneMatch(
        val number: String,
        val startIndex: Int,
        val endIndex: Int
    )

    fun findPhoneNumbers(text: String): List<PhoneMatch> {
        val matches = mutableListOf<PhoneMatch>()
        if (text.isBlank()) return matches

        val matcher = PHONE_PATTERN.matcher(text)
        while (matcher.find()) {
            val candidate = matcher.group().trim()
            val digitsCount = candidate.count { it.isDigit() }
            // Only consider candidates with 7 to 15 digits as valid phone numbers
            if (digitsCount in 7..15) {
                matches.add(
                    PhoneMatch(
                        number = candidate,
                        startIndex = matcher.start(),
                        endIndex = matcher.end()
                    )
                )
            }
        }
        return matches
    }

    fun extractPhoneNumbers(text: String): List<String> {
        return findPhoneNumbers(text).map { it.number }.distinct()
    }
}
