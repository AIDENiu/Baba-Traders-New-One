package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.model.UserEntity
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageStaffScreen(
    currentUser: UserEntity,
    users: List<UserEntity>,
    onAddWorker: (name: String, phoneOrUsername: String, pin: String, maxDiscount: Double) -> Unit,
    onToggleWorkerStatus: (UserEntity) -> Unit,
    onUpdateDiscountFloor: (UserEntity, Double) -> Unit,
    onNavigateBack: () -> Unit
) {
    BackHandler { onNavigateBack() }
    var showAddWorkerDialog by remember { mutableStateOf(false) }
    var workerToEditDiscount by remember { mutableStateOf<UserEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Manage Staff & Logins", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = { showAddWorkerDialog = true }, modifier = Modifier.testTag("add_staff_button")) {
                        Icon(Icons.Default.PersonAdd, contentDescription = "Add Worker", tint = BabaYellow)
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
            Surface(
                color = Color.White,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Default.Security, contentDescription = "Security", tint = BabaRed)
                    Column {
                        Text("Staff Access Control", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = BabaDark)
                        Text(
                            "Workers can only access Quick Job Sheet and Active Orders. They cannot see revenue, edit machine pricing, or manage staff.",
                            fontSize = 11.sp,
                            color = BabaGray
                        )
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(users, key = { it.id }) { user ->
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
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .background(
                                                if (user.role == "owner") BabaYellow else BabaLightGray,
                                                CircleShape
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = if (user.role == "owner") Icons.Default.SupervisorAccount else Icons.Default.Engineering,
                                            contentDescription = user.role,
                                            tint = if (user.role == "owner") BabaDark else BabaRed,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }

                                    Column {
                                        Text(text = user.name, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = BabaDark)
                                        Text(text = "Login: ${user.phoneOrUsername} • PIN: ${user.pin}", fontSize = 11.sp, color = BabaGray)
                                    }
                                }

                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (user.isActive) BabaSuccessLight else BabaOverdueLight
                                ) {
                                    Text(
                                        text = if (user.isActive) "ACTIVE" else "DEACTIVATED",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (user.isActive) BabaSuccess else BabaOverdue,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }

                            if (user.role == "worker") {
                                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = BabaBorder)

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text("Max Discount Floor", fontSize = 10.sp, color = BabaGray)
                                        Text(
                                            text = "₹${user.maxDiscountAmount.toInt()} limit per rental",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = BabaDark
                                        )
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton(
                                            onClick = { workerToEditDiscount = user },
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            Text("Set Discount Floor", fontSize = 11.sp)
                                        }

                                        Button(
                                            onClick = { onToggleWorkerStatus(user) },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (user.isActive) BabaOverdue else BabaSuccess
                                            ),
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            Text(if (user.isActive) "Deactivate" else "Activate", fontSize = 11.sp)
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

    // Add Worker Dialog
    if (showAddWorkerDialog) {
        AddWorkerDialog(
            onDismiss = { showAddWorkerDialog = false },
            onAdd = { name, phoneOrUsername, pin, maxDiscount ->
                onAddWorker(name, phoneOrUsername, pin, maxDiscount)
                showAddWorkerDialog = false
            }
        )
    }

    // Edit Discount Floor Dialog
    if (workerToEditDiscount != null) {
        val worker = workerToEditDiscount!!
        var discountFloorText by remember { mutableStateOf(worker.maxDiscountAmount.toInt().toString()) }

        Dialog(onDismissRequest = { workerToEditDiscount = null }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier.fillMaxWidth().padding(8.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Set Worker Discount Floor", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = BabaDark)
                    Text(
                        "Configure the maximum discount ₹ amount ${worker.name} can grant on a job sheet without owner approval.",
                        fontSize = 12.sp,
                        color = BabaGray
                    )

                    OutlinedTextField(
                        value = discountFloorText,
                        onValueChange = { discountFloorText = it },
                        label = { Text("Max Discount Amount (₹)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = babaTextFieldColors(),
                        textStyle = babaInputTextStyle(fontWeight = FontWeight.Bold),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    )

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { workerToEditDiscount = null }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp)) {
                            Text("Cancel")
                        }
                        Button(
                            onClick = {
                                val amount = discountFloorText.toDoubleOrNull() ?: worker.maxDiscountAmount
                                onUpdateDiscountFloor(worker, amount)
                                workerToEditDiscount = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = BabaRed),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Save Floor")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AddWorkerDialog(
    onDismiss: () -> Unit,
    onAdd: (name: String, phoneOrUsername: String, pin: String, maxDiscount: Double) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("0000") }
    var discountLimit by remember { mutableStateOf("150") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier.fillMaxWidth().padding(8.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Add Worker Login", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = BabaDark)

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Worker Full Name *") },
                    colors = babaTextFieldColors(),
                    textStyle = babaInputTextStyle(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                )

                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username or Phone *") },
                    colors = babaTextFieldColors(),
                    textStyle = babaInputTextStyle(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                )

                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it },
                    label = { Text("4-Digit PIN *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = babaTextFieldColors(),
                    textStyle = babaInputTextStyle(fontWeight = FontWeight.Bold),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                )

                OutlinedTextField(
                    value = discountLimit,
                    onValueChange = { discountLimit = it },
                    label = { Text("Max Discount Allowed (₹)") },
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
                            if (name.isNotBlank() && username.isNotBlank()) {
                                onAdd(name.trim(), username.trim(), pin.trim(), discountLimit.toDoubleOrNull() ?: 150.0)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = BabaRed),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Add Worker")
                    }
                }
            }
        }
    }
}
