package com.example.service

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import java.net.URLEncoder

class WhatsAppDispatcher(private val context: Context) {

    fun isWhatsAppInstalled(): Boolean {
        val packageManager = context.packageManager
        return isPackageInstalled("com.whatsapp", packageManager) ||
                isPackageInstalled("com.whatsapp.w4b", packageManager)
    }

    private fun isPackageInstalled(packageName: String, packageManager: PackageManager): Boolean {
        return try {
            packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    fun cleanPhoneNumber(rawPhone: String): String {
        // Strip everything except digits and optional leading plus
        val digitsOnly = rawPhone.replace(Regex("[^0-9]"), "")
        return digitsOnly
    }

    fun buildDeepLinkUri(rawPhone: String, messageText: String): Uri {
        val phone = cleanPhoneNumber(rawPhone)
        val encodedMessage = try {
            URLEncoder.encode(messageText, "UTF-8")
        } catch (e: Exception) {
            messageText
        }
        return Uri.parse("https://api.whatsapp.com/send?phone=$phone&text=$encodedMessage")
    }

    fun createWhatsAppIntent(rawPhone: String, messageText: String): Intent {
        val uri = buildDeepLinkUri(rawPhone, messageText)
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        // Try targeting WhatsApp specifically if installed
        if (isPackageInstalled("com.whatsapp", context.packageManager)) {
            intent.setPackage("com.whatsapp")
        } else if (isPackageInstalled("com.whatsapp.w4b", context.packageManager)) {
            intent.setPackage("com.whatsapp.w4b")
        }

        return intent
    }

    fun openWhatsAppConversation(rawPhone: String, messageText: String): Boolean {
        return try {
            val intent = createWhatsAppIntent(rawPhone, messageText)
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            // Fallback: Open in browser
            try {
                val fallbackIntent = Intent(Intent.ACTION_VIEW, buildDeepLinkUri(rawPhone, messageText)).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(fallbackIntent)
                true
            } catch (fallbackEx: Exception) {
                false
            }
        }
    }
}
