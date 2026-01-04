package com.xmobile.appclientmonitoringposturebot.util

import com.xmobile.appclientmonitoringposturebot.model.PostureRecord
import com.xmobile.appclientmonitoringposturebot.service.SupabaseProvider
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc

object StatisticPosetureRecord {
    suspend fun getRecordsByLocalDate(
        userId: String,
        date: java.time.LocalDate?
    ): List<PostureRecord> {

        return SupabaseProvider.client
            .postgrest
            .rpc(
                "get_posture_records_by_local_date",
                mapOf(
                    "p_user_id" to userId,
                    "p_date" to date.toString()
                )
            )
            .decodeList()
    }

    suspend fun getRecordsByLocalDates(
        userId: String,
        dates: List<java.time.LocalDate>
    ): List<PostureRecord> {
        return dates.flatMap { date ->
            getRecordsByLocalDate(userId, date)
        }
    }

}