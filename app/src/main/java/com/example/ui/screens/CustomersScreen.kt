package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.model.CustomerEntity
import com.example.data.model.JobSheetEntity
import com.example.ui.theme.*

enum class CustomerSortOrder(val label: String) {
    NAME_ASC("A–Z Order"),
    SERIAL_ORDER("Order Added (#1, #2...)"),
    ACTIVE_FIRST("Active Orders First"),
    RECENT_FIRST("Newest First")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomersScreen(
    customers: List<CustomerEntity>,
    allJobSheets: List<JobSheetEntity>,
    onSaveCustomer: (existingCustomer: CustomerEntity?, name: String, phone: String, address: String, onSuccess: () -> Unit, onError: (String) -> Unit) -> Unit = { _, _, _, _, _, _ -> },
    onDeleteCustomer: (customerId: String) -> Unit = {},
    onNavigateBack: () -> Unit
) {
    BackHandler { onNavigateBack() }
    var searchQuery by remember { mutableStateOf("") }
    var sortOrder by remember { mutableStateOf(CustomerSortOrder.NAME_ASC) }
    var showAddOrEditDialog by remember { mutableStateOf(false) }
    var customerToEdit by remember { mutableStateOf<CustomerEntity?>(null) }
    var customerToDelete by remember { mutableStateOf<CustomerEntity?>(null) }

    val orderedCustomers = remember(customers, allJobSheets, searchQuery, sortOrder) {
        val baseFiltered = if (searchQuery.isBlank()) {
            customers
        } else {
            val q = searchQuery.trim()
            customers.filter {
                it.name.contains(q, ignoreCase = true) ||
                it.phone.contains(q) ||
                it.address.contains(q, ignoreCase = true)
            }
        }

        when (sortOrder) {
            CustomerSortOrder.NAME_ASC -> baseFiltered.sortedWith(
                compareBy<CustomerEntity> { it.name.lowercase() }.thenBy { it.createdAt }
            )
            CustomerSortOrder.SERIAL_ORDER -> baseFiltered.sortedBy { it.createdAt }
            CustomerSortOrder.ACTIVE_FIRST -> baseFiltered.sortedWith(
                compareByDescending<CustomerEntity> { cust ->
                    allJobSheets.count {
                        (it.customerId == cust.id || it.customerPhone == cust.phone) &&
                        (it.status == "active" || it.status == "overdue")
                    }
                }.thenBy { it.name.lowercase() }
            )
            CustomerSortOrder.RECENT_FIRST -> baseFiltered.sortedByDescending { it.createdAt }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Customer Directory (${customers.size})",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = Color.White
                        )
                        Text(
                            text = "Add & maintain customers in order",
                            fontSize = 11.sp,
                            color = BabaYellow
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
                            customerToEdit = null
                            showAddOrEditDialog = true
                        },
                        modifier = Modifier.testTag("top_add_customer_button")
                    ) {
                        Icon(Icons.Default.PersonAdd, contentDescription = "Add Customer", tint = BabaYellow)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BabaRed)
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    customerToEdit = null
                    showAddOrEditDialog = true
                },
                containerColor = BabaRed,
                contentColor = Color.White,
                icon = { Icon(Icons.Default.PersonAdd, contentDescription = "Add Customer", tint = BabaYellow) },
                text = { Text("Add Customer", fontWeight = FontWeight.Bold) },
                modifier = Modifier.testTag("fab_add_customer_button")
            )
        },
        containerColor = BabaBg
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Search & Quick Add Bar
            Surface(
                color = Color.White,
                shadowElevation = 1.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search by customer name, phone, or site...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = BabaGray) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear", tint = BabaGray)
                                }
                            }
                        },
                        colors = babaTextFieldColors(),
                        textStyle = babaInputTextStyle(),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("customer_search_input"),
                        shape = RoundedCornerShape(12.dp)
                    )

                    // Order / Sort Bar + Add Customer Button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Sort,
                            contentDescription = "Order",
                            tint = BabaRed,
                            modifier = Modifier.size(18.dp)
                        )
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            CustomerSortOrder.entries.forEach { option ->
                                val isSelected = sortOrder == option
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { sortOrder = option },
                                    label = {
                                        Text(
                                            text = option.label,
                                            fontSize = 11.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = BabaYellowLight,
                                        selectedLabelColor = BabaDark
                                    )
                                )
                            }
                        }
                    }
                }
            }

            if (orderedCustomers.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = BabaRedLight,
                                modifier = Modifier.size(56.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.People,
                                        contentDescription = "No Customers",
                                        tint = BabaRed,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }
                            Text(
                                text = if (customers.isEmpty()) "No Customers Added Yet" else "No Matching Customers",
                                fontWeight = FontWeight.Black,
                                fontSize = 17.sp,
                                color = BabaDark
                            )
                            Text(
                                text = "Add your customers manually with their phone number and site address to keep your customer directory organized in order.",
                                fontSize = 12.sp,
                                color = BabaGray,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Button(
                                onClick = {
                                    customerToEdit = null
                                    showAddOrEditDialog = true
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = BabaRed),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.PersonAdd, contentDescription = null, tint = BabaYellow, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Add Customer Manually", fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    itemsIndexed(orderedCustomers, key = { _, cust -> cust.id }) { index, customer ->
                        val orderNumber = index + 1
                        val customerJobs = allJobSheets.filter { it.customerId == customer.id || it.customerPhone == customer.phone }
                        val activeJobsCount = customerJobs.count { it.status == "active" || it.status == "overdue" }

                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        modifier = Modifier.weight(1f),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        // Sequential Order Badge (#1, #2, #3...)
                                        Box(
                                            modifier = Modifier
                                                .size(38.dp)
                                                .background(BabaRedLight, CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "#$orderNumber",
                                                fontWeight = FontWeight.Black,
                                                fontSize = 13.sp,
                                                color = BabaRed
                                            )
                                        }

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = customer.name,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 15.sp,
                                                color = BabaDark
                                            )
                                            Text(
                                                text = customer.phone,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = BabaGray
                                            )
                                        }
                                    }

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (activeJobsCount > 0) BabaYellowLight else BabaLightGray
                                        ) {
                                            Text(
                                                text = if (activeJobsCount > 0) "$activeJobsCount Active" else "${customerJobs.size} Rentals",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (activeJobsCount > 0) BabaDark else BabaGray,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }

                                        IconButton(
                                            onClick = {
                                                customerToEdit = customer
                                                showAddOrEditDialog = true
                                            },
                                            modifier = Modifier.size(30.dp).testTag("edit_customer_${customer.id}")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Edit,
                                                contentDescription = "Edit Customer",
                                                tint = BabaDark,
                                                modifier = Modifier.size(17.dp)
                                            )
                                        }

                                        IconButton(
                                            onClick = { customerToDelete = customer },
                                            modifier = Modifier.size(30.dp).testTag("delete_customer_${customer.id}")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.DeleteOutline,
                                                contentDescription = "Delete Customer",
                                                tint = BabaOverdue,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }

                                if (customer.address.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(Icons.Default.Place, contentDescription = null, modifier = Modifier.size(14.dp), tint = BabaGray)
                                        Text(text = customer.address, fontSize = 12.sp, color = BabaGray)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Add / Edit Customer Dialog
    if (showAddOrEditDialog) {
        val editing = customerToEdit
        var name by remember(editing) { mutableStateOf(editing?.name ?: "") }
        var phone by remember(editing) { mutableStateOf(editing?.phone ?: "") }
        var address by remember(editing) { mutableStateOf(editing?.address ?: "") }
        var errorMessage by remember { mutableStateOf<String?>(null) }

        Dialog(onDismissRequest = {
            showAddOrEditDialog = false
            customerToEdit = null
        }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = BabaRedLight,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (editing == null) Icons.Default.PersonAdd else Icons.Default.Edit,
                                    contentDescription = null,
                                    tint = BabaRed,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = if (editing == null) "Add Customer Manually" else "Edit Customer Details",
                                fontWeight = FontWeight.Black,
                                fontSize = 17.sp,
                                color = BabaDark
                            )
                            Text(
                                text = "Saved customers auto-fill when creating job sheets",
                                fontSize = 11.5.sp,
                                color = BabaGray
                            )
                        }
                    }

                    OutlinedTextField(
                        value = name,
                        onValueChange = {
                            name = it
                            errorMessage = null
                        },
                        label = { Text("Customer Name *") },
                        placeholder = { Text("Enter full name") },
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = BabaGray) },
                        singleLine = true,
                        colors = babaTextFieldColors(),
                        textStyle = babaInputTextStyle(fontWeight = FontWeight.Bold),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("manual_customer_name_input"),
                        shape = RoundedCornerShape(10.dp)
                    )

                    OutlinedTextField(
                        value = phone,
                        onValueChange = {
                            phone = it.filter { c -> c.isDigit() }.take(10)
                            errorMessage = null
                        },
                        label = { Text("Phone Number (10 Digits) *") },
                        placeholder = { Text("e.g., 9876543210") },
                        leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null, tint = BabaGray) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        colors = babaTextFieldColors(),
                        textStyle = babaInputTextStyle(fontWeight = FontWeight.Bold),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("manual_customer_phone_input"),
                        shape = RoundedCornerShape(10.dp)
                    )

                    OutlinedTextField(
                        value = address,
                        onValueChange = {
                            address = it
                            errorMessage = null
                        },
                        label = { Text("Site / Delivery Address") },
                        placeholder = { Text("Enter site address or area") },
                        leadingIcon = { Icon(Icons.Default.Place, contentDescription = null, tint = BabaGray) },
                        colors = babaTextFieldColors(),
                        textStyle = babaInputTextStyle(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("manual_customer_address_input"),
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

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                showAddOrEditDialog = false
                                customerToEdit = null
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Cancel", color = BabaDark)
                        }

                        Button(
                            onClick = {
                                onSaveCustomer(
                                    editing,
                                    name,
                                    phone,
                                    address,
                                    {
                                        showAddOrEditDialog = false
                                        customerToEdit = null
                                    },
                                    { err ->
                                        errorMessage = err
                                    }
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = BabaRed),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("confirm_save_customer_button"),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (editing == null) "Save Customer" else "Update",
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }

    // Delete Customer Confirmation Dialog
    if (customerToDelete != null) {
        val cust = customerToDelete!!
        Dialog(onDismissRequest = { customerToDelete = null }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Delete Customer?",
                        fontWeight = FontWeight.Black,
                        fontSize = 17.sp,
                        color = BabaDark
                    )
                    Text(
                        text = "Remove ${cust.name} (${cust.phone}) from the Customer Directory?",
                        fontSize = 12.5.sp,
                        color = BabaGray
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { customerToDelete = null },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Cancel", color = BabaDark)
                        }
                        Button(
                            onClick = {
                                onDeleteCustomer(cust.id)
                                customerToDelete = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = BabaOverdue),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Delete", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
