package com.xmobile.appclientmonitoringposturebot.model

import kotlinx.serialization.Serializable

@Serializable
data class UserProfile(
    val id: String,
    val user_name: String,
    val created_at: String? = null,
    val updated_at: String? = null
)