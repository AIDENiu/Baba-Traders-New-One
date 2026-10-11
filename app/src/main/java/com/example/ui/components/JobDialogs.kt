package com.example.ui.components

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import com.example.data.model.UserEntity
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
                // Receipt Header
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(BabaRed, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ReceiptLong,
                        contentDescription = "Receipt",
                        tint = BabaYellow,
                        modifier = Modifier.size(28.dp)
                    )
                }

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
                            "returned" -> BabaSuccessLight
                            "overdue" -> BabaOverdueLight
                            else -> BabaYellowLight
                        }
                    ) {
                        Text(
                            text = jobSheet.status.uppercase(),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = when (jobSheet.status) {
                                "returned" -> BabaSuccess
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
                Column(modifier = Modifier.fillMaxWidth()) {
                    ReceiptRow("Machine / Equipment", jobSheet.machineName, isBold = true)
                    ReceiptRow("Quantity Rented", "${jobSheet.quantity} unit(s)")
                    ReceiptRow("Daily Rate", "₹${jobSheet.rateUsed}/day")
                    ReceiptRow("Billing Cycle", if (jobSheet.billingCycle == "24hr") "24-Hour Cycle" else "24-Hour Operation")
                    ReceiptRow("Checked Out", dateFormat.format(Date(jobSheet.checkoutTime)))
                    ReceiptRow("Due Date & Time", dateFormat.format(Date(jobSheet.dueTime)))
                    if (jobSheet.returnTime != null) {
                        ReceiptRow("Returned At", dateFormat.format(Date(jobSheet.returnTime)))
                    }
                    ReceiptRow("Payment Mode", jobSheet.paymentMode.uppercase(), isBold = true)
                    ReceiptRow("Discount Price", "₹${jobSheet.discount}", textColor = if (jobSheet.discount > 0) BabaRed else BabaDark)
                    ReceiptRow("Created By (User ID)", "${jobSheet.checkoutByName.ifEmpty { jobSheet.handledByName }} (ID: ${jobSheet.checkoutBy.ifEmpty { jobSheet.handledBy }})")
                    ReceiptRow(
                        "Closed By (User ID)",
                        if (jobSheet.closedBy.isNotEmpty() || jobSheet.returnedBy.isNotEmpty()) {
                            "${jobSheet.closedByName.ifEmpty { jobSheet.returnedByName }} (ID: ${jobSheet.closedBy.ifEmpty { jobSheet.returnedBy }})"
                        } else {
                            "Open (Pending Return)"
                        }
                    )
                    ReceiptRow("Security Deposit / Advance", "₹${jobSheet.depositAmount}", textColor = BabaSuccess)

                    if (jobSheet.lateFee > 0) {
                        ReceiptRow("Late Fee Charged", "+ ₹${jobSheet.lateFee}", textColor = BabaOverdue, isBold = true)
                    }

                    if (jobSheet.status == "returned" || jobSheet.status == "closed") {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = BabaBorder)
                        ReceiptRow("Total Rental Fee", "₹${jobSheet.totalRentalFee}", isBold = true)
                        if (jobSheet.finalSettlement > 0) {
                            ReceiptRow("Balance Collected", "₹${jobSheet.finalSettlement}", textColor = BabaRed, isBold = true)
                        } else if (jobSheet.finalSettlement < 0) {
                            ReceiptRow("Refund Returned", "₹${abs(jobSheet.finalSettlement)}", textColor = BabaSuccess, isBold = true)
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

                    // Secondary Text Share & Close
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
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

                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Close", fontSize = 11.sp)
                        }
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
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, fontSize = 12.sp, color = BabaGray)
        Text(
            text = value,
            fontSize = 12.sp,
            fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
            color = textColor
        )
    }
}

private fun shareReceiptText(context: Context, jobSheet: JobSheetEntity) {
    val dateFormat = SimpleDateFormat("dd/MM/yyyy hh:mm a", Locale.getDefault())
    val text = """
        *BABA TRADERS & Engineering Hardwares*
        *Machinery Rental Receipt: ${jobSheet.id}*
        ----------------------------------
        Customer: ${jobSheet.customerName} (${jobSheet.customerPhone})
        Machine: ${jobSheet.machineName}
        Quantity: ${jobSheet.quantity}
        Daily Rate: ₹${jobSheet.rateUsed}
        Billing Cycle: ${if (jobSheet.billingCycle == "24hr") "24-Hour Cycle" else "24-Hour Operation"}
        Checked Out: ${dateFormat.format(Date(jobSheet.checkoutTime))}
        Due Time: ${dateFormat.format(Date(jobSheet.dueTime))}
        Deposit Paid: ₹${jobSheet.depositAmount}
        Checkout By: ${jobSheet.checkoutByName.ifEmpty { jobSheet.handledByName }}
        Handled By: ${jobSheet.handledByName}
        Status: ${jobSheet.status.uppercase()}
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
    calculation: ReturnCalculation,
    onConfirmReturn: (paymentMethod: String, remark: String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedMethod by remember { mutableStateOf("cash") }
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
                    .verticalScroll(rememberScrollState())
            ) {
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
                            contentDescription = "Return",
                            tint = BabaSuccess,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Mark Job as Returned",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = BabaDark
                        )
                        Text(
                            text = "${jobSheet.id} • ${jobSheet.machineName}",
                            fontSize = 12.sp,
                            color = BabaGray
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = BabaBorder)

                // Calculation summary card
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = BabaLightGray,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Customer", fontSize = 12.sp, color = BabaGray)
                            Text(jobSheet.customerName, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = BabaDark)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Base Rental Fee (${jobSheet.quantity} x ₹${jobSheet.rateUsed})", fontSize = 12.sp, color = BabaGray)
                            Text("₹${calculation.baseRentalFee}", fontSize = 12.sp, color = BabaDark)
                        }

                        if (calculation.discount > 0) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Discount", fontSize = 12.sp, color = BabaGray)
                                Text("- ₹${calculation.discount}", fontSize = 12.sp, color = BabaRed)
                            }
                        }

                        if (calculation.daysLate > 0) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Late Fee (${calculation.daysLate} day(s) late)", fontSize = 12.sp, color = BabaOverdue, fontWeight = FontWeight.Bold)
                                Text("+ ₹${calculation.lateFee}", fontSize = 12.sp, color = BabaOverdue, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Late Return Fee", fontSize = 12.sp, color = BabaGray)
                                Text("₹0 (Returned on time)", fontSize = 12.sp, color = BabaSuccess)
                            }
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Security Deposit Paid", fontSize = 12.sp, color = BabaGray)
                            Text("₹${calculation.depositAmount}", fontSize = 12.sp, color = BabaDark)
                        }

                        HorizontalDivider(color = BabaBorder)

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Total Rental Due", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = BabaDark)
                            Text("₹${calculation.netTotalFee}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = BabaDark)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Settlement Banner: Balance Due vs Refund
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (calculation.balanceDueOrRefund > 0) BabaOverdueLight else BabaSuccessLight,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (calculation.balanceDueOrRefund > 0) BabaOverdue else BabaSuccess
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (calculation.balanceDueOrRefund > 0) "BALANCE DUE FROM CUSTOMER"
                            else if (calculation.balanceDueOrRefund < 0) "REFUND TO CUSTOMER"
                            else "ACCOUNT FULLY BALANCED",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (calculation.balanceDueOrRefund > 0) BabaOverdue else BabaSuccess
                        )
                        Text(
                            text = "₹${abs(calculation.balanceDueOrRefund)}",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Black,
                            color = if (calculation.balanceDueOrRefund > 0) BabaOverdue else BabaSuccess
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Payment Settlement Method
                Text(
                    text = "Settlement Payment Method",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = BabaDark
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("cash" to "Cash", "upi" to "UPI", "card" to "Card", "bank" to "Bank").forEach { (key, label) ->
                        FilterChip(
                            selected = selectedMethod == key,
                            onClick = { selectedMethod = key },
                            label = { Text(label, fontSize = 11.sp) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Remark Field (Mandatory / Required for audit)
                OutlinedTextField(
                    value = remarkText,
                    onValueChange = {
                        remarkText = it
                        errorMessage = null
                    },
                    label = { Text("Return Remark *") },
                    placeholder = { Text("e.g. Machinery tested & returned in good condition") },
                    supportingText = {
                        Text(
                            text = if (calculation.balanceDueOrRefund == 0.0) "No payment collected: Will be recorded as NIL with this remark in the Sales Report" else "Remark will be recorded in Sales Report & audit ledger",
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
                    Text(errorMessage!!, color = BabaOverdue, fontSize = 12.sp)
                }

                Spacer(modifier = Modifier.height(14.dp))

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
                                errorMessage = "Please enter a return remark (e.g. machine status / condition)"
                                return@Button
                            }
                            onConfirmReturn(selectedMethod, r)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = BabaSuccess),
                        modifier = Modifier
                            .weight(1.3f)
                            .testTag("confirm_return_button"),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Confirm Return", fontWeight = FontWeight.Bold)
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
                        Text("Machine: ${jobSheet.machineName} (Qty: ${jobSheet.quantity})", fontSize = 12.sp, color = BabaDark)
                        Text("Current Advance / Deposit: ₹${jobSheet.depositAmount}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = BabaSuccess)
                        Text("Received By: ${currentUser.name} (User ID: ${currentUser.id})", fontSize = 11.sp, color = BabaDark, fontWeight = FontWeight.SemiBold)
                    }
                }

                OutlinedTextField(
                    value = amountText,
                    onValueChange = {
                        amountText = it
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
                    listOf("cash" to "Cash", "upi" to "UPI", "card" to "Card", "bank" to "Bank").forEach { (key, label) ->
                        FilterChip(
                            selected = selectedMethod == key,
                            onClick = { selectedMethod = key },
                            label = { Text(label, fontSize = 11.sp) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                OutlinedTextField(
                    value = remarkText,
                    onValueChange = { remarkText = it },
                    label = { Text("Payment Remark") },
                    placeholder = { Text("e.g. Mid-rental installment") },
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
                            onConfirmPayment(amt, selectedMethod, remarkText.trim())
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
                                                "return_nil" -> "Return (NIL Payment)"
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
                                        Text("Mode: ${payment.method.uppercase()}", fontSize = 11.sp, color = BabaDark, fontWeight = FontWeight.SemiBold)
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
                    colors = ButtonDefaults.buttonColors(containerColor = BabaDark),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Close")
                }
            }
        }
    }
}
