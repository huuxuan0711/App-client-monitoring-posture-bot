package com.xmobile.appclientmonitoringposturebot.fragment

import android.content.Context.MODE_PRIVATE
import android.content.Intent
import android.content.res.Resources
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.service.autofill.Validators.and
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.github.mikephil.charting.animation.Easing
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.xmobile.appclientmonitoringposturebot.R
import com.xmobile.appclientmonitoringposturebot.activity.ProfileActivity
import com.xmobile.appclientmonitoringposturebot.databinding.FragmentHomeBinding
import com.xmobile.appclientmonitoringposturebot.model.DevicePairing
import com.xmobile.appclientmonitoringposturebot.model.UserDevice
import com.xmobile.appclientmonitoringposturebot.service.SupabaseProvider
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
class HomeFragment : Fragment() {
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private var userName = ""
    private var userId = ""

    private val device by lazy {
        arguments?.getSerializable("device", UserDevice::class.java)
    }

    private var realtimeJobDevice: Job? = null
    private var realtimeJobRecord: Job? = null

    private val supabase = SupabaseProvider.client

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        initControl()
        return binding.root
    }

    private fun initControl() {
        val sharedPreferences = requireContext().getSharedPreferences("MyPrefs", MODE_PRIVATE)
        userName = sharedPreferences.getString("user_name", "").toString()
        binding.userName.text = userName
        binding.botName.text = device?.deviceName

        listenBotStatus()
        listenDeviceRecords(userId)

        binding.imgSetting.setOnClickListener {
            val intent = Intent(requireContext(), ProfileActivity::class.java)
            intent.putExtra("device", device)
            startActivity(intent)
            requireActivity().finish()
        }
    }

    private fun listenDeviceRecords(userId: String) {
        val channel = supabase.realtime.channel("posture-records-$userId")

        realtimeJobRecord?.cancel()

        realtimeJobRecord = viewLifecycleOwner.lifecycleScope.launch {
            channel.subscribe()
            channel
                .postgresChangeFlow<PostgresAction>("public") {
                    table = "posture_records"
                }
                .collect { action ->

                    val recordJson = when (action) {
                        is PostgresAction.Insert -> action.record
                        else -> null
                    } ?: return@collect

                    val recordUserId =
                        recordJson["user_id"]?.jsonPrimitive?.content

                    if (recordUserId != userId) return@collect

                    handleFirstPostureRecord(recordJson)

                    realtimeJobRecord?.cancel()
                }

        }
    }

    private fun handleFirstPostureRecord(record: JsonObject) {
        val postureType =
            record["posture_type"]?.jsonPrimitive?.content
        val confidence =
            record["confidence"]?.jsonPrimitive?.doubleOrNull
        val metrics = record["metrics"]?.jsonObject

        setUpPieChart(postureType)
        setUpPosture(postureType, confidence, metrics)
    }

    private fun setUpPieChart(postureType: String?) {
        val pieChart = binding.pieChart

        // Base config
        pieChart.apply {
            setUsePercentValues(false)
            description.isEnabled = false
            legend.isEnabled = false

            isDrawHoleEnabled = true
            holeRadius = 70f
            transparentCircleRadius = 0f

            setDrawEntryLabels(false)
            isRotationEnabled = false
            setTouchEnabled(false)

            centerText = postureType?.uppercase() ?: ""
            setCenterTextSize(18f)
            setCenterTextColor(R.color.dark_blue)
            setCenterTextTypeface(Typeface.DEFAULT_BOLD)
        }

        // Decide data
        val (filledPercent, filledColor) = when (postureType?.lowercase()) {
            "good" -> Pair(100f, R.color.purple)
            "bad" -> Pair(30f, R.color.red)
            else -> Pair(60f, R.color.yellow)
        }

        val entries = mutableListOf<PieEntry>()
        entries.add(PieEntry(filledPercent))

        if (filledPercent < 100f) {
            entries.add(PieEntry(100f - filledPercent))
        }

        // Dataset
        val dataSet = PieDataSet(entries, "").apply {
            colors = if (filledPercent == 100f) {
                listOf(requireContext().getColor(filledColor))
            } else {
                listOf(
                    requireContext().getColor(filledColor),
                    Color.TRANSPARENT
                )
            }

            setDrawValues(false)
            sliceSpace = 0f
        }

        pieChart.data = PieData(dataSet)
        pieChart.animateY(
            800,
            Easing.EaseInOutQuad
        )
        pieChart.invalidate()
    }

    private fun setUpPosture(postureType: String?, confidence: Double?, metrics: JsonObject?){
        binding.confidence.text = "" + confidence?.times(100) + "%"
        binding.neckAngle.text = metrics?.get("neck_angle")?.jsonPrimitive?.content + "°"
        when (postureType?.lowercase()) {
            "good" -> {
                binding.imgPostureIcon.setImageResource(R.drawable.correct_posture)
                binding.imgPostureIcon.setColorFilter(ContextCompat.getColor(requireContext(), R.color.purple), PorterDuff.Mode.SRC_IN)
                binding.txtQuickFeedback.text = "Tư thế đang rất tốt."
            }
            "bad" -> {
                binding.imgPostureIcon.setImageResource(R.drawable.incorrect_posture)
                binding.imgPostureIcon.setColorFilter(ContextCompat.getColor(requireContext(), R.color.red), PorterDuff.Mode.SRC_IN)
                binding.txtQuickFeedback.text = "Tư thế chưa đúng. Hãy ngồi thẳng lưng."
            }
            else -> {
                binding.imgPostureIcon.setImageResource(R.drawable.warning_posture)
                binding.imgPostureIcon.setColorFilter(ContextCompat.getColor(requireContext(), R.color.yellow), PorterDuff.Mode.SRC_IN)
                binding.txtQuickFeedback.text = "Gần đúng rồi. Ngồi thẳng hơn một chút."
            }
        }
    }

    private fun listenBotStatus() {
        val deviceId = device?.deviceId ?: return
        userId = device?.userId ?: return

        val channel = supabase.realtime.channel("user-devices-$deviceId")

        realtimeJobDevice?.cancel()

        realtimeJobDevice = viewLifecycleOwner.lifecycleScope.launch {
            channel.subscribe()
            channel
                .postgresChangeFlow<PostgresAction>(
                schema = "public"
                ) {
                    table = "user_devices"
                }
                .collect { action ->
                    val deviceJson = when (action) {
                        is PostgresAction.Insert -> action.record
                        is PostgresAction.Update -> action.record
                        else -> null
                    }

                    val eventUserId =
                        deviceJson?.get("user_id")?.jsonPrimitive?.content
                    val eventDeviceId =
                        deviceJson?.get("device_id")?.jsonPrimitive?.content

                    if (eventUserId == userId && eventDeviceId == deviceId) {
                        val status =
                            deviceJson["status"]?.jsonPrimitive?.content
                        val lastSeen =
                            deviceJson["last_seen"]?.jsonPrimitive?.content
                        updateBotStatusUI(status, lastSeen)
                    }
                }
        }
    }

    private fun updateBotStatusUI(status: String?, lastSeen: String?) {
        when (status) {
            "ONLINE" -> {
                binding.botStatus.text = "Đang hoạt động"
                binding.imgStatusBot.setColorFilter(ContextCompat.getColor(requireContext(), R.color.green), PorterDuff.Mode.SRC_IN)
            }
            "OFFLINE" -> {
                binding.botStatus.text = "Đang offline"
                binding.imgStatusBot.setColorFilter(ContextCompat.getColor(requireContext(), R.color.red), PorterDuff.Mode.SRC_IN)
            }
            else -> {
                binding.botStatus.text = "Đang chờ"
                binding.imgStatusBot.setColorFilter(ContextCompat.getColor(requireContext(), R.color.yellow), PorterDuff.Mode.SRC_IN)
            }
        }
        binding.botTimeUpdate.text = lastSeen
    }

    override fun onDestroy() {
        super.onDestroy()
        realtimeJobDevice?.cancel()
        realtimeJobRecord?.cancel()
    }
}