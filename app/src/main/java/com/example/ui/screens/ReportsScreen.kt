package com.example.ui.screens

import android.app.DatePickerDialog
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.model.JobSheetEntity
import com.example.data.model.PaymentEntity
import com.example.data.model.UserEntity
import com.example.ui.theme.*
import com.example.util.PdfGenerator
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class ReportFilterMode {
    SPECIFIC_MONTH,
    CUSTOM_DATES,
    ALL_TIME
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    currentUser: UserEntity,
    allJobSheets: List<JobSheetEntity>,
    payments: List<PaymentEntity>,
    onExportCsv: (List<JobSheetEntity>) -> Unit,
    onNavigateBack: () -> Unit
) {
    BackHandler { onNavigateBack() }
    val context = LocalContext.current

    // Active filter mode: SPECIFIC_MONTH, CUSTOM_DATES, ALL_TIME
    var filterMode by remember { mutableStateOf(ReportFilterMode.SPECIFIC_MONTH) }

    // Month & Year state (for SPECIFIC_MONTH)
    val currentCal = remember { Calendar.getInstance() }
    var selectedYear by remember { mutableIntStateOf(currentCal.get(Calendar.YEAR)) }
    var selectedMonth by remember { mutableIntStateOf(currentCal.get(Calendar.MONTH)) } // 0-based
    var showMonthPickerModal by remember { mutableStateOf(false) }

    // Date Range state (for CUSTOM_DATES)
    var customStartDateMillis by remember {
        val cal = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        mutableLongStateOf(cal.timeInMillis)
    }

    var customEndDateMillis by remember {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }
        mutableLongStateOf(cal.timeInMillis)
    }

    val monthNames = remember {
        listOf(
            "January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December"
        )
    }

    val displayDateFormatter = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }
    val monthYearFormatter = remember { SimpleDateFormat("MMMM yyyy", Locale.getDefault()) }

    // Determine effective time boundaries [startMillis, endMillis]
    val (effectiveStartMillis, effectiveEndMillis, periodLabel) = remember(
        filterMode,
        selectedYear,
        selectedMonth,
        customStartDateMillis,
        customEndDateMillis
    ) {
        when (filterMode) {
            ReportFilterMode.SPECIFIC_MONTH -> {
                val startCal = Calendar.getInstance().apply {
                    set(Calendar.YEAR, selectedYear)
                    set(Calendar.MONTH, selectedMonth)
                    set(Calendar.DAY_OF_MONTH, 1)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val endCal = Calendar.getInstance().apply {
                    set(Calendar.YEAR, selectedYear)
                    set(Calendar.MONTH, selectedMonth)
                    set(Calendar.DAY_OF_MONTH, startCal.getActualMaximum(Calendar.DAY_OF_MONTH))
                    set(Calendar.HOUR_OF_DAY, 23)
                    set(Calendar.MINUTE, 59)
                    set(Calendar.SECOND, 59)
                    set(Calendar.MILLISECOND, 999)
                }
                val label = "Month: ${monthNames[selectedMonth]} $selectedYear"
                Triple(startCal.timeInMillis, endCal.timeInMillis, label)
            }

            ReportFilterMode.CUSTOM_DATES -> {
                val startCal = Calendar.getInstance().apply {
                    timeInMillis = customStartDateMillis
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val endCal = Calendar.getInstance().apply {
                    timeInMillis = customEndDateMillis
                    set(Calendar.HOUR_OF_DAY, 23)
                    set(Calendar.MINUTE, 59)
                    set(Calendar.SECOND, 59)
                    set(Calendar.MILLISECOND, 999)
                }
                val isSameDay = startCal.get(Calendar.YEAR) == endCal.get(Calendar.YEAR) &&
                        startCal.get(Calendar.DAY_OF_YEAR) == endCal.get(Calendar.DAY_OF_YEAR)
                val label = if (isSameDay) {
                    "Particular Date: ${displayDateFormatter.format(startCal.time)}"
                } else {
                    "Dates: ${displayDateFormatter.format(startCal.time)} – ${displayDateFormatter.format(endCal.time)}"
                }
                Triple(startCal.timeInMillis, endCal.timeInMillis, label)
            }

            ReportFilterMode.ALL_TIME -> {
                Triple(0L, Long.MAX_VALUE, "All Time Records")
            }
        }
    }

    // Filtered data based on chosen period
    val nonDeletedAll = remember(allJobSheets) { allJobSheets.filter { !it.isDeleted } }

    val filteredJobSheets = remember(nonDeletedAll, effectiveStartMillis, effectiveEndMillis) {
        nonDeletedAll.filter { job ->
            job.checkoutTime in effectiveStartMillis..effectiveEndMillis ||
                    (job.returnTime != null && job.returnTime!! in effectiveStartMillis..effectiveEndMillis)
        }
    }

    val filteredPayments = remember(payments, effectiveStartMillis, effectiveEndMillis) {
        payments.filter { it.timestamp in effectiveStartMillis..effectiveEndMillis }
    }

    // Key financial metrics for filtered period
    val activeRentalsInPeriod = remember(filteredJobSheets) {
        filteredJobSheets.filter { it.status == "active" || it.status == "overdue" }
    }
    val totalDepositsHeld = remember(activeRentalsInPeriod) {
        activeRentalsInPeriod.sumOf { it.depositAmount }
    }

    val totalRevenueCollected = remember(filteredPayments, filteredJobSheets) {
        if (filteredPayments.isNotEmpty()) {
            val netPay = filteredPayments.filter { it.type != "refund" }.sumOf { it.amount } -
                    filteredPayments.filter { it.type == "refund" }.sumOf { it.amount }
            if (netPay > 0) netPay else filteredJobSheets.filter { it.status == "returned" || it.status == "closed" }.sumOf { it.totalRentalFee }
        } else {
            filteredJobSheets.filter { it.status == "returned" || it.status == "closed" }
                .sumOf { it.totalRentalFee }
        }
    }

    val lateFeesCollected = remember(filteredJobSheets) {
        filteredJobSheets.sumOf { it.lateFee }
    }

    // Machine rental popularity in selected period
    val machineFrequencies = remember(filteredJobSheets) {
        filteredJobSheets.groupBy { it.machineName }
            .mapValues { it.value.sumOf { job -> job.quantity } }
            .toList()
            .sortedByDescending { it.second }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Monthly & Date Reports",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = Color.White
                        )
                        Text(
                            text = "Taking report for: $periodLabel",
                            fontSize = 11.sp,
                            color = BabaYellow,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            PdfGenerator.viewReportPdf(
                                context = context,
                                jobSheets = filteredJobSheets,
                                payments = filteredPayments,
                                generatedByName = currentUser.name,
                                periodTitle = "Report Period: $periodLabel"
                            )
                        },
                        modifier = Modifier.testTag("export_pdf_button")
                    ) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = "PDF Report", tint = BabaYellow)
                    }
                    IconButton(
                        onClick = { onExportCsv(filteredJobSheets) },
                        modifier = Modifier.testTag("export_csv_button")
                    ) {
                        Icon(Icons.Default.TableChart, contentDescription = "Export CSV", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BabaRed)
            )
        },
        containerColor = BabaBg
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // FILTER CONTROL CARD: Mode selector & Date pickers
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .background(BabaRedLight, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.FilterAlt, contentDescription = "Filter", tint = BabaRed, modifier = Modifier.size(18.dp))
                                }
                                Column {
                                    Text("Report Timeframe", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = BabaDark)
                                    Text("Select month or custom dates", fontSize = 11.sp, color = BabaGray)
                                }
                            }

                            Surface(shape = RoundedCornerShape(6.dp), color = BabaYellowLight) {
                                Text(
                                    text = "${filteredJobSheets.size} Records",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = BabaDark,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Primary Selector: Specific Month vs Particular Dates vs All Time
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = filterMode == ReportFilterMode.SPECIFIC_MONTH,
                                onClick = { filterMode = ReportFilterMode.SPECIFIC_MONTH },
                                label = { Text("Specific Month", fontSize = 13.sp, fontWeight = FontWeight.Bold) },
                                leadingIcon = {
                                    Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                            )

                            FilterChip(
                                selected = filterMode == ReportFilterMode.CUSTOM_DATES,
                                onClick = { filterMode = ReportFilterMode.CUSTOM_DATES },
                                label = { Text("Particular Dates", fontSize = 13.sp, fontWeight = FontWeight.Bold) },
                                leadingIcon = {
                                    Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                            )

                            FilterChip(
                                selected = filterMode == ReportFilterMode.ALL_TIME,
                                onClick = { filterMode = ReportFilterMode.ALL_TIME },
                                label = { Text("All Time", fontSize = 13.sp, fontWeight = FontWeight.Bold) },
                                leadingIcon = {
                                    Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // OPTION 1: SPECIFIC MONTH CONTROLS
                        if (filterMode == ReportFilterMode.SPECIFIC_MONTH) {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(
                                    text = "Select Specific Month & Year",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = BabaDark
                                )

                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = BabaBg,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, BabaBorder),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(6.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Previous month button
                                        IconButton(
                                            onClick = {
                                                if (selectedMonth == 0) {
                                                    selectedMonth = 11
                                                    selectedYear--
                                                } else {
                                                    selectedMonth--
                                                }
                                            },
                                            modifier = Modifier.size(40.dp)
                                        ) {
                                            Icon(Icons.Default.ChevronLeft, contentDescription = "Previous Month", tint = BabaDark)
                                        }

                                        // Month & Year title button (opens picker dialog)
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = Color.White,
                                            border = androidx.compose.foundation.BorderStroke(1.dp, BabaBorder),
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .clickable { showMonthPickerModal = true }
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Icon(Icons.Default.CalendarToday, contentDescription = null, tint = BabaRed, modifier = Modifier.size(16.dp))
                                                Text(
                                                    text = "${monthNames[selectedMonth]} $selectedYear",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 15.sp,
                                                    color = BabaDark
                                                )
                                                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = BabaDark)
                                            }
                                        }

                                        // Next month button
                                        IconButton(
                                            onClick = {
                                                if (selectedMonth == 11) {
                                                    selectedMonth = 0
                                                    selectedYear++
                                                } else {
                                                    selectedMonth++
                                                }
                                            },
                                            modifier = Modifier.size(40.dp)
                                        ) {
                                            Icon(Icons.Default.ChevronRight, contentDescription = "Next Month", tint = BabaDark)
                                        }
                                    }
                                }

                                // Quick month shortcut pills
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    val currentSystemCal = Calendar.getInstance()
                                    val thisYear = currentSystemCal.get(Calendar.YEAR)
                                    val thisMonth = currentSystemCal.get(Calendar.MONTH)

                                    // This Month
                                    SuggestionChip(
                                        onClick = {
                                            selectedYear = thisYear
                                            selectedMonth = thisMonth
                                        },
                                        label = { Text("This Month (${monthNames[thisMonth].take(3)})", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) }
                                    )

                                    // Last Month
                                    val prevMonthCal = Calendar.getInstance().apply { add(Calendar.MONTH, -1) }
                                    val lastYear = prevMonthCal.get(Calendar.YEAR)
                                    val lastMonth = prevMonthCal.get(Calendar.MONTH)
                                    SuggestionChip(
                                        onClick = {
                                            selectedYear = lastYear
                                            selectedMonth = lastMonth
                                        },
                                        label = { Text("Last Month (${monthNames[lastMonth].take(3)})", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) }
                                    )

                                    // Choose from full 12-month grid
                                    SuggestionChip(
                                        onClick = { showMonthPickerModal = true },
                                        label = { Text("Choose Month & Year...", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                        icon = { Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                    )
                                }

                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = BabaRedLight.copy(alpha = 0.5f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(Icons.Default.Info, contentDescription = null, tint = BabaRed, modifier = Modifier.size(14.dp))
                                        Text(
                                            text = "Taking monthly report for all of ${monthNames[selectedMonth]} $selectedYear",
                                            fontSize = 11.sp,
                                            color = BabaRed,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }

                        // OPTION 2: PARTICULAR DATES CONTROLS (Single Date or Date Range)
                        if (filterMode == ReportFilterMode.CUSTOM_DATES) {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(
                                    text = "Select Particular Dates or Date Range",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = BabaDark
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // Start Date button
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = BabaBg,
                                        border = androidx.compose.foundation.BorderStroke(1.dp, BabaBorder),
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(10.dp))
                                            .clickable {
                                                val cal = Calendar.getInstance().apply { timeInMillis = customStartDateMillis }
                                                DatePickerDialog(
                                                    context,
                                                    { _, year, month, day ->
                                                        val newCal = Calendar.getInstance().apply {
                                                            set(year, month, day, 0, 0, 0)
                                                            set(Calendar.MILLISECOND, 0)
                                                        }
                                                        customStartDateMillis = newCal.timeInMillis
                                                        if (customEndDateMillis < customStartDateMillis) {
                                                            customEndDateMillis = newCal.timeInMillis + (24 * 3600 * 1000L - 1000L)
                                                        }
                                                    },
                                                    cal.get(Calendar.YEAR),
                                                    cal.get(Calendar.MONTH),
                                                    cal.get(Calendar.DAY_OF_MONTH)
                                                ).show()
                                            }
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp)) {
                                            Text("From Date (Tap to pick)", fontSize = 10.sp, color = BabaGray, fontWeight = FontWeight.Bold)
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                Icon(Icons.Default.Event, contentDescription = null, tint = BabaRed, modifier = Modifier.size(15.dp))
                                                Text(
                                                    text = displayDateFormatter.format(Date(customStartDateMillis)),
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = BabaDark
                                                )
                                            }
                                        }
                                    }

                                    // End Date button
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = BabaBg,
                                        border = androidx.compose.foundation.BorderStroke(1.dp, BabaBorder),
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(10.dp))
                                            .clickable {
                                                val cal = Calendar.getInstance().apply { timeInMillis = customEndDateMillis }
                                                DatePickerDialog(
                                                    context,
                                                    { _, year, month, day ->
                                                        val newCal = Calendar.getInstance().apply {
                                                            set(year, month, day, 23, 59, 59)
                                                            set(Calendar.MILLISECOND, 999)
                                                        }
                                                        customEndDateMillis = newCal.timeInMillis
                                                    },
                                                    cal.get(Calendar.YEAR),
                                                    cal.get(Calendar.MONTH),
                                                    cal.get(Calendar.DAY_OF_MONTH)
                                                ).show()
                                            }
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp)) {
                                            Text("To Date (Tap to pick)", fontSize = 10.sp, color = BabaGray, fontWeight = FontWeight.Bold)
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                Icon(Icons.Default.Event, contentDescription = null, tint = BabaRed, modifier = Modifier.size(15.dp))
                                                Text(
                                                    text = displayDateFormatter.format(Date(customEndDateMillis)),
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = BabaDark
                                                )
                                            }
                                        }
                                    }
                                }

                                // Quick presets for particular dates
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    // Pick Single Particular Day
                                    SuggestionChip(
                                        onClick = {
                                            val cal = Calendar.getInstance().apply { timeInMillis = customStartDateMillis }
                                            DatePickerDialog(
                                                context,
                                                { _, year, month, day ->
                                                    val start = Calendar.getInstance().apply {
                                                        set(year, month, day, 0, 0, 0)
                                                        set(Calendar.MILLISECOND, 0)
                                                    }.timeInMillis
                                                    val end = Calendar.getInstance().apply {
                                                        set(year, month, day, 23, 59, 59)
                                                        set(Calendar.MILLISECOND, 999)
                                                    }.timeInMillis
                                                    customStartDateMillis = start
                                                    customEndDateMillis = end
                                                },
                                                cal.get(Calendar.YEAR),
                                                cal.get(Calendar.MONTH),
                                                cal.get(Calendar.DAY_OF_MONTH)
                                            ).show()
                                        },
                                        label = { Text("Pick Single Day...", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                        icon = { Icon(Icons.Default.Today, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                    )

                                    // Today
                                    SuggestionChip(
                                        onClick = {
                                            val start = Calendar.getInstance().apply {
                                                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                                            }.timeInMillis
                                            val end = Calendar.getInstance().apply {
                                                set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59); set(Calendar.MILLISECOND, 999)
                                            }.timeInMillis
                                            customStartDateMillis = start
                                            customEndDateMillis = end
                                        },
                                        label = { Text("Today", fontSize = 11.sp) }
                                    )

                                    // Yesterday
                                    SuggestionChip(
                                        onClick = {
                                            val start = Calendar.getInstance().apply {
                                                add(Calendar.DAY_OF_MONTH, -1)
                                                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                                            }.timeInMillis
                                            val end = Calendar.getInstance().apply {
                                                add(Calendar.DAY_OF_MONTH, -1)
                                                set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59); set(Calendar.MILLISECOND, 999)
                                            }.timeInMillis
                                            customStartDateMillis = start
                                            customEndDateMillis = end
                                        },
                                        label = { Text("Yesterday", fontSize = 11.sp) }
                                    )

                                    // Last 7 Days
                                    SuggestionChip(
                                        onClick = {
                                            val start = Calendar.getInstance().apply {
                                                add(Calendar.DAY_OF_MONTH, -7)
                                                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                                            }.timeInMillis
                                            val end = Calendar.getInstance().apply {
                                                set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59); set(Calendar.MILLISECOND, 999)
                                            }.timeInMillis
                                            customStartDateMillis = start
                                            customEndDateMillis = end
                                        },
                                        label = { Text("Last 7 Days", fontSize = 11.sp) }
                                    )

                                    // This Month to Date
                                    SuggestionChip(
                                        onClick = {
                                            val start = Calendar.getInstance().apply {
                                                set(Calendar.DAY_OF_MONTH, 1)
                                                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                                            }.timeInMillis
                                            val end = Calendar.getInstance().apply {
                                                set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59); set(Calendar.MILLISECOND, 999)
                                            }.timeInMillis
                                            customStartDateMillis = start
                                            customEndDateMillis = end
                                        },
                                        label = { Text("This Month to Date", fontSize = 11.sp) }
                                    )

                                    // Last 30 Days
                                    SuggestionChip(
                                        onClick = {
                                            val start = Calendar.getInstance().apply {
                                                add(Calendar.DAY_OF_MONTH, -30)
                                                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                                            }.timeInMillis
                                            val end = Calendar.getInstance().apply {
                                                set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59); set(Calendar.MILLISECOND, 999)
                                            }.timeInMillis
                                            customStartDateMillis = start
                                            customEndDateMillis = end
                                        },
                                        label = { Text("Last 30 Days", fontSize = 11.sp) }
                                    )
                                }

                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = BabaYellowLight,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(Icons.Default.DateRange, contentDescription = null, tint = BabaYellowDark, modifier = Modifier.size(14.dp))
                                        Text(
                                            text = "Taking report for: $periodLabel",
                                            fontSize = 11.sp,
                                            color = BabaDark,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // TAKE REPORT ACTIONS CARD (Executive PDF & CSV Spreadsheet)
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .background(BabaRedLight, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Download, contentDescription = "Take Report", tint = BabaRed, modifier = Modifier.size(20.dp))
                                }
                                Column {
                                    Text("Take Report Statement", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = BabaDark)
                                    Text("Period: $periodLabel", fontSize = 11.sp, color = BabaGray)
                                }
                            }

                            Surface(shape = RoundedCornerShape(6.dp), color = BabaSuccessLight) {
                                Text(
                                    text = "₹${totalRevenueCollected.toInt()} Net",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = BabaSuccess,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Take PDF Report
                            Button(
                                onClick = {
                                    PdfGenerator.viewReportPdf(
                                        context = context,
                                        jobSheets = filteredJobSheets,
                                        payments = filteredPayments,
                                        generatedByName = currentUser.name,
                                        periodTitle = "Report Period: $periodLabel"
                                    )
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = BabaRed),
                                modifier = Modifier
                                    .weight(1.1f)
                                    .testTag("view_report_pdf_button"),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Take PDF", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }

                            // Share PDF
                            Button(
                                onClick = {
                                    PdfGenerator.shareReportPdf(
                                        context = context,
                                        jobSheets = filteredJobSheets,
                                        payments = filteredPayments,
                                        generatedByName = currentUser.name,
                                        periodTitle = "Report Period: $periodLabel"
                                    )
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = BabaSuccess),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("share_report_pdf_button"),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Share PDF", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }

                            // Export CSV
                            OutlinedButton(
                                onClick = { onExportCsv(filteredJobSheets) },
                                modifier = Modifier
                                    .weight(0.9f)
                                    .testTag("export_csv_btn"),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = BabaDark)
                            ) {
                                Icon(Icons.Default.TableChart, contentDescription = null, modifier = Modifier.size(15.dp), tint = BabaDark)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("CSV", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // FINANCIAL KPI SUMMARY CARDS FOR SELECTED PERIOD
            item {
                Text(
                    text = "Financial Summary ($periodLabel)",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = BabaDark
                )
                Spacer(modifier = Modifier.height(6.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ReportStatCard(
                        title = "Net Revenue",
                        value = "₹${totalRevenueCollected.toInt()}",
                        subtitle = "Deposits & settlements in period",
                        color = BabaSuccess,
                        modifier = Modifier.weight(1f)
                    )
                    ReportStatCard(
                        title = "Job Sheets",
                        value = "${filteredJobSheets.size}",
                        subtitle = "Rentals in period",
                        color = BabaRed,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ReportStatCard(
                        title = "Deposits Held",
                        value = "₹${totalDepositsHeld.toInt()}",
                        subtitle = "Active/overdue jobs",
                        color = BabaDark,
                        modifier = Modifier.weight(1f)
                    )
                    ReportStatCard(
                        title = "Late Fees",
                        value = "₹${lateFeesCollected.toInt()}",
                        subtitle = "Collected in period",
                        color = BabaYellowDark,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // MOST RENTED MACHINERY IN SELECTED PERIOD
            item {
                Text(
                    text = "Most Rented Equipment ($periodLabel)",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = BabaDark
                )
                Spacer(modifier = Modifier.height(6.dp))

                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (machineFrequencies.isEmpty()) {
                            Text("No rental activity recorded for $periodLabel", color = BabaGray, fontSize = 12.sp)
                        } else {
                            machineFrequencies.take(6).forEachIndexed { index, (name, qty) ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = "#${index + 1}",
                                            fontWeight = FontWeight.Black,
                                            fontSize = 12.sp,
                                            color = BabaRed
                                        )
                                        Text(
                                            text = name,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = BabaDark
                                        )
                                    }
                                    Surface(shape = RoundedCornerShape(6.dp), color = BabaLightGray) {
                                        Text(
                                            text = "$qty units rented",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = BabaDark,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // LIST OF TRANSACTIONS IN PERIOD
            item {
                Text(
                    text = "Job Sheet Transactions ($periodLabel)",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = BabaDark
                )
            }

            if (filteredJobSheets.isEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color.White,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(modifier = Modifier.padding(24.dp), contentAlignment = Alignment.Center) {
                            Text(
                                text = "No job sheets found for $periodLabel",
                                fontSize = 13.sp,
                                color = BabaGray
                            )
                        }
                    }
                }
            } else {
                items(filteredJobSheets.take(15), key = { it.id }) { job ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color.White,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(text = job.id, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = BabaRed)
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = if (job.status == "closed" || job.status == "returned") BabaSuccessLight else BabaYellowLight
                                    ) {
                                        Text(
                                            text = job.status.uppercase(),
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (job.status == "closed" || job.status == "returned") BabaSuccess else BabaYellowDark,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = "${job.customerName} • ${job.machineName}",
                                    fontSize = 12.sp,
                                    color = BabaDark,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(job.checkoutTime)),
                                    fontSize = 10.sp,
                                    color = BabaGray
                                )
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "₹${job.depositAmount}",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = BabaSuccess
                                )
                                Text("Deposit", fontSize = 10.sp, color = BabaGray)
                            }
                        }
                    }
                }
            }
        }
    }

    // MONTH & YEAR SELECTOR MODAL DIALOG
    if (showMonthPickerModal) {
        Dialog(onDismissRequest = { showMonthPickerModal = false }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "Select Specific Month & Year",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = BabaDark
                    )

                    // Year Selector Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Year:", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = BabaDark)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            listOf(selectedYear - 1, selectedYear, selectedYear + 1).forEach { yr ->
                                FilterChip(
                                    selected = selectedYear == yr,
                                    onClick = { selectedYear = yr },
                                    label = { Text("$yr", fontSize = 12.sp) }
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = BabaBorder)

                    Text("Month:", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = BabaDark)

                    // 12 Months Grid (4 rows x 3 columns)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (row in 0 until 4) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                for (col in 0 until 3) {
                                    val mIdx = row * 3 + col
                                    val isSelected = selectedMonth == mIdx
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isSelected) BabaRed else BabaLightGray,
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable {
                                                selectedMonth = mIdx
                                                showMonthPickerModal = false
                                            }
                                    ) {
                                        Box(
                                            modifier = Modifier.padding(vertical = 10.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = monthNames[mIdx].take(3),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                                color = if (isSelected) Color.White else BabaDark
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showMonthPickerModal = false }) {
                            Text("Done", fontWeight = FontWeight.Bold, color = BabaRed)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ReportStatCard(
    title: String,
    value: String,
    subtitle: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(text = title, fontSize = 11.sp, color = BabaGray, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = value, fontSize = 20.sp, fontWeight = FontWeight.Black, color = color)
            Text(text = subtitle, fontSize = 10.sp, color = BabaGray)
        }
    }
}
