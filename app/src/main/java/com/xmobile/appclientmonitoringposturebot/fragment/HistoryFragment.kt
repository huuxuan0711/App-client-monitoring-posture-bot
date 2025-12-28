package com.xmobile.appclientmonitoringposturebot.fragment

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.github.mikephil.charting.animation.Easing
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.listener.OnChartValueSelectedListener
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.kizitonwose.calendar.core.CalendarDay
import com.kizitonwose.calendar.core.DayPosition
import com.kizitonwose.calendar.view.MonthDayBinder
import com.kizitonwose.calendar.view.ViewContainer
import com.xmobile.appclientmonitoringposturebot.R
import com.xmobile.appclientmonitoringposturebot.databinding.FragmentHistoryBinding
import com.xmobile.appclientmonitoringposturebot.model.HourStat
import com.xmobile.appclientmonitoringposturebot.model.MonthTrend
import com.xmobile.appclientmonitoringposturebot.model.PostureDuration
import com.xmobile.appclientmonitoringposturebot.model.PostureRecord
import com.xmobile.appclientmonitoringposturebot.model.PostureState
import com.xmobile.appclientmonitoringposturebot.model.UserDevice
import com.xmobile.appclientmonitoringposturebot.model.WeekKey
import com.xmobile.appclientmonitoringposturebot.util.StatisticPosetureRecord
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
class HistoryFragment : Fragment() {
    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!

    private var todayRecords: List<PostureRecord> = emptyList()
    private var weekRecords: List<PostureRecord> = emptyList()
    private var monthRecords: List<PostureRecord> = emptyList()

    private var currentTab: Int = 0
    private var currentFromTime: OffsetDateTime? = null
    private var currentToTime: OffsetDateTime? = null
    private val confidenceThreshold = 0.7

    private var lastHighlight: Highlight? = null

    private val device by lazy {
        arguments?.getSerializable("device", UserDevice::class.java)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentHistoryBinding.inflate(inflater, container, false)
        initControl()
        return binding.root
    }

    private fun initControl() {
        val userId = device?.userId ?: return

        binding.txtDay.setOnClickListener {
            updateTab(0)
            binding.barChart.visibility = View.VISIBLE
            binding.calendar.visibility = View.GONE
            binding.layoutSummaryDay.visibility = View.VISIBLE
            binding.layoutSummaryWeek.visibility = View.GONE
            binding.layoutSummaryMonth.visibility = View.GONE
            getTodayRecords(userId, OffsetDateTime.now().minusDays(1), OffsetDateTime.now())
            updateScrollTime()
        }

        binding.txtWeek.setOnClickListener {
            updateTab(1)
            binding.barChart.visibility = View.VISIBLE
            binding.calendar.visibility = View.GONE
            binding.layoutSummaryDay.visibility = View.GONE
            binding.layoutSummaryWeek.visibility = View.VISIBLE
            binding.layoutSummaryMonth.visibility = View.GONE
            getWeekRecords(userId, OffsetDateTime.now().minusDays(7), OffsetDateTime.now())
            updateScrollTime()
            binding.barChart.highlightValues(null)
            lastHighlight = null
        }

        binding.txtMonth.setOnClickListener {
            updateTab(2)
            binding.barChart.visibility = View.GONE
            binding.calendar.visibility = View.VISIBLE
            binding.layoutSummaryDay.visibility = View.GONE
            binding.layoutSummaryWeek.visibility = View.GONE
            binding.layoutSummaryMonth.visibility = View.VISIBLE
            val month = YearMonth.from(currentToTime)
            val zone = ZoneOffset.UTC

            val from: OffsetDateTime =
                month.atDay(1)
                    .atStartOfDay()
                    .atOffset(zone)

            val to: OffsetDateTime =
                month.plusMonths(1)
                    .atDay(1)
                    .atStartOfDay()
                    .atOffset(zone)

            getMonthRecords(userId, from, to)
            updateScrollTime()
        }

        binding.txtDay.performClick()

        binding.imgBack.setOnClickListener {
            when (currentTab) {
                0 -> {
                    getTodayRecords(userId, currentFromTime?.minusDays(1) ?: OffsetDateTime.now(),
                        currentToTime?.minusDays(1) ?: OffsetDateTime.now()
                    )
                }
                1 -> {
                    getWeekRecords(userId, currentFromTime?.minusDays(7) ?: OffsetDateTime.now(),
                        currentToTime?.minusDays(7) ?: OffsetDateTime.now())
                }
                2 -> {
                    getMonthRecords(userId, currentFromTime?.minusMonths(1) ?: OffsetDateTime.now(),
                        currentToTime?.minusMonths(1) ?: OffsetDateTime.now())
                }
            }
            updateScrollTime()
        }

        binding.imgNext.setOnClickListener {
            when (currentTab) {
                0 -> {
                    getTodayRecords(userId, currentFromTime?.plusDays(1) ?: OffsetDateTime.now(),
                        currentToTime?.plusDays(1) ?: OffsetDateTime.now()
                    )
                }
                1 -> {
                    getWeekRecords(userId, currentFromTime?.plusDays(7) ?: OffsetDateTime.now(),
                        currentToTime?.plusDays(7) ?: OffsetDateTime.now())
                }
                2 -> {
                    getMonthRecords(userId, currentFromTime?.plusMonths(1) ?: OffsetDateTime.now(),
                        currentToTime?.plusMonths(1) ?: OffsetDateTime.now())
                }
            }
            updateScrollTime()
        }

        binding.barChart.setOnChartValueSelectedListener(
            object : OnChartValueSelectedListener {

                override fun onValueSelected(
                    e: Entry?,
                    h: Highlight?
                ) {
                    if (currentTab == 1 && e != null) {

                        val dayIndex = e.x.toInt().coerceIn(0, 6)

                        // Lấy Monday của tuần hiện tại
                        val today = LocalDate.now()
                        val mondayOfWeek =
                            today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

                        val selectedDate =
                            mondayOfWeek.plusDays(dayIndex.toLong())

                        val zoneId = ZoneId.systemDefault()

                        val fromTime: OffsetDateTime =
                            selectedDate
                                .atStartOfDay(zoneId)
                                .toOffsetDateTime()

                        val toTime: OffsetDateTime =
                            selectedDate
                                .plusDays(1)
                                .atStartOfDay(zoneId)
                                .toOffsetDateTime()

                        getTodayRecords(
                            device?.userId ?: "",
                            fromTime,
                            toTime
                        )

                        currentTab = 0
                        updateTab(0)
                        updateScrollTime()
                        binding.barChart.visibility = View.VISIBLE
                        binding.calendar.visibility = View.GONE
                    } else if (currentTab == 0 && e != null) {
                        if (h == null) return

                        if (lastHighlight != null &&
                            lastHighlight!!.x == h.x &&
                            lastHighlight!!.dataSetIndex == h.dataSetIndex
                        ) {
                            binding.barChart.highlightValues(null)
                            lastHighlight = null

                            binding.layoutSession.visibility = View.GONE
                            return
                        }

                        // Click bar mới
                        lastHighlight = h
                        binding.barChart.highlightValue(h)

                        val hourIndex = h.x.toInt()
                        showSessionForHour(hourIndex)
                    }
                }

                override fun onNothingSelected() {
                    lastHighlight = null
                    binding.layoutSession.visibility = View.GONE
                }
            }
        )

    }

    private fun updateScrollTime() {

        val now = OffsetDateTime.now()
        val today = now.toLocalDate()

        val toDate = currentToTime?.toLocalDate()
        val toMonth = currentToTime?.let { YearMonth.from(it) }

        when (currentTab) {

            // ================= DAY =================
            0 -> {
                when {
                    toDate == today -> {
                        disableNext()
                        binding.txtDuration.text = "Hôm nay"
                    }

                    toDate == today.minusDays(1) -> {
                        enableNext()
                        binding.txtDuration.text = "Hôm qua"
                    }

                    else -> {
                        enableNext()
                        val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
                        binding.txtDuration.text = currentToTime?.format(formatter)
                    }
                }
            }

            // ================= WEEK =================
            1 -> {
                val currentWeekStart = today.with(DayOfWeek.MONDAY)
                val toWeekStart = toDate?.with(DayOfWeek.MONDAY)

                when {
                    toWeekStart == currentWeekStart -> {
                        disableNext()
                        binding.txtDuration.text = "Tuần này"
                    }

                    toWeekStart == currentWeekStart.minusWeeks(1) -> {
                        enableNext()
                        binding.txtDuration.text = "Tuần trước"
                    }

                    else -> {
                        enableNext()
                        val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
                        binding.txtDuration.text =
                            "${currentFromTime?.format(formatter)} - ${currentToTime?.format(formatter)}"
                    }
                }
            }

            // ================= MONTH =================
            2 -> {
                val displayMonth = YearMonth.from(currentFromTime)
                val thisMonth = YearMonth.now()

                when (displayMonth) {
                    thisMonth -> {
                        disableNext()
                        binding.txtDuration.text = "Tháng này"
                    }
                    thisMonth.minusMonths(1) -> {
                        enableNext()
                        binding.txtDuration.text = "Tháng trước"
                    }
                    else -> {
                        enableNext()
                        val formatter = DateTimeFormatter.ofPattern("MM/yyyy")
                        binding.txtDuration.text = displayMonth.format(formatter)
                    }
                }
            }
        }
    }

    private fun disableNext() {
        binding.imgNext.setColorFilter(
            ContextCompat.getColor(requireContext(), R.color.gray)
        )
        binding.imgNext.isEnabled = false
    }

    private fun enableNext() {
        binding.imgNext.setColorFilter(
            ContextCompat.getColor(requireContext(), R.color.purple)
        )
        binding.imgNext.isEnabled = true
    }



    private fun getMonthRecords(userId: String, fromTime: OffsetDateTime, toTime: OffsetDateTime){
        currentFromTime = fromTime
        currentToTime = toTime

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                monthRecords = StatisticPosetureRecord.getPostureRecordsByTimeRange(userId, fromTime, toTime)
                setUpCalendar(monthRecords)
                setUpMonthSummary(monthRecords, userId, fromTime, toTime)
            }catch (e: Exception) {
                Log.e("getDevices", e.message ?: "Unknown error")
            }
        }
    }

    private fun getWeekRecords(userId: String, fromTime: OffsetDateTime, toTime: OffsetDateTime){
        currentFromTime = fromTime
        currentToTime = toTime

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                weekRecords = StatisticPosetureRecord.getPostureRecordsByTimeRange(userId, fromTime, toTime)
                setUpBarChart(weekRecords, false)
                setUpWeekSummary(weekRecords)
            }catch (e: Exception) {
                Log.e("getDevices", e.message ?: "Unknown error")
            }
        }
    }

    private fun getTodayRecords(userId: String, fromTime: OffsetDateTime, toTime: OffsetDateTime) {
        currentFromTime = fromTime
        currentToTime = toTime

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                todayRecords = StatisticPosetureRecord.getPostureRecordsByTimeRange(userId, fromTime, toTime)
                setUpBarChart(todayRecords, true)
                setUpDaySummary(todayRecords)
            }catch (e: Exception) {
                Log.e("getDevices", e.message ?: "Unknown error")
            }
        }
    }

    private fun setUpMonthSummary(monthRecords: List<PostureRecord>, userId: String, fromTime: OffsetDateTime, toTime: OffsetDateTime) {
        val duration = calculatePostureDurationDesc(monthRecords)
        binding.txtTotalGoodDuration.text = formatDuration(duration.goodMs)
        binding.txtTotalBadDuration.text = formatDuration(duration.badMs)

        val bestWeek = calculateBestWeek(monthRecords)

        binding.txtBestWeek.text = bestWeek?.let {
            "Tuần ${it.week}"
        } ?: "--"

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val lastMonthRecords = StatisticPosetureRecord.getPostureRecordsByTimeRange(userId, fromTime, toTime)
                val trend = calculateMonthTrend(
                    currentMonthRecords = monthRecords,
                    lastMonthRecords = lastMonthRecords
                )

                binding.txtCompare.text = when {
                    trend == null -> "--"
                    trend.isBetter ->
                        "Tốt hơn ${trend.diffPercent}% so với tháng trước"
                    else ->
                        "Kém hơn ${trend.diffPercent}% so với tháng trước"
                }
            }catch (e: Exception) {
                Log.e("getDevices", e.message ?: "Unknown error")
            }
        }
    }

    private fun calculateMonthTrend(
        currentMonthRecords: List<PostureRecord>,
        lastMonthRecords: List<PostureRecord>
    ): MonthTrend? {

        if (currentMonthRecords.isEmpty() || lastMonthRecords.isEmpty()) {
            return null
        }

        val currentPercent = calculateMonthGoodPercent(currentMonthRecords)
        val lastPercent = calculateMonthGoodPercent(lastMonthRecords)

        if (lastPercent == 0f) return null

        val diff = ((currentPercent - lastPercent) / lastPercent * 100)
            .toInt()

        return MonthTrend(
            diffPercent = kotlin.math.abs(diff),
            isBetter = diff >= 0
        )
    }

    fun calculateMonthGoodPercent(
        monthRecords: List<PostureRecord>
    ): Float {

        if (monthRecords.isEmpty()) return 0f

        val recordsByDay = groupRecordsByDate(monthRecords)
        val dailyPercents = calculateDailyGoodPercent(recordsByDay)
        // List<Pair<LocalDate, Float>>

        if (dailyPercents.isEmpty()) return 0f

        return dailyPercents.map { it.second }.average().toFloat()
    }

    private fun getWeekKey(date: LocalDate): WeekKey {
        val weekFields = WeekFields.ISO
        return WeekKey(
            year = date.get(weekFields.weekBasedYear()),
            week = date.get(weekFields.weekOfWeekBasedYear())
        )
    }

    private fun groupRecordsByWeek(
        records: List<PostureRecord>
    ): Map<WeekKey, List<PostureRecord>> {

        return records.groupBy { record ->
            val date = OffsetDateTime
                .parse(record.created_at)
                .toLocalDate()

            getWeekKey(date)
        }
    }


    fun calculateWeekGoodPercent(
        weekRecords: List<PostureRecord>
    ): Float {

        val recordsByDay = groupRecordsByDate(weekRecords)
        val dailyPercents = calculateDailyGoodPercent(recordsByDay)
        // List<Pair<LocalDate, Float>>

        if (dailyPercents.isEmpty()) return 0f

        return dailyPercents.map { it.second }.average().toFloat()
    }

    fun calculateBestWeek(monthRecords: List<PostureRecord>): WeekKey? {

        if (monthRecords.isEmpty()) return null

        val recordsByWeek = groupRecordsByWeek(monthRecords)

        return recordsByWeek
            .mapValues { (_, records) ->
                calculateWeekGoodPercent(records)
            }
            .maxByOrNull { it.value }
            ?.key
    }


    private fun setUpDaySummary(todayRecords: List<PostureRecord>) {
        val duration = calculatePostureDurationDesc(todayRecords)
        binding.txtGoodDurationDay.text = formatDuration(duration.goodMs)
        binding.txtBadDurationDay.text = formatDuration(duration.badMs)
    }

    private fun setUpWeekSummary(weekRecords: List<PostureRecord>) {
        val recordsByDay = groupRecordsByDate(weekRecords)
        val dailyPercents = calculateDailyGoodPercent(recordsByDay)

        var totalGoodMs = 0L
        var dayCount = 0

        recordsByDay.forEach { (_, records) ->
            val goodMs = calculateGoodMs(records)
            if (goodMs > 0) {
                totalGoodMs += goodMs
                dayCount++
            }
        }

        val avgGoodMs = if (dayCount > 0) totalGoodMs / dayCount else 0L
        binding.avgGoodDay.text = formatDuration(avgGoodMs)

        val bestDay = dailyPercents.maxByOrNull { it.second }?.first
        val worstDay = dailyPercents.minByOrNull { it.second }?.first

        binding.bestDay.text = bestDay?.let { formatDayShort(it) } ?: "--"
        binding.worstDay.text = worstDay?.let { formatDayShort(it) } ?: "--"
    }

    private fun formatDayShort(date: LocalDate): String {
        return date.dayOfWeek.name.take(3)
    }

    private fun calculateGoodMs(records: List<PostureRecord>): Long {
        if (records.isEmpty()) return 0L

        var goodMs = 0L

        for (i in 0 until records.lastIndex) {
            val current = records[i]
            val next = records[i + 1]

            val currentTime = OffsetDateTime.parse(current.created_at)
            val nextTime = OffsetDateTime.parse(next.created_at)

            val durationMs = Duration.between(currentTime, nextTime).toMillis()

            if (current.posture_type == "GOOD") {
                goodMs += durationMs
            }
        }
        return goodMs
    }


    private fun formatDuration(ms: Long): String {
        if (ms <= 0) return "0h0m"

        val totalMinutes = ms / 1000 / 60
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60

        return "${hours}h${minutes}m"
    }

    private fun showSessionForHour(hourIndex: Int) {
        binding.layoutSession.visibility = View.VISIBLE

        val hours = calculateHourlyStats(todayRecords)
        val hourStat = hours.getOrNull(hourIndex) ?: return

        if (hourStat.totalMs == 0L) {
            binding.layoutSession.visibility = View.GONE
            return
        }

        val goodMinutes = (hourStat.goodMs / 60_000).toInt()
        val badMinutes  = (hourStat.badMs / 60_000).toInt()

        val segments = buildTimelineSegments(
            goodMinutes = goodMinutes,
            badMinutes = badMinutes
        )

        renderPostureTimeline(binding.timelineContainer, segments)

        binding.txtDuration.text = formatHourRange(hourIndex)
        binding.goodDuration.text = goodMinutes.toString() + "m"
        binding.badDuration.text = badMinutes.toString() + "m"

        binding.txtJudgement.text = if (goodMinutes > badMinutes) {
            "Tốt"
        } else {
            "Cần cải thiện"
        }
    }

    @SuppressLint("DefaultLocale")
    private fun formatHourRange(hourIndex: Int): String {
        val startHour = hourIndex.coerceIn(0, 23)
        val endHour = (startHour + 1) % 24

        return String.format(
            "%02d:00 - %02d:00",
            startHour,
            endHour
        )
    }

    private fun buildTimelineSegments(
        goodMinutes: Int,
        badMinutes: Int,
        totalSegments: Int = 8
    ): List<PostureState> {

        val totalMinutes = goodMinutes + badMinutes
        if (totalMinutes == 0) return emptyList()

        val segments = mutableListOf<PostureState>()

        repeat(totalSegments) { index ->
            val segmentStart = index * totalMinutes / totalSegments
            val segmentEnd = (index + 1) * totalMinutes / totalSegments

            val goodUntil = goodMinutes

            segments.add(
                if (segmentStart < goodUntil) PostureState.GOOD
                else PostureState.BAD
            )
        }
        return segments
    }

    private fun renderPostureTimeline(
        container: LinearLayout,
        segments: List<PostureState>
    ) {
        container.removeAllViews()

        if (segments.isEmpty()) return

        segments.forEach { state ->
            val segment = View(container.context)

            val params = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.MATCH_PARENT,
                1f
            )
            params.marginEnd = 2.dp(container.context) // khoảng cách giữa các segment
            segment.layoutParams = params

            segment.background = GradientDrawable().apply {
                cornerRadius = 4.dp(container.context).toFloat()
                setColor(
                    when (state) {
                        PostureState.GOOD -> Color.parseColor("#4CAF50")
                        PostureState.BAD  -> Color.parseColor("#F44336")
                        PostureState.IDLE -> Color.parseColor("#BDBDBD")
                    }
                )
            }

            container.addView(segment)
        }
    }

    private fun Int.dp(context: Context): Int =
        (this * context.resources.displayMetrics.density).toInt()


    private fun setUpCalendar(records: List<PostureRecord>) {
        val calendarView = binding.calendar

        val recordsByDate = groupRecordsByDate(records)

        val displayMonth: YearMonth = YearMonth.from(currentFromTime)
        val today = LocalDate.now()

        // ===== Setup calendar =====
        calendarView.setup(
            displayMonth,
            displayMonth,
            DayOfWeek.MONDAY
        )
        calendarView.scrollToMonth(displayMonth)

        // ===== Day binder =====
        calendarView.dayBinder =
            object : MonthDayBinder<DayViewContainer> {
                override fun create(view: View) =
                    DayViewContainer(view)

                override fun bind(
                    container: DayViewContainer,
                    data: CalendarDay
                ) {
                    val date = data.date
                    container.txtDay.text = date.dayOfMonth.toString()

                    val dayRecords = recordsByDate[date]

                    if (data.position != DayPosition.MonthDate){
                        container.progressRing.visibility = View.GONE
                        container.txtDay.alpha = 0.3f
                        container.view.isEnabled = false
                    }

                    if (dayRecords.isNullOrEmpty()) {
                        // ---- No data ----
                        container.progressRing.progress = 0
                        container.progressRing.setIndicatorColor(
                            requireContext().getColor(R.color.gray)
                        )
                    } else {
                        // ---- Has data ----
                        val percent =
                            calculateGoodPercentForDay(dayRecords)
                                .toInt()

                        container.progressRing.setProgress(percent, false)

                        container.progressRing.setIndicatorColor(
                            when {
                                percent >= 80 ->
                                    requireContext().getColor(R.color.purple)
                                percent >= 60 ->
                                    requireContext().getColor(R.color.blue)
                                percent >= 40 ->
                                    requireContext().getColor(R.color.yellow)
                                else ->
                                    requireContext().getColor(R.color.gray)
                            }
                        )

                        container.txtDay.alpha = 1f
                    }

                    // ---- Highlight today ----
                    if (date == today) {
                        container.txtDay.setTextColor(
                            requireContext().getColor(R.color.purple)
                        )
                    } else {
                        container.txtDay.setTextColor(
                            requireContext().getColor(R.color.dark_blue)
                        )
                    }

                    // ---- Click day ----
                    container.view.setOnClickListener {
                        Log.e("click", "click")
                        if (!dayRecords.isNullOrEmpty()) {
                            val fromTime: OffsetDateTime =
                                data.date.toStartOfDayOffset()

                            val toTime: OffsetDateTime =
                                data.date.toNextDayStartOffset()

                            getTodayRecords(
                                device?.userId ?: "",
                                fromTime,
                                toTime
                            )
                            currentTab = 0
                            updateTab(0)
                            updateScrollTime()
                            binding.barChart.visibility = View.VISIBLE
                            binding.calendar.visibility = View.GONE
                        }
                    }

                }
            }
    }

    private fun LocalDate.toStartOfDayOffset(): OffsetDateTime {
        return this
            .atStartOfDay(ZoneId.systemDefault())
            .toOffsetDateTime()
    }

    private fun LocalDate.toNextDayStartOffset(): OffsetDateTime {
        return this
            .plusDays(1)
            .atStartOfDay(ZoneId.systemDefault())
            .toOffsetDateTime()
    }


    class DayViewContainer(view: View) : ViewContainer(view) {
        val progressRing: CircularProgressIndicator =
            view.findViewById(R.id.progressRing)
        val txtDay: TextView =
            view.findViewById(R.id.dayText)
    }

    private fun groupRecordsByDate(
        records: List<PostureRecord>
    ): Map<LocalDate, List<PostureRecord>> {

        return records.groupBy { record ->
            OffsetDateTime
                .parse(record.created_at)
                .toLocalDate()
        }
    }

    private fun calculateGoodPercentForDay(
        records: List<PostureRecord>
    ): Float {
        if (records.isEmpty()) return 0f

        val duration = calculatePostureDurationDesc(records)
        return calculateGoodPercentage(duration)
    }

    private fun calculateHourlyStats(
        todayRecords: List<PostureRecord>
    ): Array<HourStat> {

        val hours = Array(24) { HourStat() }
        if (todayRecords.isEmpty()) return hours

        val now = OffsetDateTime.now()

        for (i in todayRecords.indices) {

            val current = todayRecords[i]

            val start = runCatching {
                OffsetDateTime.parse(current.created_at)
            }.getOrNull() ?: continue

            val end = if (i == 0) {
                now
            } else {
                runCatching {
                    OffsetDateTime.parse(todayRecords[i - 1].created_at)
                }.getOrNull() ?: continue
            }

            if (end.isBefore(start)) continue

            var cursor = start

            while (cursor.isBefore(end)) {

                val hour = cursor.hour

                val nextHour = cursor
                    .withMinute(0)
                    .withSecond(0)
                    .withNano(0)
                    .plusHours(1)

                val segmentEnd =
                    if (nextHour.isBefore(end)) nextHour else end

                val durationMs = Duration
                    .between(cursor, segmentEnd)
                    .toMillis()

                hours[hour].totalMs += durationMs

                when (current.posture_type.lowercase()) {
                    "good" -> hours[hour].goodMs += durationMs
                    "bad" -> hours[hour].badMs += durationMs
                }

                cursor = segmentEnd
            }
        }

        return hours
    }


    private fun setUpBarChart(records: List<PostureRecord>, isDay: Boolean) {

        val barChart = binding.barChart

        barChart.clear()
        barChart.data = null
        barChart.highlightValues(null)
        barChart.xAxis.resetAxisMinimum()
        barChart.xAxis.resetAxisMaximum()
        barChart.notifyDataSetChanged()

        barChart.apply {
            description.isEnabled = false
            legend.isEnabled = false
            setTouchEnabled(false)
            setScaleEnabled(false)
        }

        if (isDay) {
            val hourlyPercents = calculateHourlyStats(todayRecords).map {
                if (it.totalMs == 0L) 0f
                else it.goodMs * 100f / it.totalMs
            }

            val entries = hourlyPercents.mapIndexed { hour, percent ->
                BarEntry(hour.toFloat(), percent)
            }

            val dataSet = BarDataSet(entries, "").apply {
                color = requireContext().getColor(R.color.purple)
                setDrawValues(false)
                highLightAlpha = 120
                highLightColor =
                    ContextCompat.getColor(requireContext(), R.color.purple)
            }

            barChart.data = BarData(dataSet).apply {
                barWidth = 0.8f
            }

            // ===== X AXIS DAY (0 → 23) =====
            barChart.xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                axisMinimum = -0.5f
                axisMaximum = 23.5f
                granularity = 1f
                setLabelCount(8, false)
                setDrawGridLines(false)
                setDrawAxisLine(false)
                setAvoidFirstLastClipping(true)

                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String {
                        return when (value.toInt()) {
                            0, 3, 6, 9, 12, 15, 18, 21 -> value.toInt().toString()
                            else -> ""
                        }
                    }
                }
                textColor =
                    requireContext().getColor(R.color.dark_blue)
                textSize = 12f
            }

            // ===== Y AXIS =====
            barChart.axisLeft.apply {
                axisMinimum = 0f
                axisMaximum = 100f
                granularity = 25f
                setDrawGridLines(true)
                textColor =
                    requireContext().getColor(R.color.dark_blue)
            }

            barChart.axisRight.isEnabled = false

            barChart.animateY(600)

        } else {
            val recordsByDay = groupRecordsByDate(records)
            val dailyPercents = calculateDailyGoodPercent(recordsByDay)

            val entries = dailyPercents.mapIndexed { index, pair ->
                BarEntry(index.toFloat(), pair.second)
            }

            val todayIndex =
                LocalDate.now().dayOfWeek.value - 1 // MON = 0

            val dataSet = BarDataSet(entries, "").apply {
                colors = entries.mapIndexed { index, _ ->
                    if (index == todayIndex)
                        requireContext().getColor(R.color.purple)
                    else
                        requireContext().getColor(R.color.dark_blue)
                }
                setDrawValues(false)
            }

            barChart.data = BarData(dataSet).apply {
                barWidth = 0.6f
            }

            // ===== X AXIS WEEK (MON → SUN) =====
            barChart.xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM

                axisMinimum = -0.5f
                axisMaximum = 6.5f
                granularity = 1f

                setCenterAxisLabels(true)
                setAvoidFirstLastClipping(true)
                setDrawGridLines(false)
                setDrawAxisLine(false)

                textSize = 12f
                textColor = requireContext().getColor(R.color.dark_blue)

                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String {
                        if (value % 1f != 0f) return ""
                        return when (value.toInt()) {
                            0 -> "MON"
                            1 -> "TUE"
                            2 -> "WED"
                            3 -> "THU"
                            4 -> "FRI"
                            5 -> "SAT"
                            6 -> "SUN"
                            else -> ""
                        }
                    }
                }
            }

            // ===== Y AXIS =====
            barChart.axisLeft.apply {
                axisMinimum = 0f
                axisMaximum = 100f
                granularity = 25f
                setDrawGridLines(true)
                textColor =
                    requireContext().getColor(R.color.dark_blue)
            }
            barChart.axisRight.isEnabled = false
            barChart.setExtraOffsets(
                -8f,  // left  (dịch trái)
                0f,   // top
                0f,   // right
                0f    // bottom
            )
            barChart.animateY(800, Easing.EaseInOutQuad)
        }

        barChart.invalidate()
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

    private fun calculatePostureDurationDesc(
        records: List<PostureRecord>
    ): PostureDuration {

        if (records.isEmpty()) {
            return PostureDuration(0, 0, 0)
        }

        var good = 0L
        var bad = 0L
        var other = 0L

        val now = OffsetDateTime.now()

        // ---------- record mới nhất → hiện tại ----------
        val first = records.first()

        val firstTime = runCatching {
            OffsetDateTime.parse(first.created_at)
        }.getOrNull()

        if (firstTime != null && (first.confidence ?: 0.0) >= confidenceThreshold) {

            val firstDuration = Duration
                .between(firstTime, now)
                .toMillis()

            when (first.posture_type.lowercase()) {
                "good" -> good += firstDuration
                "bad" -> bad += firstDuration
                else -> other += firstDuration
            }
        }

        // ---------- các record tiếp theo ----------
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

            val duration = Duration
                .between(nextTime, currentTime)
                .toMillis()

            when (current.posture_type.lowercase()) {
                "good" -> good += duration
                "bad" -> bad += duration
                else -> other += duration
            }
        }

        return PostureDuration(good, bad, other)
    }

    private fun calculateGoodPercentage(duration: PostureDuration): Float {
        val total = duration.goodMs + duration.badMs + duration.otherMs
        if (total == 0L) return 0f
        return duration.goodMs * 100f / total
    }

    private fun getLast7Days(): List<LocalDate> {
        val today = LocalDate.now()
        return (0..6).map { today.minusDays(it.toLong()) }.reversed()
    }

    private fun updateTab(selectedIndex: Int) {
        currentTab = selectedIndex
        val items = listOf(
            binding.txtDay,
            binding.txtWeek,
            binding.txtMonth,
        )

        items.forEachIndexed { index, imageView ->
            val isSelected = index == selectedIndex
            imageView.isSelected = isSelected

            imageView.animate()
                .scaleX(if (isSelected) 1.1f else 1f)
                .scaleY(if (isSelected) 1.1f else 1f)
                .setDuration(150)
                .start()
        }
    }

}