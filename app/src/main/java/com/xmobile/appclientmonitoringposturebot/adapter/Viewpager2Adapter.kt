package com.xmobile.appclientmonitoringposturebot.adapter

import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.xmobile.appclientmonitoringposturebot.fragment.DashboardFragment
import com.xmobile.appclientmonitoringposturebot.fragment.HistoryFragment
import com.xmobile.appclientmonitoringposturebot.fragment.HomeFragment
import com.xmobile.appclientmonitoringposturebot.fragment.NotificationFragment
import com.xmobile.appclientmonitoringposturebot.model.UserDevice

class Viewpager2Adapter(
    fragmentActivity: FragmentActivity,
    private val device: UserDevice?
): FragmentStateAdapter(fragmentActivity) {
    override fun createFragment(position: Int): Fragment {
        val bundle = Bundle()
        bundle.putSerializable("device", device)

        return when (position) {
            0 -> HomeFragment().apply {
                arguments = bundle
            }
            1 -> DashboardFragment().apply {
                arguments = bundle
            }
            2 -> HistoryFragment().apply {
                arguments = bundle
            }
            3 -> NotificationFragment().apply {
                arguments = bundle
            }
            else -> HomeFragment().apply {
                arguments = bundle
            }
        }
    }

    override fun getItemCount(): Int {
        return 4
    }
}