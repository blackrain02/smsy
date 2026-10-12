package com.blackrain02.spamguard.ui.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import androidx.core.content.ContextCompat

data class SimCardInfo(
    val subscriptionId: Int,
    val slotIndex: Int, // 0 for SIM 1, 1 for SIM 2
    val displayName: String,
    val carrierName: String
) {
    val badgeLabel: String
        get() = "SIM ${slotIndex + 1}"
}

object SimHelper {

    fun getActiveSims(context: Context): List<SimCardInfo> = getActiveSimCards(context)

    fun getActiveSimCards(context: Context): List<SimCardInfo> {
        val result = mutableListOf<SimCardInfo>()
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            return result
        }

        try {
            val subManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
            val subList: List<SubscriptionInfo>? = subManager?.activeSubscriptionInfoList
            if (subList != null) {
                for (info in subList) {
                    val subId = info.subscriptionId
                    val slot = info.simSlotIndex
                    val display = info.displayName?.toString() ?: "SIM ${slot + 1}"
                    val carrier = info.carrierName?.toString() ?: ""
                    result.add(SimCardInfo(subId, slot, display, carrier))
                }
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }

        result.sortBy { it.slotIndex }
        return result
    }

    fun isMultiSim(context: Context): Boolean {
        return getActiveSimCards(context).size > 1
    }

    fun getSimLabel(context: Context, subId: Int?): String? {
        if (subId == null || subId <= 0) return null
        val sims = getActiveSimCards(context)
        val match = sims.find { it.subscriptionId == subId }
        return match?.badgeLabel ?: "SIM"
    }
}
