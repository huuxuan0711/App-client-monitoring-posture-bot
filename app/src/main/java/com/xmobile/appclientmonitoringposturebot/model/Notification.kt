package com.xmobile.appclientmonitoringposturebot.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class Notification(
    val id: Long,
    val user_id: String,
    val title: String,
    val body: String,
    val type: String,
    val created_at: String,
    val meta: JsonElement? = null
)
