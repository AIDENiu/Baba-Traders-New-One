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
import com.example.data.model.JobSheetEntity
import com.example.data.model.UserEntity
import com.example.data.repository.ReturnCalculation
import com.example.ui.theme.*
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
                    ReceiptRow("Checkout By (Billing)", jobSheet.checkoutByName.ifEmpty { jobSheet.handledByName })
                    ReceiptRow("Handled By (Equipment)", jobSheet.handledByName)
                    ReceiptRow("Security Deposit Paid", "₹${jobSheet.depositAmount}", textColor = BabaSuccess)

                    if (jobSheet.discount > 0) {
                        ReceiptRow("Discount Applied", "- ₹${jobSheet.discount}", textColor = BabaRed)
                    }

                    if (jobSheet.lateFee > 0) {
                        ReceiptRow("Late Fee Charged", "+ ₹${jobSheet.lateFee}", textColor = BabaOverdue, isBold = true)
                    }

                    if (jobSheet.status == "returned") {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = BabaBorder)
                        ReceiptRow("Total Rental Fee", "₹${jobSheet.totalRentalFee}", isBold = true)
                        if (jobSheet.finalSettlement > 0) {
                            ReceiptRow("Balance Collected", "₹${jobSheet.finalSettlement}", textColor = BabaRed, isBold = true)
                        } else if (jobSheet.finalSettlement < 0) {
                            ReceiptRow("Refund Returned", "₹${abs(jobSheet.finalSettlement)}", textColor = BabaSuccess, isBold = true)
                        } else {
                            ReceiptRow("Settlement", "Fully Settled", textColor = BabaSuccess, isBold = true)
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Close")
                    }

                    Button(
                        onClick = {
                            shareReceiptText(context, jobSheet)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = BabaRed),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("share_receipt_button"),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Share", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Share Receipt")
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
    onConfirmReturn: (paymentMethod: String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedMethod by remember { mutableStateOf("cash") }

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
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("cash" to "Cash", "upi" to "UPI / GPay", "card" to "Card").forEach { (key, label) ->
                        FilterChip(
                            selected = selectedMethod == key,
                            onClick = { selectedMethod = key },
                            label = { Text(label, fontSize = 11.sp) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

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
                        onClick = { onConfirmReturn(selectedMethod) },
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

// --- Edit Job Dialog ---
@Composable
fun EditJobDialog(
    jobSheet: JobSheetEntity,
    currentUser: UserEntity,
    onConfirmEdit: (newQty: Int, newRate: Double, newDiscount: Double, newNotes: String) -> Unit,
    onDismiss: () -> Unit
) {
    var quantityText by remember { mutableStateOf(jobSheet.quantity.toString()) }
    var rateText by remember { mutableStateOf(jobSheet.rateUsed.toString()) }
    var discountText by remember { mutableStateOf(jobSheet.discount.toString()) }
    var notesText by remember { mutableStateOf(jobSheet.notes) }
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
                Text(
                    text = "Edit Job Sheet ${jobSheet.id}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = BabaDark
                )
                Text(
                    text = "Modifications will be logged to the Activity Log and the Owner will be notified.",
                    fontSize = 11.sp,
                    color = BabaRedDark,
                    modifier = Modifier.padding(vertical = 4.dp)
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = BabaBorder)

                OutlinedTextField(
                    value = quantityText,
                    onValueChange = { quantityText = it },
                    label = { Text("Quantity") },
                    colors = babaTextFieldColors(),
                    textStyle = babaInputTextStyle(fontWeight = FontWeight.Bold),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    shape = RoundedCornerShape(8.dp)
                )

                OutlinedTextField(
                    value = rateText,
                    onValueChange = { rateText = it },
                    label = { Text("Daily Rate (₹)") },
                    colors = babaTextFieldColors(),
                    textStyle = babaInputTextStyle(fontWeight = FontWeight.Bold),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    shape = RoundedCornerShape(8.dp)
                )

                OutlinedTextField(
                    value = discountText,
                    onValueChange = { discountText = it },
                    label = { Text("Discount (₹)") },
                    colors = babaTextFieldColors(),
                    textStyle = babaInputTextStyle(fontWeight = FontWeight.Bold),
                    supportingText = {
                        if (currentUser.role == "worker") {
                            Text("Worker discount cap: ₹${currentUser.maxDiscountAmount}", color = BabaGray, fontSize = 10.sp)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    shape = RoundedCornerShape(8.dp)
                )

                OutlinedTextField(
                    value = notesText,
                    onValueChange = { notesText = it },
                    label = { Text("Notes / Reason for update") },
                    colors = babaTextFieldColors(),
                    textStyle = babaInputTextStyle(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    shape = RoundedCornerShape(8.dp)
                )

                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        color = BabaOverdue,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
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
                            val qty = quantityText.toIntOrNull() ?: 1
                            val rate = rateText.toDoubleOrNull() ?: jobSheet.rateUsed
                            val discount = discountText.toDoubleOrNull() ?: 0.0

                            if (currentUser.role == "worker" && discount > currentUser.maxDiscountAmount) {
                                errorMessage = "Worker discount cannot exceed ₹${currentUser.maxDiscountAmount}."
                                return@Button
                            }

                            onConfirmEdit(qty, rate, discount, notesText)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = BabaRed),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("save_edit_job_button"),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Save Changes")
                    }
                }
            }
        }
    }
}

// --- Soft Delete Dialog ---
@Composable
fun SoftDeleteDialog(
    jobSheet: JobSheetEntity,
    currentUser: UserEntity,
    onConfirmDelete: (reason: String) -> Unit,
    onDismiss: () -> Unit
) {
    var reason by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

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
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteForever,
                        contentDescription = "Delete",
                        tint = BabaOverdue,
                        modifier = Modifier.size(28.dp)
                    )
                    Text(
                        text = "Soft-Delete Job Sheet",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = BabaDark
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Are you sure you want to delete ${jobSheet.id} (${jobSheet.customerName}, ${jobSheet.machineName})?",
                    fontSize = 13.sp,
                    color = BabaDark
                )

                Text(
                    text = "Per shop policy, this soft-delete will restore stock, be permanently recorded in the Activity Log, and alert Owner immediately.",
                    fontSize = 11.sp,
                    color = BabaOverdue,
                    modifier = Modifier.padding(vertical = 6.dp)
                )

                OutlinedTextField(
                    value = reason,
                    onValueChange = {
                        reason = it
                        error = null
                    },
                    label = { Text("Reason for Deletion (Required)") },
                    placeholder = { Text("e.g. Customer cancelled before collection") },
                    colors = babaTextFieldColors(),
                    textStyle = babaInputTextStyle(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    shape = RoundedCornerShape(8.dp)
                )

                if (error != null) {
                    Text(text = error!!, color = BabaOverdue, fontSize = 11.sp)
                }

                Spacer(modifier = Modifier.height(12.dp))

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
                            if (reason.trim().isEmpty()) {
                                error = "Please provide a reason"
                            } else {
                                onConfirmDelete(reason.trim())
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = BabaOverdue),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("confirm_delete_button"),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Delete Job")
                    }
                }
            }
        }
    }
}
