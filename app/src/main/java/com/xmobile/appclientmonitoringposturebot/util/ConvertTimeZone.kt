package com.xmobile.appclientmonitoringposturebot.util

import com.xmobile.appclientmonitoringposturebot.model.PostureRecord
import java.time.OffsetDateTime
import java.time.ZoneId

object ConvertTimeZone {
    private val LOCAL_ZONE: ZoneId = ZoneId.systemDefault()

    fun PostureRecord.createdAtLocal(): OffsetDateTime {
        return OffsetDateTime
            .parse(this.created_at)   // UTC
            .atZoneSameInstant(LOCAL_ZONE)
            .toOffsetDateTime()
    }

}