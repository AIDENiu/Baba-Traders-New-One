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
import com.example.data.model.CustomerEntity
import com.example.data.model.JobSheetEntity
import com.example.data.model.MachineCategoryEntity
import com.example.data.model.MachineEntity
import com.example.data.model.UserEntity
import com.example.ui.components.ReceiptDialog
import com.example.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
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
        machine: MachineEntity,
        categoryName: String,
        quantity: Int,
        rateUsed: Double,
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

    val activeStaffList = remember(staffList, currentUser) {
        if (staffList.isNotEmpty()) staffList else listOf(currentUser)
    }
    var selectedCheckoutUser by remember(activeStaffList, currentUser) {
        mutableStateOf(activeStaffList.firstOrNull { it.id == currentUser.id } ?: activeStaffList.first())
    }
    var selectedHandledUser by remember(activeStaffList, currentUser) {
        mutableStateOf(activeStaffList.firstOrNull { it.id == currentUser.id } ?: activeStaffList.first())
    }

    // Customer fields
    var phone by remember { mutableStateOf("") }
    var customerName by remember { mutableStateOf("") }
    var customerAddress by remember { mutableStateOf("") }
    var isReturningCustomer by remember { mutableStateOf(false) }
    var idPhotoFrontPath by remember { mutableStateOf<String?>(null) }
    var idPhotoBackPath by remember { mutableStateOf<String?>(null) }
    var personPhotoPath by remember { mutableStateOf<String?>(null) }
    var machinePhotoPath by remember { mutableStateOf<String?>(null) }

    // Machine fields
    var selectedCategoryId by remember(categories) { mutableStateOf(categories.firstOrNull()?.id ?: "") }
    val filteredMachines = remember(machines, selectedCategoryId) {
        machines.filter { it.categoryId == selectedCategoryId }
    }
    var selectedMachine by remember(filteredMachines) { mutableStateOf(filteredMachines.firstOrNull()) }

    // Pricing & Terms fields
    var quantityText by remember { mutableStateOf("1") }
    var rateText by remember(selectedMachine) { mutableStateOf((selectedMachine?.currentRate ?: 350.0).toString()) }
    var discountText by remember { mutableStateOf("0") }
    var billingCycle by remember { mutableStateOf("24hr") } // "24hr" or "working_hours"
    var dueTimeMillis by remember { mutableStateOf(System.currentTimeMillis() + (24 * 60 * 60 * 1000L)) }
    var depositText by remember { mutableStateOf("500") }
    var depositMethod by remember { mutableStateOf("cash") } // "cash", "upi", "card", "bank"
    var notes by remember { mutableStateOf("") }

    var errorMessage by remember { mutableStateOf<String?>(null) }
    var createdJobSheetForReceipt by remember { mutableStateOf<JobSheetEntity?>(null) }

    // Photo Capture / Gallery State
    // activePhotoSide: "person", "machine", "id_front", "id_back" (or legacy "front"/"back")
    var activePhotoSide by remember { mutableStateOf<String?>(null) }
    var showPhotoSourceDialog by remember { mutableStateOf(false) }
    var tempCameraFile by remember { mutableStateOf<File?>(null) }
    var previewPhotoPath by remember { mutableStateOf<String?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            val path = tempCameraFile?.absolutePath
            if (path != null) {
                when (activePhotoSide) {
                    "person" -> personPhotoPath = path
                    "machine" -> machinePhotoPath = path
                    "id_front", "front" -> idPhotoFrontPath = path
                    "id_back", "back" -> idPhotoBackPath = path
                }
                Toast.makeText(context, "Photo captured locally", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            val tag = activePhotoSide ?: "photo"
            val file = File(context.filesDir, "${tag}_${System.currentTimeMillis()}.jpg")
            tempCameraFile = file
            try {
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                cameraLauncher.launch(uri)
            } catch (e: Exception) {
                Toast.makeText(context, "Could not open camera: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "Camera permission is required to capture photo", Toast.LENGTH_SHORT).show()
        }
    }

    val galleryPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val tag = activePhotoSide ?: "photo"
                    val file = File(context.filesDir, "${tag}_${System.currentTimeMillis()}.jpg")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(file).use { output ->
                            input.copyTo(output)
                        }
                    }
                    when (activePhotoSide) {
                        "person" -> personPhotoPath = file.absolutePath
                        "machine" -> machinePhotoPath = file.absolutePath
                        "id_front", "front" -> idPhotoFrontPath = file.absolutePath
                        "id_back", "back" -> idPhotoBackPath = file.absolutePath
                    }
                } catch (e: Exception) {
                    // handled
                }
            }
        }
    }

    fun startCameraCapture() {
        val permission = Manifest.permission.CAMERA
        if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) {
            val tag = activePhotoSide ?: "photo"
            val file = File(context.filesDir, "${tag}_${System.currentTimeMillis()}.jpg")
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

    // Search customer as phone changes
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
                        Text("New Job Sheet", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Color.White)
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
            // STEP 1: Customer Details
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                            Surface(shape = RoundedCornerShape(10.dp), color = BabaSuccessLight) {
                                Text(
                                    text = "RETURNING CUSTOMER",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = BabaSuccess,
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

            // STEP 2: Select Machine & Inventory
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .background(BabaRed, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("2", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                        Text(
                            text = "Select Machine & Category",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = BabaDark
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Category Filter Chips (Horizontally scrollable to avoid text compression)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        categories.forEach { cat ->
                            val isSelected = cat.id == selectedCategoryId
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    selectedCategoryId = cat.id
                                    val first = machines.firstOrNull { it.categoryId == cat.id }
                                    selectedMachine = first
                                    if (first != null) {
                                        rateText = first.currentRate.toString()
                                    }
                                },
                                label = { Text(cat.name, fontSize = 11.sp, maxLines = 1) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Machine Selector List / Radio Cards
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        filteredMachines.forEach { mach ->
                            val isSelected = selectedMachine?.id == mach.id
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .border(
                                        width = if (isSelected) 2.dp else 1.dp,
                                        color = if (isSelected) BabaRed else BabaBorder,
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .clickable {
                                        selectedMachine = mach
                                        rateText = mach.currentRate.toString()
                                    },
                                color = if (isSelected) BabaRedLight else Color.White
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = mach.name,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = BabaDark
                                        )
                                        Text(
                                            text = mach.modelOrSpecs,
                                            fontSize = 11.sp,
                                            color = BabaGray
                                        )
                                        Text(
                                            text = "Standard Rate: ₹${mach.baseRate}/day",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = BabaRed
                                        )
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (mach.availableQty > 0) BabaSuccessLight else BabaOverdueLight
                                    ) {
                                        Text(
                                            text = if (mach.availableQty > 0) "${mach.availableQty} Avail" else "OUT OF STOCK",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (mach.availableQty > 0) BabaSuccess else BabaOverdue,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Quantity selector
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Quantity to Rent", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = BabaDark)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val currentQty = quantityText.toIntOrNull() ?: 1
                            IconButton(
                                onClick = {
                                    if (currentQty > 1) quantityText = (currentQty - 1).toString()
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Default.RemoveCircleOutline, contentDescription = "Minus", tint = BabaRed)
                            }
                            Text(
                                text = quantityText,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Black,
                                color = BabaDark
                            )
                            IconButton(
                                onClick = {
                                    val avail = selectedMachine?.availableQty ?: 1
                                    if (currentQty < avail) quantityText = (currentQty + 1).toString()
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Default.AddCircleOutline, contentDescription = "Plus", tint = BabaRed)
                            }
                        }
                    }
                }
            }

            // STEP 3: Rates, Discount & Billing Cycle
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = rateText,
                            onValueChange = { rateText = it },
                            label = { Text("Daily Rate (₹)") },
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
                                    Text("Worker cap: ₹${currentUser.maxDiscountAmount.toInt()}", color = BabaGray, fontSize = 9.sp)
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Billing Cycle Toggle (24hr vs Working Hours)
                    Text("Billing Cycle Policy", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = BabaDark)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .border(
                                    width = if (billingCycle == "24hr") 2.dp else 1.dp,
                                    color = if (billingCycle == "24hr") BabaRed else BabaBorder,
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .clickable { billingCycle = "24hr" },
                            color = if (billingCycle == "24hr") BabaRedLight else Color.White
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("24-Hour Cycle", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = BabaDark)
                                Text("Due exactly +24 hours from checkout", fontSize = 10.sp, color = BabaGray)
                            }
                        }

                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .border(
                                    width = if (billingCycle == "working_hours") 2.dp else 1.dp,
                                    color = if (billingCycle == "working_hours") BabaRed else BabaBorder,
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .clickable { billingCycle = "working_hours" },
                            color = if (billingCycle == "working_hours") BabaRedLight else Color.White
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("24-Hour Operation", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = BabaDark)
                                Text("12:00 AM - 12:00 AM cycle. Rentals returned within 24 hours count as 1 day.", fontSize = 10.sp, color = BabaGray)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Explicit Due Date Selector
                    Text("Rental Due Date *", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = BabaDark)
                    val dateFormat = remember { SimpleDateFormat("EEE, dd MMM yyyy, hh:mm a", Locale.getDefault()) }
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = BabaBg,
                        border = androidx.compose.foundation.BorderStroke(1.dp, BabaBorder),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Default.Event, contentDescription = "Due Date", tint = BabaRed)
                                Column {
                                    Text(
                                        text = dateFormat.format(Date(dueTimeMillis)),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = BabaDark
                                    )
                                    Text(
                                        text = "Alarms fire 2h before and on due date",
                                        fontSize = 10.sp,
                                        color = BabaGray
                                    )
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                IconButton(
                                    onClick = {
                                        val dayMs = 24 * 60 * 60 * 1000L
                                        if (dueTimeMillis - dayMs > System.currentTimeMillis()) {
                                            dueTimeMillis -= dayMs
                                        }
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.RemoveCircleOutline, contentDescription = "-1 Day", tint = BabaDark)
                                }
                                IconButton(
                                    onClick = {
                                        dueTimeMillis += 24 * 60 * 60 * 1000L
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.AddCircleOutline, contentDescription = "+1 Day", tint = BabaRed)
                                }
                            }
                        }
                    }
                }
            }

            // STEP 4: Security Deposit & ID Photo Capture
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
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

                        Column(modifier = Modifier.weight(1.3f)) {
                            Text("Mode of Payment *", fontSize = 11.sp, color = BabaGray, fontWeight = FontWeight.SemiBold)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                listOf("cash" to "Cash", "upi" to "UPI", "card" to "Card", "bank" to "Bank").forEach { (methodKey, label) ->
                                    FilterChip(
                                        selected = depositMethod == methodKey,
                                        onClick = { depositMethod = methodKey },
                                        label = { Text(label, fontSize = 11.sp) }
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Mandatory Local Photos: Person, ID, Machine (Stored Locally Only)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Customer & ID Photos (App-Private Storage Only)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = BabaDark
                        )
                        Text(
                            text = "Never uploaded to cloud",
                            fontSize = 10.sp,
                            color = BabaSuccess,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Person Photo Card
                        IdDocumentCard(
                            title = "Person Photo *",
                            imagePath = personPhotoPath,
                            onCardClick = {
                                activePhotoSide = "person"
                                showPhotoSourceDialog = true
                            },
                            onPreviewClick = {
                                previewPhotoPath = personPhotoPath
                            },
                            onRemoveClick = {
                                personPhotoPath = null
                            },
                            modifier = Modifier.weight(1f)
                        )

                        // ID Photo Front Card
                        IdDocumentCard(
                            title = "ID Document *",
                            imagePath = idPhotoFrontPath,
                            onCardClick = {
                                activePhotoSide = "id_front"
                                showPhotoSourceDialog = true
                            },
                            onPreviewClick = {
                                previewPhotoPath = idPhotoFrontPath
                            },
                            onRemoveClick = {
                                idPhotoFrontPath = null
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Machine Photo Card
                        IdDocumentCard(
                            title = "Machine Photo *",
                            imagePath = machinePhotoPath,
                            onCardClick = {
                                activePhotoSide = "machine"
                                showPhotoSourceDialog = true
                            },
                            onPreviewClick = {
                                previewPhotoPath = machinePhotoPath
                            },
                            onRemoveClick = {
                                machinePhotoPath = null
                            },
                            modifier = Modifier.weight(1f)
                        )

                        // ID Back Photo (Optional / Secondary)
                        IdDocumentCard(
                            title = "ID Back (Opt)",
                            imagePath = idPhotoBackPath,
                            onCardClick = {
                                activePhotoSide = "id_back"
                                showPhotoSourceDialog = true
                            },
                            onPreviewClick = {
                                previewPhotoPath = idPhotoBackPath
                            },
                            onRemoveClick = {
                                idPhotoBackPath = null
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("Rental Notes / Specific Job Details") },
                        colors = babaTextFieldColors(),
                        textStyle = babaInputTextStyle(),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            }

            // STEP 5: Staff Accountability (Checkout & Handling)
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .background(BabaRed, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("5", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                            Text(
                                text = "Checkout Attribution (Automatic)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = BabaDark
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = BabaSuccessLight
                        ) {
                            Text(
                                text = "SESSION LOCKED",
                                color = BabaSuccess,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = BabaBg,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = BabaRed)
                            Column {
                                Text(
                                    text = "Checked Out & Handled By: ${currentUser.name}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = BabaDark
                                )
                                Text(
                                    text = "Derived automatically from active session (${currentUser.role.replaceFirstChar { it.uppercase() }}). No manual staff selection allowed.",
                                    fontSize = 11.sp,
                                    color = BabaGray
                                )
                            }
                        }
                    }
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
                    val loc = customerAddress.trim()
                    if (loc.isEmpty()) {
                        errorMessage = "Location / Site address is mandatory and cannot be skipped"
                        return@Button
                    }
                    if (personPhotoPath == null) {
                        errorMessage = "Customer person photo is mandatory (stored locally on device only)"
                        return@Button
                    }
                    if (idPhotoFrontPath == null && idPhotoBackPath == null) {
                        errorMessage = "Customer ID document photo is mandatory (stored locally on device only)"
                        return@Button
                    }
                    if (machinePhotoPath == null) {
                        errorMessage = "Machine photo is mandatory (stored locally on device only)"
                        return@Button
                    }
                    val machine = selectedMachine
                    if (machine == null) {
                        errorMessage = "Please select a machine"
                        return@Button
                    }
                    val qty = quantityText.toIntOrNull() ?: 1
                    if (qty <= 0) {
                        errorMessage = "Quantity must be at least 1"
                        return@Button
                    }
                    if (qty > machine.availableQty) {
                        errorMessage = "Only ${machine.availableQty} units available in stock"
                        return@Button
                    }
                    val rate = rateText.toDoubleOrNull() ?: machine.currentRate
                    val discount = discountText.toDoubleOrNull() ?: 0.0
                    val deposit = depositText.toDoubleOrNull() ?: 0.0

                    if (currentUser.role == "worker" && discount > currentUser.maxDiscountAmount) {
                        errorMessage = "Worker discount cannot exceed ₹${currentUser.maxDiscountAmount.toInt()} floor"
                        return@Button
                    }

                    val customer = CustomerEntity(
                        id = "cust_" + (cleanPhone.ifEmpty { UUID.randomUUID().toString() }),
                        name = custName,
                        phone = cleanPhone,
                        address = loc,
                        idPhotoFrontPath = idPhotoFrontPath,
                        idPhotoBackPath = idPhotoBackPath
                    )

                    val category = categories.firstOrNull { it.id == selectedCategoryId }?.name ?: "Hardware"

                    onCreateJobSheet(
                        customer,
                        machine,
                        category,
                        qty,
                        rate,
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
                        dueTimeMillis,
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

    // Show Receipt Dialog on successful job sheet creation!
    if (createdJobSheetForReceipt != null) {
        ReceiptDialog(
            jobSheet = createdJobSheetForReceipt!!,
            onDismiss = {
                createdJobSheetForReceipt = null
                onNavigateBack()
            }
        )
    }

    // Photo Source Selection Dialog (Camera vs Gallery)
    if (showPhotoSourceDialog) {
        val sideLabel = when (activePhotoSide) {
            "person" -> "Person"
            "machine" -> "Machine"
            "id_front", "front" -> "ID Document Front"
            "id_back", "back" -> "ID Document Back"
            else -> "Photo"
        }
        val existingPath = when (activePhotoSide) {
            "person" -> personPhotoPath
            "machine" -> machinePhotoPath
            "id_front", "front" -> idPhotoFrontPath
            "id_back", "back" -> idPhotoBackPath
            else -> null
        }

        AlertDialog(
            onDismissRequest = { showPhotoSourceDialog = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AddAPhoto,
                        contentDescription = "Photo",
                        tint = BabaRed,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = "Add $sideLabel Photo",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = BabaDark
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Choose how to capture or select photo (stored locally on this device only):",
                        fontSize = 12.sp,
                        color = BabaGray
                    )

                    // Option 1: Camera
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .border(1.dp, BabaBorder, RoundedCornerShape(12.dp))
                            .clickable {
                                showPhotoSourceDialog = false
                                startCameraCapture()
                            },
                        color = Color.White
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(BabaRedLight, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CameraAlt,
                                    contentDescription = "Camera",
                                    tint = BabaRed,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "Take Photo (Camera)",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = BabaDark
                                )
                                Text(
                                    text = "Capture $sideLabel ID with device camera",
                                    fontSize = 11.sp,
                                    color = BabaGray
                                )
                            }
                        }
                    }

                    // Option 2: Gallery
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .border(1.dp, BabaBorder, RoundedCornerShape(12.dp))
                            .clickable {
                                showPhotoSourceDialog = false
                                galleryPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                        color = Color.White
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(BabaYellowLight, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PhotoLibrary,
                                    contentDescription = "Gallery",
                                    tint = BabaYellowDark,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "Add Photo (Gallery / Files)",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = BabaDark
                                )
                                Text(
                                    text = "Select from saved photos on device",
                                    fontSize = 11.sp,
                                    color = BabaGray
                                )
                            }
                        }
                    }

                    // Option 3: View current if present
                    if (existingPath != null) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .border(1.dp, BabaBorder, RoundedCornerShape(12.dp))
                                .clickable {
                                    showPhotoSourceDialog = false
                                    previewPhotoPath = existingPath
                                },
                            color = Color.White
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .background(BabaLightGray, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Visibility,
                                        contentDescription = "Preview",
                                        tint = BabaDark,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                Column {
                                    Text(
                                        text = "View Stored $sideLabel Photo",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = BabaDark
                                    )
                                    Text(
                                        text = "Preview current image",
                                        fontSize = 11.sp,
                                        color = BabaGray
                                    )
                                }
                            }
                        }

                        // Option 4: Remove photo
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .border(1.dp, BabaOverdue.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                                .clickable {
                                    when (activePhotoSide) {
                                        "person" -> personPhotoPath = null
                                        "machine" -> machinePhotoPath = null
                                        "id_front", "front" -> idPhotoFrontPath = null
                                        "id_back", "back" -> idPhotoBackPath = null
                                    }
                                    showPhotoSourceDialog = false
                                },
                            color = BabaOverdueLight
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .background(Color.White, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteOutline,
                                        contentDescription = "Remove",
                                        tint = BabaOverdue,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                Column {
                                    Text(
                                        text = "Remove $sideLabel Photo",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = BabaOverdue
                                    )
                                    Text(
                                        text = "Delete photo from this sheet",
                                        fontSize = 11.sp,
                                        color = BabaOverdue.copy(alpha = 0.8f)
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showPhotoSourceDialog = false }) {
                    Text("Cancel", color = BabaGray, fontWeight = FontWeight.SemiBold)
                }
            },
            shape = RoundedCornerShape(16.dp),
            containerColor = Color.White
        )
    }

    // Full Screen / Dialog Image Preview
    if (previewPhotoPath != null) {
        Dialog(onDismissRequest = { previewPhotoPath = null }) {
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
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (previewPhotoPath == idPhotoFrontPath) "Front ID Document" else "Back ID Document",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = BabaDark
                        )
                        IconButton(onClick = { previewPhotoPath = null }) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = BabaDark)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    AsyncImage(
                        model = File(previewPhotoPath!!),
                        contentDescription = "ID Photo Preview",
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 380.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .border(1.dp, BabaBorder, RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Fit
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = { previewPhotoPath = null },
                        colors = ButtonDefaults.buttonColors(containerColor = BabaDark),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Close Preview")
                    }
                }
            }
        }
    }
}

@Composable
private fun IdDocumentCard(
    title: String,
    imagePath: String?,
    onCardClick: () -> Unit,
    onPreviewClick: () -> Unit,
    onRemoveClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .border(
                width = if (imagePath != null) 1.5.dp else 1.dp,
                color = if (imagePath != null) BabaSuccess else BabaBorder,
                shape = RoundedCornerShape(12.dp)
            ),
        color = if (imagePath != null) Color.White else Color.White
    ) {
        if (imagePath != null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
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
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Remove",
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(4.dp),
                        shape = RoundedCornerShape(4.dp),
                        color = Color.Black.copy(alpha = 0.5f)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Icon(Icons.Default.ZoomIn, contentDescription = "View", tint = Color.White, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(2.dp))
                            Text("View", color = Color.White, fontSize = 9.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = "Stored",
                            tint = BabaSuccess,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = title,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = BabaSuccess
                        )
                    }

                    Text(
                        text = "Change",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = BabaRed,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { onCardClick() }
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onCardClick() }
                    .padding(vertical = 14.dp, horizontal = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(BabaRedLight, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.CameraAlt,
                            contentDescription = "Camera",
                            tint = BabaRed,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(BabaYellowLight, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.PhotoLibrary,
                            contentDescription = "Add Photo",
                            tint = BabaYellowDark,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

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
