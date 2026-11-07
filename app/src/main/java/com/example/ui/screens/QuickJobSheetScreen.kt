package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.example.BuildConfig
import com.example.data.model.CustomerEntity
import com.example.data.model.JobSheetEntity
import com.example.data.model.MachineCategoryEntity
import com.example.data.model.MachineEntity
import com.example.data.model.SelectedMachineItem
import com.example.data.model.UserEntity
import com.example.ui.components.ReceiptDialog
import com.example.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickJobSheetScreen(
    currentUser: UserEntity,
    staffList: List<UserEntity> = emptyList(),
    categories: List<MachineCategoryEntity>,
    machines: List<MachineEntity>,
    onFindCustomerByPhone: suspend (String) -> CustomerEntity?,
    onCreateJobSheet: (
        customer: CustomerEntity,
        items: List<SelectedMachineItem>,
        discount: Double,
        depositAmount: Double,
        depositMethod: String,
        billingCycle: String,
        checkoutUser: UserEntity,
        handledUser: UserEntity,
        notes: String,
        personPhotoPath: String?,
        idPhotoPath: String?,
        machinePhotoPath: String?,
        dueTimeMillis: Long?,
        onSuccess: (JobSheetEntity) -> Unit,
        onError: (String) -> Unit
    ) -> Unit,
    onDeleteMachineFromInventory: (machineId: String) -> Unit = {},
    savedCustomers: List<CustomerEntity> = emptyList(),
    onSaveCustomerManually: (name: String, phone: String, address: String) -> Unit = { _, _, _ -> },
    onNavigateBack: () -> Unit
) {
    BackHandler { onNavigateBack() }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // 1. Customer State
    var phone by remember { mutableStateOf("") }
    var customerName by remember { mutableStateOf("") }
    var customerAddress by remember { mutableStateOf("") }
    var isReturningCustomer by remember { mutableStateOf(false) }
    var showSelectCustomerDialog by remember { mutableStateOf(false) }
    var showManualCustomerDialog by remember { mutableStateOf(false) }

    // 2. Multi-Category & Multi-Machine Selection State
    var selectedCategoryId by remember {
        mutableStateOf("all")
    }

    val filteredMachines = remember(machines, selectedCategoryId) {
        if (selectedCategoryId == "all") machines else machines.filter { it.categoryId == selectedCategoryId }
    }

    // Map of machineId -> SelectedMachineItem so users can pick machines across ANY categories
    // Starts empty so only the exact machines selected by the user are included in calculations
    val selectedItemsMap = remember { mutableStateMapOf<String, SelectedMachineItem>() }

    val totalSelectedUnits = selectedItemsMap.values.sumOf { it.quantity }
    val combinedDailyRate = selectedItemsMap.values.sumOf { it.quantity * it.rate }

    // 3. Rate, Rental Days, Discount & Billing Cycle State
    var dailyRateText by remember { mutableStateOf("0") }
    LaunchedEffect(combinedDailyRate) {
        dailyRateText = if (combinedDailyRate % 1.0 == 0.0) {
            combinedDailyRate.toInt().toString()
        } else {
            combinedDailyRate.toString()
        }
    }
    var rentalDays by remember { mutableStateOf(1) }
    var discountText by remember { mutableStateOf("0") }
    var billingCycle by remember { mutableStateOf("working_hours") } // 1 Day operation: 8:00 AM today to 8:00 AM tomorrow
    var dueTimeMillis by remember {
        mutableStateOf(
            com.example.data.repository.BabaRepository.computeCutoffTimeForDays(System.currentTimeMillis(), 1)
        )
    }

    fun updateDueTimeForDays(days: Int, cycle: String = "working_hours") {
        val safeDays = days.coerceAtLeast(1)
        rentalDays = safeDays
        dueTimeMillis = com.example.data.repository.BabaRepository.computeCutoffTimeForDays(System.currentTimeMillis(), safeDays)
    }

    // 4. Security Deposit & ID Photo State (matches screenshot)
    var depositText by remember { mutableStateOf("500") }
    var depositMethod by remember { mutableStateOf("cash") } // "cash", "gpay", or "split"
    var splitDepositCashText by remember { mutableStateOf("250") }
    var splitDepositGpayText by remember { mutableStateOf("250") }
    var idPhotoFrontPath by remember { mutableStateOf<String?>(null) }
    var idPhotoBackPath by remember { mutableStateOf<String?>(null) }
    var personPhotoPath by remember { mutableStateOf<String?>(null) }
    var machinePhotoPath by remember { mutableStateOf<String?>(null) }
    var notes by remember { mutableStateOf("") }

    var errorMessage by remember { mutableStateOf<String?>(null) }
    var createdJobSheetForReceipt by remember { mutableStateOf<JobSheetEntity?>(null) }
    var inventoryMachineToDelete by remember { mutableStateOf<MachineEntity?>(null) }

    // Photo Capture Support
    var targetPhotoType by remember { mutableStateOf<String?>(null) } // "front" or "back"
    var tempCameraFile by remember { mutableStateOf<File?>(null) }
    var previewPhotoPath by remember { mutableStateOf<String?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            val path = tempCameraFile?.absolutePath
            if (path != null) {
                when (targetPhotoType) {
                    "front" -> idPhotoFrontPath = path
                    "back" -> idPhotoBackPath = path
                    "person" -> personPhotoPath = path
                    "machine" -> machinePhotoPath = path
                }
                Toast.makeText(context, "Photo captured locally", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            val tag = targetPhotoType ?: "id_photo"
            val file = File(context.filesDir, "${tag}_${System.currentTimeMillis()}.jpg")
            tempCameraFile = file
            try {
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                cameraLauncher.launch(uri)
            } catch (e: Exception) {
                Toast.makeText(context, "Could not open camera: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "Camera permission required to capture photo", Toast.LENGTH_SHORT).show()
        }
    }

    val galleryPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val tag = targetPhotoType ?: "id_photo"
                    val file = File(context.filesDir, "${tag}_${System.currentTimeMillis()}.jpg")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(file).use { output ->
                            input.copyTo(output)
                        }
                    }
                    when (targetPhotoType) {
                        "front" -> idPhotoFrontPath = file.absolutePath
                        "back" -> idPhotoBackPath = file.absolutePath
                        "person" -> personPhotoPath = file.absolutePath
                        "machine" -> machinePhotoPath = file.absolutePath
                    }
                } catch (e: Exception) {
                    // ignore
                }
            }
        }
    }

    fun startCameraFor(type: String) {
        targetPhotoType = type
        val permission = Manifest.permission.CAMERA
        if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) {
            val file = File(context.filesDir, "${type}_${System.currentTimeMillis()}.jpg")
            tempCameraFile = file
            try {
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                cameraLauncher.launch(uri)
            } catch (e: Exception) {
                Toast.makeText(context, "Could not open camera: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        } else {
            cameraPermissionLauncher.launch(permission)
        }
    }

    fun startGalleryFor(type: String) {
        targetPhotoType = type
        galleryPickerLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    // Auto-fill returning customer by phone
    LaunchedEffect(phone) {
        if (phone.length >= 10) {
            val existing = onFindCustomerByPhone(phone.trim())
            if (existing != null) {
                customerName = existing.name
                customerAddress = existing.address
                idPhotoFrontPath = existing.idPhotoFrontPath
                idPhotoBackPath = existing.idPhotoBackPath
                isReturningCustomer = true
            } else {
                isReturningCustomer = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "New Job Sheet",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = Color.White
                            )
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = BabaYellow.copy(alpha = 0.25f)
                            ) {
                                Text(
                                    text = "v${BuildConfig.VERSION_NAME}",
                                    color = BabaYellow,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                )
                            }
                        }
                        Text(
                            text = "BABA Traders • Handled by ${currentUser.name}",
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // STEP 1: Customer Information
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .background(BabaRed, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("1", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                            Text(
                                text = "Customer Information",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = BabaDark
                            )
                        }

                        if (isReturningCustomer) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(shape = RoundedCornerShape(10.dp), color = BabaSuccessLight) {
                                Text(
                                    text = "RETURNING CUSTOMER",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = BabaSuccess,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Quick Customer Directory Picker & Manual Add Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showSelectCustomerDialog = true },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("select_saved_customer_button")
                        ) {
                            Icon(Icons.Default.People, contentDescription = null, tint = BabaRed, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Select Customer (${savedCustomers.size})",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = BabaDark
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                val cleanPh = phone.trim().filter { it.isDigit() }
                                if (customerName.isNotBlank() && cleanPh.length >= 10) {
                                    onSaveCustomerManually(customerName.trim(), cleanPh, customerAddress.trim())
                                    isReturningCustomer = true
                                } else {
                                    showManualCustomerDialog = true
                                }
                            },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("quick_add_customer_button")
                        ) {
                            Icon(Icons.Default.PersonAdd, contentDescription = null, tint = BabaRed, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "+ Add Customer",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = BabaRed
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = phone,
                        onValueChange = {
                            phone = it
                            errorMessage = null
                        },
                        label = { Text("Phone Number (Auto-fills returning customers)") },
                        leadingIcon = { Icon(Icons.Default.Phone, contentDescription = "Phone", tint = BabaGray) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        colors = babaTextFieldColors(),
                        textStyle = babaInputTextStyle(),
                        modifier = Modifier.fillMaxWidth().testTag("customer_phone_input"),
                        shape = RoundedCornerShape(10.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = customerName,
                        onValueChange = {
                            customerName = it
                            errorMessage = null
                        },
                        label = { Text("Customer Name *") },
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = "Name", tint = BabaGray) },
                        singleLine = true,
                        colors = babaTextFieldColors(),
                        textStyle = babaInputTextStyle(),
                        modifier = Modifier.fillMaxWidth().testTag("customer_name_input"),
                        shape = RoundedCornerShape(10.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = customerAddress,
                        onValueChange = {
                            customerAddress = it
                            errorMessage = null
                        },
                        label = { Text("Site / Delivery Address") },
                        leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = "Address", tint = BabaGray) },
                        colors = babaTextFieldColors(),
                        textStyle = babaInputTextStyle(),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            }

            // STEP 2: Select Machine & Category (Supports selecting multiple machines across ANY category)
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .background(BabaRed, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("2", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Select Machine & Category",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = BabaDark
                            )
                            Text(
                                text = "Switch categories to add multiple machines to one order",
                                fontSize = 10.5.sp,
                                color = BabaGray
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Category filter chips row (switching categories preserves all selected items!)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val isAllSelected = selectedCategoryId == "all"
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isAllSelected) BabaYellowLight else Color.White,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isAllSelected) BabaYellowDark else BabaBorder
                            ),
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { selectedCategoryId = "all" }
                                .testTag("category_chip_all")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "All (${machines.size})",
                                    fontSize = 12.sp,
                                    fontWeight = if (isAllSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = BabaDark
                                )
                            }
                        }

                        categories.forEach { cat ->
                            val isSelected = cat.id == selectedCategoryId
                            val countInCat = selectedItemsMap.values
                                .filter { it.categoryId == cat.id }
                                .sumOf { it.quantity }

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) BabaYellowLight else Color.White,
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (isSelected) BabaYellowDark else if (countInCat > 0) BabaRed else BabaBorder
                                ),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        selectedCategoryId = cat.id
                                    }
                                    .testTag("category_chip_${cat.id}")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = cat.name,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected || countInCat > 0) FontWeight.Bold else FontWeight.Medium,
                                        color = BabaDark
                                    )
                                    if (countInCat > 0) {
                                        Box(
                                            modifier = Modifier
                                                .size(18.dp)
                                                .background(BabaRed, CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "$countInCat",
                                                color = Color.White,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Machine Items List in Selected Category
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (filteredMachines.isEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = BabaLightGray,
                                border = androidx.compose.foundation.BorderStroke(1.dp, BabaBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(18.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.HomeRepairService,
                                        contentDescription = null,
                                        tint = BabaGray,
                                        modifier = Modifier.size(28.dp)
                                    )
                                    Text(
                                        text = if (machines.isEmpty()) "No machines in inventory yet" else "No machines in this category",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = BabaDark
                                    )
                                    Text(
                                        text = "Add your shop's machines and daily rates in the Machinery & Rates section.",
                                        fontSize = 11.sp,
                                        color = BabaGray
                                    )
                                }
                            }
                        }
                        filteredMachines.forEach { mach ->
                            val existingItem = selectedItemsMap[mach.id]
                            val isSelected = existingItem != null
                            val catName = categories.firstOrNull { it.id == mach.categoryId }?.name ?: "Hardware"

                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .border(
                                        width = if (isSelected) 2.dp else 1.dp,
                                        color = if (isSelected) BabaRed else BabaBorder,
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .clickable {
                                        errorMessage = null
                                        if (isSelected) {
                                            selectedItemsMap.remove(mach.id)
                                        } else if (mach.availableQty > 0) {
                                            selectedItemsMap[mach.id] = SelectedMachineItem(
                                                machineId = mach.id,
                                                machineName = mach.name,
                                                categoryId = mach.categoryId,
                                                categoryName = catName,
                                                quantity = 1,
                                                rate = mach.currentRate
                                            )
                                        } else {
                                            Toast.makeText(context, "${mach.name} is currently out of stock", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                    .testTag("machine_card_${mach.id}"),
                                color = if (isSelected) Color(0xFFFFF9F9) else Color.White
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Icon(
                                                    imageVector = if (isSelected) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                                                    contentDescription = if (isSelected) "Selected" else "Not selected",
                                                    tint = if (isSelected) BabaRed else BabaGray,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Text(
                                                    text = mach.name,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 14.sp,
                                                    color = BabaDark
                                                )
                                            }
                                            if (mach.modelOrSpecs.isNotEmpty()) {
                                                Text(
                                                    text = mach.modelOrSpecs,
                                                    fontSize = 12.sp,
                                                    color = BabaGray,
                                                    modifier = Modifier.padding(top = 2.dp, start = 24.dp)
                                                )
                                            }
                                            Text(
                                                text = "Standard Rate: ₹${mach.baseRate.toInt()}/day",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = BabaRed,
                                                modifier = Modifier.padding(top = 4.dp, start = 24.dp)
                                            )
                                        }

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = if (mach.availableQty > 0) BabaSuccessLight else BabaOverdueLight
                                            ) {
                                                Text(
                                                    text = when {
                                                        mach.availableQty == 0 && mach.maintenanceQty > 0 -> "Out of Stock (Maint.)"
                                                        mach.availableQty == 0 -> "Out of Stock"
                                                        mach.maintenanceQty > 0 -> "${mach.availableQty} Avail (${mach.maintenanceQty} Maint.)"
                                                        else -> "${mach.availableQty} Avail"
                                                    },
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (mach.availableQty > 0) BabaSuccess else BabaOverdue,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                )
                                            }

                                            if (currentUser.role == "owner") {
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = BabaOverdueLight.copy(alpha = 0.5f),
                                                    border = androidx.compose.foundation.BorderStroke(1.dp, BabaOverdue.copy(alpha = 0.4f)),
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(6.dp))
                                                        .clickable {
                                                            inventoryMachineToDelete = mach
                                                        }
                                                        .testTag("quick_delete_inventory_machine_${mach.id}")
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.DeleteOutline,
                                                            contentDescription = "Delete ${mach.name}",
                                                            tint = BabaOverdue,
                                                            modifier = Modifier.size(14.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    // Quantity Selector inside the selected machine card
                                    if (isSelected && existingItem != null) {
                                        Spacer(modifier = Modifier.height(12.dp))
                                        HorizontalDivider(color = BabaBorder.copy(alpha = 0.6f))
                                        Spacer(modifier = Modifier.height(10.dp))

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                                Text(
                                                    text = "Quantity to Rent",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp,
                                                    color = BabaDark
                                                )
                                                Text(
                                                    text = "Subtotal: ₹${(existingItem.quantity * existingItem.rate).toInt()}/day",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = BabaSuccess
                                                )
                                            }

                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Surface(
                                                    shape = RoundedCornerShape(8.dp),
                                                    color = BabaOverdueLight,
                                                    border = androidx.compose.foundation.BorderStroke(1.dp, BabaOverdue.copy(alpha = 0.4f)),
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .clickable {
                                                            selectedItemsMap.remove(mach.id)
                                                        }
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.DeleteOutline,
                                                            contentDescription = "Remove Machine",
                                                            tint = BabaOverdue,
                                                            modifier = Modifier.size(14.dp)
                                                        )
                                                        Text(
                                                            text = "Delete",
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = BabaOverdue
                                                        )
                                                    }
                                                }

                                                Surface(
                                                    shape = CircleShape,
                                                    color = Color.White,
                                                    border = androidx.compose.foundation.BorderStroke(1.5.dp, BabaRed),
                                                    modifier = Modifier
                                                        .size(30.dp)
                                                        .clip(CircleShape)
                                                        .clickable {
                                                            if (existingItem.quantity > 1) {
                                                                selectedItemsMap[mach.id] = existingItem.copy(quantity = existingItem.quantity - 1)
                                                            } else {
                                                                selectedItemsMap.remove(mach.id)
                                                            }
                                                        }
                                                ) {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        Icon(
                                                            imageVector = Icons.Default.Remove,
                                                            contentDescription = "Decrease",
                                                            tint = BabaRed,
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                    }
                                                }

                                                Text(
                                                    text = "${existingItem.quantity}",
                                                    fontSize = 16.sp,
                                                    fontWeight = FontWeight.Black,
                                                    color = BabaDark,
                                                    modifier = Modifier.padding(horizontal = 4.dp)
                                                )

                                                Surface(
                                                    shape = CircleShape,
                                                    color = Color.White,
                                                    border = androidx.compose.foundation.BorderStroke(1.5.dp, BabaRed),
                                                    modifier = Modifier
                                                        .size(30.dp)
                                                        .clip(CircleShape)
                                                        .clickable {
                                                            if (existingItem.quantity < mach.availableQty) {
                                                                selectedItemsMap[mach.id] = existingItem.copy(quantity = existingItem.quantity + 1)
                                                            }
                                                        }
                                                ) {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        Icon(
                                                            imageVector = Icons.Default.Add,
                                                            contentDescription = "Increase",
                                                            tint = BabaRed,
                                                            modifier = Modifier.size(16.dp)
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

                    // Selected Machines Summary Across All Categories
                    if (selectedItemsMap.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = BabaLightGray,
                            border = androidx.compose.foundation.BorderStroke(1.dp, BabaBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                        Text(
                                            text = "Added Machines ($totalSelectedUnits unit(s) in ${selectedItemsMap.size} item(s))",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = BabaDark
                                        )
                                        Text(
                                            text = "1 Day: ₹${combinedDailyRate.toInt()}/day" + if (rentalDays > 1) " • $rentalDays Days: ₹${(combinedDailyRate * rentalDays).toInt()}" else "",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = BabaRed
                                        )
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = BabaOverdueLight,
                                        border = androidx.compose.foundation.BorderStroke(1.dp, BabaOverdue.copy(alpha = 0.4f)),
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable { selectedItemsMap.clear() }
                                            .testTag("clear_all_selected_machines_button")
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.DeleteSweep,
                                                contentDescription = "Clear All",
                                                tint = BabaOverdue,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Text(
                                                text = "Clear All",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = BabaOverdue
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                selectedItemsMap.values.forEach { item ->
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color.White,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 3.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 10.dp, vertical = 8.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "${item.machineName} (×${item.quantity})",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = BabaDark
                                                )
                                                Text(
                                                    text = "${item.categoryName} • ₹${item.rate.toInt()}/day each",
                                                    fontSize = 10.sp,
                                                    color = BabaGray
                                                )
                                            }
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Column(horizontalAlignment = Alignment.End) {
                                                    Text(
                                                        text = "₹${(item.quantity * item.rate).toInt()}/day",
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = BabaDark
                                                    )
                                                    if (rentalDays > 1) {
                                                        Text(
                                                            text = "₹${(item.quantity * item.rate * rentalDays).toInt()} ($rentalDays d)",
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.SemiBold,
                                                            color = BabaSuccess
                                                        )
                                                    }
                                                }
                                                Surface(
                                                    shape = RoundedCornerShape(8.dp),
                                                    color = BabaOverdueLight,
                                                    border = androidx.compose.foundation.BorderStroke(1.dp, BabaOverdue.copy(alpha = 0.4f)),
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .clickable { selectedItemsMap.remove(item.machineId) }
                                                        .testTag("remove_added_machine_${item.machineId}")
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.DeleteOutline,
                                                            contentDescription = "Delete ${item.machineName}",
                                                            tint = BabaOverdue,
                                                            modifier = Modifier.size(14.dp)
                                                        )
                                                        Text(
                                                            text = "Delete",
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = BabaOverdue
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
                }
            }

            // STEP 3: Rate, Discount & Billing Cycle
            val effectiveDailyRate = dailyRateText.toDoubleOrNull() ?: combinedDailyRate
            val discountVal = discountText.toDoubleOrNull() ?: 0.0
            val depositVal = depositText.toDoubleOrNull() ?: 0.0
            val grossRentForPeriod = effectiveDailyRate * rentalDays
            val netRentForPeriod = maxOf(0.0, grossRentForPeriod - discountVal)
            val estBalanceOnReturn = netRentForPeriod - depositVal

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .background(BabaRed, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("3", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                        Text(
                            text = "Rate, Discount & Billing Cycle",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = BabaDark
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Combined Daily Rate (₹/day) and Discount (₹) side by side
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = dailyRateText,
                            onValueChange = { newRateStr ->
                                dailyRateText = newRateStr
                                errorMessage = null
                                val parsed = newRateStr.toDoubleOrNull()
                                if (parsed != null && selectedItemsMap.isNotEmpty()) {
                                    if (selectedItemsMap.size == 1) {
                                        val onlyKey = selectedItemsMap.keys.first()
                                        val onlyItem = selectedItemsMap[onlyKey]
                                        if (onlyItem != null && onlyItem.quantity > 0) {
                                            selectedItemsMap[onlyKey] = onlyItem.copy(rate = parsed / onlyItem.quantity)
                                        }
                                    } else if (combinedDailyRate > 0) {
                                        val ratio = parsed / combinedDailyRate
                                        selectedItemsMap.keys.toList().forEach { key ->
                                            val item = selectedItemsMap[key]
                                            if (item != null) {
                                                selectedItemsMap[key] = item.copy(rate = item.rate * ratio)
                                            }
                                        }
                                    }
                                }
                            },
                            label = { Text("Combined Daily Rate (₹/day)") },
                            supportingText = {
                                Text(
                                    text = if (totalSelectedUnits > 0) "For $totalSelectedUnits selected machine(s) per day" else "Select machines in Step 2",
                                    color = BabaGray,
                                    fontSize = 9.sp
                                )
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = babaTextFieldColors(),
                            textStyle = babaInputTextStyle(fontWeight = FontWeight.Bold),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        )

                        OutlinedTextField(
                            value = discountText,
                            onValueChange = {
                                discountText = it
                                errorMessage = null
                            },
                            label = { Text("Discount (₹)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = babaTextFieldColors(),
                            textStyle = babaInputTextStyle(fontWeight = FontWeight.Bold),
                            supportingText = {
                                if (currentUser.role == "worker") {
                                    Text("Worker limit: ₹${currentUser.maxDiscountAmount.toInt()}", color = BabaGray, fontSize = 9.sp)
                                } else {
                                    Text("Deducted from total rent", color = BabaGray, fontSize = 9.sp)
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Rental Duration (Days) Selector
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = BabaYellowLight.copy(alpha = 0.45f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, BabaYellowDark.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                    Text(
                                        text = "Rental Duration (Number of Days)",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = BabaDark
                                    )
                                    Text(
                                        text = "₹${effectiveDailyRate.toInt()}/day × $rentalDays day(s) = ₹${grossRentForPeriod.toInt()}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = BabaRed
                                    )
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = Color.White,
                                        border = androidx.compose.foundation.BorderStroke(1.5.dp, BabaRed),
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .clickable {
                                                if (rentalDays > 1) {
                                                    updateDueTimeForDays(rentalDays - 1, billingCycle)
                                                }
                                            }
                                            .testTag("decrease_rental_days")
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(Icons.Default.Remove, contentDescription = "-1 Day", tint = BabaRed, modifier = Modifier.size(16.dp))
                                        }
                                    }

                                    Text(
                                        text = "$rentalDays ${if (rentalDays == 1) "Day" else "Days"}",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Black,
                                        color = BabaDark,
                                        maxLines = 1,
                                        softWrap = false,
                                        modifier = Modifier.padding(horizontal = 4.dp)
                                    )

                                    Surface(
                                        shape = CircleShape,
                                        color = Color.White,
                                        border = androidx.compose.foundation.BorderStroke(1.5.dp, BabaRed),
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .clickable {
                                                updateDueTimeForDays(rentalDays + 1, billingCycle)
                                            }
                                            .testTag("increase_rental_days")
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(Icons.Default.Add, contentDescription = "+1 Day", tint = BabaRed, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Quick day chips: 1 Day, 2 Days, 3 Days, 5 Days, 7 Days
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf(1, 2, 3, 5, 7).forEach { d ->
                                    val isDaySelected = rentalDays == d
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (isDaySelected) BabaRed else Color.White,
                                        border = androidx.compose.foundation.BorderStroke(
                                            1.dp,
                                            if (isDaySelected) BabaRed else BabaBorder
                                        ),
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(6.dp))
                                            .clickable {
                                                updateDueTimeForDays(d, billingCycle)
                                            }
                                            .testTag("rental_days_chip_$d")
                                    ) {
                                        Box(
                                            modifier = Modifier.padding(vertical = 6.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "${d}d",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isDaySelected) Color.White else BabaDark
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text("Billing Cycle Policy", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = BabaDark)
                    Spacer(modifier = Modifier.height(6.dp))

                    // 1 Day operation: 8:00 AM today to 8:00 AM tomorrow (24-Hour option removed)
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .border(
                                width = 2.dp,
                                color = BabaRed,
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable {
                                billingCycle = "working_hours"
                                updateDueTimeForDays(rentalDays, "working_hours")
                            },
                        color = Color(0xFFFFF0F0)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("1 Day operation", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = BabaDark)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                "8:00 AM today to 8:00 AM tomorrow",
                                fontSize = 11.5.sp,
                                color = BabaRedDark,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                "Extra rent is automatically applied if rental exceeds the 8:00 AM cutoff time.",
                                fontSize = 10.sp,
                                color = BabaGray
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Due Time display
                    val dateFormat = remember { SimpleDateFormat("EEE, dd MMM yyyy, hh:mm a", Locale.getDefault()) }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = BabaBg,
                        border = androidx.compose.foundation.BorderStroke(1.dp, BabaBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f).padding(end = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Default.Event, contentDescription = null, tint = BabaRed, modifier = Modifier.size(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Due Date ($rentalDays Day(s)): ${dateFormat.format(Date(dueTimeMillis))}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = BabaDark)
                                    Text("Alarms notify 2 hours prior to due return", fontSize = 9.5.sp, color = BabaGray)
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                IconButton(
                                    onClick = {
                                        if (rentalDays > 1) {
                                            updateDueTimeForDays(rentalDays - 1, billingCycle)
                                        }
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.Remove, contentDescription = "-1d", modifier = Modifier.size(14.dp))
                                }
                                IconButton(
                                    onClick = {
                                        updateDueTimeForDays(rentalDays + 1, billingCycle)
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = "+1d", modifier = Modifier.size(14.dp), tint = BabaRed)
                                }
                            }
                        }
                    }

                    // Live Rental Calculation Breakdown Card
                    if (selectedItemsMap.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = BabaLightGray,
                            border = androidx.compose.foundation.BorderStroke(1.dp, BabaBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Text(
                                    text = "RENTAL CALCULATION BREAKDOWN",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black,
                                    color = BabaRed
                                )
                                selectedItemsMap.values.forEach { item ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "${item.machineName} (₹${item.rate.toInt()}/day)",
                                            fontSize = 11.sp,
                                            color = BabaGray,
                                            modifier = Modifier.weight(1f).padding(end = 8.dp)
                                        )
                                        Text(
                                            text = "₹${(item.quantity * item.rate).toInt()}/day",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = BabaDark,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                }
                                HorizontalDivider(color = BabaBorder)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Combined Daily Rate ($totalSelectedUnits machine(s))",
                                        fontSize = 11.5.sp,
                                        color = BabaDark,
                                        modifier = Modifier.weight(1f).padding(end = 8.dp)
                                    )
                                    Text(
                                        text = "₹${effectiveDailyRate.toInt()} / day",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
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
                                    Text(
                                        text = "Gross Rent ($rentalDays day(s) × ₹${effectiveDailyRate.toInt()}/day)",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = BabaDark,
                                        modifier = Modifier.weight(1f).padding(end = 8.dp)
                                    )
                                    Text(
                                        text = "₹${grossRentForPeriod.toInt()}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Black,
                                        color = BabaDark,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                                if (discountVal > 0) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Discount Applied", fontSize = 11.5.sp, color = BabaRed, modifier = Modifier.weight(1f).padding(end = 8.dp))
                                        Text("- ₹${discountVal.toInt()}", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = BabaRed, maxLines = 1, softWrap = false)
                                    }
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Total Net Rent ($rentalDays day(s))",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Black,
                                        color = BabaRed,
                                        modifier = Modifier.weight(1f).padding(end = 8.dp)
                                    )
                                    Text(
                                        text = "₹${netRentForPeriod.toInt()}",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Black,
                                        color = BabaRed,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Advance / Security Deposit Paid", fontSize = 11.sp, color = BabaGray, modifier = Modifier.weight(1f).padding(end = 8.dp))
                                    Text("₹${depositVal.toInt()}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = BabaSuccess, maxLines = 1, softWrap = false)
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Est. Balance at Return", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = BabaDark, modifier = Modifier.weight(1f).padding(end = 8.dp))
                                    Text(
                                        text = when {
                                            estBalanceOnReturn > 0 -> "₹${estBalanceOnReturn.toInt()} Payable"
                                            estBalanceOnReturn < 0 -> "₹${kotlin.math.abs(estBalanceOnReturn).toInt()} Refund"
                                            else -> "₹0 (Covered by Deposit)"
                                        },
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (estBalanceOnReturn > 0) BabaOverdue else BabaSuccess,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // STEP 4: Security Deposit & ID Photo (Matches screenshot)
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .background(BabaRed, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("4", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                        Text(
                            text = "Security Deposit & ID Photo",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = BabaDark
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Security Deposit (₹) and Deposit Method (Cash / GPay / Split)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = depositText,
                            onValueChange = {
                                depositText = it
                                errorMessage = null
                                val totalDep = it.toDoubleOrNull() ?: 0.0
                                val currentCash = splitDepositCashText.toDoubleOrNull() ?: 0.0
                                if (depositMethod == "split") {
                                    val remGpay = (totalDep - currentCash).coerceAtLeast(0.0)
                                    splitDepositGpayText = if (remGpay % 1.0 == 0.0) remGpay.toInt().toString() else remGpay.toString()
                                }
                            },
                            label = { Text("Security Deposit (₹)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = babaTextFieldColors(),
                            textStyle = babaInputTextStyle(fontWeight = FontWeight.Bold),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        )

                        Column(modifier = Modifier.weight(1.2f)) {
                            Text("Deposit Method", fontSize = 11.sp, color = BabaGray, fontWeight = FontWeight.Medium)
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                // Cash Button
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (depositMethod == "cash") BabaYellowLight else Color.White,
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        if (depositMethod == "cash") BabaYellowDark else BabaBorder
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { depositMethod = "cash" }
                                ) {
                                    Box(
                                        modifier = Modifier.padding(vertical = 12.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "Cash",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = BabaDark
                                        )
                                    }
                                }

                                // GPay Button
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (depositMethod == "upi" || depositMethod == "gpay") BabaYellowLight else Color.White,
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        if (depositMethod == "upi" || depositMethod == "gpay") BabaYellowDark else BabaBorder
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { depositMethod = "gpay" }
                                ) {
                                    Box(
                                        modifier = Modifier.padding(vertical = 12.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "GPay",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = BabaDark
                                        )
                                    }
                                }

                                // Split Button
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (depositMethod == "split") BabaYellowLight else Color.White,
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        if (depositMethod == "split") BabaYellowDark else BabaBorder
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            depositMethod = "split"
                                            val totalDep = depositText.toDoubleOrNull() ?: 0.0
                                            val half = (totalDep / 2.0).toInt()
                                            splitDepositCashText = half.toString()
                                            splitDepositGpayText = (totalDep.toInt() - half).coerceAtLeast(0).toString()
                                        }
                                ) {
                                    Box(
                                        modifier = Modifier.padding(vertical = 12.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "Split",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = BabaDark
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (depositMethod == "split") {
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = BabaLightGray,
                            border = androidx.compose.foundation.BorderStroke(1.dp, BabaBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                val splitTotal = (splitDepositCashText.toDoubleOrNull() ?: 0.0) + (splitDepositGpayText.toDoubleOrNull() ?: 0.0)
                                Text(
                                    text = "Split Deposit Breakdown (Cash + GPay = ₹${splitTotal.toInt()})",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = BabaDark
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = splitDepositCashText,
                                        onValueChange = {
                                            splitDepositCashText = it.filter { c -> c.isDigit() || c == '.' }
                                            val cashVal = splitDepositCashText.toDoubleOrNull() ?: 0.0
                                            val gpayVal = splitDepositGpayText.toDoubleOrNull() ?: 0.0
                                            val sum = cashVal + gpayVal
                                            depositText = if (sum % 1.0 == 0.0) sum.toInt().toString() else sum.toString()
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
                                        value = splitDepositGpayText,
                                        onValueChange = {
                                            splitDepositGpayText = it.filter { c -> c.isDigit() || c == '.' }
                                            val cashVal = splitDepositCashText.toDoubleOrNull() ?: 0.0
                                            val gpayVal = splitDepositGpayText.toDoubleOrNull() ?: 0.0
                                            val sum = cashVal + gpayVal
                                            depositText = if (sum % 1.0 == 0.0) sum.toInt().toString() else sum.toString()
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
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Customer ID Document (Stored Locally Only)   Aadhaar / DL
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Customer ID Document (Stored Locally Only)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = BabaDark
                        )
                        Text(
                            text = "Aadhaar / DL",
                            fontSize = 11.sp,
                            color = BabaGray
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Front Photo and Back Photo cards side by side (Exact match to screenshot)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Front Photo Card
                        IdPhotoUploadBox(
                            title = "Front Photo",
                            imagePath = idPhotoFrontPath,
                            onCameraClick = { startCameraFor("front") },
                            onGalleryClick = { startGalleryFor("front") },
                            onPreviewClick = { previewPhotoPath = idPhotoFrontPath },
                            onRemoveClick = { idPhotoFrontPath = null },
                            modifier = Modifier.weight(1f)
                        )

                        // Back Photo Card
                        IdPhotoUploadBox(
                            title = "Back Photo",
                            imagePath = idPhotoBackPath,
                            onCameraClick = { startCameraFor("back") },
                            onGalleryClick = { startGalleryFor("back") },
                            onPreviewClick = { previewPhotoPath = idPhotoBackPath },
                            onRemoveClick = { idPhotoBackPath = null },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Directly below ID Photo: Person's Photo
                    Text(
                        text = "Person's Photo",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = BabaDark
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    IdPhotoUploadBox(
                        title = "Person's Photo",
                        imagePath = personPhotoPath,
                        onCameraClick = { startCameraFor("person") },
                        onGalleryClick = { startGalleryFor("person") },
                        onPreviewClick = { previewPhotoPath = personPhotoPath },
                        onRemoveClick = { personPhotoPath = null },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Directly below Person's Photo: Machine's Photo
                    Text(
                        text = "Machine's Photo",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = BabaDark
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    IdPhotoUploadBox(
                        title = "Machine's Photo",
                        imagePath = machinePhotoPath,
                        onCameraClick = { startCameraFor("machine") },
                        onGalleryClick = { startGalleryFor("machine") },
                        onPreviewClick = { previewPhotoPath = machinePhotoPath },
                        onRemoveClick = { machinePhotoPath = null },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("Rental Notes / Specific Job Details (Optional)") },
                        colors = babaTextFieldColors(),
                        textStyle = babaInputTextStyle(),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            }

            if (errorMessage != null) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = BabaOverdueLight,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = errorMessage ?: "",
                            color = BabaOverdue,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { errorMessage = null },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear Error",
                                tint = BabaOverdue,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // Confirm & Generate Receipt Button
            Button(
                onClick = {
                    val custName = customerName.trim()
                    val custPhone = phone.trim()
                    if (custName.isEmpty() || custPhone.isEmpty()) {
                        errorMessage = "Please enter customer phone and name"
                        return@Button
                    }
                    val cleanPhone = custPhone.filter { it.isDigit() }
                    if (cleanPhone.length < 10) {
                        errorMessage = "Customer phone must be a valid 10-digit number"
                        return@Button
                    }

                    if (selectedItemsMap.isEmpty()) {
                        errorMessage = "Please select at least one machine to rent"
                        return@Button
                    }

                    // Validate available stock for each selected machine across categories
                    for (item in selectedItemsMap.values) {
                        val mach = machines.firstOrNull { it.id == item.machineId }
                        if (mach != null && item.quantity > mach.availableQty) {
                            errorMessage = "Only ${mach.availableQty} units available in stock for ${mach.name}"
                            return@Button
                        }
                    }

                    val discount = discountText.toDoubleOrNull() ?: 0.0
                    val splitCashAmt = splitDepositCashText.toDoubleOrNull() ?: 0.0
                    val splitGpayAmt = splitDepositGpayText.toDoubleOrNull() ?: 0.0
                    val deposit = if (depositMethod == "split") {
                        splitCashAmt + splitGpayAmt
                    } else {
                        depositText.toDoubleOrNull() ?: 0.0
                    }

                    if (currentUser.role == "worker" && discount > currentUser.maxDiscountAmount) {
                        errorMessage = "Worker discount cannot exceed ₹${currentUser.maxDiscountAmount.toInt()} floor"
                        return@Button
                    }

                    val itemsList = selectedItemsMap.values.toList()
                    val exactDueTimeMillis = dueTimeMillis

                    val customer = CustomerEntity(
                        id = "cust_" + (cleanPhone.ifEmpty { UUID.randomUUID().toString() }),
                        name = custName,
                        phone = cleanPhone,
                        address = customerAddress.trim(),
                        idPhotoFrontPath = idPhotoFrontPath,
                        idPhotoBackPath = idPhotoBackPath,
                        personPhotoPath = personPhotoPath
                    )

                    val effectiveDepositMethod = if (depositMethod == "split") {
                        "split:$splitCashAmt:$splitGpayAmt"
                    } else {
                        depositMethod
                    }

                    onCreateJobSheet(
                        customer,
                        itemsList,
                        discount,
                        deposit,
                        effectiveDepositMethod,
                        billingCycle,
                        currentUser,
                        currentUser,
                        notes,
                        personPhotoPath,
                        idPhotoFrontPath ?: idPhotoBackPath,
                        machinePhotoPath,
                        exactDueTimeMillis,
                        { createdJob ->
                            createdJobSheetForReceipt = createdJob
                        },
                        { err ->
                            errorMessage = err
                        }
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = BabaRed),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("submit_job_sheet_button")
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = "Submit", tint = BabaYellow)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Confirm & Generate Receipt",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // Receipt Dialog on created
    if (createdJobSheetForReceipt != null) {
        ReceiptDialog(
            jobSheet = createdJobSheetForReceipt!!,
            onDismiss = {
                createdJobSheetForReceipt = null
                onNavigateBack()
            }
        )
    }

    // Preview Dialog
    if (previewPhotoPath != null) {
        val previewFile = remember(previewPhotoPath) { File(previewPhotoPath!!) }
        val existsLocally = remember(previewPhotoPath) { previewFile.exists() && previewFile.length() > 0L }
        Dialog(onDismissRequest = { previewPhotoPath = null }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier.fillMaxWidth().padding(8.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("ID Photo Preview", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = BabaDark)
                        IconButton(onClick = { previewPhotoPath = null }) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = BabaDark)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    if (existsLocally) {
                        AsyncImage(
                            model = previewFile,
                            contentDescription = "ID Photo",
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 360.dp)
                                .clip(RoundedCornerShape(10.dp)),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(BabaLightGray),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PhotoCamera,
                                    contentDescription = "Photo is on the other device",
                                    tint = BabaGray,
                                    modifier = Modifier.size(36.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Photo is on the other device",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = BabaDark,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    text = previewFile.name,
                                    fontSize = 11.sp,
                                    color = BabaGray,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { previewPhotoPath = null },
                        colors = ButtonDefaults.buttonColors(containerColor = BabaDark),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Close", color = Color.White)
                    }
                }
            }
        }
    }

    // Delete Inventory Machine Confirmation Dialog (for Owner in Step 2)
    if (inventoryMachineToDelete != null) {
        val targetMachine = inventoryMachineToDelete!!
        Dialog(onDismissRequest = { inventoryMachineToDelete = null }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier.fillMaxWidth().padding(8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Delete Machine from Inventory?",
                        fontWeight = FontWeight.Black,
                        fontSize = 16.sp,
                        color = BabaDark
                    )
                    Text(
                        text = "Are you sure you want to permanently remove \"${targetMachine.name}\" (₹${targetMachine.currentRate.toInt()}/day) from the machinery list?",
                        fontSize = 13.sp,
                        color = BabaGray
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { inventoryMachineToDelete = null },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Cancel", color = BabaDark)
                        }
                        Button(
                            onClick = {
                                selectedItemsMap.remove(targetMachine.id)
                                onDeleteMachineFromInventory(targetMachine.id)
                                inventoryMachineToDelete = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = BabaOverdue),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Delete", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    // Select Saved Customer Dialog (Ordered A-Z with #1, #2, #3...)
    if (showSelectCustomerDialog) {
        var customerSearch by remember { mutableStateOf("") }
        val orderedList = remember(savedCustomers, customerSearch) {
            val sorted = savedCustomers.sortedWith(
                compareBy<CustomerEntity> { it.name.lowercase() }.thenBy { it.createdAt }
            )
            if (customerSearch.isBlank()) {
                sorted
            } else {
                val q = customerSearch.trim()
                sorted.filter {
                    it.name.contains(q, ignoreCase = true) ||
                    it.phone.contains(q) ||
                    it.address.contains(q, ignoreCase = true)
                }
            }
        }

        Dialog(onDismissRequest = { showSelectCustomerDialog = false }) {
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
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Select Customer (${savedCustomers.size})",
                                fontWeight = FontWeight.Black,
                                fontSize = 16.sp,
                                color = BabaDark
                            )
                            Text(
                                text = "Ordered A–Z • Tap a customer to fill order",
                                fontSize = 11.sp,
                                color = BabaGray
                            )
                        }
                        IconButton(onClick = { showSelectCustomerDialog = false }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = BabaDark)
                        }
                    }

                    OutlinedTextField(
                        value = customerSearch,
                        onValueChange = { customerSearch = it },
                        placeholder = { Text("Search customer name or phone...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = BabaGray) },
                        singleLine = true,
                        colors = babaTextFieldColors(),
                        textStyle = babaInputTextStyle(),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )

                    if (orderedList.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = if (savedCustomers.isEmpty()) "No customers saved yet" else "No matching customer found",
                                    fontSize = 13.sp,
                                    color = BabaGray
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = {
                                        showSelectCustomerDialog = false
                                        showManualCustomerDialog = true
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = BabaRed),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("+ Add Customer Manually", color = Color.White, fontSize = 12.sp)
                                }
                            }
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 320.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            orderedList.forEachIndexed { idx, cust ->
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = BabaLightGray,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, BabaBorder),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable {
                                            phone = cust.phone
                                            customerName = cust.name
                                            customerAddress = cust.address
                                            if (cust.idPhotoFrontPath != null) idPhotoFrontPath = cust.idPhotoFrontPath
                                            if (cust.idPhotoBackPath != null) idPhotoBackPath = cust.idPhotoBackPath
                                            if (cust.personPhotoPath != null) personPhotoPath = cust.personPhotoPath
                                            isReturningCustomer = true
                                            showSelectCustomerDialog = false
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(30.dp)
                                                .background(BabaRedLight, CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "#${idx + 1}",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Black,
                                                color = BabaRed
                                            )
                                        }
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = cust.name,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.5.sp,
                                                color = BabaDark
                                            )
                                            Text(
                                                text = cust.phone + if (cust.address.isNotBlank()) " • ${cust.address}" else "",
                                                fontSize = 11.5.sp,
                                                color = BabaGray
                                            )
                                        }
                                        Icon(
                                            imageVector = Icons.Default.CheckCircleOutline,
                                            contentDescription = "Select",
                                            tint = BabaRed,
                                            modifier = Modifier.size(18.dp)
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

    // Add Customer Manually Dialog in QuickJobSheetScreen
    if (showManualCustomerDialog) {
        var newCustName by remember { mutableStateOf(customerName) }
        var newCustPhone by remember { mutableStateOf(phone) }
        var newCustAddress by remember { mutableStateOf(customerAddress) }
        var newCustErr by remember { mutableStateOf<String?>(null) }

        Dialog(onDismissRequest = { showManualCustomerDialog = false }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Add Customer Manually",
                        fontWeight = FontWeight.Black,
                        fontSize = 17.sp,
                        color = BabaDark
                    )
                    Text(
                        text = "Save customer to directory and fill into this order",
                        fontSize = 12.sp,
                        color = BabaGray
                    )

                    OutlinedTextField(
                        value = newCustName,
                        onValueChange = {
                            newCustName = it
                            newCustErr = null
                        },
                        label = { Text("Customer Name *") },
                        singleLine = true,
                        colors = babaTextFieldColors(),
                        textStyle = babaInputTextStyle(fontWeight = FontWeight.Bold),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )

                    OutlinedTextField(
                        value = newCustPhone,
                        onValueChange = {
                            newCustPhone = it.filter { c -> c.isDigit() }.take(10)
                            newCustErr = null
                        },
                        label = { Text("Phone Number (10 Digits) *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        colors = babaTextFieldColors(),
                        textStyle = babaInputTextStyle(fontWeight = FontWeight.Bold),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )

                    OutlinedTextField(
                        value = newCustAddress,
                        onValueChange = {
                            newCustAddress = it
                            newCustErr = null
                        },
                        label = { Text("Site / Delivery Address") },
                        colors = babaTextFieldColors(),
                        textStyle = babaInputTextStyle(),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )

                    if (newCustErr != null) {
                        Text(newCustErr!!, color = BabaOverdue, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showManualCustomerDialog = false },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Cancel", color = BabaDark)
                        }
                        Button(
                            onClick = {
                                val cleanPh = newCustPhone.trim().filter { it.isDigit() }
                                if (newCustName.trim().isEmpty()) {
                                    newCustErr = "Please enter customer name"
                                    return@Button
                                }
                                if (cleanPh.length < 10) {
                                    newCustErr = "Please enter a valid 10-digit phone number"
                                    return@Button
                                }
                                customerName = newCustName.trim()
                                phone = cleanPh
                                customerAddress = newCustAddress.trim()
                                isReturningCustomer = true
                                onSaveCustomerManually(customerName, cleanPh, customerAddress)
                                showManualCustomerDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = BabaRed),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Save & Use", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

/**
 * ID Document Photo Upload Card matching the screenshot:
 * Shows a red camera circle button and a yellow gallery circle button,
 * "Front Photo" / "Back Photo", and "Add Photo / Camera" subtitle.
 */
@Composable
private fun IdPhotoUploadBox(
    title: String,
    imagePath: String?,
    onCameraClick: () -> Unit,
    onGalleryClick: () -> Unit,
    onPreviewClick: () -> Unit,
    onRemoveClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .border(
                width = 1.dp,
                color = if (imagePath != null) BabaSuccess else BabaBorder,
                shape = RoundedCornerShape(12.dp)
            ),
        color = Color.White
    ) {
        if (imagePath != null) {
            val localFile = remember(imagePath) { File(imagePath) }
            val existsLocally = remember(imagePath) { localFile.exists() && localFile.length() > 0L }
            Column(modifier = Modifier.padding(8.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(88.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onPreviewClick() }
                ) {
                    if (existsLocally) {
                        AsyncImage(
                            model = localFile,
                            contentDescription = title,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(BabaLightGray),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PhotoCamera,
                                    contentDescription = "Photo is on the other device",
                                    tint = BabaGray,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Photo is on the other device",
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = BabaDark,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp),
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.6f)
                    ) {
                        IconButton(
                            onClick = onRemoveClick,
                            modifier = Modifier.size(22.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Remove",
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = title, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = BabaSuccess)
                    Text(
                        text = "Change",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = BabaRed,
                        modifier = Modifier.clickable { onCameraClick() }
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp, horizontal = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Red Camera circle button
                    Surface(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .clickable { onCameraClick() },
                        color = BabaRedLight
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.CameraAlt,
                                contentDescription = "Take Photo",
                                tint = BabaRed,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // Yellow Gallery circle button
                    Surface(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .clickable { onGalleryClick() },
                        color = BabaYellowLight
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.PhotoLibrary,
                                contentDescription = "Pick Photo",
                                tint = BabaYellowDark,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = title,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = BabaDark
                )
                Text(
                    text = "Add Photo / Camera",
                    fontSize = 10.sp,
                    color = BabaGray
                )
            }
        }
    }
}
