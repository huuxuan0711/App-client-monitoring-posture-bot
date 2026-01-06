package com.xmobile.appclientmonitoringposturebot.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.xmobile.appclientmonitoringposturebot.R
import com.xmobile.appclientmonitoringposturebot.helper.NotificationUiMapper
import com.xmobile.appclientmonitoringposturebot.model.Notification
import com.xmobile.appclientmonitoringposturebot.model.NotificationLevel
import com.xmobile.appclientmonitoringposturebot.model.NotificationUiItem
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

class NotificationAdapter(
    private var notifications: List<Notification> = emptyList()
) : ListAdapter<NotificationUiItem, RecyclerView.ViewHolder>(DiffCallback()) {

    private var newNotifications: List<NotificationUiItem> = emptyList()

    class DiffCallback : DiffUtil.ItemCallback<NotificationUiItem>() {

        override fun areItemsTheSame(
            oldItem: NotificationUiItem,
            newItem: NotificationUiItem
        ): Boolean {
            return when {
                oldItem is NotificationUiItem.Header &&
                        newItem is NotificationUiItem.Header ->
                    oldItem.title == newItem.title

                oldItem is NotificationUiItem.Item &&
                        newItem is NotificationUiItem.Item ->
                    oldItem.data.id == newItem.data.id

                else -> false
            }
        }

        override fun areContentsTheSame(
            oldItem: NotificationUiItem,
            newItem: NotificationUiItem
        ): Boolean = oldItem == newItem
    }

    fun submitListNoti(list: List<Notification>) {
        notifications = list
        newNotifications = NotificationUiMapper.buildNotificationUiItems(list)
        submitList(newNotifications)
    }

    fun filterByType(type: NotificationLevel?) {
        val filtered = if (type != null) {
            notifications.filter { noti ->
                noti.meta?.jsonObject
                    ?.get("level")?.jsonPrimitive?.content
                    ?.let { NotificationLevel.valueOf(it) } == type
            }
        } else {
            notifications
        }

        newNotifications = NotificationUiMapper.buildNotificationUiItems(filtered)
        submitList(newNotifications)
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): RecyclerView.ViewHolder {
        return if (viewType == VIEW_TYPE_ITEM) {
            NotificationViewHolder(
                LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_notification, parent, false)
            )
        } else {
            NotificationHeaderViewHolder(
                LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_notification_header, parent, false)
            )
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is NotificationViewHolder -> {
                val item = newNotifications[position] as NotificationUiItem.Item
                val notification = item.data

                holder.txtTitle.text = notification.title
                holder.txtBody.text = notification.body
                holder.txtTime.text = formatNotificationTime(notification.created_at)

                val level = notification.meta?.jsonObject
                    ?.get("level")?.jsonPrimitive?.content
                    ?.let { runCatching { NotificationLevel.valueOf(it) }.getOrNull() }

                holder.indicator.backgroundTintList = when (level) {
                    NotificationLevel.mild ->
                        holder.itemView.context.getColorStateList(R.color.purple)
                    NotificationLevel.moderate ->
                        holder.itemView.context.getColorStateList(R.color.yellow)
                    NotificationLevel.severe ->
                        holder.itemView.context.getColorStateList(R.color.red)
                    null ->
                        holder.itemView.context.getColorStateList(R.color.white)
                }
            }

            is NotificationHeaderViewHolder -> {
                holder.txtHeader.text =
                    (newNotifications[position] as NotificationUiItem.Header).title
            }
        }
    }

    override fun getItemViewType(position: Int): Int {
        return when (newNotifications[position]) {
            is NotificationUiItem.Header -> VIEW_TYPE_HEADER
            is NotificationUiItem.Item -> VIEW_TYPE_ITEM
        }
    }

    private fun formatNotificationTime(createdAt: String?): String {
        if (createdAt.isNullOrBlank()) return ""

        val zonedTime = runCatching {
            OffsetDateTime.parse(createdAt)
                .atZoneSameInstant(ZoneId.systemDefault())
        }.getOrNull() ?: return ""

        val now = ZonedDateTime.now(ZoneId.systemDefault())

        val date = zonedTime.toLocalDate()
        val today = now.toLocalDate()

        return when {
            date == today || date == today.minusDays(1) ->
                zonedTime.format(DateTimeFormatter.ofPattern("HH:mm"))

            date.isAfter(today.minusDays(7)) ->
                zonedTime.format(DateTimeFormatter.ofPattern("dd/MM"))

            else ->
                zonedTime.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
        }
    }


    // -------------------- ViewHolder --------------------
    inner class NotificationViewHolder(itemView: View) :
        RecyclerView.ViewHolder(itemView) {

        val txtTitle: TextView = itemView.findViewById(R.id.txtTitle)
        val txtBody: TextView = itemView.findViewById(R.id.txtBody)
        val txtTime: TextView = itemView.findViewById(R.id.txtTime)
        val indicator: View = itemView.findViewById(R.id.indicatorView)
    }

    inner class NotificationHeaderViewHolder(itemView: View) :
        RecyclerView.ViewHolder(itemView) {

        val txtHeader: TextView = itemView.findViewById(R.id.txtHeader)
    }

    companion object {
        private const val VIEW_TYPE_ITEM = 0
        private const val VIEW_TYPE_HEADER = 1
    }
}
