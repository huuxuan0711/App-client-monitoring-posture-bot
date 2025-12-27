package com.xmobile.appclientmonitoringposturebot.model

sealed class NotificationUiItem {
    data class Header(val title: String) : NotificationUiItem()
    data class Item(val data: Notification) : NotificationUiItem()
}