package com.xmobile.appclientmonitoringposturebot.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.xmobile.appclientmonitoringposturebot.R
import com.xmobile.appclientmonitoringposturebot.model.UserDevice

class DevicesAdapter(
    private val devices: List<UserDevice>,
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
        holder.botStatus.text = devices[position].status
        holder.botLastSeen.text = devices[position].lastSeen

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
    }
}