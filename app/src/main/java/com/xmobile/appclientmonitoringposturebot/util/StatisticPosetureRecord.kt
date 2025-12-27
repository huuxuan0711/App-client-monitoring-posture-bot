package com.xmobile.appclientmonitoringposturebot.util

import com.xmobile.appclientmonitoringposturebot.model.PostureRecord
import com.xmobile.appclientmonitoringposturebot.service.SupabaseProvider
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order

import java.time.OffsetDateTime

object StatisticPosetureRecord {
    suspend fun getPostureRecordsByTimeRange(
        userId: String,
        fromTime: OffsetDateTime,
        toTime: OffsetDateTime
    ): List<PostureRecord> {

        return SupabaseProvider.client
            .from("posture_records")
            .select {
                filter {
                    eq("user_id", userId)
                    gte("created_at", fromTime)
                    lte("created_at", toTime)
                }
                order("created_at", Order.DESCENDING)
            }
            .decodeList()
    }

}