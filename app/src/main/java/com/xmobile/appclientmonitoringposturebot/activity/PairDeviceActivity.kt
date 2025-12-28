package com.xmobile.appclientmonitoringposturebot.activity

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.xmobile.appclientmonitoringposturebot.R
import com.xmobile.appclientmonitoringposturebot.adapter.DevicesAdapter
import com.xmobile.appclientmonitoringposturebot.databinding.ActivityPairDeviceBinding
import com.xmobile.appclientmonitoringposturebot.model.DevicePairing
import com.xmobile.appclientmonitoringposturebot.model.UserDevice
import com.xmobile.appclientmonitoringposturebot.service.SupabaseProvider
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class PairDeviceActivity : AppCompatActivity() {
    private lateinit var binding: ActivityPairDeviceBinding
    private val supabase = SupabaseProvider.client
    private var realtimeJob: Job? = null

    private var devices: List<UserDevice> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPairDeviceBinding.inflate(layoutInflater)
        setContentView(binding.root)
        initControl()
    }

    private fun initControl() {

        val userName = getSharedPreferences("MyPrefs", MODE_PRIVATE).getString("user_name", "")
        binding.userName.text = userName

        val userId = getSharedPreferences("MyPrefs", MODE_PRIVATE).getString("user_id", "")
        Log.e("userId", userId.toString())

        listenUserDevices(userId)
        getDevices(userId)

        binding.btnAddDevice.setOnClickListener {
            addDevice(userId)
        }

        binding.txtRemoveDevice.setOnClickListener {
            lifecycleScope.launch {
                try {
                    SupabaseProvider.client
                        .from("user_devices")
                        .delete {
                            filter {
                                userId?.let { value -> eq("user_id", value) }
                               eq("device_id", devices[0].deviceId)
                            }
                        }

                    binding.txtRemoveDevice.visibility = View.GONE
                    binding.btnAddDevice.visibility = View.VISIBLE
                    Toast.makeText(
                        this@PairDeviceActivity,
                        "Đã gỡ thiết bị",
                        Toast.LENGTH_SHORT
                    ).show()
                }catch (e: Exception) {
                    Log.e("removeDevice", e.message ?: "Unknown error")
                    Toast.makeText(this@PairDeviceActivity, "Có lỗi xảy ra", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun addDevice(userId: String?) {
        val builder = AlertDialog.Builder(this)
        val view = LayoutInflater.from(this).inflate(R.layout.layout_add_device, null)
        builder.setView(view)
        val dialog = builder.create()
        val txtCancel = view.findViewById<TextView>(R.id.txtCancel)
        val txtConfirm = view.findViewById<TextView>(R.id.txtConfirm)
        val edtCode = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.edtCode)
        val checkCode = view.findViewById<TextView>(R.id.checkCode)

        txtCancel.setOnClickListener {
            dialog.dismiss()
        }

        txtConfirm.setOnClickListener {
            val code = edtCode.text.toString()
            if (code.isEmpty()) {
                checkCode.visibility = View.VISIBLE
                checkCode.text = "Vui lòng nhập mã thiết bị"
            } else {
                checkCode.visibility = View.GONE
                lifecycleScope.launch {
                    val device: DevicePairing? = supabase
                        .from("device_pairing")
                        .select {
                            filter {
                                eq("pairing_code", code)
                            }
                            limit(1)
                        }
                        .decodeSingleOrNull<DevicePairing>()
                    Log.e("device", device.toString())
                    if (device != null) {
                        supabase
                            .from("user_devices")
                            .insert(
                                mapOf(
                                    "user_id" to userId,
                                    "device_id" to device.deviceId,
                                    "device_name" to (device.deviceName ?: "Posture Bot"),
                                    "status" to "OFFLINE"
                                )
                            )
                        binding.btnAddDevice.visibility = View.GONE
                        binding.txtRemoveDevice.visibility = View.VISIBLE
                        dialog.dismiss()
                    }else {
                        checkCode.visibility = View.VISIBLE
                        checkCode.text = "Mã không tồn tại"
                    }
                }
            }
        }

        dialog.show()
    }

    private fun listenUserDevices(userId: String?) {
        val channel = supabase.realtime.channel("user-devices")

         realtimeJob = lifecycleScope.launch {
             channel.subscribe()
            channel
                .postgresChangeFlow<PostgresAction>(
                    schema = "public"
                ) {
                    table = "user_devices"
                }
                .collect {
                    getDevices(userId)
                }
        }
    }

    private fun getDevices(userId: String?) {
        if (userId == null) return
        lifecycleScope.launch {
            try {
                SupabaseProvider.client.auth.awaitInitialization()

                val session = SupabaseProvider.client.auth.currentSessionOrNull()
                if (session == null) {
                    startActivity(Intent(this@PairDeviceActivity, LoginActivity::class.java))
                    finish()
                    return@launch
                }
                 devices = supabase
                    .from("user_devices")
                    .select {
                        filter {
                            eq("user_id", userId)
                        }
                        order(
                            column = "paired_at",
                            order = Order.ASCENDING
                        )
                    }
                    .decodeList<UserDevice>()
                Log.e("devices", devices.toString())
                if (devices.isEmpty()) binding.btnAddDevice.visibility = View.VISIBLE
                else {
                    binding.btnAddDevice.visibility = View.GONE
                    binding.txtRemoveDevice.visibility = View.VISIBLE
                }
                setUpRecyclerView(devices)

            } catch (e: Exception) {
                Log.e("getDevices", e.message ?: "Unknown error")
            }
        }
    }

    private fun setUpRecyclerView(devices: List<UserDevice>) {
        val adapter = DevicesAdapter(devices, this) {
            val intentNav = Intent(this, MainActivity::class.java)
            if (intent.hasExtra("navigate_to")) {
                intentNav.putExtra("navigate_to", "notification")
            }
            intentNav.putExtra("device", it)
            startActivity(intentNav)
        }
        binding.recyclerViewDevices.adapter = adapter
        binding.recyclerViewDevices.layoutManager = LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)
    }

    override fun onDestroy() {
        super.onDestroy()
        realtimeJob?.cancel()
    }
}