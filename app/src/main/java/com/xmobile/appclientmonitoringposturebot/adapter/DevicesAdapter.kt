package com.xmobile.appclientmonitoringposturebot.adapter

import android.content.Context
import android.graphics.PorterDuff
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.xmobile.appclientmonitoringposturebot.R
import com.xmobile.appclientmonitoringposturebot.model.UserDevice

class DevicesAdapter(
    private val devices: List<UserDevice>,
    private val context: Context,
    private val onItemClick: (UserDevice) -> Unit
): RecyclerView.Adapter<DevicesAdapter.DeviceViewHolder>() {
    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): DeviceViewHolder {
        return DeviceViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_pair_device, parent, false))
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        holder.deviceName.text = devices[position].deviceName
        holder.botLastSeen.text = devices[position].lastSeen

        when (devices[position].status) {
            "ONLINE" -> {
                holder.botStatus.text = "Đang hoạt động"
                holder.botStatusIcon.setColorFilter(
                    ContextCompat.getColor(context, R.color.green),
                    PorterDuff.Mode.SRC_IN
                )
            }

            "OFFLINE" -> {
                holder.botStatus.text = "Đang offline"
                holder.botStatusIcon.setColorFilter(
                    ContextCompat.getColor(context, R.color.red),
                    PorterDuff.Mode.SRC_IN
                )
            }

            else -> {
                holder.botStatus.text = "Đang chờ"
                holder.botStatusIcon.setColorFilter(
                    ContextCompat.getColor(context, R.color.yellow),
                    PorterDuff.Mode.SRC_IN
                )
            }
        }

        holder.itemView.setOnClickListener {
            onItemClick(devices[position])
        }
    }

    override fun getItemCount(): Int {
        return devices.size
    }

    inner class DeviceViewHolder(
        itemView: View
    ): RecyclerView.ViewHolder(
        itemView
    ) {
        val deviceName: TextView = itemView.findViewById(R.id.device_name)
        val botStatus: TextView = itemView.findViewById(R.id.bot_status)
        val botLastSeen: TextView = itemView.findViewById(R.id.bot_time_update)
        val botStatusIcon: ImageView = itemView.findViewById(R.id.imgStatusBot)
    }
}