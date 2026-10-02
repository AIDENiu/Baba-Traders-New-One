package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ActivityLogEntity
import com.example.data.model.JobSheetEntity
import com.example.data.model.UserEntity
import com.example.ui.components.BabaBrandTopBar
import com.example.ui.theme.*
import com.example.ui.viewmodel.DashboardStats
import com.example.ui.viewmodel.OwnerAlertNotification
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun OwnerDashboardScreen(
    currentUser: UserEntity,
    stats: DashboardStats,
    activeJobs: List<JobSheetEntity>,
    recentLogs: List<ActivityLogEntity>,
    alertNotification: OwnerAlertNotification?,
    onDismissAlert: () -> Unit,
    onNavigateToNewJob: () -> Unit,
    onNavigateToActiveJobs: () -> Unit,
    onNavigateToMachines: () -> Unit,
    onNavigateToCustomers: () -> Unit,
    onNavigateToReports: () -> Unit,
    onNavigateToStaff: () -> Unit,
    onNavigateToActivityLog: () -> Unit,
    onReturnJobClick: (JobSheetEntity) -> Unit,
    onViewReceiptClick: (JobSheetEntity) -> Unit,
    onSwitchUser: () -> Unit
) {
    val now = remember { System.currentTimeMillis() }
    val overdueJobs = remember(activeJobs) {
        activeJobs.filter { !it.isDeleted && (it.status == "overdue" || (it.status == "active" && it.dueTime < now)) }
    }
    val dateFormat = remember { SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()) }

    Scaffold(
        topBar = {
            BabaBrandTopBar(
                currentUser = currentUser,
                onSwitchUser = onSwitchUser,
                alertNotification = alertNotification,
                onDismissAlert = onDismissAlert
            )
        },
        containerColor = BabaBg
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Owner Welcome & Shop Status
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Shop Overview",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            color = BabaDark
                        )
                        Text(
                            text = SimpleDateFormat("EEEE, dd MMMM yyyy", Locale.getDefault()).format(Date()),
                            fontSize = 12.sp,
                            color = BabaGray
                        )
                    }

                    Button(
                        onClick = onNavigateToNewJob,
                        colors = ButtonDefaults.buttonColors(containerColor = BabaRed),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("owner_quick_new_job_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "New", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("New Rental", fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Key Financial & Rental Metric Cards: Daily Sales vs. Monthly Sales
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Financial Performance",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = BabaDark,
                        modifier = Modifier.padding(top = 2.dp)
                    )

                    // Daily Sales Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        MetricCard(
                            title = "Today's Sales (Net Revenue)",
                            value = "₹${stats.todayRevenue.toInt()}",
                            subtitle = "Deposits + settlements today",
                            icon = Icons.Default.CurrencyRupee,
                            iconColor = BabaSuccess,
                            modifier = Modifier.weight(1f)
                        )
                        MetricCard(
                            title = "Today's Job Sheets",
                            value = "${stats.todayJobSheetsCount}",
                            subtitle = "Rentals processed today",
                            icon = Icons.Default.Receipt,
                            iconColor = BabaRed,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Monthly Sales Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        MetricCard(
                            title = "Monthly Sales (Net Revenue)",
                            value = "₹${stats.monthlyRevenue.toInt()}",
                            subtitle = "Total collected this month",
                            icon = Icons.AutoMirrored.Filled.TrendingUp,
                            iconColor = BabaYellowDark,
                            modifier = Modifier.weight(1f)
                        )
                        MetricCard(
                            title = "Monthly Job Sheets",
                            value = "${stats.monthlyJobSheetsCount}",
                            subtitle = "Rentals this month",
                            icon = Icons.Default.DateRange,
                            iconColor = BabaDark,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Active Rentals & Dues Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        MetricCard(
                            title = "Active Rentals",
                            value = "${stats.activeRentalsCount}",
                            subtitle = if (stats.overdueRentalsCount > 0) "${stats.overdueRentalsCount} Overdue!" else "All on track",
                            icon = Icons.Default.Build,
                            iconColor = if (stats.overdueRentalsCount > 0) BabaOverdue else BabaDark,
                            modifier = Modifier.weight(1f)
                        )
                        MetricCard(
                            title = "Full Customer Dues",
                            value = "₹${stats.totalCustomerDues.toInt()}",
                            subtitle = "Outstanding balances",
                            icon = Icons.Default.AccountBalanceWallet,
                            iconColor = BabaYellowDark,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Staff Accountability Metrics (Checkout & Handling)
            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
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
                                Icon(Icons.Default.People, contentDescription = "Staff", tint = BabaRed, modifier = Modifier.size(18.dp))
                            }
                            Column {
                                Text(
                                    text = "Staff Performance & Handling",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = BabaDark
                                )
                                Text(
                                    text = "Accountability breakdown for billing & equipment handover",
                                    fontSize = 11.sp,
                                    color = BabaGray
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        if (stats.staffPerformance.isEmpty()) {
                            Text("No staff activity recorded yet", fontSize = 12.sp, color = BabaGray)
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                stats.staffPerformance.forEach { staff ->
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = BabaLightGray,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
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
                                                            .size(28.dp)
                                                            .background(if (staff.role == "owner") BabaRed else BabaDark, CircleShape),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Text(
                                                            text = staff.staffName.take(1).uppercase(),
                                                            color = Color.White,
                                                            fontSize = 12.sp,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }
                                                    Text(
                                                        text = staff.staffName,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 14.sp,
                                                        color = BabaDark
                                                    )
                                                }
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = if (staff.role == "owner") BabaRedLight else Color.White
                                                ) {
                                                    Text(
                                                        text = staff.role.uppercase(),
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (staff.role == "owner") BabaRed else BabaGray,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(8.dp))

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                // Checkout Agent metric
                                                Surface(
                                                    shape = RoundedCornerShape(8.dp),
                                                    color = Color.White,
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(8.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.PointOfSale,
                                                            contentDescription = "Checkout",
                                                            tint = BabaSuccess,
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                        Column {
                                                            Text(
                                                                text = "Checkout Agent",
                                                                fontSize = 10.sp,
                                                                color = BabaGray,
                                                                fontWeight = FontWeight.SemiBold
                                                            )
                                                            Text(
                                                                text = "${staff.staffName}: ${staff.checkoutsToday} checkouts today",
                                                                fontSize = 11.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                color = BabaDark
                                                            )
                                                            Text(
                                                                text = "(${staff.checkoutsMonth} this month)",
                                                                fontSize = 9.sp,
                                                                color = BabaGray
                                                            )
                                                        }
                                                    }
                                                }

                                                // Handled By metric
                                                Surface(
                                                    shape = RoundedCornerShape(8.dp),
                                                    color = Color.White,
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(8.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Handyman,
                                                            contentDescription = "Handled",
                                                            tint = BabaRed,
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                        Column {
                                                            Text(
                                                                text = "Handled By",
                                                                fontSize = 10.sp,
                                                                color = BabaGray,
                                                                fontWeight = FontWeight.SemiBold
                                                            )
                                                            Text(
                                                                text = "${staff.staffName}: ${staff.handledToday} handovers today",
                                                                fontSize = 11.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                color = BabaDark
                                                            )
                                                            Text(
                                                                text = "(${staff.handledMonth} this month)",
                                                                fontSize = 9.sp,
                                                                color = BabaGray
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
            }

            // Quick Nav Action Grid
            item {
                Text(
                    text = "Shop Management",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = BabaDark,
                    modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ShopActionTile(
                        title = "Active Orders",
                        subtitle = "${stats.activeRentalsCount} active",
                        icon = Icons.AutoMirrored.Filled.Assignment,
                        color = BabaRed,
                        modifier = Modifier.weight(1f),
                        onClick = onNavigateToActiveJobs
                    )
                    ShopActionTile(
                        title = "Machines",
                        subtitle = "Rates & stock",
                        icon = Icons.Default.HomeRepairService,
                        color = BabaDark,
                        modifier = Modifier.weight(1f),
                        onClick = onNavigateToMachines
                    )
                    ShopActionTile(
                        title = "Customers",
                        subtitle = "Rental history",
                        icon = Icons.Default.People,
                        color = BabaYellowDark,
                        modifier = Modifier.weight(1f),
                        onClick = onNavigateToCustomers
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ShopActionTile(
                        title = "Reports & CSV",
                        subtitle = "Export data",
                        icon = Icons.Default.Assessment,
                        color = BabaSuccess,
                        modifier = Modifier.weight(1f),
                        onClick = onNavigateToReports
                    )
                    ShopActionTile(
                        title = "Manage Staff",
                        subtitle = "Workers & PINs",
                        icon = Icons.Default.Badge,
                        color = BabaRedDark,
                        modifier = Modifier.weight(1f),
                        onClick = onNavigateToStaff
                    )
                    ShopActionTile(
                        title = "Activity Log",
                        subtitle = "Staff audit trail",
                        icon = Icons.Default.History,
                        color = BabaDark,
                        modifier = Modifier.weight(1f),
                        onClick = onNavigateToActivityLog
                    )
                }
            }

            // Overdue Rentals Section (if any exist)
            if (overdueJobs.isNotEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = BabaOverdueLight),
                        border = androidx.compose.foundation.BorderStroke(1.dp, BabaOverdue)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(Icons.Default.Warning, contentDescription = "Alert", tint = BabaOverdue, modifier = Modifier.size(20.dp))
                                    Text(
                                        text = "Overdue Machinery (${overdueJobs.size})",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = BabaOverdue
                                    )
                                }
                                TextButton(onClick = onNavigateToActiveJobs) {
                                    Text("View All", fontSize = 12.sp, color = BabaOverdue, fontWeight = FontWeight.Bold)
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            overdueJobs.take(2).forEach { job ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color.White,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(
                                                text = "${job.customerName} • ${job.machineName}",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                                color = BabaDark
                                            )
                                            Text(
                                                text = "Due: ${dateFormat.format(Date(job.dueTime))} • Ph: ${job.customerPhone}",
                                                fontSize = 11.sp,
                                                color = BabaOverdue
                                            )
                                        }

                                        Button(
                                            onClick = { onReturnJobClick(job) },
                                            colors = ButtonDefaults.buttonColors(containerColor = BabaSuccess),
                                            shape = RoundedCornerShape(6.dp),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                        ) {
                                            Text("Return", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Recent Activity Stream
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Recent Staff Activity",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = BabaDark
                    )
                    TextButton(onClick = onNavigateToActivityLog) {
                        Text("Full Audit Log", fontSize = 12.sp, color = BabaRed)
                    }
                }
            }

            if (recentLogs.isEmpty()) {
                item {
                    Text(
                        text = "No recent activity recorded",
                        fontSize = 12.sp,
                        color = BabaGray,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            } else {
                items(recentLogs.take(5), key = { it.id }) { log ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color.White,
                        modifier = Modifier.fillMaxWidth()
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
                                    .size(32.dp)
                                    .background(
                                        when (log.action) {
                                            "created" -> BabaSuccessLight
                                            "returned" -> BabaYellowLight
                                            "deleted" -> BabaOverdueLight
                                            else -> BabaLightGray
                                        },
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = when (log.action) {
                                        "created" -> Icons.Default.Add
                                        "returned" -> Icons.Default.Check
                                        "deleted" -> Icons.Default.Delete
                                        else -> Icons.Default.Edit
                                    },
                                    contentDescription = log.action,
                                    tint = when (log.action) {
                                        "created" -> BabaSuccess
                                        "returned" -> BabaYellowDark
                                        "deleted" -> BabaOverdue
                                        else -> BabaDark
                                    },
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = log.changeSummary,
                                    fontSize = 12.sp,
                                    color = BabaDark,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = dateFormat.format(Date(log.timestamp)),
                                    fontSize = 10.sp,
                                    color = BabaGray
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MetricCard(
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    iconColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = title, fontSize = 11.sp, color = BabaGray, fontWeight = FontWeight.SemiBold)
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(iconColor.copy(alpha = 0.12f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = icon, contentDescription = title, tint = iconColor, modifier = Modifier.size(16.dp))
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                fontSize = 20.sp,
                fontWeight = FontWeight.Black,
                color = BabaDark
            )
            Text(text = subtitle, fontSize = 10.sp, color = BabaGray)
        }
    }
}

@Composable
fun ShopActionTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color.White,
        shadowElevation = 1.dp,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(color.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = title, tint = color, modifier = Modifier.size(18.dp))
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = title,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = BabaDark
            )
            Text(
                text = subtitle,
                fontSize = 9.sp,
                color = BabaGray
            )
        }
    }
}
