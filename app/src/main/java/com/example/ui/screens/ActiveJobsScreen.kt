package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.JobSheetEntity
import com.example.data.model.UserEntity
import com.example.data.repository.ReturnCalculation
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.util.PdfGenerator
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveJobsScreen(
    currentUser: UserEntity,
    allJobSheets: List<JobSheetEntity>,
    onReturnJob: (JobSheetEntity) -> Unit,
    onAddPayment: (JobSheetEntity) -> Unit,
    onViewPaymentHistory: (JobSheetEntity) -> Unit,
    onViewReceipt: (JobSheetEntity) -> Unit,
    onNavigateBack: () -> Unit
) {
    BackHandler { onNavigateBack() }
    var selectedTab by remember { mutableStateOf(0) } // 0: Active, 1: Overdue, 2: Returned/Closed
    var searchQuery by remember { mutableStateOf("") }
    val now = remember { System.currentTimeMillis() }
    val dateFormat = remember { SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()) }

    val nonDeleted = remember(allJobSheets) { allJobSheets.filter { !it.isDeleted } }

    val filteredList = remember(nonDeleted, selectedTab, searchQuery) {
        val tabFiltered = when (selectedTab) {
            0 -> nonDeleted.filter { it.status == "active" || it.status == "overdue" }
            1 -> nonDeleted.filter { it.status == "overdue" || (it.status == "active" && it.dueTime < now) }
            2 -> nonDeleted.filter { it.status == "returned" || it.status == "closed" }
            else -> nonDeleted
        }

        if (searchQuery.trim().isEmpty()) {
            tabFiltered
        } else {
            val q = searchQuery.trim().lowercase()
            tabFiltered.filter {
                it.customerName.lowercase().contains(q) ||
                it.customerPhone.contains(q) ||
                it.id.lowercase().contains(q) ||
                it.machineName.lowercase().contains(q)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Rental Orders", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BabaRed)
            )
        },
        containerColor = BabaBg
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Search Input
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search by customer, phone, or job #", fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = BabaGray) },
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
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )

            // Tabs: Active, Overdue, Returned
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.White,
                contentColor = BabaRed
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        Text(
                            text = "Active (${nonDeleted.count { it.status == "active" || it.status == "overdue" }})",
                            fontSize = 12.sp,
                            fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        val ovCount = nonDeleted.count { it.status == "overdue" || (it.status == "active" && it.dueTime < now) }
                        Text(
                            text = "Overdue ($ovCount)",
                            fontSize = 12.sp,
                            color = if (ovCount > 0) BabaOverdue else BabaDark,
                            fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = {
                        Text(
                            text = "Returned (${nonDeleted.count { it.status == "returned" || it.status == "closed" }})",
                            fontSize = 12.sp,
                            fontWeight = if (selectedTab == 2) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                )
            }

            // List Content
            if (filteredList.isEmpty()) {
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
                            contentDescription = "No rental orders",
                            tint = BabaGray,
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = if (searchQuery.isNotEmpty()) "No rental orders match your search" else "No rental orders found",
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
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredList, key = { it.id }) { job ->
                        val isOverdue = job.status == "overdue" || (job.status == "active" && job.dueTime < now)
                        OrderCard(
                            jobSheet = job,
                            isOverdue = isOverdue,
                            dateFormat = dateFormat,
                            onReturn = { onReturnJob(job) },
                            onAddPayment = { onAddPayment(job) },
                            onViewPaymentHistory = { onViewPaymentHistory(job) },
                            onViewReceipt = { onViewReceipt(job) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun OrderCard(
    jobSheet: JobSheetEntity,
    isOverdue: Boolean,
    dateFormat: SimpleDateFormat,
    onReturn: () -> Unit,
    onAddPayment: () -> Unit,
    onViewPaymentHistory: () -> Unit,
    onViewReceipt: () -> Unit
) {
    val context = LocalContext.current
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (isOverdue) 1.5.dp else 0.5.dp,
                color = if (isOverdue) BabaOverdue else BabaBorder,
                shape = RoundedCornerShape(14.dp)
            )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
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
                    Text(text = "• ${jobSheet.categoryName}", fontSize = 11.sp, color = BabaGray)
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = when (jobSheet.status) {
                        "returned", "closed" -> BabaSuccessLight
                        else -> if (isOverdue) BabaOverdueLight else BabaYellowLight
                    }
                ) {
                    Text(
                        text = when (jobSheet.status) {
                            "returned", "closed" -> "RETURNED"
                            else -> if (isOverdue) "OVERDUE" else "ACTIVE"
                        },
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        color = when (jobSheet.status) {
                            "returned", "closed" -> BabaSuccess
                            else -> if (isOverdue) BabaOverdue else BabaYellowDark
                        },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            val quantityDisplay = if (jobSheet.initialQuantity > 0 && jobSheet.returnedQuantity > 0) {
                "${jobSheet.remainingActiveQty}x ${jobSheet.machineName} (${jobSheet.returnedQuantity} of ${jobSheet.initialQuantity} returned)"
            } else {
                "${jobSheet.quantity}x ${jobSheet.machineName}"
            }

            Text(
                text = quantityDisplay,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = BabaDark
            )

            Text(
                text = "Customer: ${jobSheet.customerName} • ${jobSheet.customerPhone}",
                fontSize = 12.sp,
                color = BabaDark,
                modifier = Modifier.padding(vertical = 2.dp)
            )

            // Attribution Tag
            val isJobClosed = jobSheet.status == "returned" || jobSheet.status == "closed"
            val attributionText = if (isJobClosed && jobSheet.returnedByName.isNotEmpty()) {
                "${jobSheet.customerName}'s machine — checked out by ${jobSheet.checkoutByName.ifEmpty { jobSheet.handledByName }}, returned to ${jobSheet.returnedByName}"
            } else {
                "${jobSheet.customerName}'s machine — checked out by ${jobSheet.checkoutByName.ifEmpty { jobSheet.handledByName }}"
            }
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = BabaLightGray,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.VerifiedUser,
                        contentDescription = null,
                        tint = BabaRed,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = attributionText,
                        fontSize = 11.sp,
                        color = BabaDark,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Due: ${dateFormat.format(Date(jobSheet.dueTime))}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isOverdue && !isJobClosed) BabaOverdue else BabaGray
                )
                Text(
                    text = if (isJobClosed) "Deposit Settled" else "Deposit: ₹${jobSheet.depositAmount}",
                    fontSize = 11.sp,
                    color = BabaSuccess,
                    fontWeight = FontWeight.Bold
                )
            }

            if (isJobClosed) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = BabaBorder)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Settled: ₹${jobSheet.totalRentalFee}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = BabaDark
                    )
                    Text(
                        text = "Account Fully Settled",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = BabaSuccess
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = BabaBorder)

            // Actions row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val isOpen = jobSheet.status != "returned" && jobSheet.status != "closed"
                if (isOpen) {
                    Button(
                        onClick = onReturn,
                        colors = ButtonDefaults.buttonColors(containerColor = BabaSuccess),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("order_return_${jobSheet.id}")
                    ) {
                        Icon(Icons.Default.Check, contentDescription = "Return", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Return", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = onAddPayment,
                        colors = ButtonDefaults.buttonColors(containerColor = BabaDark, contentColor = Color.White),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("order_add_payment_${jobSheet.id}")
                    ) {
                        Icon(Icons.Default.Payments, contentDescription = "Add Payment", modifier = Modifier.size(16.dp), tint = BabaYellow)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("+ Payment", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }

                IconButton(
                    onClick = onViewPaymentHistory,
                    modifier = Modifier.size(36.dp).testTag("order_history_${jobSheet.id}")
                ) {
                    Icon(Icons.Default.History, contentDescription = "Payment History", tint = BabaDark, modifier = Modifier.size(20.dp))
                }

                IconButton(
                    onClick = {
                        PdfGenerator.viewJobSheetPdf(context, jobSheet)
                    },
                    modifier = Modifier.size(36.dp).testTag("order_pdf_${jobSheet.id}")
                ) {
                    Icon(Icons.Default.PictureAsPdf, contentDescription = "PDF Invoice", tint = BabaRed, modifier = Modifier.size(18.dp))
                }

                IconButton(onClick = onViewReceipt, modifier = Modifier.size(36.dp).testTag("order_receipt_${jobSheet.id}")) {
                    Icon(Icons.Default.Receipt, contentDescription = "Receipt", tint = BabaDark, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}
