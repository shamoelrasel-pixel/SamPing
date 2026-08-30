package com.example

import com.example.domain.util.SenderIdentityHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SenderIdentityAndPrivacyUnitTest {

    @Test
    fun testBangladeshPhoneNumberNormalization() {
        val raw1 = "01712345678"
        val raw2 = "+8801712345678"
        val raw3 = "8801712345678"
        val raw4 = "017-1234-5678"
        val raw5 = "+880 1712-345678"

        val norm1 = SenderIdentityHelper.normalizeSenderKey(raw1)
        val norm2 = SenderIdentityHelper.normalizeSenderKey(raw2)
        val norm3 = SenderIdentityHelper.normalizeSenderKey(raw3)
        val norm4 = SenderIdentityHelper.normalizeSenderKey(raw4)
        val norm5 = SenderIdentityHelper.normalizeSenderKey(raw5)

        assertEquals("PHONE_1712345678", norm1)
        assertEquals("PHONE_1712345678", norm2)
        assertEquals("PHONE_1712345678", norm3)
        assertEquals("PHONE_1712345678", norm4)
        assertEquals("PHONE_1712345678", norm5)
    }

    @Test
    fun testAlphanumericSenderNormalization() {
        val sender1 = "BRAC Bank"
        val sender2 = "bracbank"
        val sender3 = "GP"

        val norm1 = SenderIdentityHelper.normalizeSenderKey(sender1)
        val norm2 = SenderIdentityHelper.normalizeSenderKey(sender2)
        val norm3 = SenderIdentityHelper.normalizeSenderKey(sender3)

        assertEquals("ALPHA_BRACBANK", norm1)
        assertEquals("ALPHA_BRACBANK", norm2)
        assertEquals("ALPHA_GP", norm3)
    }

    @Test
    fun testOrganizationNameResolution() {
        val org1 = SenderIdentityHelper.resolveOrganizationName("BRACBANK")
        val org2 = SenderIdentityHelper.resolveOrganizationName("GP")
        val org3 = SenderIdentityHelper.resolveOrganizationName("01712345678", "Your BRAC Bank account balance is 50,000 BDT")
        val org4 = SenderIdentityHelper.resolveOrganizationName("01712345678", "Welcome to Grameenphone service")
        val org5 = SenderIdentityHelper.resolveOrganizationName("01812345678", "SEBL internet banking notification")

        assertEquals("BRAC Bank", org1)
        assertEquals("Grameenphone", org2)
        assertEquals("BRAC Bank", org3)
        assertEquals("Grameenphone", org4)
        assertEquals("SEBL", org5)
    }

    @Test
    fun testSensitiveCodePrivacyDetection() {
        // OTP messages
        assertTrue(SenderIdentityHelper.isOtpOrSecurityMessage("Your BRAC Bank OTP is 849201. Do not share with anyone."))
        assertTrue(SenderIdentityHelper.isOtpOrSecurityMessage("Use verification code 492013 to complete your login."))
        assertTrue(SenderIdentityHelper.isOtpOrSecurityMessage("Your secret PIN is 1234."))
        assertTrue(SenderIdentityHelper.isOtpOrSecurityMessage("bKash PIN: 9876. Never disclose your PIN."))
        assertTrue(SenderIdentityHelper.isOtpOrSecurityMessage("Your one-time password is 654321 for transaction approval."))
        assertTrue(SenderIdentityHelper.isOtpOrSecurityMessage("Security code: 582103"))
        assertTrue(SenderIdentityHelper.isOtpOrSecurityMessage("Use code 4321 to access your account"))

        // Regular non-sensitive messages
        assertFalse(SenderIdentityHelper.isOtpOrSecurityMessage("Hey, are we still meeting for lunch today?"))
        assertFalse(SenderIdentityHelper.isOtpOrSecurityMessage("Your order has been shipped and will arrive tomorrow."))
        assertFalse(SenderIdentityHelper.isOtpOrSecurityMessage("Happy Birthday! Wishing you all the best."))
        assertFalse(SenderIdentityHelper.isOtpOrSecurityMessage("Your remaining data balance is 2.5 GB valid until Friday."))
    }
}
