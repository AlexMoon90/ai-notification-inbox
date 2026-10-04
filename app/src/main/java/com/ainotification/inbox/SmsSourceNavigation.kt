package com.ainotification.inbox

import android.content.Intent
import android.net.Uri

/** Only an observed phone address, never a display name, body, or group participant. */
internal fun smsSenderNumber(row:CapturedNotification):String? {
    if((row.serviceType!="SMS" && row.packageName !in smsPackages) || row.isGroupConversation==true || row.isGroupSummary)return null
    val address=row.personIdentity?.takeIf{it.startsWith("tel:")}?.removePrefix("tel:") ?: row.title ?: return null
    if(!Regex("\\+?[0-9][0-9 ()-]*").matches(address.trim()))return null
    val number=address.trim().filter{it.isDigit() || it=='+'}
    return number.takeIf{it.count(Char::isDigit) in 3..15}
}
internal fun smsSenderIntent(row:CapturedNotification):Intent? = smsSenderNumber(row)?.let {
    Intent(Intent.ACTION_SENDTO,Uri.fromParts("smsto",it,null)).setPackage(row.packageName)
}
