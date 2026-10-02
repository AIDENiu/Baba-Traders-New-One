package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
    onAddMachine: (categoryId: String, name: String, rate: Double, totalQty: Int, specs: String) -> Unit,
    onUpdatePricing: (machineId: String, currentRate: Double, totalQty: Int) -> Unit,
    onNavigateBack: () -> Unit
) {
    BackHandler { onNavigateBack() }
    var selectedCategoryId by remember(categories) { mutableStateOf<String?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var machineToEdit by remember { mutableStateOf<MachineEntity?>(null) }

    val filtered = remember(machines, selectedCategoryId) {
        if (selectedCategoryId == null) machines else machines.filter { it.categoryId == selectedCategoryId }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Machinery Inventory", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Color.White)
                        Text(
                            text = if (currentUser.role == "owner") "Owner Mode (Editable)" else "Worker Mode (Read-Only Live Stock)",
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
                    if (currentUser.role == "owner") {
                        IconButton(onClick = { showAddDialog = true }, modifier = Modifier.testTag("add_machine_button")) {
                            Icon(Icons.Default.AddCircle, contentDescription = "Add Machine", tint = BabaYellow)
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
            // Category Filter Row (Horizontally scrollable to prevent vertical character wrapping)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = selectedCategoryId == null,
                    onClick = { selectedCategoryId = null },
                    label = { Text("All (${machines.size})", fontSize = 11.sp, maxLines = 1) }
                )
                categories.forEach { cat ->
                    val count = machines.count { it.categoryId == cat.id }
                    FilterChip(
                        selected = selectedCategoryId == cat.id,
                        onClick = { selectedCategoryId = cat.id },
                        label = { Text("${cat.name} ($count)", fontSize = 11.sp, maxLines = 1) }
                    )
                }
            }

            // Machines List or Empty State
            if (filtered.isEmpty()) {
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
                            imageVector = Icons.Default.HomeRepairService,
                            contentDescription = "No machinery",
                            tint = BabaGray,
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = "No machinery found in this category",
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
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filtered, key = { it.id }) { machine ->
                        MachineCard(
                            machine = machine,
                            isOwner = currentUser.role == "owner",
                            onEdit = { machineToEdit = machine }
                        )
                    }
                }
            }
        }
    }

    // Add Machine Dialog (Owner Only)
    if (showAddDialog) {
        AddMachineDialog(
            categories = categories,
            onDismiss = { showAddDialog = false },
            onAdd = { catId, name, rate, qty, specs ->
                onAddMachine(catId, name, rate, qty, specs)
                showAddDialog = false
            }
        )
    }

    // Edit Machine Pricing Dialog (Owner Only)
    if (machineToEdit != null) {
        EditMachinePricingDialog(
            machine = machineToEdit!!,
            onDismiss = { machineToEdit = null },
            onSave = { newRate, newTotalQty ->
                onUpdatePricing(machineToEdit!!.id, newRate, newTotalQty)
                machineToEdit = null
            }
        )
    }
}

@Composable
fun MachineCard(
    machine: MachineEntity,
    isOwner: Boolean,
    onEdit: () -> Unit
) {
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
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = BabaDark
                    )
                    if (machine.modelOrSpecs.isNotEmpty()) {
                        Text(
                            text = machine.modelOrSpecs,
                            fontSize = 11.sp,
                            color = BabaGray
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (machine.availableQty > 0) BabaSuccessLight else BabaOverdueLight
                ) {
                    Text(
                        text = "${machine.availableQty} / ${machine.totalQty} Available",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (machine.availableQty > 0) BabaSuccess else BabaOverdue,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column {
                        Text("Current Rate", fontSize = 10.sp, color = BabaGray)
                        Text(
                            text = "₹${machine.currentRate}/day",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Black,
                            color = BabaRed
                        )
                    }
                    Column {
                        Text("Base Rate", fontSize = 10.sp, color = BabaGray)
                        Text(
                            text = "₹${machine.baseRate}/day",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = BabaDark
                        )
                    }
                }

                if (isOwner) {
                    OutlinedButton(
                        onClick = onEdit,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Pricing & Stock", fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun AddMachineDialog(
    categories: List<MachineCategoryEntity>,
    onDismiss: () -> Unit,
    onAdd: (categoryId: String, name: String, rate: Double, totalQty: Int, specs: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var selectedCatId by remember { mutableStateOf(categories.firstOrNull()?.id ?: "") }
    var rateText by remember { mutableStateOf("400") }
    var qtyText by remember { mutableStateOf("3") }
    var specs by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier.fillMaxWidth().padding(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Add New Machinery", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = BabaDark)

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Machine Name *") },
                    colors = babaTextFieldColors(),
                    textStyle = babaInputTextStyle(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                )

                Text("Category", fontSize = 11.sp, color = BabaGray)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    categories.take(3).forEach { cat ->
                        FilterChip(
                            selected = selectedCatId == cat.id,
                            onClick = { selectedCatId = cat.id },
                            label = { Text(cat.name, fontSize = 10.sp) }
                        )
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = rateText,
                        onValueChange = { rateText = it },
                        label = { Text("Daily Rate (₹)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = babaTextFieldColors(),
                        textStyle = babaInputTextStyle(fontWeight = FontWeight.Bold),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    )
                    OutlinedTextField(
                        value = qtyText,
                        onValueChange = { qtyText = it },
                        label = { Text("Total Stock") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = babaTextFieldColors(),
                        textStyle = babaInputTextStyle(fontWeight = FontWeight.Bold),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    )
                }

                OutlinedTextField(
                    value = specs,
                    onValueChange = { specs = it },
                    label = { Text("Specs / Model / Capacity") },
                    colors = babaTextFieldColors(),
                    textStyle = babaInputTextStyle(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp)) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = {
                            if (name.isNotBlank()) {
                                onAdd(selectedCatId, name.trim(), rateText.toDoubleOrNull() ?: 400.0, qtyText.toIntOrNull() ?: 1, specs.trim())
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = BabaRed),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Add Machine")
                    }
                }
            }
        }
    }
}

@Composable
fun EditMachinePricingDialog(
    machine: MachineEntity,
    onDismiss: () -> Unit,
    onSave: (newRate: Double, newTotalQty: Int) -> Unit
) {
    var rateText by remember { mutableStateOf(machine.currentRate.toString()) }
    var qtyText by remember { mutableStateOf(machine.totalQty.toString()) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier.fillMaxWidth().padding(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Edit Pricing & Stock", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = BabaDark)
                Text(machine.name, fontSize = 13.sp, color = BabaGray)

                OutlinedTextField(
                    value = rateText,
                    onValueChange = { rateText = it },
                    label = { Text("Current Daily Rate (₹)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = babaTextFieldColors(),
                    textStyle = babaInputTextStyle(fontWeight = FontWeight.Bold),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                )

                OutlinedTextField(
                    value = qtyText,
                    onValueChange = { qtyText = it },
                    label = { Text("Total Stock Quantity") },
                    supportingText = { Text("Live available will adjust with changes", fontSize = 10.sp) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = babaTextFieldColors(),
                    textStyle = babaInputTextStyle(fontWeight = FontWeight.Bold),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp)) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = {
                            val rate = rateText.toDoubleOrNull() ?: machine.currentRate
                            val qty = qtyText.toIntOrNull() ?: machine.totalQty
                            onSave(rate, qty)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = BabaRed),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Save")
                    }
                }
            }
        }
    }
}
