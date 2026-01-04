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
import android.util.Log
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
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
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
import java.time.Duration
import java.time.OffsetDateTime

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val supabase = SupabaseProvider.client

    private var userId: String = ""
    private var userName: String = ""

    private val device: UserDevice? by lazy {
        arguments?.getSerializable("device", UserDevice::class.java)
    }

    private var botStatusJob: Job? = null
    private var postureRealtimeJob: Job? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        init()
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        botStatusJob?.cancel()
        postureRealtimeJob?.cancel()
        _binding = null
    }

    private fun init() {
        initUserInfo()
        initDeviceInfo()

        initActions()

        listenBotStatus()

        loadInitialPostureRecord()
        listenPostureRealtime()
    }

    private fun initUserInfo() {
        val prefs = requireContext().getSharedPreferences("MyPrefs", MODE_PRIVATE)
        userName = prefs.getString("user_name", "") ?: ""
        binding.userName.text = userName
    }

    private fun initDeviceInfo() {
        device ?: return
        userId = device!!.userId
        binding.botName.text = device!!.deviceName
        renderBotStatusUI(device!!.status, device!!.lastSeen)
    }

    private fun initActions() {
        binding.imgSetting.setOnClickListener {
            startActivity(
                Intent(requireContext(), ProfileActivity::class.java)
                    .putExtra("device", device)
            )
            requireActivity().finish()
        }
    }

    private fun listenBotStatus() {
        val deviceId = device?.deviceId ?: return

        val channel = supabase.realtime.channel("user-devices-$deviceId")

        botStatusJob?.cancel()
        botStatusJob = viewLifecycleOwner.lifecycleScope.launch {
            channel.subscribe()

            channel.postgresChangeFlow<PostgresAction>(schema = "public") {
                table = "user_devices"
            }.collect { action ->
                val record = when (action) {
                    is PostgresAction.Insert -> action.record
                    is PostgresAction.Update -> action.record
                    else -> null
                } ?: return@collect

                val eventUserId = record["user_id"]?.jsonPrimitive?.content
                val eventDeviceId = record["device_id"]?.jsonPrimitive?.content

                if (eventUserId == userId && eventDeviceId == deviceId) {
                    renderBotStatusUI(
                        status = record["status"]?.jsonPrimitive?.content,
                        lastSeen = record["last_seen"]?.jsonPrimitive?.content
                    )
                }
            }
        }
    }

    private fun renderBotStatusUI(status: String?, lastSeen: String?) {
        when (status) {
            "ONLINE" -> {
                binding.botStatus.text = "Đang hoạt động"
                binding.imgStatusBot.setColorFilter(
                    ContextCompat.getColor(requireContext(), R.color.green),
                    PorterDuff.Mode.SRC_IN
                )
            }

            "OFFLINE" -> {
                binding.botStatus.text = "Đang offline"
                binding.imgStatusBot.setColorFilter(
                    ContextCompat.getColor(requireContext(), R.color.red),
                    PorterDuff.Mode.SRC_IN
                )
            }

            else -> {
                binding.botStatus.text = "Đang chờ"
                binding.imgStatusBot.setColorFilter(
                    ContextCompat.getColor(requireContext(), R.color.yellow),
                    PorterDuff.Mode.SRC_IN
                )
            }
        }
        binding.botTimeUpdate.text = lastSeen
    }

    private fun loadInitialPostureRecord() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val record = supabase
                    .from("posture_records")
                    .select {
                        filter { eq("user_id", userId) }
                        order("created_at", Order.DESCENDING)
                        limit(1)
                    }
                    .decodeSingleOrNull<JsonObject>()
                Log.e("record", record.toString())
                if (record == null) {
                    binding.cardStatusPosture3.visibility = View.GONE
                    binding.pieChart.visibility = View.GONE
                    binding.txtNullData.visibility = View.VISIBLE
                }
                record?.let { renderPosture(it) }
            } catch (_: Exception) {
            }
        }
    }

    private fun listenPostureRealtime() {
        val channel = supabase.realtime.channel("posture-records-$userId")

        postureRealtimeJob?.cancel()
        postureRealtimeJob = viewLifecycleOwner.lifecycleScope.launch {
            channel.subscribe()

            channel.postgresChangeFlow<PostgresAction>("public") {
                table = "posture_records"
            }.collect { action ->
                val record = (action as? PostgresAction.Insert)?.record ?: return@collect
                Log.e("record", record.toString())
                val recordUserId =
                    record["user_id"]?.jsonPrimitive?.content ?: return@collect

                if (recordUserId == userId) {
                    binding.cardStatusPosture3.visibility = View.VISIBLE
                    binding.pieChart.visibility = View.VISIBLE
                    binding.txtNullData.visibility = View.GONE
                    renderPosture(record)
                }
            }
        }
    }

    private fun renderPosture(record: JsonObject) {
        val postureType = record["posture_type"]?.jsonPrimitive?.content
        val confidence = record["confidence"]?.jsonPrimitive?.doubleOrNull
        val metrics = record["metrics"]?.jsonObject

        val createdAt = record["created_at"]?.jsonPrimitive?.content
        binding.botTimeUpdate.text = formatDurationFromNow(OffsetDateTime.parse(createdAt))

        setUpPieChart(postureType)
        setUpPosture(postureType, confidence, metrics)
    }

    private fun formatDurationFromNow(time: OffsetDateTime): String {

        val now = OffsetDateTime.now()
        val diffMs = Duration.between(time, now).toMillis()

        if (diffMs <= 0) return "0h0m"

        val totalMinutes = diffMs / 1000 / 60
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60

        return "${hours}h${minutes}m trước"
    }

    private fun setUpPieChart(postureType: String?) {
        Log.e("postureType", postureType.toString())
        val pieChart = binding.pieChart

        pieChart.apply {
            setUsePercentValues(false)
            description.isEnabled = false
            legend.isEnabled = false
            isDrawHoleEnabled = true
            holeRadius = 70f
            setDrawEntryLabels(false)
            isRotationEnabled = false
            setTouchEnabled(false)
            centerText = postureType?.uppercase() ?: ""
            setCenterTextSize(18f)
            setCenterTextTypeface(Typeface.DEFAULT_BOLD)
        }

        val (percent, color) = when (postureType?.lowercase()) {
            "good" -> 100f to R.color.purple
            "bad" -> 30f to R.color.red
            else -> 60f to R.color.yellow
        }

        val entries = listOf(
            PieEntry(percent),
            PieEntry(100f - percent)
        ).filter { it.value > 0 }

        val dataSet = PieDataSet(entries, "").apply {
            colors = listOf(
                requireContext().getColor(color),
                Color.TRANSPARENT
            )
            setDrawValues(false)
        }

        pieChart.data = PieData(dataSet)
        pieChart.animateY(600, Easing.EaseInOutQuad)
        pieChart.invalidate()
    }

    private fun setUpPosture(
        postureType: String?,
        confidence: Double?,
        metrics: JsonObject?
    ) {
        Log.e("postureType231", postureType.toString())

        binding.confidence.text = "${confidence?.times(100)?.toInt()}%"

        val neckAngle = metrics
            ?.get("neck_angle")
            ?.jsonPrimitive
            ?.doubleOrNull
            ?.toInt()

        binding.neckAngle.text = neckAngle?.let { "$it°" } ?: "--"

        when (postureType?.lowercase()) {
            "good" -> {
                binding.imgPostureIcon.setImageResource(R.drawable.correct_posture)
                binding.txtQuickFeedback.text = "Tư thế đang rất tốt."
            }

            "bad" -> {
                binding.imgPostureIcon.setImageResource(R.drawable.incorrect_posture)
                binding.txtQuickFeedback.text = "Tư thế chưa đúng. Hãy ngồi thẳng lưng."
            }

            else -> {
                binding.imgPostureIcon.setImageResource(R.drawable.warning_posture)
                binding.txtQuickFeedback.text = "Gần đúng rồi. Ngồi thẳng hơn một chút."
            }
        }
    }

}
