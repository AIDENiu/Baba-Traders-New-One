package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.data.model.CustomerEntity
import com.example.data.model.JobSheetEntity
import com.example.ui.theme.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomersScreen(
    customers: List<CustomerEntity>,
    allJobSheets: List<JobSheetEntity>,
    onNavigateBack: () -> Unit
) {
    BackHandler { onNavigateBack() }
    var searchQuery by remember { mutableStateOf("") }
    var selectedCustomerForHistory by remember { mutableStateOf<CustomerEntity?>(null) }

    val filteredCustomers = remember(customers, searchQuery) {
        if (searchQuery.trim().isEmpty()) {
            customers
        } else {
            val q = searchQuery.trim().lowercase()
            customers.filter {
                it.name.lowercase().contains(q) || it.phone.contains(q) || it.address.lowercase().contains(q)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Customer Directory & History", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Color.White) },
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
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search by customer name or phone...", fontSize = 13.sp) },
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
                    .padding(16.dp)
            )

            if (filteredCustomers.isEmpty()) {
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
                            imageVector = Icons.Default.PeopleOutline,
                            contentDescription = "No customers",
                            tint = BabaGray,
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = if (searchQuery.isNotEmpty()) "No customers match your search" else "No customers found",
                            color = BabaGray,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredCustomers, key = { it.id }) { customer ->
                        val customerJobs = allJobSheets.filter { it.customerId == customer.id && !it.isDeleted }
                        val pendingDues = customerJobs.filter { it.finalSettlement > 0 }.sumOf { it.finalSettlement }

                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { selectedCustomerForHistory = customer }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(42.dp)
                                            .background(BabaRedLight, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.Person, contentDescription = "User", tint = BabaRed, modifier = Modifier.size(24.dp))
                                    }

                                    Column {
                                        Text(text = customer.name, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = BabaDark)
                                        Text(text = "Ph: ${customer.phone}", fontSize = 12.sp, color = BabaGray)
                                        if (customer.address.isNotEmpty()) {
                                            Text(text = customer.address, fontSize = 11.sp, color = BabaGray, maxLines = 1)
                                        }
                                    }
                                }

                                Column(horizontalAlignment = Alignment.End) {
                                    Surface(shape = RoundedCornerShape(8.dp), color = BabaLightGray) {
                                        Text(
                                            text = "${customerJobs.size} Rentals",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = BabaDark,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                    if (pendingDues > 0) {
                                        Text(
                                            text = "Due: ₹${pendingDues.toInt()}",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = BabaOverdue,
                                            modifier = Modifier.padding(top = 4.dp)
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

    // Customer History Dialog
    if (selectedCustomerForHistory != null) {
        val cust = selectedCustomerForHistory!!
        val customerJobs = allJobSheets.filter { it.customerId == cust.id && !it.isDeleted }
        val dateFormat = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }

        Dialog(onDismissRequest = { selectedCustomerForHistory = null }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier.fillMaxWidth().padding(8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(text = cust.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = BabaDark)
                            Text(text = "Phone: ${cust.phone}", fontSize = 12.sp, color = BabaGray)
                            if (cust.address.isNotEmpty()) {
                                Text(text = cust.address, fontSize = 11.sp, color = BabaGray)
                            }
                        }
                        IconButton(onClick = { selectedCustomerForHistory = null }) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = BabaBorder)

                    if (cust.idPhotoFrontPath != null || cust.idPhotoBackPath != null) {
                        Text(
                            text = "Customer ID Documents on File",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = BabaDark,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (cust.idPhotoFrontPath != null) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Front ID Photo", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = BabaGray)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    AsyncImage(
                                        model = File(cust.idPhotoFrontPath),
                                        contentDescription = "Front ID",
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(84.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .border(1.dp, BabaBorder, RoundedCornerShape(8.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                            }
                            if (cust.idPhotoBackPath != null) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Back ID Photo", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = BabaGray)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    AsyncImage(
                                        model = File(cust.idPhotoBackPath),
                                        contentDescription = "Back ID",
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(84.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .border(1.dp, BabaBorder, RoundedCornerShape(8.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                            }
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = BabaBorder)
                    }

                    Text(
                        text = "Rental History (${customerJobs.size} transactions)",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = BabaDark,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    if (customerJobs.isEmpty()) {
                        Text("No past rentals found", fontSize = 12.sp, color = BabaGray)
                    } else {
                        customerJobs.forEach { job ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = BabaLightGray,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(text = job.id, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = BabaRed)
                                        Text(text = dateFormat.format(Date(job.checkoutTime)), fontSize = 10.sp, color = BabaGray)
                                    }
                                    Text(
                                        text = "${job.quantity}x ${job.machineName}",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = BabaDark
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(text = "Rate: ₹${job.rateUsed} • Deposit: ₹${job.depositAmount}", fontSize = 11.sp, color = BabaGray)
                                        Text(
                                            text = job.status.uppercase(),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (job.status == "returned") BabaSuccess else BabaYellowDark
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Button(
                        onClick = { selectedCustomerForHistory = null },
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
}
