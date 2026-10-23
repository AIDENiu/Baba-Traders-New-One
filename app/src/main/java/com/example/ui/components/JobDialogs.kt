package com.example.ui.components

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import com.example.data.model.JobSheetEntity
import com.example.data.model.PaymentEntity
import com.example.data.model.SelectedMachineItem
import com.example.data.model.UserEntity
import com.example.data.repository.BabaRepository
import com.example.data.repository.ReturnCalculation
import com.example.ui.theme.*
import com.example.util.PdfGenerator
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

@Composable
fun ReceiptDialog(
    jobSheet: JobSheetEntity,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()) }

    Dialog(onDismissRequest = onDismiss) {
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
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Receipt Header with BABA Logo
                BabaLogoIcon(size = 52.dp, cornerRadius = 12.dp)

                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "BABA TRADERS",
                    fontWeight = FontWeight.Black,
                    fontSize = 18.sp,
                    color = BabaDark
                )
                Text(
                    text = "& Engineering Hardwares - Machinery Rentals",
                    fontSize = 11.sp,
                    color = BabaGray
                )
                Text(
                    text = "Bypass Road, Near Bus Stand • Ph: 9876543210",
                    fontSize = 10.sp,
                    color = BabaGray
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = BabaBorder)

                // Job ID & Status
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = jobSheet.id,
                        fontWeight = FontWeight.Black,
                        fontSize = 16.sp,
                        color = BabaRed
                    )
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = when (jobSheet.status) {
                            "returned", "closed" -> BabaSuccessLight
                            "overdue" -> BabaOverdueLight
                            else -> BabaYellowLight
                        }
                    ) {
                        Text(
                            text = if (jobSheet.status == "closed" || jobSheet.status == "returned") "BILLED" else jobSheet.status.uppercase(),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = when (jobSheet.status) {
                                "returned", "closed" -> BabaSuccess
                                "overdue" -> BabaOverdue
                                else -> BabaYellowDark
                            },
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Customer Details Box
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = BabaLightGray,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "CUSTOMER DETAILS",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = BabaGray
                        )
                        Text(
                            text = jobSheet.customerName,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = BabaDark
                        )
                        Text(
                            text = "Phone: ${jobSheet.customerPhone}",
                            fontSize = 12.sp,
                            color = BabaDark
                        )
                        if (jobSheet.customerAddress.isNotEmpty()) {
                            Text(
                                text = "Address: ${jobSheet.customerAddress}",
                                fontSize = 11.sp,
                                color = BabaGray
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Item Details Table
                val rentedItems = remember(jobSheet.itemsJson) {
                    SelectedMachineItem.listFromJson(jobSheet.itemsJson)
                }
                val bookedDays = remember(jobSheet.checkoutTime, jobSheet.dueTime) {
                    BabaRepository.calculateBookedDays(jobSheet.checkoutTime, jobSheet.dueTime)
                }
                val estimatedGrossRent = bookedDays * jobSheet.rateUsed
                val estimatedNetRent = maxOf(0.0, estimatedGrossRent - jobSheet.discount)

                Column(modifier = Modifier.fillMaxWidth()) {
                    ReceiptRow("Machine / Equipment", jobSheet.cleanMachineName, isBold = true)
                    if (rentedItems.size > 1) {
                        rentedItems.forEach { item ->
                            ReceiptRow(
                                "  • ${item.machineName}",
                                "₹${(item.quantity * item.rate).toInt()}/day"
                            )
                        }
                    }
                    ReceiptRow("Total Units Rented", "${jobSheet.effectiveInitialQty} machine(s)")
                    ReceiptRow("Combined Daily Rate", "₹${jobSheet.rateUsed.toInt()}/day", isBold = true)
                    ReceiptRow("Booked Duration", "$bookedDays day(s)", isBold = true)
                    ReceiptRow("Billing Cycle", "8:00 AM today to 8:00 AM tomorrow")
                    ReceiptRow("Checked Out", dateFormat.format(Date(jobSheet.checkoutTime)))
                    ReceiptRow("Due Date & Time", dateFormat.format(Date(jobSheet.dueTime)))
                    if (jobSheet.returnTime != null) {
                        ReceiptRow("Returned At", dateFormat.format(Date(jobSheet.returnTime)))
                    }
                    ReceiptRow("Payment Mode", jobSheet.paymentMode.uppercase(), isBold = true)
                    ReceiptRow(
                        "Gross Rent ($bookedDays day(s) × ₹${jobSheet.rateUsed.toInt()}/day)",
                        "₹${estimatedGrossRent.toInt()}",
                        isBold = true
                    )
                    ReceiptRow("Discount Price", "₹${jobSheet.discount.toInt()}", textColor = if (jobSheet.discount > 0) BabaRed else BabaDark)
                    ReceiptRow("Net Rent ($bookedDays day(s))", "₹${estimatedNetRent.toInt()}", isBold = true, textColor = BabaRed)
                    ReceiptRow("Created By (User ID)", "${jobSheet.checkoutByName.ifEmpty { jobSheet.handledByName }} (ID: ${jobSheet.checkoutBy.ifEmpty { jobSheet.handledBy }})")
                    ReceiptRow(
                        "Closed By (User ID)",
                        if (jobSheet.closedBy.isNotEmpty() || jobSheet.returnedBy.isNotEmpty()) {
                            "${jobSheet.closedByName.ifEmpty { jobSheet.returnedByName }} (ID: ${jobSheet.closedBy.ifEmpty { jobSheet.returnedBy }})"
                        } else {
                            "Open (Pending Return)"
                        }
                    )
                    ReceiptRow("Security Deposit / Advance", "₹${jobSheet.depositAmount.toInt()}", textColor = BabaSuccess, isBold = true)

                    if (jobSheet.status != "returned" && jobSheet.status != "closed") {
                        val estBalance = estimatedNetRent - jobSheet.depositAmount
                        ReceiptRow(
                            "Est. Balance on Return",
                            when {
                                estBalance > 0 -> "₹${estBalance.toInt()} Payable"
                                estBalance < 0 -> "₹${abs(estBalance).toInt()} Refund"
                                else -> "₹0 (Covered by Deposit)"
                            },
                            textColor = if (estBalance > 0) BabaOverdue else BabaSuccess,
                            isBold = true
                        )
                    }

                    if (jobSheet.lateFee > 0) {
                        ReceiptRow("Late Fee Charged", "+ ₹${jobSheet.lateFee}", textColor = BabaOverdue, isBold = true)
                    }

                    if (jobSheet.status == "returned" || jobSheet.status == "closed") {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = BabaBorder)
                        ReceiptRow("Final Settled Rental Fee", "₹${jobSheet.totalRentalFee.toInt()}", isBold = true)
                        if (jobSheet.returnRemark.contains("no work done", ignoreCase = true)) {
                            ReceiptRow("Settlement", "No Work Done (NIL)", textColor = BabaSuccess, isBold = true)
                        } else if (jobSheet.finalSettlement > 0) {
                            ReceiptRow("Balance Collected", "₹${jobSheet.finalSettlement.toInt()}", textColor = BabaRed, isBold = true)
                        } else if (jobSheet.finalSettlement < 0) {
                            ReceiptRow("Refund Returned", "₹${abs(jobSheet.finalSettlement).toInt()}", textColor = BabaSuccess, isBold = true)
                        } else {
                            ReceiptRow("Settlement", "Fully Settled", textColor = BabaSuccess, isBold = true)
                        }
                        if (jobSheet.returnRemark.isNotEmpty()) {
                            ReceiptRow("Return Remark", jobSheet.returnRemark)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Rental Terms Note
                Text(
                    text = "Terms: Customer is responsible for machinery safe handling. Late return rolls over according to billing terms. Any physical damages will be deducted from deposit.",
                    fontSize = 9.sp,
                    color = BabaGray,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Action Buttons
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Primary PDF Action Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                PdfGenerator.viewJobSheetPdf(context, jobSheet)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = BabaRed),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("view_pdf_button"),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = "PDF", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("View PDF", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                PdfGenerator.shareJobSheetPdf(context, jobSheet)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = BabaSuccess),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("share_pdf_button"),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = "Share", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Share PDF", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Secondary Save PDF, Text Share & Close
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val file = PdfGenerator.generateJobSheetPdf(context, jobSheet)
                                PdfGenerator.savePdfToDownloads(context, file)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("save_pdf_button"),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = "Save PDF", modifier = Modifier.size(14.dp), tint = BabaDark)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Save PDF", fontSize = 11.sp, color = BabaDark)
                        }

                        OutlinedButton(
                            onClick = {
                                shareReceiptText(context, jobSheet)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("share_receipt_button"),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.TextSnippet, contentDescription = "Text", modifier = Modifier.size(14.dp), tint = BabaDark)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Share Text", fontSize = 11.sp, color = BabaDark)
                        }
                    }

                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Close", fontSize = 11.sp, color = BabaRed, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun ReceiptRow(
    label: String,
    value: String,
    isBold: Boolean = false,
    textColor: Color = BabaDark
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = BabaGray,
            modifier = Modifier.padding(end = 12.dp)
        )
        Text(
            text = value,
            fontSize = 12.sp,
            fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
            color = textColor,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f)
        )
    }
}

private fun shareReceiptText(context: Context, jobSheet: JobSheetEntity) {
    val dateFormat = SimpleDateFormat("dd/MM/yyyy hh:mm a", Locale.getDefault())
    val bookedDays = BabaRepository.calculateBookedDays(jobSheet.checkoutTime, jobSheet.dueTime)
    val estTotalRent = maxOf(0.0, (bookedDays * jobSheet.rateUsed) - jobSheet.discount)
    val text = """
        *BABA TRADERS & Engineering Hardwares*
        *Machinery Rental Receipt: ${jobSheet.id}*
        ----------------------------------
        Customer: ${jobSheet.customerName} (${jobSheet.customerPhone})
        Machines: ${jobSheet.cleanMachineName}
        Total Units: ${jobSheet.effectiveInitialQty}
        Combined Daily Rate: ₹${jobSheet.rateUsed.toInt()}/day
        Booked Duration: $bookedDays day(s)
        Estimated Rent ($bookedDays day(s)): ₹${estTotalRent.toInt()}
        Billing Cycle: 8:00 AM today to 8:00 AM tomorrow
        Checked Out: ${dateFormat.format(Date(jobSheet.checkoutTime))}
        Due Time: ${dateFormat.format(Date(jobSheet.dueTime))}
        Deposit Paid: ₹${jobSheet.depositAmount.toInt()}
        Checkout By: ${jobSheet.checkoutByName.ifEmpty { jobSheet.handledByName }}
        Handled By: ${jobSheet.handledByName}
        Status: ${if (jobSheet.status == "returned" || jobSheet.status == "closed") "BILLED" else jobSheet.status.uppercase()}
        ----------------------------------
        *Tools for a Stronger Tomorrow*
        Contact: 9876543210
    """.trimIndent()

    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "BABA Traders Receipt - ${jobSheet.id}")
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, "Share Job Sheet Receipt"))
}

// --- Return Job Dialog ---
@Composable
fun ReturnJobDialog(
    jobSheet: JobSheetEntity,
    calculation: ReturnCalculation? = null,
    payments: List<PaymentEntity> = emptyList(),
    currentUser: UserEntity? = null,
    onConfirmReturn: (paymentMethod: String, remark: String) -> Unit = { _, _ -> },
    onConfirmFullReturn: (returningQty: Int, discount: Double, paymentMethod: String, remark: String, daysUsed: Int) -> Unit = { _, _, m, r, _ -> onConfirmReturn(m, r) },
    onConfirmPartialReturn: (returningQty: Int, discount: Double, paymentMethod: String, remark: String, daysUsed: Int) -> Unit = { _, _, m, r, _ -> onConfirmReturn(m, r) },
    onDismiss: () -> Unit
) {
    val activeUnits = jobSheet.remainingActiveQty.coerceAtLeast(1)
    val initialUnits = jobSheet.effectiveInitialQty.coerceAtLeast(1)
    var returningQuantity by remember { mutableStateOf(activeUnits) }
    var discountInput by remember { mutableStateOf("") }
    var selectedMethod by remember { mutableStateOf("cash") } // "cash", "gpay", "split", or "no_work_done"
    var splitCashInput by remember { mutableStateOf("") }
    var splitGpayInput by remember { mutableStateOf("") }
    var remarkText by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showTransactions by remember { mutableStateOf(true) }

    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()) }
    val now = remember { System.currentTimeMillis() }

    // Daily rental cycle: 8:00 AM today to 8:00 AM tomorrow.
    // If return exceeds the 8:00 AM cutoff time, extra day(s) rent is automatically applied.
    val elapsedDays = remember(jobSheet.checkoutTime) {
        BabaRepository.calculateDaysUsed(jobSheet.checkoutTime, now)
    }
    val bookedDays = remember(jobSheet.checkoutTime, jobSheet.dueTime) {
        BabaRepository.calculateBookedDays(jobSheet.checkoutTime, jobSheet.dueTime)
    }
    val initialCutoffMillis = remember(jobSheet.checkoutTime, bookedDays) {
        BabaRepository.computeCutoffTimeForDays(jobSheet.checkoutTime, bookedDays)
    }
    val exceeded8AmCutoff = now > initialCutoffMillis
    var daysUsed by remember(jobSheet.id) {
        mutableStateOf(maxOf(elapsedDays, bookedDays).coerceAtLeast(1))
    }

    val manualDiscount = discountInput.toDoubleOrNull() ?: 0.0
    val isNoWorkDone = selectedMethod == "no_work_done"

    // jobSheet.rateUsed is ALREADY the combined daily rate for all initialUnits machines!
    // Never multiply jobSheet.rateUsed by returningQuantity a second time!
    val dailyRateForReturning = if (returningQuantity >= initialUnits) {
        jobSheet.rateUsed
    } else {
        (jobSheet.rateUsed * returningQuantity.toDouble()) / initialUnits.toDouble()
    }

    // Formula: (Days Used × Combined Daily Rate for Returning Machines) - Discount - Deposit Allocated
    val grossRentalFee = daysUsed * dailyRateForReturning
    val extraDaysCount = (daysUsed - bookedDays).coerceAtLeast(0)
    val extraRentApplied = extraDaysCount * dailyRateForReturning
    val proratedInitialDiscount = if (initialUnits > 0) {
        (jobSheet.discount * returningQuantity) / initialUnits
    } else {
        jobSheet.discount
    }
    val totalDiscount = proratedInitialDiscount + manualDiscount
    val netRentalFee = if (isNoWorkDone) 0.0 else maxOf(0.0, grossRentalFee - totalDiscount)

    val depositAllocated = if (returningQuantity >= activeUnits) {
        jobSheet.depositAmount
    } else {
        (jobSheet.depositAmount * returningQuantity) / activeUnits
    }

    val finalPayable = if (isNoWorkDone) -depositAllocated else (netRentalFee - depositAllocated)
    val isPartialReturn = returningQuantity < activeUnits

    // Keep split inputs synced when finalPayable changes if user hasn't typed custom split yet
    val targetBillAmount = kotlin.math.abs(finalPayable)
    LaunchedEffect(targetBillAmount, selectedMethod) {
        if (selectedMethod == "split") {
            val currentCash = splitCashInput.toDoubleOrNull() ?: 0.0
            val currentGpay = splitGpayInput.toDoubleOrNull() ?: 0.0
            if (kotlin.math.abs((currentCash + currentGpay) - targetBillAmount) > 0.01) {
                val defaultCash = (targetBillAmount / 2.0).toInt()
                val defaultGpay = (targetBillAmount.toInt() - defaultCash).coerceAtLeast(0)
                splitCashInput = defaultCash.toString()
                splitGpayInput = defaultGpay.toString()
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier
                .fillMaxWidth()
                .padding(6.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(BabaSuccessLight, RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Invoice",
                            tint = BabaSuccess,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isPartialReturn) "Partial Return & Invoice" else "Close Job Sheet & Invoice",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = BabaDark
                        )
                        Text(
                            text = "${jobSheet.id} • ${jobSheet.customerName}",
                            fontSize = 12.sp,
                            color = BabaGray
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = BabaGray)
                    }
                }

                HorizontalDivider(color = BabaBorder)

                // Multi-machine / Partial Return selector
                if (activeUnits > 1) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = BabaLightGray,
                        border = androidx.compose.foundation.BorderStroke(1.dp, BabaBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "Returning Quantity",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = BabaDark
                                    )
                                    Text(
                                        text = "$activeUnits machine(s) currently active",
                                        fontSize = 11.sp,
                                        color = BabaGray
                                    )
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    FilledTonalIconButton(
                                        onClick = {
                                            if (returningQuantity > 1) returningQuantity--
                                        },
                                        enabled = returningQuantity > 1,
                                        modifier = Modifier.size(32.dp).testTag("decrease_return_qty")
                                    ) {
                                        Icon(Icons.Default.Remove, contentDescription = "Decrease", modifier = Modifier.size(16.dp))
                                    }

                                    Text(
                                        text = "$returningQuantity",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Black,
                                        color = BabaDark,
                                        modifier = Modifier.padding(horizontal = 4.dp)
                                    )

                                    FilledTonalIconButton(
                                        onClick = {
                                            if (returningQuantity < activeUnits) returningQuantity++
                                        },
                                        enabled = returningQuantity < activeUnits,
                                        modifier = Modifier.size(32.dp).testTag("increase_return_qty")
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = "Increase", modifier = Modifier.size(16.dp))
                                    }
                                }
                            }

                            if (isPartialReturn) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = BabaYellowLight,
                                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                                ) {
                                    Text(
                                        text = "Partial Return: Settling $returningQuantity machine(s). Remaining ${activeUnits - returningQuantity} machine(s) stay active on this job sheet.",
                                        fontSize = 10.5.sp,
                                        color = BabaYellowDark,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(6.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Transaction Tracking View
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = BabaLightGray,
                    border = androidx.compose.foundation.BorderStroke(1.dp, BabaBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showTransactions = !showTransactions },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.ReceiptLong, contentDescription = null, tint = BabaRed, modifier = Modifier.size(16.dp))
                                Text(
                                    text = "Transaction & Payment History",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = BabaDark
                                )
                            }
                            Icon(
                                imageVector = if (showTransactions) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                tint = BabaGray,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        if (showTransactions) {
                            Spacer(modifier = Modifier.height(6.dp))
                            if (payments.isEmpty()) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Initial Advance Deposit (${jobSheet.paymentMode.uppercase()})", fontSize = 11.sp, color = BabaDark)
                                    Text("₹${jobSheet.depositAmount}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = BabaSuccess)
                                }
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    payments.forEach { p ->
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text(
                                                text = "${when (p.type) {
                                                    "advance" -> "Advance Deposit"
                                                    "extra" -> "Extra Payment"
                                                    "settlement" -> "Return Settlement"
                                                    "refund" -> "Deposit Refund"
                                                    else -> p.type.uppercase()
                                                }} (${p.method.uppercase()})",
                                                fontSize = 11.sp,
                                                color = if (p.type == "refund") BabaOverdue else BabaDark
                                            )
                                            Text(
                                                text = if (p.type == "refund") "- ₹${p.amount}" else "₹${p.amount}",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (p.type == "refund") BabaOverdue else BabaSuccess
                                            )
                                        }
                                    }
                                }
                            }
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = BabaBorder)
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Total Deposit Held for this Job", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = BabaDark)
                                Text("₹${jobSheet.depositAmount}", fontSize = 11.sp, fontWeight = FontWeight.Black, color = BabaSuccess)
                            }
                        }
                    }
                }

                // Automatic Calculation Breakdown Card
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = BabaLightGray,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Text("Rental Machines", fontSize = 12.sp, color = BabaGray, modifier = Modifier.padding(end = 12.dp))
                            Text(
                                text = jobSheet.cleanMachineName,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = BabaDark,
                                textAlign = TextAlign.End,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isPartialReturn) "Daily Rate ($returningQuantity of $initialUnits units)" else "Combined Daily Rate ($returningQuantity machine(s))",
                                fontSize = 12.sp,
                                color = BabaGray,
                                modifier = Modifier.weight(1f).padding(end = 8.dp)
                            )
                            Text(
                                text = "₹${dailyRateForReturning.toInt()} / day",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = BabaDark,
                                maxLines = 1,
                                softWrap = false
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Text("Rental Duration (Days)", fontSize = 12.sp, color = BabaGray)
                                Text(
                                    text = "Booked: $bookedDays day(s) • Elapsed: $elapsedDays day(s)",
                                    fontSize = 10.sp,
                                    color = BabaGray
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                FilledTonalIconButton(
                                    onClick = { if (daysUsed > 1) daysUsed-- },
                                    enabled = daysUsed > 1,
                                    modifier = Modifier.size(28.dp).testTag("decrease_days_used")
                                ) {
                                    Icon(Icons.Default.Remove, contentDescription = "Decrease Days", modifier = Modifier.size(14.dp))
                                }
                                Text(
                                    text = "$daysUsed day(s)",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Black,
                                    color = BabaDark,
                                    maxLines = 1,
                                    softWrap = false
                                )
                                FilledTonalIconButton(
                                    onClick = { daysUsed++ },
                                    modifier = Modifier.size(28.dp).testTag("increase_days_used")
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = "Increase Days", modifier = Modifier.size(14.dp))
                                }
                            }
                        }

                        if (exceeded8AmCutoff || extraDaysCount > 0) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = BabaOverdueLight,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Exceeded 8:00 AM Cutoff (+$extraDaysCount extra day(s))",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = BabaOverdue,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = "+ ₹${extraRentApplied.toInt()} Extra Rent",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Black,
                                        color = BabaOverdue
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Gross Rent ($daysUsed day(s) × ₹${dailyRateForReturning.toInt()}/day)",
                                fontSize = 12.sp,
                                color = BabaGray,
                                modifier = Modifier.weight(1f).padding(end = 8.dp)
                            )
                            Text(
                                text = if (isNoWorkDone) "₹0.0 (Waived)" else "₹${grossRentalFee.toInt()}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isNoWorkDone) BabaSuccess else BabaDark,
                                maxLines = 1,
                                softWrap = false
                            )
                        }

                        if (!isNoWorkDone && totalDiscount > 0) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Total Discount", fontSize = 12.sp, color = BabaGray, modifier = Modifier.weight(1f).padding(end = 8.dp))
                                Text("- ₹${totalDiscount.toInt()}", fontSize = 12.sp, color = BabaRed, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isPartialReturn) "Advance Deposit Allocated ($returningQuantity of $activeUnits)" else "Advance Deposit Deducted",
                                fontSize = 12.sp,
                                color = BabaGray,
                                modifier = Modifier.weight(1f).padding(end = 8.dp)
                            )
                            Text(
                                text = "- ₹${depositAllocated.toInt()}",
                                fontSize = 12.sp,
                                color = BabaSuccess,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false
                            )
                        }

                        HorizontalDivider(color = BabaBorder)

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Net Rental Payable Fee", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = BabaDark)
                            Text(
                                text = if (isNoWorkDone) "₹0.0 (NIL)" else "₹${netRentalFee.toInt()}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isNoWorkDone) BabaSuccess else BabaDark
                            )
                        }
                    }
                }

                // Manual Discount Input Field
                OutlinedTextField(
                    value = discountInput,
                    onValueChange = {
                        discountInput = it.filter { c -> c.isDigit() || c == '.' }
                        errorMessage = null
                    },
                    label = { Text("Manual Closing Discount (₹)") },
                    placeholder = { Text("Enter amount (Deducts from final payable)") },
                    leadingIcon = {
                        Icon(Icons.Default.Percent, contentDescription = null, modifier = Modifier.size(16.dp))
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    supportingText = {
                        Text(
                            text = if (manualDiscount > 0) "Discount ₹$manualDiscount will be deducted from final payable amount" else "Optional: Enter discount to reduce payable balance",
                            fontSize = 10.sp,
                            color = if (manualDiscount > 0) BabaRed else BabaGray
                        )
                    },
                    colors = babaTextFieldColors(),
                    textStyle = babaInputTextStyle(),
                    modifier = Modifier.fillMaxWidth().testTag("closing_discount_input"),
                    shape = RoundedCornerShape(8.dp)
                )

                // Settlement Banner: Balance Due vs Refund
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isNoWorkDone) BabaSuccessLight
                    else if (finalPayable > 0) BabaOverdueLight
                    else BabaSuccessLight,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isNoWorkDone) BabaSuccess
                        else if (finalPayable > 0) BabaOverdue
                        else BabaSuccess
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (isNoWorkDone) "NO WORK DONE • FULL DEPOSIT REFUND"
                            else if (finalPayable > 0) "BALANCE PAYABLE BY CUSTOMER"
                            else if (finalPayable < 0) "DEPOSIT REFUND TO CUSTOMER"
                            else "ACCOUNT FULLY BALANCED",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isNoWorkDone) BabaSuccess
                            else if (finalPayable > 0) BabaOverdue
                            else BabaSuccess
                        )
                        Text(
                            text = if (isNoWorkDone) "₹$depositAllocated" else "₹${kotlin.math.abs(finalPayable)}",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Black,
                            color = if (isNoWorkDone) BabaSuccess
                            else if (finalPayable > 0) BabaOverdue
                            else BabaSuccess
                        )
                        val modeSummary = if (selectedMethod == "split") {
                            "SPLIT (Cash ₹${splitCashInput.ifEmpty { "0" }} + GPay ₹${splitGpayInput.ifEmpty { "0" }})"
                        } else if (selectedMethod == "upi" || selectedMethod == "gpay") {
                            "GPAY"
                        } else {
                            selectedMethod.uppercase()
                        }
                        Text(
                            text = if (isNoWorkDone) "Full deposit of ₹$depositAllocated refunded to customer (No work done)"
                            else if (finalPayable > 0) "Collect ₹$finalPayable via $modeSummary"
                            else if (finalPayable < 0) "Refund ₹${kotlin.math.abs(finalPayable)} to customer via $modeSummary"
                            else "No cash or settlement adjustment required (NIL)",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (finalPayable > 0) BabaOverdue else BabaSuccess,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }

                // Payment Mode Selection: Cash, GPay, and Split Payment
                Text(
                    text = "Payment Mode (Single or Split Payment)",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = BabaDark
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Cash Checkbox
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clickable { selectedMethod = "cash" }
                            .padding(vertical = 4.dp)
                            .testTag("checkbox_cash")
                    ) {
                        Checkbox(
                            checked = selectedMethod == "cash",
                            onCheckedChange = { if (it) selectedMethod = "cash" },
                            colors = CheckboxDefaults.colors(checkedColor = BabaRed)
                        )
                        Text("Cash", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = BabaDark)
                    }

                    // GPay / UPI Checkbox
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clickable { selectedMethod = "gpay" }
                            .padding(vertical = 4.dp)
                            .testTag("checkbox_upi")
                    ) {
                        Checkbox(
                            checked = selectedMethod == "upi" || selectedMethod == "gpay",
                            onCheckedChange = { if (it) selectedMethod = "gpay" },
                            colors = CheckboxDefaults.colors(checkedColor = BabaRed)
                        )
                        Text("GPay", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = BabaDark)
                    }

                    // Split Payment Checkbox (Cash + GPay)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clickable { selectedMethod = "split" }
                            .padding(vertical = 4.dp)
                            .testTag("checkbox_split")
                    ) {
                        Checkbox(
                            checked = selectedMethod == "split",
                            onCheckedChange = { if (it) selectedMethod = "split" },
                            colors = CheckboxDefaults.colors(checkedColor = BabaRed)
                        )
                        Text("Split (Cash + GPay)", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = BabaDark)
                    }
                }

                if (selectedMethod == "split") {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = BabaLightGray,
                        border = androidx.compose.foundation.BorderStroke(1.dp, BabaBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "Split Payment Breakdown (Total: ₹${targetBillAmount.toInt()})",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = BabaDark
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = splitCashInput,
                                    onValueChange = {
                                        splitCashInput = it.filter { c -> c.isDigit() || c == '.' }
                                        val cashVal = splitCashInput.toDoubleOrNull() ?: 0.0
                                        val remGpay = (targetBillAmount - cashVal).coerceAtLeast(0.0)
                                        splitGpayInput = if (remGpay % 1.0 == 0.0) remGpay.toInt().toString() else remGpay.toString()
                                        errorMessage = null
                                    },
                                    label = { Text("Cash Amount (₹)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    colors = babaTextFieldColors(),
                                    textStyle = babaInputTextStyle(fontWeight = FontWeight.Bold),
                                    modifier = Modifier.weight(1f).testTag("split_cash_input"),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                OutlinedTextField(
                                    value = splitGpayInput,
                                    onValueChange = {
                                        splitGpayInput = it.filter { c -> c.isDigit() || c == '.' }
                                        val gpayVal = splitGpayInput.toDoubleOrNull() ?: 0.0
                                        val remCash = (targetBillAmount - gpayVal).coerceAtLeast(0.0)
                                        splitCashInput = if (remCash % 1.0 == 0.0) remCash.toInt().toString() else remCash.toString()
                                        errorMessage = null
                                    },
                                    label = { Text("GPay Amount (₹)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    colors = babaTextFieldColors(),
                                    textStyle = babaInputTextStyle(fontWeight = FontWeight.Bold),
                                    modifier = Modifier.weight(1f).testTag("split_gpay_input"),
                                    shape = RoundedCornerShape(8.dp)
                                )
                            }
                        }
                    }
                }

                // No Work Done Option (Waive charges)
                FilterChip(
                    selected = isNoWorkDone,
                    onClick = {
                        if (isNoWorkDone) {
                            selectedMethod = "cash"
                        } else {
                            selectedMethod = "no_work_done"
                            if (remarkText.isBlank() || remarkText == "Machinery tested & returned in good condition") {
                                remarkText = "No work done - machinery returned without use"
                            }
                        }
                    },
                    label = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Cancel, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("No Work Done", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("(Waive all charges • Full refund)", fontSize = 10.sp, color = if (isNoWorkDone) BabaDark else BabaGray)
                        }
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = BabaSuccessLight,
                        selectedLabelColor = BabaSuccess,
                        selectedLeadingIconColor = BabaSuccess
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = isNoWorkDone,
                        borderColor = if (isNoWorkDone) BabaSuccess else BabaBorder,
                        selectedBorderColor = BabaSuccess,
                        borderWidth = if (isNoWorkDone) 1.5.dp else 1.dp
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("payment_method_no_work_done")
                )

                // Return Remark Field (Mandatory for audit)
                OutlinedTextField(
                    value = remarkText,
                    onValueChange = {
                        remarkText = it
                        errorMessage = null
                    },
                    label = { Text(if (isNoWorkDone) "Return Remark * (Reason for No Work)" else "Return Remark *") },
                    placeholder = {
                        Text(if (isNoWorkDone) "Returned unused / power issue at site" else "Machinery inspected & received in good condition")
                    },
                    supportingText = {
                        Text(
                            text = "Remark will be permanently saved in Sales Report & audit ledger",
                            fontSize = 10.sp,
                            color = BabaGray
                        )
                    },
                    colors = babaTextFieldColors(),
                    textStyle = babaInputTextStyle(),
                    modifier = Modifier.fillMaxWidth().testTag("return_remark_input"),
                    shape = RoundedCornerShape(8.dp)
                )

                if (errorMessage != null) {
                    Text(errorMessage!!, color = BabaOverdue, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Cancel")
                    }

                    Button(
                        onClick = {
                            val r = remarkText.trim()
                            if (r.isEmpty()) {
                                errorMessage = if (isNoWorkDone) "Please enter a remark explaining why no work was done" else "Please enter a return remark (machine condition / status)"
                                return@Button
                            }

                            // Validate worker discount floor if worker
                            if (currentUser?.role == "worker" && manualDiscount > currentUser.maxDiscountAmount) {
                                errorMessage = "Worker discount cannot exceed ₹${currentUser.maxDiscountAmount} as per owner policy."
                                return@Button
                            }

                            val effectiveMethod = if (selectedMethod == "split") {
                                val cashAmt = splitCashInput.toDoubleOrNull() ?: 0.0
                                val gpayAmt = splitGpayInput.toDoubleOrNull() ?: 0.0
                                if (targetBillAmount > 0 && kotlin.math.abs((cashAmt + gpayAmt) - targetBillAmount) > 0.01) {
                                    errorMessage = "Split Cash (₹${cashAmt.toInt()}) + GPay (₹${gpayAmt.toInt()}) must equal ₹${targetBillAmount.toInt()}"
                                    return@Button
                                }
                                "split:$cashAmt:$gpayAmt"
                            } else {
                                selectedMethod
                            }

                            if (isPartialReturn) {
                                onConfirmPartialReturn(returningQuantity, manualDiscount, effectiveMethod, r, daysUsed)
                            } else {
                                onConfirmFullReturn(returningQuantity, manualDiscount, effectiveMethod, r, daysUsed)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = BabaSuccess),
                        modifier = Modifier
                            .weight(1.4f)
                            .testTag("confirm_return_button"),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = if (isNoWorkDone) "Confirm NIL Invoice"
                            else if (isPartialReturn) "Invoice ($returningQuantity Units)"
                            else "Confirm & Invoice",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

// --- Add Extra Payment Dialog ---
@Composable
fun AddExtraPaymentDialog(
    jobSheet: JobSheetEntity,
    currentUser: UserEntity,
    onConfirmPayment: (amount: Double, paymentMode: String, remark: String) -> Unit,
    onDismiss: () -> Unit
) {
    var amountText by remember { mutableStateOf("") }
    var selectedMethod by remember { mutableStateOf("cash") }
    var splitCashText by remember { mutableStateOf("") }
    var splitGpayText by remember { mutableStateOf("") }
    var remarkText by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
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
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Payments,
                        contentDescription = "Add Payment",
                        tint = BabaSuccess,
                        modifier = Modifier.size(28.dp)
                    )
                    Column {
                        Text(
                            text = "Add Extra Payment",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = BabaDark
                        )
                        Text(
                            text = "${jobSheet.id} • ${jobSheet.customerName}",
                            fontSize = 12.sp,
                            color = BabaGray
                        )
                    }
                }

                HorizontalDivider(color = BabaBorder)

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = BabaLightGray,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Machine: ${jobSheet.cleanMachineName}", fontSize = 12.sp, color = BabaDark)
                        Text("Current Advance / Deposit: ₹${jobSheet.depositAmount}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = BabaSuccess)
                        Text("Received By: ${currentUser.name} (User ID: ${currentUser.id})", fontSize = 11.sp, color = BabaDark, fontWeight = FontWeight.SemiBold)
                    }
                }

                OutlinedTextField(
                    value = amountText,
                    onValueChange = {
                        amountText = it
                        val totalAmt = it.toDoubleOrNull() ?: 0.0
                        if (selectedMethod == "split") {
                            val cashVal = splitCashText.toDoubleOrNull() ?: 0.0
                            val remGpay = (totalAmt - cashVal).coerceAtLeast(0.0)
                            splitGpayText = if (remGpay % 1.0 == 0.0) remGpay.toInt().toString() else remGpay.toString()
                        }
                        errorMessage = null
                    },
                    label = { Text("Payment Amount (₹) *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = babaTextFieldColors(),
                    textStyle = babaInputTextStyle(fontWeight = FontWeight.Bold),
                    modifier = Modifier.fillMaxWidth().testTag("extra_payment_amount_input"),
                    shape = RoundedCornerShape(8.dp)
                )

                Text("Mode of Payment *", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = BabaDark)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("cash" to "Cash", "gpay" to "GPay", "split" to "Split (Cash+GPay)").forEach { (key, label) ->
                        FilterChip(
                            selected = selectedMethod == key,
                            onClick = {
                                selectedMethod = key
                                if (key == "split") {
                                    val totalAmt = amountText.toDoubleOrNull() ?: 0.0
                                    val half = (totalAmt / 2.0).toInt()
                                    splitCashText = half.toString()
                                    splitGpayText = (totalAmt.toInt() - half).coerceAtLeast(0).toString()
                                }
                            },
                            label = { Text(label, fontSize = 11.sp) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                if (selectedMethod == "split") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = splitCashText,
                            onValueChange = {
                                splitCashText = it.filter { c -> c.isDigit() || c == '.' }
                                val totalAmt = amountText.toDoubleOrNull() ?: 0.0
                                val cashVal = splitCashText.toDoubleOrNull() ?: 0.0
                                val remGpay = (totalAmt - cashVal).coerceAtLeast(0.0)
                                splitGpayText = if (remGpay % 1.0 == 0.0) remGpay.toInt().toString() else remGpay.toString()
                                errorMessage = null
                            },
                            label = { Text("Cash (₹)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = babaTextFieldColors(),
                            textStyle = babaInputTextStyle(fontWeight = FontWeight.Bold),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        OutlinedTextField(
                            value = splitGpayText,
                            onValueChange = {
                                splitGpayText = it.filter { c -> c.isDigit() || c == '.' }
                                val totalAmt = amountText.toDoubleOrNull() ?: 0.0
                                val gpayVal = splitGpayText.toDoubleOrNull() ?: 0.0
                                val remCash = (totalAmt - gpayVal).coerceAtLeast(0.0)
                                splitCashText = if (remCash % 1.0 == 0.0) remCash.toInt().toString() else remCash.toString()
                                errorMessage = null
                            },
                            label = { Text("GPay (₹)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = babaTextFieldColors(),
                            textStyle = babaInputTextStyle(fontWeight = FontWeight.Bold),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                }

                OutlinedTextField(
                    value = remarkText,
                    onValueChange = { remarkText = it },
                    label = { Text("Payment Remark") },
                    placeholder = { Text("Mid-rental installment") },
                    colors = babaTextFieldColors(),
                    textStyle = babaInputTextStyle(),
                    modifier = Modifier.fillMaxWidth().testTag("extra_payment_remark_input"),
                    shape = RoundedCornerShape(8.dp)
                )

                if (errorMessage != null) {
                    Text(errorMessage!!, color = BabaOverdue, fontSize = 12.sp)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Cancel")
                    }

                    Button(
                        onClick = {
                            val amt = amountText.toDoubleOrNull()
                            if (amt == null || amt <= 0) {
                                errorMessage = "Please enter a valid amount greater than 0"
                                return@Button
                            }
                            val effectiveMode = if (selectedMethod == "split") {
                                val cashAmt = splitCashText.toDoubleOrNull() ?: 0.0
                                val gpayAmt = splitGpayText.toDoubleOrNull() ?: 0.0
                                if (kotlin.math.abs((cashAmt + gpayAmt) - amt) > 0.01) {
                                    errorMessage = "Split Cash (₹${cashAmt.toInt()}) + GPay (₹${gpayAmt.toInt()}) must equal ₹${amt.toInt()}"
                                    return@Button
                                }
                                "split:$cashAmt:$gpayAmt"
                            } else {
                                selectedMethod
                            }
                            onConfirmPayment(amt, effectiveMode, remarkText.trim())
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = BabaSuccess),
                        modifier = Modifier.weight(1.3f).testTag("confirm_extra_payment_button"),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Save Payment", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// --- Payment History Dialog ---
@Composable
fun PaymentHistoryDialog(
    jobSheet: JobSheetEntity,
    payments: List<PaymentEntity>,
    onDismiss: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()) }
    val totalPaid = remember(payments) {
        payments.filter { it.type != "refund" }.sumOf { it.amount } - payments.filter { it.type == "refund" }.sumOf { it.amount }
    }

    Dialog(onDismissRequest = onDismiss) {
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
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Payment History", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = BabaDark)
                        Text("${jobSheet.id} • ${jobSheet.customerName}", fontSize = 12.sp, color = BabaGray)
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = BabaDark)
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = BabaBorder)

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = BabaSuccessLight,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Total Collected So Far", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = BabaDark)
                        Text("₹${totalPaid.toInt()}", fontWeight = FontWeight.Black, fontSize = 16.sp, color = BabaSuccess)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (payments.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text("No payment entries found", color = BabaGray, fontSize = 13.sp)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f, fill = false).heightIn(max = 350.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(payments) { payment ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = BabaLightGray,
                                border = androidx.compose.foundation.BorderStroke(1.dp, BabaBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(
                                            text = when (payment.type) {
                                                "advance" -> "Advance Payment"
                                                "extra" -> "Additional Payment"
                                                "settlement" -> "Return Settlement"
                                                "refund" -> "Deposit Refund"
                                                "return_nil" -> if (payment.method == "no_work_done") "No Work Done (NIL Payment)" else "Return (NIL Payment)"
                                                else -> payment.type.uppercase()
                                            },
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = if (payment.type == "refund") BabaRedDark else BabaDark
                                        )
                                        Text(
                                            text = if (payment.type == "return_nil") "NIL" else if (payment.type == "refund") "- ₹${payment.amount}" else "₹${payment.amount}",
                                            fontWeight = FontWeight.Black,
                                            fontSize = 13.sp,
                                            color = if (payment.type == "refund") BabaOverdue else BabaSuccess
                                        )
                                    }
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(
                                            text = if (payment.method == "no_work_done") "Mode: NO WORK DONE" else "Mode: ${payment.method.uppercase()}",
                                            fontSize = 11.sp,
                                            color = BabaDark,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(dateFormat.format(Date(payment.timestamp)), fontSize = 10.sp, color = BabaGray)
                                    }
                                    Text("Received By: ${payment.handledByName} (User ID: ${payment.handledBy})", fontSize = 10.sp, color = BabaGray)
                                    if (payment.remark.isNotEmpty()) {
                                        Text("Remark: ${payment.remark}", fontSize = 11.sp, color = BabaDark)
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = BabaDark, contentColor = Color.White),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Close", color = Color.White)
                }
            }
        }
    }
}
