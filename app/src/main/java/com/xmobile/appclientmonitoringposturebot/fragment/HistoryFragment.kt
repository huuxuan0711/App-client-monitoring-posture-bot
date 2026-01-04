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
import com.github.mikephil.charting.charts.BarChart
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
import com.xmobile.appclientmonitoringposturebot.util.ConvertTimeZone.createdAtLocal
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
import kotlin.math.min

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
class HistoryFragment : Fragment() {
    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!

    private var todayRecords: List<PostureRecord> = emptyList()
    private var weekRecords: List<PostureRecord> = emptyList()
    private var monthRecords: List<PostureRecord> = emptyList()

    private var currentTab: Int = 0

    private var currentDay: LocalDate = LocalDate.now()
    private var currentWeekStart: LocalDate =
        LocalDate.now().with(DayOfWeek.MONDAY)
    private var currentMonth: YearMonth = YearMonth.now()
    private var txtDuration: String = ""

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
            binding.layoutSession.visibility = View.GONE
            binding.layoutSummaryDay.visibility = View.VISIBLE
            binding.layoutSummaryWeek.visibility = View.GONE
            binding.layoutSummaryMonth.visibility = View.GONE
            getTodayRecords(userId, LocalDate.now())
            updateScrollTime()
        }

        binding.txtWeek.setOnClickListener {
            updateTab(1)
            binding.barChart.visibility = View.VISIBLE
            binding.calendar.visibility = View.GONE
            binding.layoutSession.visibility = View.GONE
            binding.layoutSummaryDay.visibility = View.GONE
            binding.layoutSummaryWeek.visibility = View.VISIBLE
            binding.layoutSummaryMonth.visibility = View.GONE
            val monday =
                LocalDate.now().with(DayOfWeek.MONDAY)

            getWeekRecords(userId, monday)
            updateScrollTime()
            binding.barChart.highlightValues(null)
            lastHighlight = null
        }

        binding.txtMonth.setOnClickListener {
            updateTab(2)
            binding.barChart.visibility = View.GONE
            binding.calendar.visibility = View.VISIBLE
            binding.layoutSession.visibility = View.GONE
            binding.layoutSummaryDay.visibility = View.GONE
            binding.layoutSummaryWeek.visibility = View.GONE
            val month = YearMonth.now()
            getMonthRecords(userId, month)
            updateScrollTime()
        }

        binding.txtDay.performClick()

        binding.imgBack.setOnClickListener {
            when (currentTab) {

                0 -> {
                    currentDay = currentDay.minusDays(1)
                    getTodayRecords(userId, currentDay)
                }

                1 -> {
                    currentWeekStart = currentWeekStart.minusWeeks(1)
                    getWeekRecords(userId, currentWeekStart)
                }

                // ===== MONTH =====
                2 -> {
                    currentMonth = currentMonth.minusMonths(1)
                    getMonthRecords(userId, currentMonth)
                }
            }
            updateScrollTime()
        }

        binding.imgNext.setOnClickListener {
            when (currentTab) {

                0 -> {
                    currentDay = currentDay.plusDays(1)
                    binding.layoutSession.visibility = View.GONE
                    getTodayRecords(userId, currentDay)
                }

                1 -> {
                    currentWeekStart = currentWeekStart.plusWeeks(1)
                    getWeekRecords(userId, currentWeekStart)
                }

                2 -> {
                    currentMonth = currentMonth.plusMonths(1)
                    getMonthRecords(userId, currentMonth)
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
                    if (e == null) return

                    // ================= WEEK → CLICK 1 NGÀY =================
                    if (currentTab == 1) {

                        val dayIndex = e.x.toInt().coerceIn(0, 6)

                        // 👉 dùng cursor tuần hiện tại, KHÔNG dùng LocalDate.now()
                        val selectedDate =
                            currentWeekStart.plusDays(dayIndex.toLong())

                        // cập nhật cursor DAY
                        currentDay = selectedDate

                        // fetch DAY theo LocalDate
                        getTodayRecords(
                            device?.userId ?: return,
                            selectedDate
                        )

                        currentTab = 0
                        updateTab(0)
                        updateScrollTime()

                        binding.barChart.visibility = View.VISIBLE
                        binding.calendar.visibility = View.GONE
                        binding.layoutSession.visibility = View.GONE

                        return
                    }

                    // ================= DAY → CLICK 1 GIỜ =================
                    if (currentTab == 0) {

                        if (h == null) return

                        // click lại cùng bar → unselect
                        if (lastHighlight != null &&
                            lastHighlight!!.x == h.x &&
                            lastHighlight!!.dataSetIndex == h.dataSetIndex
                        ) {
                            binding.barChart.highlightValues(null)
                            lastHighlight = null
                            binding.layoutSession.visibility = View.GONE
                            binding.txtDuration.text = txtDuration
                            return
                        }

                        // click bar mới
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

        val today = LocalDate.now()

        when (currentTab) {

            // ================= DAY =================
            0 -> {
                when (currentDay) {
                    today -> {
                        disableNext()
                        binding.txtDuration.text = "Hôm nay"
                        txtDuration = "Hôm nay"
                    }
                    today.minusDays(1) -> {
                        enableNext()
                        binding.txtDuration.text = "Hôm qua"
                        txtDuration = "Hôm qua"
                    }
                    else -> {
                        enableNext()
                        val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
                        binding.txtDuration.text = currentDay.format(formatter)
                        txtDuration = currentDay.format(formatter)
                    }
                }
            }

            // ================= WEEK =================
            1 -> {
                val thisWeekStart = today.with(DayOfWeek.MONDAY)

                when (currentWeekStart) {
                    thisWeekStart -> {
                        disableNext()
                        binding.txtDuration.text = "Tuần này"
                    }
                    thisWeekStart.minusWeeks(1) -> {
                        enableNext()
                        binding.txtDuration.text = "Tuần trước"
                    }
                    else -> {
                        enableNext()
                        val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
                        val end = currentWeekStart.plusDays(6)
                        binding.txtDuration.text =
                            "${currentWeekStart.format(formatter)} - ${end.format(formatter)}"
                    }
                }
            }

            // ================= MONTH =================
            2 -> {
                val thisMonth = YearMonth.now()

                when (currentMonth) {
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
                        binding.txtDuration.text = currentMonth.format(formatter)
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

    private fun getMonthRecords(
        userId: String,
        month: YearMonth
    ) {
        // cập nhật cursor tháng
        currentMonth = month

        val days =
            (1..month.lengthOfMonth())
                .map { month.atDay(it) }

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                monthRecords =
                    StatisticPosetureRecord
                        .getRecordsByLocalDates(userId, days)

                setUpCalendar(monthRecords)
                binding.layoutSummaryMonth.visibility = View.VISIBLE
                setUpMonthSummary(
                    userId = userId,
                    currentMonth = currentMonth,
                    currentMonthRecords = monthRecords
                )

            } catch (e: Exception) {
                Log.e("getMonthRecords", e.message ?: "Unknown error")
            }
        }
    }

    private fun getWeekRecords(
        userId: String,
        weekStart: LocalDate
    ) {
        // cập nhật cursor tuần
        currentWeekStart = weekStart

        val days =
            (0..6).map { weekStart.plusDays(it.toLong()) }

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                weekRecords =
                    StatisticPosetureRecord
                        .getRecordsByLocalDates(userId, days)

                setUpBarChart(weekRecords, false)
                setUpWeekSummary(weekRecords)

            } catch (e: Exception) {
                Log.e("getWeekRecords", e.message ?: "Unknown error")
            }
        }
    }

    private fun getTodayRecords(
        userId: String,
        date: LocalDate
    ) {
        // cập nhật cursor ngày
        currentDay = date

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                todayRecords =
                    StatisticPosetureRecord
                        .getRecordsByLocalDate(userId, date)

                setUpBarChart(todayRecords, true)
                setUpDaySummary(todayRecords)

            } catch (e: Exception) {
                Log.e("getTodayRecords", e.message ?: "Unknown error")
            }
        }
    }

    private fun setUpMonthSummary(
        userId: String,
        currentMonth: YearMonth,
        currentMonthRecords: List<PostureRecord>
    ) {
        // ===== Tổng thời gian =====
        val duration = calculatePostureDurationDesc(currentMonthRecords)
        binding.txtTotalGoodDuration.text = formatDuration(duration.goodMs)
        binding.txtTotalBadDuration.text = formatDuration(duration.badMs)

        // ===== Tuần tốt nhất =====
        val bestWeek = calculateBestWeek(currentMonthRecords)
        bestWeek?.let { weekKey ->
            val (start, end) = getIsoWeekRange(weekKey)

            binding.txtBestWeek.text =
                "${start.dayOfMonth}/${start.monthValue} – ${end.dayOfMonth}/${end.monthValue}"
        } ?: run {
            binding.txtBestWeek.text = "--"
        }

        // ===== So sánh với tháng trước =====
        val lastMonth = currentMonth.minusMonths(1)

        val lastMonthDays =
            (1..lastMonth.lengthOfMonth())
                .map { lastMonth.atDay(it) }

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val lastMonthRecords =
                    StatisticPosetureRecord
                        .getRecordsByLocalDates(
                            userId,
                            lastMonthDays
                        )

                val trend = calculateMonthTrend(
                    currentMonthRecords = currentMonthRecords,
                    lastMonthRecords = lastMonthRecords
                )

                binding.txtCompare.text = when {
                    trend == null -> "--"
                    trend.isBetter ->
                        "Tốt hơn ${trend.diffPercent}% so với tháng trước"
                    else ->
                        "Kém hơn ${trend.diffPercent}% so với tháng trước"
                }

            } catch (e: Exception) {
                Log.e("setUpMonthSummary", e.message ?: "Unknown error")
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
            val date = record.createdAtLocal().toLocalDate()
            getWeekKey(date)
        }
    }


    private fun calculateWeekGoodPercent(
        weekRecords: List<PostureRecord>
    ): Float {

        val recordsByDay = groupRecordsByDate(weekRecords)
        val dailyPercents = calculateDailyGoodPercent(recordsByDay)
        // List<Pair<LocalDate, Float>>

        if (dailyPercents.isEmpty()) return 0f

        return dailyPercents.map { it.second }.average().toFloat()
    }

    private fun calculateBestWeek(monthRecords: List<PostureRecord>): WeekKey? {

        if (monthRecords.isEmpty()) return null

        val recordsByWeek = groupRecordsByWeek(monthRecords)

        return recordsByWeek
            .mapValues { (_, records) ->
                calculateWeekGoodPercent(records)
            }
            .maxByOrNull { it.value }
            ?.key
    }

    private fun getIsoWeekRange(weekKey: WeekKey): Pair<LocalDate, LocalDate> {

        val weekFields = WeekFields.ISO

        // Lấy ngày bất kỳ trong tuần đó (ISO: tuần 1 luôn chứa ngày 4/1)
        val startOfWeek = LocalDate
            .of(weekKey.year, 1, 4)
            .with(weekFields.weekOfWeekBasedYear(), weekKey.week.toLong())
            .with(weekFields.dayOfWeek(), 1) // Monday

        val endOfWeek = startOfWeek.plusDays(6)

        return startOfWeek to endOfWeek
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
        Log.e("bestDay", bestDay.toString())
        Log.e("worstDay", worstDay.toString())

        binding.bestDay.text = bestDay?.let { formatDayShort(it) } ?: "--"
        binding.worstDay.text = worstDay?.let { formatDayShort(it) } ?: "--"
    }

    private fun formatDayShort(date: LocalDate): String {
        return date.dayOfWeek.name.take(3)
    }

    private fun calculateGoodMs(
        records: List<PostureRecord>
    ): Long {

        if (records.isEmpty()) return 0L

        val MAX_GAP_MS = 60_000L
        var goodMs = 0L
        val now = OffsetDateTime.now()

        // ---------- record mới nhất → hiện tại ----------
        val first = records.first()
        val firstTime = first.createdAtLocal()

        if ((first.confidence ?: 0.0) >= confidenceThreshold &&
            first.posture_type.equals("good", ignoreCase = true)
        ) {
            val raw = Duration.between(firstTime, now).toMillis()
            val duration = min(raw, MAX_GAP_MS)
            if (duration > 0) goodMs += duration
        }

        // ---------- record → record ----------
        for (i in 0 until records.lastIndex) {

            val current = records[i]
            val next = records[i + 1]

            val confidence = current.confidence ?: 0.0
            if (confidence < confidenceThreshold) continue
            if (!current.posture_type.equals("good", ignoreCase = true)) continue

            val currentTime = current.createdAtLocal()
            val nextTime = next.createdAtLocal()

            val raw = Duration.between(nextTime, currentTime).toMillis()
            if (raw <= 0 || raw > MAX_GAP_MS) continue

            goodMs += raw
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
        Log.e("hours", hours.contentToString())

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
        binding.hourDuration.text = formatHourRange(hourIndex)
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
        Log.e("records", records.toString())
        val recordsByDate = groupRecordsByDate(records)

        val displayMonth: YearMonth = currentMonth
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
                        if (dayRecords.isNullOrEmpty()) return@setOnClickListener

                        val selectedDate = data.date // LocalDate

                        // cập nhật cursor DAY
                        currentDay = selectedDate

                        // fetch DAY theo LocalDate (RPC)
                        getTodayRecords(
                            device?.userId ?: return@setOnClickListener,
                            selectedDate
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
            record.createdAtLocal().toLocalDate()
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

        val MAX_GAP_MS = 60_000L
        val now = OffsetDateTime.now()

        for (i in todayRecords.indices) {

            val current = todayRecords[i]

            // ---------- confidence filter (GIỐNG summary) ----------
            val confidence = current.confidence ?: 0.0
            if (confidence < confidenceThreshold) continue

            val start = current.createdAtLocal()

            val rawEnd = if (i == 0) {
                now
            } else {
                todayRecords[i - 1].createdAtLocal()
            }

            // ---------- GAP handling ----------
            val rawDurationMs = Duration
                .between(start, rawEnd)
                .toMillis()

            if (rawDurationMs <= 0) continue

            val effectiveEnd =
                if (rawDurationMs > MAX_GAP_MS)
                    start.plus(Duration.ofMillis(MAX_GAP_MS))
                else
                    rawEnd

            var cursor = start

            while (cursor.isBefore(effectiveEnd)) {

                val hour = cursor.hour

                val nextHour = cursor
                    .withMinute(0)
                    .withSecond(0)
                    .withNano(0)
                    .plusHours(1)

                val segmentEnd =
                    if (nextHour.isBefore(effectiveEnd)) nextHour else effectiveEnd

                val durationMs = Duration
                    .between(cursor, segmentEnd)
                    .toMillis()

                if (durationMs <= 0) break

                hours[hour].totalMs += durationMs

                when (current.posture_type.lowercase()) {
                    "good" -> hours[hour].goodMs += durationMs
                    "bad" -> hours[hour].badMs += durationMs
                    else -> { /* ignore */ }
                }

                cursor = segmentEnd
            }
        }

        return hours
    }

    private fun setUpBarChart(
        records: List<PostureRecord>,
        isDay: Boolean
    ) {
        val barChart = binding.barChart

        // ===== BASIC CONFIG (chỉ set 1 lần mỗi lần update) =====
        barChart.apply {
            description.isEnabled = false
            legend.isEnabled = false
            setScaleEnabled(false)
            axisRight.isEnabled = false
        }

        if (isDay) {
            setupDayChart(barChart, records)
        } else {
            setupWeekChart(barChart, records)
        }

        barChart.data?.notifyDataChanged()
        barChart.notifyDataSetChanged()
        barChart.invalidate()
    }

    private fun setupDayChart(
        barChart: BarChart,
        records: List<PostureRecord>
    ) {
        val hourlyPercents = calculateHourlyStats(records).map {
            if (it.totalMs == 0L) 0f
            else it.goodMs * 100f / it.totalMs
        }

        val entries = hourlyPercents.mapIndexed { hour, percent ->
            BarEntry(hour.toFloat(), percent)
        }

        val dataSet = BarDataSet(
            entries,
            "DAY_${System.currentTimeMillis()}" // 🔑 bắt chart rebuild
        ).apply {
            color = requireContext().getColor(R.color.purple)
            setDrawValues(false)
        }

        barChart.data = BarData(dataSet).apply {
            barWidth = 0.8f
        }

        // ===== X AXIS =====
        barChart.xAxis.apply {
            position = XAxis.XAxisPosition.BOTTOM
            axisMinimum = -0.5f
            axisMaximum = 23.5f
            granularity = 1f
            setLabelCount(8, false)
            setDrawGridLines(false)
            setDrawAxisLine(false)
            setAvoidFirstLastClipping(true)
            textColor = requireContext().getColor(R.color.dark_blue)
            textSize = 12f

            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    return when (value.toInt()) {
                        0, 3, 6, 9, 12, 15, 18, 21 -> value.toInt().toString()
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
            textColor = requireContext().getColor(R.color.dark_blue)
        }

        barChart.animateY(500)
    }

    private fun setupWeekChart(
        barChart: BarChart,
        records: List<PostureRecord>
    ) {
        val recordsByDay = groupRecordsByDate(records)
        val dailyPercents = calculateDailyGoodPercent(recordsByDay)

        val entries = dailyPercents.mapIndexed { index, pair ->
            BarEntry(index.toFloat(), pair.second)
        }

        val today = LocalDate.now()
        val todayIndex = dailyPercents.indexOfFirst { it.first == today }

        val dataSet = BarDataSet(
            entries,
            "WEEK_${System.currentTimeMillis()}" // 🔑 bắt chart rebuild
        ).apply {
            colors = entries.mapIndexed { i, _ ->
                if (i == todayIndex)
                    requireContext().getColor(R.color.purple)
                else
                    requireContext().getColor(R.color.dark_blue)
            }
            setDrawValues(false)
        }

        barChart.data = BarData(dataSet).apply {
            barWidth = 0.6f
        }

        val labels = dailyPercents.map { (date, _) ->
            date.dayOfWeek.name.take(3) // MON, TUE, ...
        }

        // ===== X AXIS =====
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
                    val index = value.toInt()
                    return labels.getOrNull(index) ?: ""
                }
            }
        }

        // ===== Y AXIS =====
        barChart.axisLeft.apply {
            axisMinimum = 0f
            axisMaximum = 100f
            granularity = 25f
            setDrawGridLines(true)
            textColor = requireContext().getColor(R.color.dark_blue)
        }

        barChart.setExtraOffsets(-8f, 0f, 0f, 0f)
        barChart.animateY(700, Easing.EaseInOutQuad)
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

        val MAX_GAP_MS = 60_000L

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

            val raw = Duration.between(firstTime, now).toMillis()
            val duration = min(raw, MAX_GAP_MS)

            when (first.posture_type.lowercase()) {
                "good" -> good += duration
                "bad" -> bad += duration
                else -> other += duration
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