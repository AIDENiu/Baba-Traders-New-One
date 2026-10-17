package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.model.MachineCategoryEntity
import com.example.data.model.MachineEntity
import com.example.data.model.UserEntity
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MachinesScreen(
    currentUser: UserEntity,
    categories: List<MachineCategoryEntity>,
    machines: List<MachineEntity>,
    onAddMachine: (categoryId: String, name: String, rate: Double, qty: Int, specs: String) -> Unit,
    onUpdatePricing: (machineId: String, currentRate: Double, totalQty: Int) -> Unit,
    onNavigateBack: () -> Unit
) {
    BackHandler { onNavigateBack() }
    var selectedCategoryId by remember { mutableStateOf<String?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var machineToEdit by remember { mutableStateOf<MachineEntity?>(null) }

    val filteredMachines = remember(machines, selectedCategoryId) {
        if (selectedCategoryId == null) machines
        else machines.filter { it.categoryId == selectedCategoryId }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Machinery & Rates", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    if (currentUser.role == "owner") {
                        IconButton(onClick = { showAddDialog = true }, modifier = Modifier.testTag("add_machine_button")) {
                            Icon(Icons.Default.Add, contentDescription = "Add Machine", tint = BabaYellow)
                        }
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
            // Category Filter Scrollable Tabs
            ScrollableTabRow(
                selectedTabIndex = if (selectedCategoryId == null) 0 else (categories.indexOfFirst { it.id == selectedCategoryId } + 1).coerceAtLeast(0),
                edgePadding = 16.dp,
                containerColor = Color.White,
                contentColor = BabaRed
            ) {
                Tab(
                    selected = selectedCategoryId == null,
                    onClick = { selectedCategoryId = null },
                    text = { Text("All (${machines.size})", fontWeight = if (selectedCategoryId == null) FontWeight.Bold else FontWeight.Normal) }
                )
                categories.forEach { cat ->
                    val count = machines.count { it.categoryId == cat.id }
                    Tab(
                        selected = selectedCategoryId == cat.id,
                        onClick = { selectedCategoryId = cat.id },
                        text = { Text("${cat.name} ($count)", fontWeight = if (selectedCategoryId == cat.id) FontWeight.Bold else FontWeight.Normal) }
                    )
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredMachines, key = { it.id }) { machine ->
                    val category = categories.find { it.id == machine.categoryId }?.name ?: "Equipment"
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
                                verticalAlignment = Alignment.Top
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = machine.name,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = BabaDark
                                    )
                                    Text(
                                        text = category + if (machine.modelOrSpecs.isNotBlank()) " • ${machine.modelOrSpecs}" else "",
                                        fontSize = 12.sp,
                                        color = BabaGray
                                    )
                                }

                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (machine.availableQty > 0) BabaSuccessLight else BabaOverdueLight
                                ) {
                                    Text(
                                        text = "${machine.availableQty}/${machine.totalQty} Available",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (machine.availableQty > 0) BabaSuccess else BabaOverdue,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = BabaBorder)

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("Rental Rate", fontSize = 10.sp, color = BabaGray)
                                    Row(verticalAlignment = Alignment.Bottom) {
                                        Text(
                                            text = "₹${machine.currentRate.toInt()}",
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Black,
                                            color = BabaDark
                                        )
                                        Text(
                                            text = " / 24 hrs",
                                            fontSize = 11.sp,
                                            color = BabaGray,
                                            modifier = Modifier.padding(bottom = 2.dp)
                                        )
                                    }
                                }

                                if (currentUser.role == "owner") {
                                    OutlinedButton(
                                        onClick = { machineToEdit = machine },
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                        modifier = Modifier.testTag("edit_pricing_${machine.id}")
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Update Rate", fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Add Machine Dialog
    if (showAddDialog) {
        var name by remember { mutableStateOf("") }
        var selectedCatId by remember { mutableStateOf(categories.firstOrNull()?.id ?: "") }
        var rateText by remember { mutableStateOf("") }
        var qtyText by remember { mutableStateOf("1") }
        var specs by remember { mutableStateOf("") }

        Dialog(onDismissRequest = { showAddDialog = false }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier.fillMaxWidth().padding(8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Add Machinery to Inventory", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = BabaDark)

                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Machine Name *") },
                        colors = babaTextFieldColors(),
                        textStyle = babaInputTextStyle(),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    )

                    OutlinedTextField(
                        value = rateText,
                        onValueChange = { rateText = it },
                        label = { Text("Daily Rate (₹) *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = babaTextFieldColors(),
                        textStyle = babaInputTextStyle(fontWeight = FontWeight.Bold),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    )

                    OutlinedTextField(
                        value = qtyText,
                        onValueChange = { qtyText = it },
                        label = { Text("Total Quantity *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = babaTextFieldColors(),
                        textStyle = babaInputTextStyle(),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    )

                    OutlinedTextField(
                        value = specs,
                        onValueChange = { specs = it },
                        label = { Text("Specs / Model (e.g. Bosch 800W)") },
                        colors = babaTextFieldColors(),
                        textStyle = babaInputTextStyle(),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(onClick = { showAddDialog = false }, modifier = Modifier.weight(1f)) {
                            Text("Cancel")
                        }
                        Button(
                            onClick = {
                                val rate = rateText.toDoubleOrNull() ?: 0.0
                                val qty = qtyText.toIntOrNull() ?: 1
                                if (name.isNotBlank() && rate > 0) {
                                    onAddMachine(selectedCatId, name.trim(), rate, qty, specs.trim())
                                    showAddDialog = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = BabaRed),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Add Machine", color = Color.White)
                        }
                    }
                }
            }
        }
    }

    // Edit Pricing Dialog
    if (machineToEdit != null) {
        val machine = machineToEdit!!
        var currentRateText by remember { mutableStateOf(machine.currentRate.toInt().toString()) }
        var totalQtyText by remember { mutableStateOf(machine.totalQty.toString()) }

        Dialog(onDismissRequest = { machineToEdit = null }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier.fillMaxWidth().padding(8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Update Machine Rate & Stock", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = BabaDark)
                    Text(machine.name, fontSize = 13.sp, color = BabaGray)

                    OutlinedTextField(
                        value = currentRateText,
                        onValueChange = { currentRateText = it },
                        label = { Text("Current Daily Rate (₹)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = babaTextFieldColors(),
                        textStyle = babaInputTextStyle(fontWeight = FontWeight.Bold),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    )

                    OutlinedTextField(
                        value = totalQtyText,
                        onValueChange = { totalQtyText = it },
                        label = { Text("Total Stock Quantity") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = babaTextFieldColors(),
                        textStyle = babaInputTextStyle(),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(onClick = { machineToEdit = null }, modifier = Modifier.weight(1f)) {
                            Text("Cancel")
                        }
                        Button(
                            onClick = {
                                val rate = currentRateText.toDoubleOrNull() ?: machine.currentRate
                                val qty = totalQtyText.toIntOrNull() ?: machine.totalQty
                                onUpdatePricing(machine.id, rate, qty)
                                machineToEdit = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = BabaRed),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Save Changes", color = Color.White)
                        }
                    }
                }
            }
        }
    }
}
