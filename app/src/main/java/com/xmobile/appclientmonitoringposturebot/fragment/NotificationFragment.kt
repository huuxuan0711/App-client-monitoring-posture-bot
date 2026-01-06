package com.xmobile.appclientmonitoringposturebot.fragment

import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.RequiresApi
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.xmobile.appclientmonitoringposturebot.R
import com.xmobile.appclientmonitoringposturebot.adapter.NotificationAdapter
import com.xmobile.appclientmonitoringposturebot.databinding.FragmentNotificationBinding
import com.xmobile.appclientmonitoringposturebot.helper.NotificationTypePopupHelper
import com.xmobile.appclientmonitoringposturebot.model.Notification
import com.xmobile.appclientmonitoringposturebot.model.NotificationLevel
import com.xmobile.appclientmonitoringposturebot.model.NotificationUiItem
import com.xmobile.appclientmonitoringposturebot.model.UserDevice
import com.xmobile.appclientmonitoringposturebot.service.SupabaseProvider
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.time.LocalDate

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
class NotificationFragment : Fragment() {
    private var _binding: FragmentNotificationBinding? = null
    private val binding get() = _binding!!

    private var notificationList: List<Notification> = emptyList()
    private var adapter: NotificationAdapter? = null

    private var realtimeJob: Job? = null

    private val device by lazy {
        arguments?.getSerializable("device", UserDevice::class.java)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentNotificationBinding.inflate(inflater, container, false)
        initControl()
        return binding.root
    }

    private fun initControl() {
        listenNotification(device?.userId ?: "")
        getNotification(device?.userId ?: "")

        binding.layoutFilter.setOnClickListener { view ->
            NotificationTypePopupHelper.show(
                requireContext(),
                view,
                binding.txtFilter.text.toString()
            ) { selectedId ->
                when (selectedId) {
                    R.id.txtAll -> {
                        binding.txtFilter.text = "Tất cả"
                        adapter?.filterByType(null)
                    }
                    R.id.txtMild -> {
                        binding.txtFilter.text = "Nhắc nhở"
                        adapter?.filterByType(NotificationLevel.mild)
                    }
                    R.id.txtModerate -> {
                        binding.txtFilter.text = "Cảnh báo"
                        adapter?.filterByType(NotificationLevel.moderate)
                    }
                    R.id.txtSevere -> {
                        binding.txtFilter.text = "Xấu"
                        adapter?.filterByType(NotificationLevel.severe)
                    }
                }
            }
        }
    }

    private fun listenNotification(userId: String) {
        val channel = SupabaseProvider.client.realtime.channel("notifications")

        realtimeJob = lifecycleScope.launch {
            channel.subscribe()
            channel
                .postgresChangeFlow<PostgresAction>(
                    schema = "public"
                ) {
                    table = "notifications"
                }
                .collect {
                    getNotification(userId)
                }

        }
    }

    private fun getNotification(userId: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                notificationList = SupabaseProvider.client
                    .from("notifications")
                    .select {
                        filter {
                            eq("user_id", userId)
                        }
                        order(
                            column = "created_at",
                            order = Order.DESCENDING
                        )
                    }
                    .decodeList<Notification>()
                Log.e("getNotification", notificationList.toString())
                setUpNotificationList(notificationList)

            } catch (e: Exception) {
                Log.e("getNotification", e.message ?: "Unknown error")
            }
        }
    }

    private fun setUpNotificationList(notifications: List<Notification>) {
        adapter = NotificationAdapter()
        binding.recyclerViewNotification.adapter = adapter
        binding.recyclerViewNotification.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false)
        adapter?.submitListNoti(notifications)
    }

    override fun onDestroy() {
        super.onDestroy()
        realtimeJob?.cancel()
    }
}