package com.example.ui.screens

import android.app.DatePickerDialog
import android.widget.Toast
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
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.model.DailyCashEntity
import com.example.data.model.ExpenseEntity
import com.example.data.model.JobSheetEntity
import com.example.data.model.MachineCategoryEntity
import com.example.data.model.MachineEntity
import com.example.data.model.PaymentEntity
import com.example.data.model.SelectedMachineItem
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

enum class ReportSubTab(val label: String, val tag: String) {
    SALES("Sales Report", "report_tab_sales"),
    CATEGORY("Category Report", "report_tab_category"),
    DAY_WISE("Day-wise Sales", "report_tab_daywise"),
    STOCK("Stock in Hand", "report_tab_stock"),
    CASH("Opening & Closing Cash", "report_tab_cash")
}

data class ReconciledSaleLine(
    val jobId: String,
    val customerName: String,
    val machineSummary: String,
    val categoryBreakdown: Map<String, Double>,
    val eventTimestamp: Long,
    val dateKey: String,
    val netSaleAmount: Double,
    val discount: Double,
    val lateFee: Double,
    val depositAmount: Double,
    val paymentMode: String,
    val createdBy: String,
    val closedBy: String,
    val returnRemark: String,
    val isNilReturn: Boolean,
    val status: String,
    val sourceJob: JobSheetEntity?
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    currentUser: UserEntity,
    allJobSheets: List<JobSheetEntity>,
    payments: List<PaymentEntity>,
    categories: List<MachineCategoryEntity> = emptyList(),
    machines: List<MachineEntity> = emptyList(),
    expenses: List<ExpenseEntity> = emptyList(),
    dailyCashList: List<DailyCashEntity> = emptyList(),
    onSaveOpeningCash: (dateStr: String, amount: Double) -> Unit = { _, _ -> },
    onSaveClosingCash: (dateStr: String, amount: Double) -> Unit = { _, _ -> },
    onViewReceipt: (JobSheetEntity) -> Unit = {},
    onExportCsv: (List<JobSheetEntity>) -> Unit,
    onNavigateBack: () -> Unit
) {
    BackHandler { onNavigateBack() }
    val context = LocalContext.current

    var selectedTab by remember { mutableStateOf(ReportSubTab.SALES) }

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
    val dayKeyFormatter = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }
    val expenseDateFormatter = remember { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()) }

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

    val nonDeletedAll = remember(allJobSheets) { allJobSheets.filter { !it.isDeleted } }

    val filteredJobSheets = remember(nonDeletedAll, effectiveStartMillis, effectiveEndMillis) {
        nonDeletedAll.filter { job ->
            val eventTime = job.returnTime ?: job.checkoutTime
            job.checkoutTime in effectiveStartMillis..effectiveEndMillis ||
                    eventTime in effectiveStartMillis..effectiveEndMillis
        }.sortedByDescending { it.returnTime ?: it.checkoutTime }
    }

    val filteredPayments = remember(payments, effectiveStartMillis, effectiveEndMillis) {
        payments.filter { it.timestamp in effectiveStartMillis..effectiveEndMillis }
    }

    // Reconciled sales lines: every closed/returned job in the period produces a deterministic sale line
    // whose category breakdown sums to its exact netSaleAmount, guaranteeing:
    // Sales Report Total == Category Report Total == Day-wise Sales Report Total
    val reconciledSaleLines = remember(filteredJobSheets, dayKeyFormatter) {
        filteredJobSheets.map { job ->
            val isClosedOrReturned = job.status == "returned" || job.status == "closed"
            val netSale = if (isClosedOrReturned) job.totalRentalFee.coerceAtLeast(0.0) else 0.0
            val isNil = isClosedOrReturned && netSale == 0.0
            val eventTs = job.returnTime ?: job.checkoutTime
            val dateStr = displayDateFormatter.format(Date(eventTs))

            val parsedItems = SelectedMachineItem.listFromJson(job.itemsJson)
            val categoryMap = LinkedHashMap<String, Double>()
            if (parsedItems.isNotEmpty() && netSale > 0.0) {
                val rawWeightSum = parsedItems.sumOf { (it.rate * it.quantity).coerceAtLeast(0.0) }
                if (rawWeightSum > 0.0) {
                    var allocated = 0.0
                    parsedItems.forEachIndexed { idx, item ->
                        val catLabel = item.categoryName.ifBlank { job.categoryName.ifBlank { "General Equipment" } }
                        val share = if (idx == parsedItems.lastIndex) {
                            (netSale - allocated).coerceAtLeast(0.0)
                        } else {
                            Math.round((netSale * ((item.rate * item.quantity) / rawWeightSum)) * 100.0) / 100.0
                        }
                        allocated += share
                        categoryMap[catLabel] = (categoryMap[catLabel] ?: 0.0) + share
                    }
                } else {
                    val catLabel = job.categoryName.ifBlank { "General Equipment" }
                    categoryMap[catLabel] = netSale
                }
            } else {
                val catLabel = parsedItems.firstOrNull()?.categoryName?.ifBlank { null }
                    ?: job.categoryName.ifBlank { "General Equipment" }
                categoryMap[catLabel] = netSale
            }

            ReconciledSaleLine(
                jobId = job.id,
                customerName = job.customerName,
                machineSummary = job.cleanMachineName,
                categoryBreakdown = categoryMap,
                eventTimestamp = eventTs,
                dateKey = dateStr,
                netSaleAmount = netSale,
                discount = job.discount,
                lateFee = job.lateFee,
                depositAmount = job.depositAmount,
                paymentMode = job.paymentMode.ifBlank { "CASH" }.uppercase(),
                createdBy = job.checkoutByName.ifBlank { job.handledByName.ifBlank { "Staff" } },
                closedBy = job.closedByName.ifBlank { if (isClosedOrReturned) job.handledByName.ifBlank { "Staff" } else "-" },
                returnRemark = job.returnRemark,
                isNilReturn = isNil,
                status = job.status,
                sourceJob = job
            )
        }
    }

    val totalRevenueCollected = remember(reconciledSaleLines) {
        reconciledSaleLines.sumOf { it.netSaleAmount }
    }

    val activeRentalsInPeriod = remember(filteredJobSheets) {
        filteredJobSheets.filter { it.status == "active" || it.status == "overdue" }
    }
    val totalDepositsHeld = remember(activeRentalsInPeriod) {
        activeRentalsInPeriod.sumOf { it.depositAmount }
    }
    val lateFeesCollected = remember(filteredJobSheets) {
        filteredJobSheets.sumOf { it.lateFee }
    }

    // Category Breakdown (guaranteed to sum to totalRevenueCollected)
    val categorySummary = remember(reconciledSaleLines) {
        val totals = LinkedHashMap<String, Double>()
        val counts = LinkedHashMap<String, Int>()
        reconciledSaleLines.forEach { line ->
            line.categoryBreakdown.forEach { (cat, amt) ->
                totals[cat] = (totals[cat] ?: 0.0) + amt
                counts[cat] = (counts[cat] ?: 0) + 1
            }
        }
        totals.entries.map { Triple(it.key, counts[it.key] ?: 0, it.value) }
            .sortedByDescending { it.third }
    }

    // Day-wise Sales Breakdown (guaranteed to sum to totalRevenueCollected)
    val dayWiseSummary = remember(reconciledSaleLines) {
        reconciledSaleLines.groupBy { it.dateKey }
            .map { (dateStr, lines) ->
                val daySales = lines.sumOf { it.netSaleAmount }
                val nilCount = lines.count { it.isNilReturn }
                Triple(dateStr, lines, Pair(daySales, nilCount))
            }
    }

    val machineFrequencies = remember(filteredJobSheets) {
        filteredJobSheets.groupBy { it.cleanMachineName }
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
            // REPORT TYPE SUB-TABS (Sales | Category | Day-wise | Stock in Hand | Opening & Closing Cash)
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ReportSubTab.entries.forEach { tab ->
                        val isSelected = selectedTab == tab
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedTab = tab },
                            label = {
                                Text(
                                    text = tab.label,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = BabaRed,
                                selectedLabelColor = Color.White
                            ),
                            modifier = Modifier.testTag(tab.tag)
                        )
                    }
                }
            }

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

                        if (filterMode == ReportFilterMode.SPECIFIC_MONTH) {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                            }
                        }

                        if (filterMode == ReportFilterMode.CUSTOM_DATES) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
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
                                        Text("From Date", fontSize = 10.sp, color = BabaGray, fontWeight = FontWeight.Bold)
                                        Text(
                                            text = displayDateFormatter.format(Date(customStartDateMillis)),
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = BabaDark
                                        )
                                    }
                                }

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
                                        Text("To Date", fontSize = 10.sp, color = BabaGray, fontWeight = FontWeight.Bold)
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
                        title = "Net Sales Revenue",
                        value = "₹${totalRevenueCollected.toInt()}",
                        subtitle = "Closed & returned rentals",
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

            // TAB 1: SALES REPORT
            if (selectedTab == ReportSubTab.SALES) {
                item {
                    Text(
                        text = "Sales Report Ledger ($periodLabel)",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = BabaDark
                    )
                }

                if (reconciledSaleLines.isEmpty()) {
                    item {
                        EmptyReportBox("No job sheets found for $periodLabel")
                    }
                } else {
                    items(reconciledSaleLines, key = { it.jobId }) { line ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color.White,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { line.sourceJob?.let(onViewReceipt) }
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(text = line.jobId, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = BabaRed)
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = when {
                                                line.isNilReturn -> BabaOverdueLight
                                                line.status == "closed" || line.status == "returned" -> BabaSuccessLight
                                                else -> BabaYellowLight
                                            }
                                        ) {
                                            Text(
                                                text = when {
                                                    line.isNilReturn -> "NIL RETURN"
                                                    line.status == "closed" || line.status == "returned" -> "CLOSED"
                                                    else -> line.status.uppercase()
                                                },
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = when {
                                                    line.isNilReturn -> BabaOverdue
                                                    line.status == "closed" || line.status == "returned" -> BabaSuccess
                                                    else -> BabaYellowDark
                                                },
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }

                                    Text(
                                        text = if (line.isNilReturn) "NIL" else "₹${line.netSaleAmount.toInt()}",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Black,
                                        color = if (line.isNilReturn) BabaOverdue else BabaSuccess,
                                        modifier = Modifier.testTag("sale_amount_${line.jobId}")
                                    )
                                }

                                Text(
                                    text = "${line.customerName} • ${line.machineSummary}",
                                    fontSize = 12.sp,
                                    color = BabaDark,
                                    fontWeight = FontWeight.SemiBold
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Mode: ${line.paymentMode} • Disc: ₹${line.discount.toInt()} • Adv: ₹${line.depositAmount.toInt()}",
                                        fontSize = 11.sp,
                                        color = BabaGray
                                    )
                                    Text(
                                        text = line.dateKey,
                                        fontSize = 11.sp,
                                        color = BabaGray
                                    )
                                }

                                Text(
                                    text = "Created by: ${line.createdBy}  |  Closed by: ${line.closedBy}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = BabaDark
                                )

                                if (line.returnRemark.isNotBlank()) {
                                    Text(
                                        text = "Remark: ${line.returnRemark}",
                                        fontSize = 11.sp,
                                        color = BabaOverdue,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }

                // Most Rented Machinery summary at bottom of Sales tab
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
                                        Text("#${index + 1}  $name", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = BabaDark)
                                        Text("$qty units", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = BabaRed)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // TAB 2: CATEGORY REPORT
            if (selectedTab == ReportSubTab.CATEGORY) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Category-wise Sales ($periodLabel)",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = BabaDark
                        )
                        Text(
                            text = "Total: ₹${categorySummary.sumOf { it.third }.toInt()}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black,
                            color = BabaSuccess
                        )
                    }
                }

                if (categorySummary.isEmpty()) {
                    item { EmptyReportBox("No category sales recorded for $periodLabel") }
                } else {
                    items(categorySummary, key = { it.first }) { (categoryName, jobCount, catSales) ->
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(categoryName, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = BabaDark)
                                    Text("$jobCount job sheet(s)", fontSize = 11.sp, color = BabaGray)
                                }
                                Text(
                                    text = if (catSales == 0.0) "NIL (₹0)" else "₹${catSales.toInt()}",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 16.sp,
                                    color = if (catSales == 0.0) BabaGray else BabaSuccess
                                )
                            }
                        }
                    }
                }
            }

            // TAB 3: DAY-WISE SALES REPORT
            if (selectedTab == ReportSubTab.DAY_WISE) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Day-wise Sales Report ($periodLabel)",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = BabaDark
                        )
                        Text(
                            text = "Total: ₹${dayWiseSummary.sumOf { it.third.first }.toInt()}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black,
                            color = BabaSuccess
                        )
                    }
                }

                if (dayWiseSummary.isEmpty()) {
                    item { EmptyReportBox("No day-wise sales recorded for $periodLabel") }
                } else {
                    items(dayWiseSummary, key = { it.first }) { (dateStr, linesForDay, totalsPair) ->
                        val (dayTotal, nilCount) = totalsPair
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(dateStr, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = BabaDark)
                                        Text(
                                            text = "${linesForDay.size} job(s)" + if (nilCount > 0) " • $nilCount NIL return(s)" else "",
                                            fontSize = 11.sp,
                                            color = BabaGray
                                        )
                                    }
                                    Text(
                                        text = if (dayTotal == 0.0 && nilCount > 0) "NIL" else "₹${dayTotal.toInt()}",
                                        fontWeight = FontWeight.Black,
                                        fontSize = 16.sp,
                                        color = if (dayTotal == 0.0 && nilCount > 0) BabaOverdue else BabaSuccess
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // TAB 4: STOCK IN HAND REPORT
            if (selectedTab == ReportSubTab.STOCK) {
                item {
                    val totalUnits = machines.sumOf { it.totalQty }
                    val availableUnits = machines.sumOf { it.availableQty }
                    val maintUnits = machines.sumOf { it.maintenanceQty }
                    val rentedUnits = (totalUnits - availableUnits - maintUnits).coerceAtLeast(0)

                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Total Fleet", fontSize = 11.sp, color = BabaGray)
                                Text("$totalUnits", fontSize = 18.sp, fontWeight = FontWeight.Black, color = BabaDark)
                            }
                            Column {
                                Text("Stock In Hand", fontSize = 11.sp, color = BabaSuccess)
                                Text("$availableUnits", fontSize = 18.sp, fontWeight = FontWeight.Black, color = BabaSuccess)
                            }
                            Column {
                                Text("Rented Out", fontSize = 11.sp, color = BabaRed)
                                Text("$rentedUnits", fontSize = 18.sp, fontWeight = FontWeight.Black, color = BabaRed)
                            }
                            Column {
                                Text("Maintenance", fontSize = 11.sp, color = BabaYellowDark)
                                Text("$maintUnits", fontSize = 18.sp, fontWeight = FontWeight.Black, color = BabaYellowDark)
                            }
                        }
                    }
                }

                items(machines, key = { it.id }) { mach ->
                    val rentedOut = (mach.totalQty - mach.availableQty - mach.maintenanceQty).coerceAtLeast(0)
                    val catName = categories.firstOrNull { it.id == mach.categoryId }?.name ?: "Equipment"
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(mach.name, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = BabaDark)
                                Text(
                                    text = "$catName • ₹${mach.currentRate.toInt()}/day • Rented: $rentedOut • Maint: ${mach.maintenanceQty}",
                                    fontSize = 11.sp,
                                    color = BabaGray
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (mach.availableQty > 0) BabaSuccessLight else BabaOverdueLight
                            ) {
                                Text(
                                    text = "In Hand: ${mach.availableQty}/${mach.totalQty}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (mach.availableQty > 0) BabaSuccess else BabaOverdue,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            }

            // TAB 5: OPENING & CLOSING CASH REPORT
            if (selectedTab == ReportSubTab.CASH) {
                item {
                    val todayKey = dayKeyFormatter.format(Date())
                    val todayExpenseDate = expenseDateFormatter.format(Date())
                    val todayRecord = dailyCashList.firstOrNull { it.date == todayKey }
                    val openingCashToday = todayRecord?.openingCash ?: 0.0

                    val startOfToday = Calendar.getInstance().apply {
                        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                    }.timeInMillis

                    val todayCashPayments = payments.filter { it.timestamp >= startOfToday && it.method.equals("cash", ignoreCase = true) }
                    val cashInToday = todayCashPayments.filter { it.type != "refund" && it.type != "return_nil" }.sumOf { it.amount }
                    val cashRefundsToday = todayCashPayments.filter { it.type == "refund" }.sumOf { it.amount }
                    val todayExpensesTotal = expenses.filter { it.date == todayExpenseDate }.sumOf { it.amount }
                    val expectedClosingCash = openingCashToday + cashInToday - cashRefundsToday - todayExpensesTotal

                    var openingInput by remember(openingCashToday) { mutableStateOf(if (openingCashToday > 0) openingCashToday.toInt().toString() else "") }
                    var closingInput by remember(todayRecord?.closingCash, expectedClosingCash) {
                        val initial = todayRecord?.closingCash ?: expectedClosingCash.coerceAtLeast(0.0)
                        mutableStateOf(initial.toInt().toString())
                    }

                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Today's Opening & Closing Cash ($todayKey)", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = BabaDark)

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Opening Cash:", fontSize = 13.sp, color = BabaGray)
                                Text("₹${openingCashToday.toInt()}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = BabaDark)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("+ Cash Received Today (Adv + Settlements):", fontSize = 13.sp, color = BabaSuccess)
                                Text("₹${cashInToday.toInt()}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = BabaSuccess)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("- Cash Refunds Today:", fontSize = 13.sp, color = BabaOverdue)
                                Text("₹${cashRefundsToday.toInt()}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = BabaOverdue)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("- Daily Shop Expenses Today:", fontSize = 13.sp, color = BabaOverdue)
                                Text("₹${todayExpensesTotal.toInt()}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = BabaOverdue)
                            }
                            HorizontalDivider(color = BabaBorder)
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Expected Closing Cash in Drawer:", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = BabaDark)
                                Text("₹${expectedClosingCash.toInt()}", fontSize = 16.sp, fontWeight = FontWeight.Black, color = BabaSuccess)
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = openingInput,
                                    onValueChange = { openingInput = it.filter { c -> c.isDigit() || c == '.' } },
                                    label = { Text("Opening Cash (₹)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("opening_cash_input")
                                )
                                Button(
                                    onClick = {
                                        val amt = openingInput.toDoubleOrNull() ?: 0.0
                                        onSaveOpeningCash(todayKey, amt)
                                        Toast.makeText(context, "Opening cash saved", Toast.LENGTH_SHORT).show()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = BabaRed),
                                    modifier = Modifier.testTag("save_opening_cash_btn")
                                ) {
                                    Text("Save Open", fontSize = 12.sp)
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = closingInput,
                                    onValueChange = { closingInput = it.filter { c -> c.isDigit() || c == '.' } },
                                    label = { Text("Closing Cash (₹)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("closing_cash_input")
                                )
                                Button(
                                    onClick = {
                                        val amt = closingInput.toDoubleOrNull() ?: 0.0
                                        onSaveClosingCash(todayKey, amt)
                                        Toast.makeText(context, "Closing cash saved", Toast.LENGTH_SHORT).show()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = BabaSuccess),
                                    modifier = Modifier.testTag("save_closing_cash_btn")
                                ) {
                                    Text("Save Close", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }

                items(dailyCashList, key = { it.date }) { cashEntry ->
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(cashEntry.date, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = BabaDark)
                                Text("Updated by ${cashEntry.enteredByName.ifBlank { "Staff" }}", fontSize = 11.sp, color = BabaGray)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Opening: ₹${cashEntry.openingCash.toInt()}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = BabaDark)
                                Text(
                                    text = "Closing: " + (cashEntry.closingCash?.let { "₹${it.toInt()}" } ?: "Open"),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = BabaSuccess
                                )
                            }
                        }
                    }
                }
            }
        }
    }

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
private fun EmptyReportBox(message: String) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = Color.White,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(modifier = Modifier.padding(24.dp), contentAlignment = Alignment.Center) {
            Text(text = message, fontSize = 13.sp, color = BabaGray)
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
