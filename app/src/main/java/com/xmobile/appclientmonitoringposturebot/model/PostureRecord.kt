package com.xmobile.appclientmonitoringposturebot.model

import kotlinx.serialization.Serializable
import java.time.OffsetDateTime
import java.util.UUID

@Serializable
data class PostureRecord(
    val id: Long,
    val user_id: String,
    val posture_type: String,
    val confidence: Double?,
    val keypoints: Map<String, String>?,
    val created_at: String,
    val metrics: Map<String, String>?
)