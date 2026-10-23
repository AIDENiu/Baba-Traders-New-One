package com.example.ui.screens

import android.app.DatePickerDialog
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
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
import com.example.data.model.ExpenseEntity
import com.example.data.model.UserEntity
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpensesScreen(
    expenses: List<ExpenseEntity>,
    currentUser: UserEntity,
    onAddExpense: (date: String, timestamp: Long, expenseType: String, amount: Double, remarks: String, onSuccess: () -> Unit, onError: (String) -> Unit) -> Unit,
    onDeleteExpense: (String) -> Unit,
    onNavigateBack: () -> Unit
) {
    BackHandler { onNavigateBack() }

    val context = LocalContext.current
    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()) }
    val monthFormat = remember { SimpleDateFormat("MMMM yyyy", Locale.getDefault()) }

    // Daily Expense Form State (Date auto-populated with today's date)
    var selectedDateMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    var dateText by remember { mutableStateOf(dateFormat.format(Date(selectedDateMillis))) }
    var expenseType by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var remarks by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Monthly Report Selector State (Defaults to current month & year)
    val nowCal = remember { Calendar.getInstance() }
    var selectedYear by remember { mutableStateOf(nowCal.get(Calendar.YEAR)) }
    var selectedMonth by remember { mutableStateOf(nowCal.get(Calendar.MONTH)) } // 0-indexed (0 = Jan)

    val selectedMonthTitle = remember(selectedYear, selectedMonth) {
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, selectedYear)
            set(Calendar.MONTH, selectedMonth)
            set(Calendar.DAY_OF_MONTH, 1)
        }
        monthFormat.format(cal.time)
    }

    // Filter expenses matching selected month & year
    val monthlyExpenses = remember(expenses, selectedYear, selectedMonth) {
        expenses.filter { exp ->
            val cal = Calendar.getInstance().apply { timeInMillis = exp.timestamp }
            cal.get(Calendar.YEAR) == selectedYear && cal.get(Calendar.MONTH) == selectedMonth
        }.sortedByDescending { it.timestamp }
    }

    val totalMonthlyExpense = remember(monthlyExpenses) {
        monthlyExpenses.sumOf { it.amount }
    }

    val todayDateStr = remember { dateFormat.format(Date()) }
    val totalTodayExpense = remember(expenses, todayDateStr) {
        expenses.filter { it.date == todayDateStr }.sumOf { it.amount }
    }

    fun openDatePicker() {
        val cal = Calendar.getInstance().apply { timeInMillis = selectedDateMillis }
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val pickedCal = Calendar.getInstance().apply {
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month)
                    set(Calendar.DAY_OF_MONTH, dayOfMonth)
                }
                selectedDateMillis = pickedCal.timeInMillis
                dateText = dateFormat.format(pickedCal.time)
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Expenses & Monthly Report",
                            fontWeight = FontWeight.Black,
                            fontSize = 18.sp,
                            color = Color.White
                        )
                        Text(
                            text = "Log Daily Expenses & View Monthly Totals",
                            fontSize = 11.sp,
                            color = BabaYellow
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
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
                .padding(paddingValues)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Section 1: Log Daily Expense Card
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
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
                                        .background(BabaRed, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AddCard,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Text(
                                    text = "Log Daily Expense",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = BabaDark
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = BabaOverdueLight
                            ) {
                                Text(
                                    text = "Today: ₹${totalTodayExpense.toInt()}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = BabaOverdue,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        // 1. Date (Auto-populated with today's date, clickable to pick date)
                        OutlinedTextField(
                            value = dateText,
                            onValueChange = { dateText = it },
                            label = { Text("Date") },
                            readOnly = true,
                            trailingIcon = {
                                IconButton(onClick = { openDatePicker() }) {
                                    Icon(Icons.Default.CalendarToday, contentDescription = "Pick Date", tint = BabaRed)
                                }
                            },
                            colors = babaTextFieldColors(),
                            textStyle = babaInputTextStyle(fontWeight = FontWeight.Bold),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { openDatePicker() }
                                .testTag("expense_date_input"),
                            shape = RoundedCornerShape(10.dp)
                        )

                        // 2. Expense Type / Description
                        OutlinedTextField(
                            value = expenseType,
                            onValueChange = {
                                expenseType = it
                                errorMessage = null
                            },
                            label = { Text("Expense Type / Description *") },
                            placeholder = { Text("e.g., Machine Repair, Diesel, Shop Maintenance, Tea/Staff") },
                            colors = babaTextFieldColors(),
                            textStyle = babaInputTextStyle(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("expense_type_input"),
                            shape = RoundedCornerShape(10.dp)
                        )

                        // 3. Amount
                        OutlinedTextField(
                            value = amountText,
                            onValueChange = {
                                amountText = it.filter { c -> c.isDigit() || c == '.' }
                                errorMessage = null
                            },
                            label = { Text("Amount (₹) *") },
                            placeholder = { Text("Enter expense amount") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = babaTextFieldColors(),
                            textStyle = babaInputTextStyle(fontWeight = FontWeight.Bold),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("expense_amount_input"),
                            shape = RoundedCornerShape(10.dp)
                        )

                        // 4. Remarks
                        OutlinedTextField(
                            value = remarks,
                            onValueChange = { remarks = it },
                            label = { Text("Remarks") },
                            placeholder = { Text("Optional notes or bill reference") },
                            colors = babaTextFieldColors(),
                            textStyle = babaInputTextStyle(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("expense_remarks_input"),
                            shape = RoundedCornerShape(10.dp)
                        )

                        if (errorMessage != null) {
                            Text(
                                text = errorMessage!!,
                                color = BabaOverdue,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Button(
                            onClick = {
                                val amt = amountText.toDoubleOrNull() ?: 0.0
                                if (expenseType.trim().isEmpty()) {
                                    errorMessage = "Please enter Expense Type / Description"
                                    return@Button
                                }
                                if (amt <= 0.0) {
                                    errorMessage = "Please enter a valid Amount greater than ₹0"
                                    return@Button
                                }
                                onAddExpense(
                                    dateText,
                                    selectedDateMillis,
                                    expenseType.trim(),
                                    amt,
                                    remarks.trim(),
                                    {
                                        expenseType = ""
                                        amountText = ""
                                        remarks = ""
                                        selectedDateMillis = System.currentTimeMillis()
                                        dateText = dateFormat.format(Date(selectedDateMillis))
                                        Toast.makeText(context, "Expense saved successfully", Toast.LENGTH_SHORT).show()
                                    },
                                    { err ->
                                        errorMessage = err
                                    }
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = BabaRed),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("save_expense_button")
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = BabaYellow)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Add Expense", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White)
                        }
                    }
                }
            }

            // Section 2: Monthly Expense View / Report Header & Month Selector
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ReceiptLong,
                                    contentDescription = null,
                                    tint = BabaRed,
                                    modifier = Modifier.size(22.dp)
                                )
                               Column {
                                    Text(
                                        text = "Monthly Expense Report",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = BabaDark
                                    )
                                    Text(
                                        text = "Itemized list & month-end total expense",
                                        fontSize = 11.sp,
                                        color = BabaGray
                                    )
                                }
                            }
                        }

                        // Month Navigation Bar
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = BabaLightGray,
                            border = androidx.compose.foundation.BorderStroke(1.dp, BabaBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = {
                                        if (selectedMonth == 0) {
                                            selectedMonth = 11
                                            selectedYear -= 1
                                        } else {
                                            selectedMonth -= 1
                                        }
                                    }
                                ) {
                                    Icon(Icons.Default.ChevronLeft, contentDescription = "Previous Month", tint = BabaDark)
                                }

                                Text(
                                    text = selectedMonthTitle,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 15.sp,
                                    color = BabaDark
                                )

                                IconButton(
                                    onClick = {
                                        if (selectedMonth == 11) {
                                            selectedMonth = 0
                                            selectedYear += 1
                                        } else {
                                            selectedMonth += 1
                                        }
                                    }
                                ) {
                                    Icon(Icons.Default.ChevronRight, contentDescription = "Next Month", tint = BabaDark)
                                }
                            }
                        }

                        // Total Monthly Expense Banner
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = BabaOverdueLight,
                            border = androidx.compose.foundation.BorderStroke(1.dp, BabaOverdue),
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
                                    Text(
                                        text = "TOTAL MONTHLY EXPENSE",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = BabaOverdue
                                    )
                                    Text(
                                        text = "$selectedMonthTitle (${monthlyExpenses.size} entries)",
                                        fontSize = 11.sp,
                                        color = BabaDark
                                    )
                                }
                                Text(
                                    text = "₹${totalMonthlyExpense.toInt()}",
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Black,
                                    color = BabaOverdue
                                )
                            }
                        }
                    }
                }
            }

            // Itemized Monthly Expenses List
            if (monthlyExpenses.isEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(28.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No expenses recorded for $selectedMonthTitle",
                                fontSize = 13.sp,
                                color = BabaGray
                            )
                        }
                    }
                }
            } else {
                items(monthlyExpenses, key = { it.id }) { exp ->
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = BabaLightGray
                                    ) {
                                        Text(
                                            text = exp.date,
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = BabaDark,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                    Text(
                                        text = exp.expenseType,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = BabaDark
                                    )
                                }
                                if (exp.remarks.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Remarks: ${exp.remarks}",
                                        fontSize = 12.sp,
                                        color = BabaGray
                                    )
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Logged by: ${exp.createdByName}",
                                    fontSize = 10.sp,
                                    color = BabaGray
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "₹${exp.amount.toInt()}",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 16.sp,
                                    color = BabaOverdue
                                )
                                if (currentUser.role == "owner") {
                                    IconButton(
                                        onClick = { onDeleteExpense(exp.id) },
                                        modifier = Modifier.size(30.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.DeleteOutline,
                                            contentDescription = "Delete Expense",
                                            tint = BabaGray,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // End-of-Month Total Footer Card
                item {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = BabaDark,
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
                                Text(
                                    text = "MONTH-END TOTAL EXPENSE",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = BabaYellow
                                )
                                Text(
                                    text = selectedMonthTitle,
                                    fontSize = 12.sp,
                                    color = Color.White
                                )
                            }
                            Text(
                                text = "₹${totalMonthlyExpense.toInt()}",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Black,
                                color = BabaYellow
                            )
                        }
                    }
                }
            }
        }
    }
}
