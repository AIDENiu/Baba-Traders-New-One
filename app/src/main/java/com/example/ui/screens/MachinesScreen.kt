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

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.draw.clip

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MachinesScreen(
    currentUser: UserEntity,
    categories: List<MachineCategoryEntity>,
    machines: List<MachineEntity>,
    onAddMachine: (categoryId: String, name: String, rate: Double, qty: Int, specs: String) -> Unit,
    onUpdatePricing: (machineId: String, currentRate: Double, totalQty: Int) -> Unit,
    onDeleteMachine: (machineId: String) -> Unit = {},
    onDeleteAllMachines: () -> Unit = {},
    onNavigateBack: () -> Unit
) {
    BackHandler { onNavigateBack() }
    var selectedCategoryId by remember { mutableStateOf<String?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var machineToEdit by remember { mutableStateOf<MachineEntity?>(null) }
    var machineToDelete by remember { mutableStateOf<MachineEntity?>(null) }
    var showDeleteAllDialog by remember { mutableStateOf(false) }

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
                    if (machines.isNotEmpty()) {
                        TextButton(
                            onClick = { showDeleteAllDialog = true },
                            colors = ButtonDefaults.textButtonColors(contentColor = Color.White),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.testTag("delete_all_machines_button")
                        ) {
                            Icon(
                                Icons.Default.DeleteSweep,
                                contentDescription = "Delete All Machines",
                                tint = BabaYellow,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Delete All", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = BabaYellow)
                        }
                    }
                    IconButton(onClick = { showAddDialog = true }, modifier = Modifier.testTag("add_machine_button")) {
                        Icon(Icons.Default.Add, contentDescription = "Add Machine", tint = BabaYellow)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BabaRed)
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = BabaRed,
                contentColor = Color.White,
                icon = { Icon(Icons.Default.Add, contentDescription = "Add Machine", tint = BabaYellow) },
                text = { Text("Add Machine", fontWeight = FontWeight.Bold) }
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

            if (filteredMachines.isEmpty()) {
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
                                        imageVector = Icons.Default.HomeRepairService,
                                        contentDescription = "No Machines",
                                        tint = BabaRed,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }
                            Text(
                                text = if (machines.isEmpty()) "No Machinery Added Yet" else "No Machines in This Category",
                                fontWeight = FontWeight.Black,
                                fontSize = 17.sp,
                                color = BabaDark
                            )
                            Text(
                                text = "Your machinery & rates catalog is clean. Tap below to add your shop's machines and daily rental rates.",
                                fontSize = 12.sp,
                                color = BabaGray,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Button(
                                onClick = { showAddDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = BabaRed),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, tint = BabaYellow, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Add Machine", fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 88.dp),
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

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
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

                                        Surface(
                                            shape = CircleShape,
                                            color = BabaOverdueLight,
                                            modifier = Modifier
                                                .size(28.dp)
                                                .clip(CircleShape)
                                                .clickable { machineToDelete = machine }
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = Icons.Default.DeleteOutline,
                                                    contentDescription = "Delete ${machine.name}",
                                                    tint = BabaOverdue,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
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

                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        OutlinedButton(
                                            onClick = { machineToDelete = machine },
                                            shape = RoundedCornerShape(8.dp),
                                            border = BorderStroke(1.dp, BabaOverdue.copy(alpha = 0.6f)),
                                            colors = ButtonDefaults.outlinedButtonColors(
                                                containerColor = BabaOverdueLight.copy(alpha = 0.45f),
                                                contentColor = BabaOverdue
                                            ),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                            modifier = Modifier.testTag("delete_machine_${machine.id}")
                                        ) {
                                            Icon(Icons.Default.DeleteOutline, contentDescription = "Delete ${machine.name}", modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Delete", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }

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
    }

    // Add Machine Dialog
    if (showAddDialog) {
        var name by remember { mutableStateOf("") }
        var selectedCatId by remember { mutableStateOf(selectedCategoryId ?: categories.firstOrNull()?.id ?: "") }
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

                    if (categories.isNotEmpty()) {
                        Text("Category", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = BabaGray)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            categories.forEach { cat ->
                                val isSel = cat.id == selectedCatId
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSel) BabaYellowLight else BabaLightGray,
                                    border = BorderStroke(1.dp, if (isSel) BabaYellowDark else BabaBorder),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { selectedCatId = cat.id }
                                ) {
                                    Text(
                                        text = cat.name,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                        color = BabaDark,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }

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
                        label = { Text("Brand / Model Specification") },
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

                    HorizontalDivider(color = BabaBorder)

                    OutlinedButton(
                        onClick = {
                            val target = machine
                            machineToEdit = null
                            machineToDelete = target
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = BabaOverdue),
                        border = BorderStroke(1.dp, BabaOverdue.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Delete Machine from Inventory", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }
    }

    // Delete Machine Confirmation Dialog
    if (machineToDelete != null) {
        val machine = machineToDelete!!
        val categoryName = categories.find { it.id == machine.categoryId }?.name ?: "Equipment"
        val rentedOutCount = (machine.totalQty - machine.availableQty).coerceAtLeast(0)

        Dialog(onDismissRequest = { machineToDelete = null }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier.fillMaxWidth().padding(8.dp)
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
                            color = BabaOverdueLight,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.DeleteForever,
                                    contentDescription = "Delete Machine",
                                    tint = BabaOverdue,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Delete Machine?",
                                fontWeight = FontWeight.Black,
                                fontSize = 17.sp,
                                color = BabaDark
                            )
                            Text(
                                text = "Remove from machinery inventory",
                                fontSize = 12.sp,
                                color = BabaGray
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = BabaLightGray,
                        border = BorderStroke(1.dp, BabaBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = machine.name,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = BabaDark
                            )
                            Text(
                                text = "$categoryName • ₹${machine.currentRate.toInt()}/day • Stock: ${machine.availableQty}/${machine.totalQty}",
                                fontSize = 12.sp,
                                color = BabaGray
                            )
                            if (rentedOutCount > 0) {
                                Text(
                                    text = "Note: $rentedOutCount unit(s) are currently checked out on active rentals.",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = BabaOverdue,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                    }

                    Text(
                        text = "Are you sure you want to delete this machine? Existing job sheet receipts will retain their recorded machine names.",
                        fontSize = 12.sp,
                        color = BabaGray
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { machineToDelete = null },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Cancel", color = BabaDark)
                        }
                        Button(
                            onClick = {
                                onDeleteMachine(machine.id)
                                machineToDelete = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = BabaOverdue),
                            modifier = Modifier.weight(1f).testTag("confirm_delete_machine_button"),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Delete", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    // Delete All Machines Confirmation Dialog
    if (showDeleteAllDialog) {
        Dialog(onDismissRequest = { showDeleteAllDialog = false }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier.fillMaxWidth().padding(8.dp)
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
                            color = BabaOverdueLight,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.DeleteSweep,
                                    contentDescription = "Delete All Machines",
                                    tint = BabaOverdue,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Delete All Machines?",
                                fontWeight = FontWeight.Black,
                                fontSize = 17.sp,
                                color = BabaDark
                            )
                            Text(
                                text = "Clear all ${machines.size} machine(s) from inventory",
                                fontSize = 12.sp,
                                color = BabaGray
                            )
                        }
                    }

                    Text(
                        text = "This will remove all machines from Machinery & Rates so you can add a fresh list of machines.",
                        fontSize = 12.sp,
                        color = BabaGray
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showDeleteAllDialog = false },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Cancel", color = BabaDark)
                        }
                        Button(
                            onClick = {
                                onDeleteAllMachines()
                                showDeleteAllDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = BabaOverdue),
                            modifier = Modifier.weight(1f).testTag("confirm_delete_all_machines_button"),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Delete All", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
