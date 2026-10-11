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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.JobSheetEntity
import com.example.data.model.UserEntity
import com.example.ui.components.*
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun WorkerHomeScreen(
    currentUser: UserEntity,
    activeJobs: List<JobSheetEntity>,
    onNewJobSheetClick: () -> Unit,
    onReturnJobClick: (JobSheetEntity) -> Unit,
    onAddPaymentClick: (JobSheetEntity) -> Unit,
    onViewPaymentHistoryClick: (JobSheetEntity) -> Unit,
    onViewReceiptClick: (JobSheetEntity) -> Unit,
    onSwitchUser: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val now = remember { System.currentTimeMillis() }
    val dateFormat = remember { SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()) }

    val filteredJobs = remember(activeJobs, searchQuery) {
        val nonDeleted = activeJobs.filter { !it.isDeleted && it.status != "returned" }
        if (searchQuery.trim().isEmpty()) {
            nonDeleted
        } else {
            val q = searchQuery.trim().lowercase()
            nonDeleted.filter {
                it.customerName.lowercase().contains(q) ||
                it.customerPhone.contains(q) ||
                it.id.lowercase().contains(q) ||
                it.machineName.lowercase().contains(q)
            }
        }
    }

    Scaffold(
        topBar = {
            BabaBrandTopBar(
                currentUser = currentUser,
                onSwitchUser = onSwitchUser
            )
        },
        containerColor = BabaBg
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Prominent Header Card for Quick Job Sheet (1 TAP)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                color = Color.White,
                shadowElevation = 3.dp
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Machinery Checkout & Return",
                        fontSize = 13.sp,
                        color = BabaGray,
                        fontWeight = FontWeight.Medium
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // GIGANTIC ONE-TAP QUICK JOB SHEET BUTTON
                    Button(
                        onClick = onNewJobSheetClick,
                        colors = ButtonDefaults.buttonColors(containerColor = BabaRed),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .testTag("quick_job_sheet_button")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AddCircle,
                                contentDescription = "New Job",
                                tint = BabaYellow,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "QUICK JOB SHEET",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color.White,
                                    letterSpacing = 0.5.sp
                                )
                                Text(
                                    text = "Tap to checkout machine for customer",
                                    fontSize = 11.sp,
                                    color = BabaYellow,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }

            // Active & Overdue Orders Section Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Active Rentals",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = BabaDark
                    )
                    Surface(
                        shape = CircleShape,
                        color = BabaRedLight
                    ) {
                        Text(
                            text = "${filteredJobs.size}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = BabaRed,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }

                val overdueCount = filteredJobs.count { it.status == "overdue" || (it.status == "active" && it.dueTime < now) }
                if (overdueCount > 0) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = BabaOverdueLight
                    ) {
                        Text(
                            text = "⚠ $overdueCount OVERDUE",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            color = BabaOverdue,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            // Fast Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search by customer name, phone, or job #", fontSize = 13.sp) },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = "Search", tint = BabaGray)
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear", tint = BabaGray)
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = babaTextFieldColors(),
                textStyle = babaInputTextStyle(fontSize = 14.sp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            )

            // Jobs List
            if (filteredJobs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircleOutline,
                            contentDescription = "No jobs",
                            tint = BabaGray,
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = if (searchQuery.isNotEmpty()) "No rentals match your search" else "No active machinery rentals right now",
                            fontSize = 14.sp,
                            color = BabaGray,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredJobs, key = { it.id }) { job ->
                        val isOverdue = job.status == "overdue" || (job.status == "active" && job.dueTime < now)
                        WorkerJobCard(
                            jobSheet = job,
                            isOverdue = isOverdue,
                            dateFormat = dateFormat,
                            onReturn = { onReturnJobClick(job) },
                            onAddPayment = { onAddPaymentClick(job) },
                            onViewPaymentHistory = { onViewPaymentHistoryClick(job) },
                            onViewReceipt = { onViewReceiptClick(job) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun WorkerJobCard(
    jobSheet: JobSheetEntity,
    isOverdue: Boolean,
    dateFormat: SimpleDateFormat,
    onReturn: () -> Unit,
    onAddPayment: () -> Unit,
    onViewPaymentHistory: () -> Unit,
    onViewReceipt: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (isOverdue) 1.5.dp else 0.5.dp,
                color = if (isOverdue) BabaOverdue else BabaBorder,
                shape = RoundedCornerShape(14.dp)
            )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header: Job ID + Status badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = jobSheet.id,
                        fontWeight = FontWeight.Black,
                        fontSize = 14.sp,
                        color = BabaRed
                    )
                    Text(
                        text = "• ${jobSheet.categoryName}",
                        fontSize = 11.sp,
                        color = BabaGray
                    )
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isOverdue) BabaOverdueLight else BabaYellowLight
                ) {
                    Text(
                        text = if (isOverdue) "OVERDUE" else "ACTIVE",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        color = if (isOverdue) BabaOverdue else BabaYellowDark,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Machine Name & Qty
            Text(
                text = "${jobSheet.quantity}x ${jobSheet.machineName}",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = BabaDark
            )

            // Customer Info
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(Icons.Default.Person, contentDescription = "Customer", tint = BabaGray, modifier = Modifier.size(14.dp))
                Text(
                    text = "${jobSheet.customerName} (${jobSheet.customerPhone})",
                    fontSize = 12.sp,
                    color = BabaDark
                )
            }

            // Timeline details
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Due Time", fontSize = 10.sp, color = BabaGray)
                    Text(
                        text = dateFormat.format(Date(jobSheet.dueTime)),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isOverdue) BabaOverdue else BabaDark
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Deposit Taken", fontSize = 10.sp, color = BabaGray)
                    Text(
                        text = "₹${jobSheet.depositAmount}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = BabaSuccess
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = BabaBorder)

            // Quick Actions: Mark Returned, Add Payment, Payment History, Receipt
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Mark Returned Button
                Button(
                    onClick = onReturn,
                    colors = ButtonDefaults.buttonColors(containerColor = BabaSuccess),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1.3f).testTag("return_job_${jobSheet.id}")
                ) {
                    Icon(Icons.Default.Check, contentDescription = "Return", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Return", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                // Add Extra Payment Button
                Button(
                    onClick = onAddPayment,
                    colors = ButtonDefaults.buttonColors(containerColor = BabaDark),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1.3f).testTag("add_payment_job_${jobSheet.id}")
                ) {
                    Icon(Icons.Default.Payments, contentDescription = "Add Payment", tint = BabaYellow, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("+ Pay", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                // History Icon Button
                IconButton(
                    onClick = onViewPaymentHistory,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Default.History, contentDescription = "Payment History", tint = BabaDark, modifier = Modifier.size(18.dp))
                }

                // Receipt Icon Button
                IconButton(
                    onClick = onViewReceipt,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Default.Receipt, contentDescription = "Receipt", tint = BabaDark, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}
