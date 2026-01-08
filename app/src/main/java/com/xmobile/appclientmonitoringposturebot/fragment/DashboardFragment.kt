package com.xmobile.appclientmonitoringposturebot.fragment

import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.RequiresApi
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.github.mikephil.charting.animation.Easing
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.listener.OnChartValueSelectedListener
import com.xmobile.appclientmonitoringposturebot.R
import com.xmobile.appclientmonitoringposturebot.databinding.FragmentDashboardBinding
import com.xmobile.appclientmonitoringposturebot.model.PostureDuration
import com.xmobile.appclientmonitoringposturebot.model.PostureRecord
import com.xmobile.appclientmonitoringposturebot.model.UserDevice
import com.xmobile.appclientmonitoringposturebot.service.SupabaseProvider
import com.xmobile.appclientmonitoringposturebot.util.StatisticPosetureRecord
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDate
import java.time.OffsetDateTime
import kotlin.math.min
import androidx.core.content.edit
import com.xmobile.appclientmonitoringposturebot.util.ConvertTimeZone.createdAtLocal
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.Job

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
class DashboardFragment : Fragment() {
    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    private var userId = ""
    private var todayRecords: List<PostureRecord> = emptyList()
    private var weekRecords: List<PostureRecord> = emptyList()

    private val confidenceThreshold = 0.7

    private var realtimeJob: Job? = null


    private val device by lazy {
        arguments?.getSerializable("device", UserDevice::class.java)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)

        binding.swipeRefresh.setOnRefreshListener {
            initControl()
        }

        initControl()
        return binding.root
    }

    private fun initControl() {
        binding.swipeRefresh.isRefreshing = false
        binding.nestedScroll.setOnScrollChangeListener { _, _, scrollY, _, _ ->
            binding.swipeRefresh.isEnabled = scrollY == 0
        }

        userId = device?.userId ?: return
        listenRecord(userId)
        getTodayRecords(userId)
        getWeekRecords(userId)

        binding.barChartWeek.setOnChartValueSelectedListener(
            object : OnChartValueSelectedListener {

                override fun onValueSelected(
                    e: Entry?,
                    h: Highlight?
                ) {
                    requireActivity()
                        .findViewById<ViewPager2>(R.id.viewpager2)
                        .currentItem = 2
                }

                override fun onNothingSelected() {}
            }
        )
    }

    private fun getWeekRecords(userId: String) {

        val days = getLast7Days()

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                weekRecords =
                    StatisticPosetureRecord
                        .getRecordsByLocalDates(userId, days)

                setUpBarChartWeek(weekRecords)
                updateStreak(weekRecords)

            } catch (e: Exception) {
                Log.e("getWeekRecords", e.message ?: "Unknown error")
            }
        }
    }

    private fun listenRecord(userId: String) {
        val channel = SupabaseProvider.client.realtime.channel("posture_records")

        realtimeJob = lifecycleScope.launch {
            channel.subscribe()
            channel
                .postgresChangeFlow<PostgresAction>(
                    schema = "public"
                ) {
                    table = "posture_records"
                }
                .collect {
                    getTodayRecords(userId)
                }

        }
    }

    private fun getTodayRecords(userId: String) {

        val today = LocalDate.now()

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                todayRecords =
                    StatisticPosetureRecord
                        .getRecordsByLocalDate(userId, today)
                Log.e("getTodayRecords", todayRecords.toString())
                setUpPieChartScore(todayRecords)
                setUpGoalStreak(todayRecords)

            } catch (e: Exception) {
                Log.e("getTodayRecords", e.message ?: "Unknown error")
            }
        }
    }


    private fun setUpGoalStreak(todayRecords: List<PostureRecord>) {
        val duration = calculatePostureDurationDesc(todayRecords)

        val goalMs = requireContext().getSharedPreferences("MyPrefs", MODE_PRIVATE).getLong("goal_ms", 0)

        if (goalMs == 0L) {
            binding.layoutStreak.visibility = View.GONE
            return
        }

        val goalDuration = formatDuration(goalMs)

        binding.txtCurrentDuration.text = formatDuration(duration.goodMs)
        binding.txtGoalDuration.text = goalDuration
        binding.txtGoalPercent.text =
            "${((duration.goodMs * 100f) / goalMs).toInt()}%"

        val progressRatio = min(
            duration.goodMs.toFloat() / goalMs,
            1f
        )

        val progressPercent = (progressRatio * 100).toInt()

        binding.progressGoal.apply {
            isIndeterminate = false
            setProgress(progressPercent, true) // animate
        }

        val context = requireContext()
        binding.progressGoal.setIndicatorColor(
            when {
                progressPercent >= 75 ->
                    context.getColor(R.color.purple)
                progressPercent >= 50 ->
                    context.getColor(R.color.yellow)
                else ->
                    context.getColor(R.color.red)
            }
        )
    }

    private fun updateStreak(weekRecords: List<PostureRecord>) {
        val prefs = requireContext()
            .getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)

        if (prefs.getLong("goal_ms", 0).toInt() == 0) {
            binding.layout1.visibility = View.GONE
            return
        }

        val today = LocalDate.now()
        val yesterday = today.minusDays(1)

        val lastProcessedDate = prefs
            .getString("last_success_date", null)
            ?.let { LocalDate.parse(it) }

        // Đã xử lý streak cho hôm qua rồi
        if (lastProcessedDate == yesterday) {
            val streak = prefs.getInt("current_streak", 0)
            binding.txtStreak.text = "$streak"
            return
        }

        val yesterdayRecords = weekRecords.filter { record ->
            record.createdAtLocal().toLocalDate() == yesterday
        }

        if (yesterdayRecords.isEmpty()) {
            // Không có dữ liệu → không tăng streak
            val streak = prefs.getInt("current_streak", 0)
            binding.txtStreak.text = "$streak"
            return
        }

        val yesterdayDuration =
            calculatePostureDurationDesc(yesterdayRecords)

        val yesterdayGoodMs = yesterdayDuration.goodMs
        val goalMs = requireContext().getSharedPreferences("MyPrefs", MODE_PRIVATE).getLong("goal_ms", 0)


        val currentStreak = prefs.getInt("current_streak", 0)

        val newStreak = if (yesterdayGoodMs >= goalMs) {
            when {
                lastProcessedDate == null -> 1
                lastProcessedDate.plusDays(1) == yesterday ->
                    currentStreak + 1
                else -> 1 // đứt streak
            }
        } else {
            // Không đạt goal → reset streak
            0
        }

        prefs.edit {
            putInt("current_streak", newStreak)
                .putString("last_success_date", yesterday.toString())
        }

        binding.txtStreak.text = "$newStreak"
    }

    private fun setUpBarChartWeek(weekRecords: List<PostureRecord>) {
        setCompareText(weekRecords)

        val barChart = binding.barChartWeek

        val recordsByDay = groupRecordsByDay(weekRecords)
        val dailyPercents = calculateDailyGoodPercent(recordsByDay)
        Log.e("dailyPercents", dailyPercents.toString())


        // ===== Bar entries =====
        val entries = dailyPercents.mapIndexed { index, pair ->
            BarEntry(index.toFloat(), pair.second)
        }

        val today = LocalDate.now()
        val todayIndex = dailyPercents.indexOfFirst { it.first == today }

        val dataSet = BarDataSet(entries, "").apply {
            colors = entries.mapIndexed { index, _ ->
                if (index == todayIndex) {
                    requireContext().getColor(R.color.purple)
                } else {
                    requireContext().getColor(R.color.dark_blue)
                }
            }
            setDrawValues(false)
        }

        barChart.data = BarData(dataSet).apply {
            barWidth = 0.6f
        }

        // ===== Chart config =====
        barChart.apply {
            description.isEnabled = false
            legend.isEnabled = false
            setTouchEnabled(false)
            setScaleEnabled(false)
            animateY(800, Easing.EaseInOutQuad)
        }

        val labels = dailyPercents.map { (date, _) ->
            date.dayOfWeek.name.take(3) // MON, TUE, ...
        }

        barChart.xAxis.apply {
            position = XAxis.XAxisPosition.BOTTOM
            granularity = 1f
            setDrawGridLines(false)
            setDrawAxisLine(false)
            textSize = 12f

            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    val index = value.toInt()
                    return labels.getOrNull(index) ?: ""
                }
            }

            textColor = requireContext().getColor(R.color.dark_blue)
        }

        // ===== Y Axis =====
        barChart.axisLeft.apply {
            axisMinimum = 0f
            axisMaximum = 100f
            granularity = 25f
            setDrawGridLines(true)
            textColor = requireContext().getColor(R.color.dark_blue)
        }

        barChart.axisRight.isEnabled = false

        barChart.invalidate()
    }

    private fun groupRecordsByDay(
        records: List<PostureRecord>
    ): Map<LocalDate, List<PostureRecord>> {
        return records.groupBy { record ->
            record.createdAtLocal().toLocalDate()
        }
    }

    private fun getLast7Days(): List<LocalDate> {
        val today = LocalDate.now()
        return (0..6).map { today.minusDays(it.toLong()) }.reversed()
    }

    private fun calculateDailyGoodPercent(
        recordsByDay: Map<LocalDate, List<PostureRecord>>
    ): List<Pair<LocalDate, Float>> {

        val days = getLast7Days()

        return days.map { day ->
            val records = recordsByDay[day].orEmpty()
            val percent = if (records.isNotEmpty()) {
                calculateGoodPercentage(
                    calculatePostureDurationDesc(records)
                )
            } else {
                0f
            }
            day to percent
        }
    }

    private fun setCompareText(weekRecords: List<PostureRecord>) {
        val (todayRecords, yesterdayRecords) = splitRecordsByDay(weekRecords)

        if (todayRecords.isEmpty() || yesterdayRecords.isEmpty()) {
            binding.txtCompare.text = "Chưa đủ dữ liệu để so sánh"
            return
        }

        val todayPercent = calculateGoodPercentForDay(todayRecords)
        val yesterdayPercent = calculateGoodPercentForDay(yesterdayRecords)

        val diff = todayPercent - yesterdayPercent
        val diffAbs = kotlin.math.abs(diff)

        when {
            diff > 0 -> {
                binding.txtCompare.text =
                    "Tốt hơn ${diffAbs.toInt()}% so với hôm qua"
                binding.txtCompare.setTextColor(
                    requireContext().getColor(R.color.purple)
                )
            }

            diff < 0 -> {
                binding.txtCompare.text =
                    "Cần cải thiện ${diffAbs.toInt()}% so với hôm qua"
            }

            else -> {
                binding.txtCompare.text =
                    "Không thay đổi so với hôm qua"
            }
        }
    }

    private fun calculateGoodPercentForDay(
        records: List<PostureRecord>
    ): Float {
        if (records.isEmpty()) return 0f

        val duration = calculatePostureDurationDesc(records)
        return calculateGoodPercentage(duration)
    }

    private fun splitRecordsByDay(
        records: List<PostureRecord>
    ): Pair<List<PostureRecord>, List<PostureRecord>> {

        val today = LocalDate.now()
        val yesterday = today.minusDays(1)

        val todayRecords = records.filter { record ->
            record.createdAtLocal().toLocalDate() == today
        }

        val yesterdayRecords = records.filter { record ->
            runCatching {
                OffsetDateTime.parse(record.created_at).toLocalDate()
            }.getOrNull() == yesterday
        }

        return Pair(todayRecords, yesterdayRecords)
    }

    private fun setUpPieChartScore(todayRecords: List<PostureRecord>) {
        val duration = calculatePostureDurationDesc(todayRecords)
        Log.e("duration", duration.toString())
        val goodPercent = calculateGoodPercentage(duration)

        val pieChart = binding.pieChartScore
        binding.txtGoodDuration.text = formatDuration(duration.goodMs)
        binding.txtWarningDuration.text = formatDuration(duration.otherMs)
        binding.txtBadDuration.text = formatDuration(duration.badMs)

        Log.e("goodDuration", duration.goodMs.toString())
        Log.e("totalDuration", (duration.goodMs + duration.badMs + duration.otherMs).toString())

        pieChart.apply {
            description.isEnabled = false
            legend.isEnabled = false

            isDrawHoleEnabled = true
            holeRadius = 75f
            transparentCircleRadius = 0f

            setDrawEntryLabels(false)
            isRotationEnabled = false
            setTouchEnabled(false)

            centerText = "${goodPercent.toInt()}%"
            setCenterTextSize(18f)
            setCenterTextTypeface(Typeface.DEFAULT_BOLD)
        }

        if (todayRecords.isEmpty()) {
            pieChart.centerText = "Chưa có dữ liệu"
        }

        pieChart.setCenterTextColor(
            when {
                goodPercent >= 75 -> requireContext().getColor(R.color.purple)
                goodPercent >= 50 -> requireContext().getColor(R.color.yellow)
                else -> requireContext().getColor(R.color.red)
            }
        )

        val entries = listOf(
            PieEntry(goodPercent),
            PieEntry(100f - goodPercent)
        )

        val dataSet = PieDataSet(entries, "").apply {
            colors = listOf(
                requireContext().getColor(R.color.purple),
                Color.TRANSPARENT
            )
            setDrawValues(false)
        }

        pieChart.data = PieData(dataSet)
        pieChart.animateY(900, Easing.EaseInOutQuad)
        pieChart.invalidate()
    }

    private fun calculatePostureDurationDesc(
        records: List<PostureRecord>
    ): PostureDuration {

        if (records.isEmpty()) {
            return PostureDuration(0, 0, 0)
        }

        val MAX_GAP_MS = 60_000L

        var good = 0L
        var bad = 0L
        var other = 0L

        val now = OffsetDateTime.now()

        // record mới nhất → hiện tại
        val first = records.first()

        val firstTime = runCatching {
            OffsetDateTime.parse(first.created_at)
        }.getOrNull()

        if (firstTime != null && (first.confidence ?: 0.0) >= confidenceThreshold) {

            val raw = Duration.between(firstTime, now).toMillis()
            val duration = min(raw, MAX_GAP_MS)

            when (first.posture_type.lowercase()) {
                "good" -> good += duration
                "bad" -> bad += duration
                else -> other += duration
            }
        }

        // các record tiếp theo
        for (i in 0 until records.size - 1) {

            val current = records[i]
            val next = records[i + 1]

            val confidence = current.confidence ?: 0.0
            if (confidence < confidenceThreshold) continue

            val currentTime = runCatching {
                OffsetDateTime.parse(current.created_at)
            }.getOrNull()

            val nextTime = runCatching {
                OffsetDateTime.parse(next.created_at)
            }.getOrNull()

            if (currentTime == null || nextTime == null) continue

            val raw = Duration
                .between(nextTime, currentTime)
                .toMillis()

            if (raw > MAX_GAP_MS) continue

            when (current.posture_type.lowercase()) {
                "good" -> good += raw
                "bad" -> bad += raw
                else -> other += raw
            }
        }

        return PostureDuration(good, bad, other)
    }

    private fun calculateGoodPercentage(duration: PostureDuration): Float {
        val total = duration.goodMs + duration.badMs + duration.otherMs
        if (total == 0L) return 0f
        return duration.goodMs * 100f / total
    }

    private fun formatDuration(ms: Long): String {
        if (ms <= 0) return "0h0m"
        Log.e("time", "$ms")
        val totalMinutes = ms / 1000 / 60
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        Log.e("time", "${hours}h${minutes}m")
        return "${hours}h${minutes}m"
    }

    override fun onDestroy() {
        super.onDestroy()
        realtimeJob?.cancel()
    }

}