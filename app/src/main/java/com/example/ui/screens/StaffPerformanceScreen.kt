package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ActivityLogEntity
import com.example.data.model.JobSheetEntity
import com.example.data.model.PaymentEntity
import com.example.data.model.UserEntity
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class DetailedStaffMetrics(
    val user: UserEntity,
    // Checkout Agent metrics
    val checkoutsCount: Int,
    val checkoutsUnitsQty: Int,
    val advanceCollectedOnCheckouts: Double,
    val discountsGrantedTotal: Double,
    val discountsCount: Int,
    // Equipment Handover (Handled By) metrics
    val handoversCount: Int,
    val handoversUnitsQty: Int,
    val activeHandoversCount: Int,
    val overdueHandoversCount: Int,
    // Returns & Settlement metrics
    val returnsProcessedCount: Int,
    val finalizedRevenueOnReturns: Double,
    // Payments breakdown (from PaymentEntity)
    val totalCashCollected: Double,
    val totalOnlineCollected: Double,
    val totalRefundsIssued: Double,
    val extraPaymentsCount: Int,
    // Associated records in the selected period
    val involvedJobs: List<JobSheetEntity>,
    val recentLogs: List<ActivityLogEntity>
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StaffPerformanceScreen(
    users: List<UserEntity>,
    allJobSheets: List<JobSheetEntity>,
    payments: List<PaymentEntity>,
    activityLogs: List<ActivityLogEntity>,
    onViewReceipt: (JobSheetEntity) -> Unit,
    onNavigateToManageStaff: () -> Unit,
    onNavigateBack: () -> Unit
) {
    BackHandler(onBack = onNavigateBack)

    var selectedPeriod by remember { mutableStateOf("month") } // "today", "week", "month", "all"
    var selectedStaffId by remember { mutableStateOf<String?>(null) } // null = All Staff
    var expandedStaffIds by remember { mutableStateOf(setOf<String>()) }

    val now = remember { System.currentTimeMillis() }
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()) }

    val periodStartTimestamp = remember(selectedPeriod) {
        val cal = Calendar.getInstance()
        when (selectedPeriod) {
            "today" -> {
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                cal.timeInMillis
            }
            "week" -> {
                cal.add(Calendar.DAY_OF_YEAR, -7)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                cal.timeInMillis
            }
            "month" -> {
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                cal.timeInMillis
            }
            else -> 0L
        }
    }

    val detailedMetricsList = remember(users, allJobSheets, payments, activityLogs, periodStartTimestamp) {
        val validJobs = allJobSheets.filter { !it.isDeleted }
        val periodPayments = payments.filter { it.timestamp >= periodStartTimestamp }
        val periodLogs = activityLogs.filter { it.timestamp >= periodStartTimestamp }

        users.map { staff ->
            fun matchesCheckout(job: JobSheetEntity): Boolean {
                return job.checkoutBy == staff.id ||
                    job.checkoutByName.equals(staff.name, ignoreCase = true) ||
                    (job.checkoutByName.isEmpty() && job.handledByName.equals(staff.name, ignoreCase = true))
            }

            fun matchesHandover(job: JobSheetEntity): Boolean {
                return job.handledBy == staff.id ||
                    job.handledByName.equals(staff.name, ignoreCase = true)
            }

            fun matchesReturn(job: JobSheetEntity): Boolean {
                return job.returnedBy == staff.id ||
                    job.returnedByName.equals(staff.name, ignoreCase = true) ||
                    job.closedBy == staff.id ||
                    job.closedByName.equals(staff.name, ignoreCase = true)
            }

            val checkoutJobsInPeriod = validJobs.filter {
                it.checkoutTime >= periodStartTimestamp && matchesCheckout(it)
            }
            val handoverJobsInPeriod = validJobs.filter {
                it.checkoutTime >= periodStartTimestamp && matchesHandover(it)
            }
            val returnJobsInPeriod = validJobs.filter {
                (it.status == "returned" || it.status == "closed") &&
                    (it.returnTime ?: 0L) >= periodStartTimestamp &&
                    matchesReturn(it)
            }

            val allActiveHandovers = validJobs.filter {
                (it.status == "active" || it.status == "overdue") && matchesHandover(it)
            }
            val overdueHandovers = allActiveHandovers.filter {
                it.status == "overdue" || it.dueTime < now
            }

            val staffPayments = periodPayments.filter {
                it.handledBy == staff.id || it.handledByName.equals(staff.name, ignoreCase = true)
            }
            val cashCollected = staffPayments.filter {
                it.method.equals("cash", ignoreCase = true) && it.type != "refund" && it.type != "return_nil"
            }.sumOf { it.amount }
            val onlineCollected = staffPayments.filter {
                !it.method.equals("cash", ignoreCase = true) &&
                    !it.method.equals("no_work_done", ignoreCase = true) &&
                    it.type != "refund" && it.type != "return_nil"
            }.sumOf { it.amount }
            val refundsIssued = staffPayments.filter { it.type == "refund" }.sumOf { it.amount }
            val extraPaymentsCount = staffPayments.count { it.type == "extra" }

            val involvedJobs = (checkoutJobsInPeriod + handoverJobsInPeriod + returnJobsInPeriod)
                .distinctBy { it.id }
                .sortedByDescending { maxOf(it.checkoutTime, it.returnTime ?: 0L) }

            val staffLogs = periodLogs.filter {
                it.performedBy == staff.id ||
                    it.performedByName.equals(staff.name, ignoreCase = true) ||
                    it.handledByUid == staff.id ||
                    it.handledByName.equals(staff.name, ignoreCase = true)
            }

            DetailedStaffMetrics(
                user = staff,
                checkoutsCount = checkoutJobsInPeriod.size,
                checkoutsUnitsQty = checkoutJobsInPeriod.sumOf { it.effectiveInitialQty },
                advanceCollectedOnCheckouts = checkoutJobsInPeriod.sumOf { it.depositAmount },
                discountsGrantedTotal = checkoutJobsInPeriod.sumOf { it.discount },
                discountsCount = checkoutJobsInPeriod.count { it.discount > 0.0 },
                handoversCount = handoverJobsInPeriod.size,
                handoversUnitsQty = handoverJobsInPeriod.sumOf { it.effectiveInitialQty },
                activeHandoversCount = allActiveHandovers.size,
                overdueHandoversCount = overdueHandovers.size,
                returnsProcessedCount = returnJobsInPeriod.size,
                finalizedRevenueOnReturns = returnJobsInPeriod.sumOf { it.totalRentalFee },
                totalCashCollected = cashCollected,
                totalOnlineCollected = onlineCollected,
                totalRefundsIssued = refundsIssued,
                extraPaymentsCount = extraPaymentsCount,
                involvedJobs = involvedJobs,
                recentLogs = staffLogs
            )
        }
    }

    val filteredMetrics = remember(detailedMetricsList, selectedStaffId) {
        if (selectedStaffId == null) detailedMetricsList
        else detailedMetricsList.filter { it.user.id == selectedStaffId }
    }

    val totalShopCheckouts = remember(detailedMetricsList) {
        detailedMetricsList.sumOf { it.checkoutsCount }.coerceAtLeast(1)
    }
    val totalShopHandovers = remember(detailedMetricsList) {
        detailedMetricsList.sumOf { it.handoversCount }.coerceAtLeast(1)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Staff Performance & Handling",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )
                        Text(
                            text = "Detailed Billing, Handover & Settlement Analytics",
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("staff_performance_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    TextButton(
                        onClick = onNavigateToManageStaff,
                        colors = ButtonDefaults.textButtonColors(contentColor = BabaYellow),
                        modifier = Modifier.testTag("staff_performance_manage_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Badge,
                            contentDescription = "Manage Staff",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Staff & PINs", fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
            // 1. Period Selector Tabs
            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "TIME PERIOD FILTER",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = BabaGray
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val periods = listOf(
                                "today" to "Today",
                                "week" to "Last 7 Days",
                                "month" to "This Month",
                                "all" to "All Time"
                            )
                            periods.forEach { (key, label) ->
                                val isSelected = selectedPeriod == key
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSelected) BabaRed else BabaLightGray,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { selectedPeriod = key }
                                        .testTag("period_filter_$key")
                                ) {
                                    Box(
                                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = label,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) Color.White else BabaDark,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }

                        // Staff Member Quick Filter Row
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FilterChip(
                                selected = selectedStaffId == null,
                                onClick = { selectedStaffId = null },
                                label = { Text("All Staff (${users.size})", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = BabaDark,
                                    selectedLabelColor = Color.White
                                )
                            )
                            users.forEach { u ->
                                FilterChip(
                                    selected = selectedStaffId == u.id,
                                    onClick = {
                                        selectedStaffId = if (selectedStaffId == u.id) null else u.id
                                    },
                                    label = {
                                        Text(
                                            text = "${u.name} (${u.role.uppercase()})",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = BabaRed,
                                        selectedLabelColor = Color.White
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // 2. Overall Shop Handling Summary Banner
            item {
                val totalCheckouts = filteredMetrics.sumOf { it.checkoutsCount }
                val totalUnitsCheckedOut = filteredMetrics.sumOf { it.checkoutsUnitsQty }
                val totalHandovers = filteredMetrics.sumOf { it.handoversCount }
                val totalUnitsHandedOver = filteredMetrics.sumOf { it.handoversUnitsQty }
                val totalReturns = filteredMetrics.sumOf { it.returnsProcessedCount }
                val totalCashAndOnline = filteredMetrics.sumOf { it.totalCashCollected + it.totalOnlineCollected }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SummaryStatMiniCard(
                            title = "Total Checkouts",
                            value = "$totalCheckouts jobs",
                            subValue = "$totalUnitsCheckedOut machines billed",
                            icon = Icons.Default.PointOfSale,
                            accentColor = BabaSuccess,
                            modifier = Modifier.weight(1f)
                        )
                        SummaryStatMiniCard(
                            title = "Equipment Handovers",
                            value = "$totalHandovers jobs",
                            subValue = "$totalUnitsHandedOver units handed over",
                            icon = Icons.Default.Handyman,
                            accentColor = BabaRed,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SummaryStatMiniCard(
                            title = "Returns Closed",
                            value = "$totalReturns jobs",
                            subValue = "Inspected & restocked",
                            icon = Icons.Default.AssignmentTurnedIn,
                            accentColor = BabaYellowDark,
                            modifier = Modifier.weight(1f)
                        )
                        SummaryStatMiniCard(
                            title = "Payments Handled",
                            value = "₹${totalCashAndOnline.toInt()}",
                            subValue = "Cash + Online collected",
                            icon = Icons.Default.CurrencyRupee,
                            accentColor = BabaDark,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // 3. Detailed Per-Staff Cards
            if (filteredMetrics.isEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.PeopleOutline,
                                contentDescription = null,
                                tint = BabaGray,
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "No staff members found",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = BabaDark
                            )
                        }
                    }
                }
            } else {
                items(filteredMetrics, key = { it.user.id }) { metric ->
                    val isExpanded = expandedStaffIds.contains(metric.user.id) || selectedStaffId == metric.user.id
                    DetailedStaffPerformanceCard(
                        metric = metric,
                        totalShopCheckouts = totalShopCheckouts,
                        totalShopHandovers = totalShopHandovers,
                        isExpanded = isExpanded,
                        dateFormat = dateFormat,
                        onToggleExpand = {
                            expandedStaffIds = if (expandedStaffIds.contains(metric.user.id)) {
                                expandedStaffIds - metric.user.id
                            } else {
                                expandedStaffIds + metric.user.id
                            }
                        },
                        onViewReceipt = onViewReceipt
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryStatMiniCard(
    title: String,
    value: String,
    subValue: String,
    icon: ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(accentColor.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = accentColor,
                    modifier = Modifier.size(18.dp)
                )
            }
            Column {
                Text(
                    text = title,
                    fontSize = 10.sp,
                    color = BabaGray,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = value,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black,
                    color = BabaDark
                )
                Text(
                    text = subValue,
                    fontSize = 10.sp,
                    color = BabaGray
                )
            }
        }
    }
}

@Composable
private fun DetailedStaffPerformanceCard(
    metric: DetailedStaffMetrics,
    totalShopCheckouts: Int,
    totalShopHandovers: Int,
    isExpanded: Boolean,
    dateFormat: SimpleDateFormat,
    onToggleExpand: () -> Unit,
    onViewReceipt: (JobSheetEntity) -> Unit
) {
    val staff = metric.user
    var selectedDetailTab by remember { mutableIntStateOf(0) } // 0 = Job Sheets, 1 = Activity Audit

    val checkoutShare = (metric.checkoutsCount.toFloat() / totalShopCheckouts.toFloat()).coerceIn(0f, 1f)
    val handoverShare = (metric.handoversCount.toFloat() / totalShopHandovers.toFloat()).coerceIn(0f, 1f)

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("staff_performance_card_${staff.id}")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Staff Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .background(
                                if (staff.role.equals("owner", ignoreCase = true)) BabaRed else BabaDark,
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = staff.name.take(1).uppercase(),
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = staff.name,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Black,
                                color = BabaDark
                            )
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = if (staff.role.equals("owner", ignoreCase = true)) BabaRedLight else BabaLightGray
                            ) {
                                Text(
                                    text = staff.role.uppercase(),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (staff.role.equals("owner", ignoreCase = true)) BabaRed else BabaDark,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            if (!staff.isActive) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = BabaOverdueLight
                                ) {
                                    Text(
                                        text = "DISABLED",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = BabaOverdue,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        Text(
                            text = "ID: @${staff.phoneOrUsername.ifEmpty { staff.name.lowercase() }} • Max Discount: ₹${staff.maxDiscountAmount.toInt()}",
                            fontSize = 11.sp,
                            color = BabaGray
                        )
                    }
                }

                // Active & Overdue Badge
                Column(horizontalAlignment = Alignment.End) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (metric.overdueHandoversCount > 0) BabaOverdueLight else BabaSuccessLight
                    ) {
                        Text(
                            text = if (metric.overdueHandoversCount > 0) {
                                "${metric.overdueHandoversCount} Overdue / ${metric.activeHandoversCount} Active"
                            } else {
                                "${metric.activeHandoversCount} Active Rentals"
                            },
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (metric.overdueHandoversCount > 0) BabaOverdue else BabaSuccess,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Primary Accountability Breakdown: Checkout Agent vs Equipment Handover
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // 1. Checkout Agent Box
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = BabaLightGray,
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PointOfSale,
                                contentDescription = "Checkout Agent",
                                tint = BabaSuccess,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "CHECKOUT AGENT",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = BabaSuccess
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "${metric.checkoutsCount} Job Sheets",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Black,
                            color = BabaDark
                        )
                        Text(
                            text = "${metric.checkoutsUnitsQty} machines billed",
                            fontSize = 11.sp,
                            color = BabaGray
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { checkoutShare },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(5.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = BabaSuccess,
                            trackColor = Color.White
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Advance In: ₹${metric.advanceCollectedOnCheckouts.toInt()}",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = BabaDark
                        )
                    }
                }

                // 2. Equipment Handover (Handled By) Box
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = BabaLightGray,
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Handyman,
                                contentDescription = "Handled By",
                                tint = BabaRed,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "HANDLED BY",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = BabaRed
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "${metric.handoversCount} Handovers",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Black,
                            color = BabaDark
                        )
                        Text(
                            text = "${metric.handoversUnitsQty} units dispatched",
                            fontSize = 11.sp,
                            color = BabaGray
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { handoverShare },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(5.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = BabaRed,
                            trackColor = Color.White
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Returns Closed: ${metric.returnsProcessedCount} jobs",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = BabaDark
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Secondary Financial & Operational Accountability Grid (4 Pillars)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MiniDetailPill(
                    label = "Cash Collected",
                    value = "₹${metric.totalCashCollected.toInt()}",
                    sub = "Refunds: ₹${metric.totalRefundsIssued.toInt()}",
                    modifier = Modifier.weight(1f)
                )
                MiniDetailPill(
                    label = "Online / UPI",
                    value = "₹${metric.totalOnlineCollected.toInt()}",
                    sub = "${metric.extraPaymentsCount} extra pmts",
                    modifier = Modifier.weight(1f)
                )
                MiniDetailPill(
                    label = "Discounts Given",
                    value = "₹${metric.discountsGrantedTotal.toInt()}",
                    sub = "Across ${metric.discountsCount} jobs",
                    modifier = Modifier.weight(1f)
                )
                MiniDetailPill(
                    label = "Closed Revenue",
                    value = "₹${metric.finalizedRevenueOnReturns.toInt()}",
                    sub = "${metric.returnsProcessedCount} returns",
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = BabaLightGray)
            Spacer(modifier = Modifier.height(6.dp))

            // Expand / Collapse Button for Job-by-Job & Audit History
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onToggleExpand() }
                    .padding(vertical = 6.dp, horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isExpanded) {
                        "Hide Detailed Job Sheets & Audit Trail"
                    } else {
                        "View ${metric.involvedJobs.size} Handled Job Sheets & ${metric.recentLogs.size} Audit Logs"
                    },
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = BabaRed
                )
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = "Toggle details",
                    tint = BabaRed
                )
            }

            // Expanded Job-by-Job & Activity Breakdown
            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val tab0Selected = selectedDetailTab == 0
                        val tab1Selected = selectedDetailTab == 1
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (tab0Selected) BabaDark else BabaLightGray,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { selectedDetailTab = 0 }
                        ) {
                            Box(
                                modifier = Modifier.padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Handled Job Sheets (${metric.involvedJobs.size})",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (tab0Selected) Color.White else BabaDark
                                )
                            }
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (tab1Selected) BabaDark else BabaLightGray,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { selectedDetailTab = 1 }
                        ) {
                            Box(
                                modifier = Modifier.padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Audit Log (${metric.recentLogs.size})",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (tab1Selected) Color.White else BabaDark
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    if (selectedDetailTab == 0) {
                        if (metric.involvedJobs.isEmpty()) {
                            Text(
                                text = "No job sheets handled by ${staff.name} in this period.",
                                fontSize = 12.sp,
                                color = BabaGray,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                metric.involvedJobs.take(15).forEach { job ->
                                    val didCheckout = job.checkoutBy == staff.id ||
                                        job.checkoutByName.equals(staff.name, ignoreCase = true)
                                    val didHandover = job.handledBy == staff.id ||
                                        job.handledByName.equals(staff.name, ignoreCase = true)
                                    val didReturn = job.returnedBy == staff.id ||
                                        job.returnedByName.equals(staff.name, ignoreCase = true) ||
                                        job.closedBy == staff.id ||
                                        job.closedByName.equals(staff.name, ignoreCase = true)

                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = BabaBg,
                                        border = androidx.compose.foundation.BorderStroke(1.dp, BabaBorder.copy(alpha = 0.4f)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = job.id,
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Black,
                                                        color = BabaRed
                                                    )
                                                    if (didCheckout) {
                                                        RoleActionBadge("CHECKOUT", BabaSuccessLight, BabaSuccess)
                                                    }
                                                    if (didHandover) {
                                                        RoleActionBadge("HANDOVER", BabaRedLight, BabaRed)
                                                    }
                                                    if (didReturn) {
                                                        RoleActionBadge("RETURNED", BabaYellowLight, BabaYellowDark)
                                                    }
                                                }
                                                TextButton(
                                                    onClick = { onViewReceipt(job) },
                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                                    modifier = Modifier.height(26.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.ReceiptLong,
                                                        contentDescription = "Receipt",
                                                        tint = BabaDark,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("Receipt", fontSize = 10.sp, color = BabaDark, fontWeight = FontWeight.Bold)
                                                }
                                            }

                                            Text(
                                                text = "${job.customerName} (${job.customerPhone})",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = BabaDark
                                            )
                                            Text(
                                                text = "${job.machineName} • Qty: ${job.effectiveInitialQty} • Rate: ₹${job.rateUsed.toInt()}/day",
                                                fontSize = 11.sp,
                                                color = BabaDark,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    text = "Out: ${dateFormat.format(Date(job.checkoutTime))}",
                                                    fontSize = 10.sp,
                                                    color = BabaGray
                                                )
                                                Text(
                                                    text = "Adv: ₹${job.depositAmount.toInt()} | Disc: ₹${job.discount.toInt()}",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = BabaDark
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        if (metric.recentLogs.isEmpty()) {
                            Text(
                                text = "No audit log entries recorded for ${staff.name} in this period.",
                                fontSize = 12.sp,
                                color = BabaGray,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                metric.recentLogs.take(15).forEach { log ->
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = BabaBg,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    text = log.action.uppercase(),
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = BabaRed
                                                )
                                                Text(
                                                    text = dateFormat.format(Date(log.timestamp)),
                                                    fontSize = 10.sp,
                                                    color = BabaGray
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = log.changeSummary,
                                                fontSize = 11.sp,
                                                color = BabaDark
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniDetailPill(
    label: String,
    value: String,
    sub: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = BabaBg,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = label,
                fontSize = 9.sp,
                color = BabaGray,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = value,
                fontSize = 13.sp,
                fontWeight = FontWeight.Black,
                color = BabaDark,
                maxLines = 1
            )
            Text(
                text = sub,
                fontSize = 8.5.sp,
                color = BabaGray,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun RoleActionBadge(
    text: String,
    bgColor: Color,
    textColor: Color
) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = bgColor
    ) {
        Text(
            text = text,
            fontSize = 8.5.sp,
            fontWeight = FontWeight.Bold,
            color = textColor,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
        )
    }
}
