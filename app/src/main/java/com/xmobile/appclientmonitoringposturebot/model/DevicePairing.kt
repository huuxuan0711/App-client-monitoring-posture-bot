package com.xmobile.appclientmonitoringposturebot.model

import kotlinx.serialization.SerialName
import java.io.Serializable

@kotlinx.serialization.Serializable
data class DevicePairing(
    @SerialName("pairing_code")
    val pairingCode: String,

    @SerialName("device_id")
    val deviceId: String,

    @SerialName("device_name")
    val deviceName: String? = null
)