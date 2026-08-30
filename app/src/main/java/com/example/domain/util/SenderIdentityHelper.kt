package com.example.domain.util

object SenderIdentityHelper {

    private val KNOWN_ORGS = listOf(
        "BRAC Bank" to listOf("brac bank", "bracbank", "brac", "brac_bank", "astha", "bracastha", "brac astha", "16221"),
        "Grameenphone" to listOf("grameenphone", "gp", "gp-info", "gpinfo", "gpstar", "flexiplan", "flexiload", "gp flexiload", "gprecharge", "gp recharge", "121", "4444"),
        "BTRC" to listOf("btrc"),
        "DBBL" to listOf("dbbl", "dutch-bangla bank", "dutch bangla bank", "dutch bangla", "nexuspay", "nexus pay", "rocket", "16216"),
        "SEBL" to listOf("sebl", "southeast bank", "southeast", "telecash"),
        "bKash" to listOf("bkash", "bkash_info", "bkash info", "bkash alert", "16247"),
        "Nagad" to listOf("nagad", "nagad_info", "nagad info", "nagad alert", "16167"),
        "Upay" to listOf("upay", "16268"),
        "City Bank" to listOf("city bank", "citytouch", "citytouch alert", "the city bank", "citybank", "16234"),
        "EBL" to listOf("ebl", "eastern bank", "skybanking", "ebl skybanking", "16230"),
        "Islami Bank" to listOf("islami bank", "ibbl", "cellfin", "m-cash", "16259"),
        "Standard Chartered" to listOf("standard chartered", "scb", "stan chart", "sc mobile", "16233"),
        "HSBC" to listOf("hsbc", "16240"),
        "Mutual Trust Bank" to listOf("mutual trust bank", "mtb", "mtb smart", "16219"),
        "Premier Bank" to listOf("premier bank", "pbl", "16411"),
        "Prime Bank" to listOf("prime bank", "altitude", "16218"),
        "UCB" to listOf("ucb", "united commercial bank", "upay", "16419"),
        "Dhaka Bank" to listOf("dhaka bank", "dhaka bank go", "16513"),
        "NCC Bank" to listOf("ncc bank", "nccb", "16315"),
        "Pubali Bank" to listOf("pubali bank", "pi banking", "16281"),
        "AB Bank" to listOf("ab bank", "ab direct", "16207"),
        "Bank Asia" to listOf("bank asia", "bankasia", "smart app", "16205"),
        "Sonali Bank" to listOf("sonali bank", "sonaliepay", "sonali e-wallet", "16639"),
        "Janata Bank" to listOf("janata bank", "e-janata", "16280"),
        "Agrani Bank" to listOf("agrani bank", "agrani smart banking", "16246"),
        "Rupali Bank" to listOf("rupali bank", "rupali surecash", "16252"),
        "Banglalink" to listOf("banglalink", "bl", "blinfo", "bl-info", "bl recharge", "amar offer", "121"),
        "Robi" to listOf("robi", "robi-info", "robiinfo", "robi recharge", "jhotpot", "123", "8123"),
        "Airtel" to listOf("airtel", "airtel-info", "airtelinfo", "airtel recharge", "786"),
        "Teletalk" to listOf("teletalk", "teletalk-info", "teletalk recharge", "121"),
        "Uber" to listOf("uber"),
        "Pathao" to listOf("pathao"),
        "Foodpanda" to listOf("foodpanda"),
        "Shohoz" to listOf("shohoz"),
        "Chaldal" to listOf("chaldal"),
        "Daraz" to listOf("daraz"),
        "Google" to listOf("google", "g-"),
        "WhatsApp" to listOf("whatsapp"),
        "Microsoft" to listOf("microsoft"),
        "Facebook" to listOf("facebook", "meta"),
        "Amazon" to listOf("amazon"),
        "Apple" to listOf("apple"),
        "Telegram" to listOf("telegram")
    )

    /**
     * Normalizes any phone number, shortcode, or alphanumeric sender into a stable canonical key
     * for grouping all incoming, outgoing, scheduled, and draft messages.
     */
    fun normalizeSenderKey(rawAddress: String?): String {
        if (rawAddress.isNullOrBlank()) return "UNKNOWN"
        val trimmed = rawAddress.trim()

        // 1. If sender contains letters (e.g. "BRAC Bank", "GP", "Google", "Flexiload")
        val hasLetters = trimmed.any { it.isLetter() }
        if (hasLetters) {
            val cleaned = trimmed.replace(Regex("[^a-zA-Z0-9]"), "").uppercase()
            return if (cleaned.isNotBlank()) "ALPHA_$cleaned" else "RAW_${trimmed.hashCode().toUInt().toString(16)}"
        }

        // 2. Clean out standard formatting punctuation: spaces, dashes, parens, dots, slashes
        var digitsOnly = trimmed.replace(Regex("[^0-9+]"), "")
        if (digitsOnly.startsWith("+")) {
            digitsOnly = digitsOnly.substring(1)
        }

        // Normalize Bangladesh prefix: 8801XXXXXXXX (13 digits) -> 01XXXXXXXX (11 digits)
        if (digitsOnly.startsWith("880") && digitsOnly.length == 13) {
            digitsOnly = digitsOnly.substring(2)
        }

        // Standard 10 or 11 digit numbers
        if (digitsOnly.length >= 10) {
            val lastSignificant = digitsOnly.takeLast(10)
            return "PHONE_$lastSignificant"
        }

        if (digitsOnly.length in 7..9) {
            return "PHONE_$digitsOnly"
        }

        if (digitsOnly.isNotBlank()) {
            return "SHORTCODE_$digitsOnly"
        }

        return "RAW_${trimmed.hashCode().toUInt().toString(16)}"
    }

    /**
     * Checks if two addresses belong to the exact same sender / recipient identity.
     */
    fun isSameSender(addr1: String?, addr2: String?): Boolean {
        if (addr1.isNullOrBlank() || addr2.isNullOrBlank()) return false
        val key1 = normalizeSenderKey(addr1)
        val key2 = normalizeSenderKey(addr2)
        if (key1 != "UNKNOWN" && key2 != "UNKNOWN" && key1 == key2) return true

        val clean1 = addr1.trim().replace(Regex("[^a-zA-Z0-9]"), "").uppercase()
        val clean2 = addr2.trim().replace(Regex("[^a-zA-Z0-9]"), "").uppercase()
        if (clean1.isNotBlank() && clean1 == clean2) return true

        return false
    }

    /**
     * Resolves recognizable business/organization name from address or message body.
     */
    fun resolveOrganizationName(address: String, messageSnippet: String? = null): String? {
        val addrLower = address.lowercase().trim()
        for ((orgName, aliases) in KNOWN_ORGS) {
            for (alias in aliases) {
                val isNumericAlias = alias.all { it.isDigit() }
                if (isNumericAlias) {
                    if (addrLower == alias) return orgName
                } else {
                    if (addrLower.contains(alias) || addrLower == alias) return orgName
                }
            }
        }
        if (!messageSnippet.isNullOrBlank()) {
            val snippetLower = messageSnippet.lowercase()
            for ((orgName, aliases) in KNOWN_ORGS) {
                if (aliases.any { alias ->
                    if (alias.all { it.isDigit() }) false
                    else {
                        snippetLower.startsWith(alias) ||
                        snippetLower.contains("[$alias]") ||
                        snippetLower.contains("($alias)") ||
                        snippetLower.contains("$alias:") ||
                        snippetLower.contains("dear customer, your $alias") ||
                        snippetLower.contains("from $alias") ||
                        snippetLower.contains("$alias ")
                    }
                }) {
                    return orgName
                }
            }
        }
        return null
    }

    /**
     * Detects if the message body contains sensitive security codes, OTP, PIN, 2FA tokens.
     */
    fun isOtpOrSecurityMessage(body: String?): Boolean {
        if (body.isNullOrBlank()) return false
        val lower = body.lowercase()
        val securityKeywords = listOf(
            "otp", "one time password", "one-time password", "verification code",
            "security code", "auth code", "authentication code", "secret code",
            "login code", "passcode", "password reset", "pin code", "your pin is",
            "your pin:", "your code is", "your otp is", "is your pin", "is your otp",
            "2fa", "two-factor", "is your code", "code is", "use code",
            "confirmation code", "access code", "token is", "temporary password",
            "pin:", "otp:", "code:", "bkash pin", "nagad pin", "rocket pin", "secret pin"
        )
        if (securityKeywords.any { lower.contains(it) }) {
            return true
        }

        // Regex check for 4-8 digit numeric code preceded or followed by security terms
        val otpRegex = Regex("""\b(otp|code|pin|passcode|token)\s*[:=is\-]?\s*(\d{4,8})\b|\b(\d{4,8})\s*(is your|as your)\s*(otp|pin|code|password)\b""", RegexOption.IGNORE_CASE)
        return otpRegex.containsMatchIn(body)
    }

    /**
     * Robustly extracts a 4 to 8 digit OTP / Verification / Banking PIN code from message text.
     */
    fun extractOtpCode(body: String?): String? {
        if (body.isNullOrBlank()) return null

        // 1. Specific prefixed formats e.g. "G-123456", "OTP: 123456", "code is 123456", "PIN: 1234"
        val prefixPatterns = listOf(
            Regex("""\b(?:otp|one[- ]time password|verification code|security code|auth code|login code|passcode|pin code|secret code|access code|pin|token|code)\s*(?:is|:|=|-|\.)?\s*([0-9]{4,8})\b""", RegexOption.IGNORE_CASE),
            Regex("""\buse\s+(?:code|otp|pin)?\s*([0-9]{4,8})\b""", RegexOption.IGNORE_CASE),
            Regex("""\b([0-9]{4,8})\s+(?:is\s+(?:your\s+)?(?:otp|pin|code|verification|passcode|secret|login))""", RegexOption.IGNORE_CASE),
            Regex("""\b[Gg]-([0-9]{6})\b"""),
            Regex("""\b(?:code|otp|pin)\s*#?\s*:\s*([0-9]{4,8})\b""", RegexOption.IGNORE_CASE)
        )

        for (pattern in prefixPatterns) {
            val match = pattern.find(body)
            if (match != null && match.groupValues.size > 1) {
                val candidate = match.groupValues[1]
                if (candidate.isNotBlank()) {
                    return candidate
                }
            }
        }

        // 2. If message is flagged as OTP / Security message, find standalone 4 to 8 digit numbers (excluding currency amounts or phone numbers)
        if (isOtpOrSecurityMessage(body)) {
            val standaloneDigits = Regex("""\b(?<![+A-Za-z0-9$৳€£¥₹])(\d{4,8})(?![A-Za-z0-9])\b""")
            val matches = standaloneDigits.findAll(body).toList()
            for (match in matches) {
                val candidate = match.groupValues[1]
                // Skip if looks like a year (19xx, 20xx) when 4 digits unless explicitly preceded by code/pin
                if (candidate.length == 4 && (candidate.startsWith("19") || candidate.startsWith("20"))) {
                    continue
                }
                return candidate
            }
        }

        return null
    }
}
