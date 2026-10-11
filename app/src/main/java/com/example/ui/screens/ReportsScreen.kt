package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import com.example.data.model.PaymentEntity
import com.example.data.model.UserEntity
import com.example.ui.theme.*
import com.example.util.PdfGenerator
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    currentUser: UserEntity,
    allJobSheets: List<JobSheetEntity>,
    payments: List<PaymentEntity>,
    onExportCsv: () -> Unit,
    onNavigateBack: () -> Unit
) {
    BackHandler { onNavigateBack() }
    val context = LocalContext.current
    val now = remember { System.currentTimeMillis() }

    val startOfMonth = remember {
        Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    val nonDeleted = remember(allJobSheets) { allJobSheets.filter { !it.isDeleted } }
    val thisMonthJobs = remember(nonDeleted) { nonDeleted.filter { it.checkoutTime >= startOfMonth } }
    val activeRentals = remember(nonDeleted) { nonDeleted.filter { it.status == "active" || it.status == "overdue" } }

    val totalDepositsHeld = remember(activeRentals) { activeRentals.sumOf { it.depositAmount } }
    val totalRevenueCollected = remember(payments) {
        payments.filter { it.type != "refund" }.sumOf { it.amount } - payments.filter { it.type == "refund" }.sumOf { it.amount }
    }
    val lateFeesCollected = remember(nonDeleted) { nonDeleted.sumOf { it.lateFee } }

    // Popular rented machinery
    val machineFrequencies = remember(nonDeleted) {
        nonDeleted.groupBy { it.machineName }
            .mapValues { it.value.sumOf { job -> job.quantity } }
            .toList()
            .sortedByDescending { it.second }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Monthly Reports & Analytics", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            PdfGenerator.viewReportPdf(context, allJobSheets, payments, currentUser.name)
                        },
                        modifier = Modifier.testTag("export_pdf_button")
                    ) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = "PDF Report", tint = BabaYellow)
                    }
                    IconButton(onClick = onExportCsv, modifier = Modifier.testTag("export_csv_button")) {
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
            // PDF Executive Report Card Action
            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
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
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .background(BabaOverdueLight, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.PictureAsPdf, contentDescription = "PDF", tint = BabaRed)
                                }
                                Column {
                                    Text("Executive PDF Report", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = BabaDark)
                                    Text("Formal financial statement & audit ledger", fontSize = 11.sp, color = BabaGray)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    PdfGenerator.viewReportPdf(context, allJobSheets, payments, currentUser.name)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = BabaRed),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("view_report_pdf_button"),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.PictureAsPdf, contentDescription = "View", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("View PDF", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = {
                                    PdfGenerator.shareReportPdf(context, allJobSheets, payments, currentUser.name)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = BabaSuccess),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("share_report_pdf_button"),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = "Share", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Share PDF", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // CSV Export Banner Action
            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(BabaSuccessLight, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.TableChart, contentDescription = "CSV", tint = BabaSuccess)
                            }
                            Column {
                                Text("Export to CSV Spreadsheet", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = BabaDark)
                                Text("Full transaction & rental records v1", fontSize = 11.sp, color = BabaGray)
                            }
                        }

                        Button(
                            onClick = onExportCsv,
                            colors = ButtonDefaults.buttonColors(containerColor = BabaSuccess),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = "Share", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Export", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Summary Stats Cards
            item {
                Text("Financial Performance", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = BabaDark)
                Spacer(modifier = Modifier.height(6.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ReportStatCard(
                        title = "Total Net Revenue",
                        value = "₹${totalRevenueCollected.toInt()}",
                        subtitle = "Deposits & settlements",
                        color = BabaSuccess,
                        modifier = Modifier.weight(1f)
                    )
                    ReportStatCard(
                        title = "Monthly Job Sheets",
                        value = "${thisMonthJobs.size}",
                        subtitle = "Rentals this month",
                        color = BabaRed,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ReportStatCard(
                        title = "Security Deposits Held",
                        value = "₹${totalDepositsHeld.toInt()}",
                        subtitle = "Across ${activeRentals.size} active jobs",
                        color = BabaDark,
                        modifier = Modifier.weight(1f)
                    )
                    ReportStatCard(
                        title = "Late Fees Collected",
                        value = "₹${lateFeesCollected.toInt()}",
                        subtitle = "From overdue returns",
                        color = BabaYellowDark,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Machine Popularity Breakdown
            item {
                Text("Most Rented Equipment", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = BabaDark)
                Spacer(modifier = Modifier.height(6.dp))

                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (machineFrequencies.isEmpty()) {
                            Text("No rental activity yet", color = BabaGray, fontSize = 12.sp)
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
