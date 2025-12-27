package com.xmobile.appclientmonitoringposturebot.model

import kotlinx.serialization.SerialName
import java.io.Serializable

@kotlinx.serialization.Serializable
data class UserDevice(
    val id: String,

    @SerialName("user_id")
    val userId: String,

    @SerialName("device_id")
    val deviceId: String,

    @SerialName("device_name")
    val deviceName: String = "Posture Bot",

    val status: String = "OFFLINE",

    @SerialName("paired_at")
    val pairedAt: String,

    @SerialName("last_seen")
    val lastSeen: String? = null,

    val metadata: Map<String, String>? = null
): Serializable