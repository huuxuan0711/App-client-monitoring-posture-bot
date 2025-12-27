package com.xmobile.appclientmonitoringposturebot.helper

import com.xmobile.appclientmonitoringposturebot.model.Notification
import com.xmobile.appclientmonitoringposturebot.model.NotificationUiItem
import java.time.LocalDate
import java.time.OffsetDateTime

object NotificationUiMapper {
    fun buildNotificationUiItems(
        notifications: List<Notification>
    ): List<NotificationUiItem> {

        if (notifications.isEmpty()) return emptyList()

        val today = LocalDate.now()

        val sorted = notifications
            .filter { it.created_at != null }
            .sortedByDescending { it.created_at }

        val result = mutableListOf<NotificationUiItem>()

        var currentHeader: String? = null

        for (noti in sorted) {

            val date: LocalDate = OffsetDateTime
                .parse(noti.created_at)
                .toLocalDate()

            val header = when {
                date == today -> "HÔM NAY"
                date == today.minusDays(1) -> "HÔM QUA"
                date.isAfter(today.minusDays(7)) -> "7 NGÀY GẦN ĐÂY"
                else -> "CŨ HƠN"
            }

            // chỉ add header khi header thay đổi
            if (header != currentHeader) {
                result.add(NotificationUiItem.Header(header))
                currentHeader = header
            }

            result.add(NotificationUiItem.Item(noti))
        }

        return result
    }
}