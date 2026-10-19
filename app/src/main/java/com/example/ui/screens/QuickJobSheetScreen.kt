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

    // 2. Multi-Category & Multi-Machine Selection State
    var selectedCategoryId by remember(categories) {
        mutableStateOf(categories.firstOrNull()?.id ?: "all")
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
    var billingCycle by remember { mutableStateOf("24hr") } // "24hr" or "working_hours"
    var dueTimeMillis by remember { mutableStateOf(System.currentTimeMillis() + (24 * 60 * 60 * 1000L)) }

    fun updateDueTimeForDays(days: Int, cycle: String) {
        val safeDays = days.coerceAtLeast(1)
        rentalDays = safeDays
        dueTimeMillis = if (cycle == "working_hours") {
            Calendar.getInstance().apply {
                add(Calendar.DAY_OF_MONTH, safeDays)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
        } else {
            System.currentTimeMillis() + (safeDays * 24 * 60 * 60 * 1000L)
        }
    }

    // 4. Security Deposit & ID Photo State (matches screenshot)
    var depositText by remember { mutableStateOf("500") }
    var depositMethod by remember { mutableStateOf("cash") } // "cash" or "upi"
    var idPhotoFrontPath by remember { mutableStateOf<String?>(null) }
    var idPhotoBackPath by remember { mutableStateOf<String?>(null) }
    var personPhotoPath by remember { mutableStateOf<String?>(null) }
    var machinePhotoPath by remember { mutableStateOf<String?>(null) }
    var notes by remember { mutableStateOf("") }

    var errorMessage by remember { mutableStateOf<String?>(null) }
    var createdJobSheetForReceipt by remember { mutableStateOf<JobSheetEntity?>(null) }

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

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
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
                        onValueChange = { customerName = it },
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
                        onValueChange = { customerAddress = it },
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

                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = if (mach.availableQty > 0) BabaSuccessLight else BabaOverdueLight
                                        ) {
                                            Text(
                                                text = if (mach.availableQty > 0) "${mach.availableQty} Avail" else "Out of Stock",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (mach.availableQty > 0) BabaSuccess else BabaOverdue,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                            )
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
                                    Text(
                                        text = "Selected Machines ($totalSelectedUnits unit(s) in ${selectedItemsMap.size} item(s))",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = BabaDark,
                                        modifier = Modifier.weight(1f).padding(end = 8.dp)
                                    )
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = "1 Day: ₹${combinedDailyRate.toInt()}/day",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Black,
                                            color = BabaRed,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                        if (rentalDays > 1) {
                                            Text(
                                                text = "$rentalDays Days: ₹${(combinedDailyRate * rentalDays).toInt()}",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = BabaSuccess,
                                                maxLines = 1,
                                                softWrap = false
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
                                                    text = item.machineName,
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
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
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
                                                IconButton(
                                                    onClick = { selectedItemsMap.remove(item.machineId) },
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Close,
                                                        contentDescription = "Remove ${item.machineName}",
                                                        tint = BabaOverdue,
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
                            onValueChange = { discountText = it },
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

                    // 24-Hour Cycle vs Working Hours cards side by side
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // 24-Hour Cycle
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .border(
                                    width = if (billingCycle == "24hr") 2.dp else 1.dp,
                                    color = if (billingCycle == "24hr") BabaRed else BabaBorder,
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .clickable {
                                    billingCycle = "24hr"
                                    updateDueTimeForDays(rentalDays, "24hr")
                                },
                            color = if (billingCycle == "24hr") Color(0xFFFFF0F0) else Color.White
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("24-Hour Cycle", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = BabaDark)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text("Due +${rentalDays * 24} hours from checkout", fontSize = 10.5.sp, color = BabaGray)
                            }
                        }

                        // 1 Day operation
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .border(
                                    width = if (billingCycle == "working_hours") 2.dp else 1.dp,
                                    color = if (billingCycle == "working_hours") BabaRed else BabaBorder,
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .clickable {
                                    billingCycle = "working_hours"
                                    updateDueTimeForDays(rentalDays, "working_hours")
                                },
                            color = if (billingCycle == "working_hours") Color(0xFFFFF0F0) else Color.White
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("1 Day operation", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = BabaDark)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text("12:00 AM – 12:00 AM", fontSize = 11.sp, color = BabaGray, fontWeight = FontWeight.Medium)
                            }
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

                    // Security Deposit (₹) and Deposit Method (Cash / UPI)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = depositText,
                            onValueChange = { depositText = it },
                            label = { Text("Security Deposit (₹)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = babaTextFieldColors(),
                            textStyle = babaInputTextStyle(fontWeight = FontWeight.Bold),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        )

                        Column(modifier = Modifier.weight(1f)) {
                            Text("Deposit Method", fontSize = 11.sp, color = BabaGray, fontWeight = FontWeight.Medium)
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = BabaDark
                                        )
                                    }
                                }

                                // UPI Button
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (depositMethod == "upi") BabaYellowLight else Color.White,
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        if (depositMethod == "upi") BabaYellowDark else BabaBorder
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { depositMethod = "upi" }
                                ) {
                                    Box(
                                        modifier = Modifier.padding(vertical = 12.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "UPI",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = BabaDark
                                        )
                                    }
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
                    Text(
                        text = "⚠ $errorMessage",
                        color = BabaOverdue,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(12.dp)
                    )
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
                    val deposit = depositText.toDoubleOrNull() ?: 500.0

                    if (currentUser.role == "worker" && discount > currentUser.maxDiscountAmount) {
                        errorMessage = "Worker discount cannot exceed ₹${currentUser.maxDiscountAmount.toInt()} floor"
                        return@Button
                    }

                    val itemsList = selectedItemsMap.values.toList()
                    val exactDueTimeMillis = System.currentTimeMillis() + (rentalDays.coerceAtLeast(1) * 24 * 60 * 60 * 1000L)

                    val customer = CustomerEntity(
                        id = "cust_" + (cleanPhone.ifEmpty { UUID.randomUUID().toString() }),
                        name = custName,
                        phone = cleanPhone,
                        address = customerAddress.trim(),
                        idPhotoFrontPath = idPhotoFrontPath,
                        idPhotoBackPath = idPhotoBackPath
                    )

                    onCreateJobSheet(
                        customer,
                        itemsList,
                        discount,
                        deposit,
                        depositMethod,
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
                    AsyncImage(
                        model = File(previewPhotoPath!!),
                        contentDescription = "ID Photo",
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 360.dp)
                            .clip(RoundedCornerShape(10.dp)),
                        contentScale = ContentScale.Fit
                    )
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
            Column(modifier = Modifier.padding(8.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(88.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onPreviewClick() }
                ) {
                    AsyncImage(
                        model = File(imagePath),
                        contentDescription = title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
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
