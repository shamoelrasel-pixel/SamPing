package com.example.service

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import com.example.domain.model.SimInfo

class SimManager(private val context: Context) {

    fun hasPhoneStatePermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    fun getAvailableSims(): List<SimInfo> {
        val result = mutableListOf<SimInfo>()

        if (!hasPhoneStatePermission()) {
            return listOf(
                SimInfo(
                    subscriptionId = -1,
                    slotIndex = 0,
                    displayName = "Default SIM (System Selected)",
                    carrierName = "Default Carrier",
                    isDefault = true
                )
            )
        }

        try {
            val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
            val activeList: List<SubscriptionInfo>? = subscriptionManager?.activeSubscriptionInfoList

            if (!activeList.isNullOrEmpty()) {
                activeList.forEachIndexed { index, info ->
                    val carrierName = info.carrierName?.toString() ?: "SIM ${info.simSlotIndex + 1}"
                    val displayName = info.displayName?.toString() ?: carrierName
                    val subId = info.subscriptionId
                    val slotIndex = info.simSlotIndex

                    result.add(
                        SimInfo(
                            subscriptionId = subId,
                            slotIndex = slotIndex,
                            displayName = if (displayName.isNotBlank()) displayName else "SIM ${slotIndex + 1} ($carrierName)",
                            carrierName = carrierName,
                            iccId = info.iccId ?: "",
                            isDefault = index == 0
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (result.isEmpty()) {
            val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            val networkOperator = telephonyManager?.networkOperatorName?.takeIf { it.isNotBlank() } ?: "Device SIM"
            result.add(
                SimInfo(
                    subscriptionId = -1,
                    slotIndex = 0,
                    displayName = "$networkOperator (SIM 1)",
                    carrierName = networkOperator,
                    isDefault = true
                )
            )
        }

        return result
    }

    fun getSimInfoForSubscriptionId(subId: Int?): SimInfo? {
        if (subId == null || subId == -1) return null
        return getAvailableSims().firstOrNull { it.subscriptionId == subId }
    }
}
