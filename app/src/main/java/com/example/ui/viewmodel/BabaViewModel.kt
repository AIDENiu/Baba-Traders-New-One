package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.backup.BackupFileInfo
import com.example.data.backup.BackupManager
import com.example.data.firebase.CloudCatchUpSyncWorker
import com.example.data.firebase.CloudSyncStatus
import com.example.data.firebase.FirestoreSyncService
import com.example.data.firebase.TabletMigrationReport
import java.io.File
import com.example.data.model.ActivityLogEntity
import com.example.data.model.CustomerEntity
import com.example.data.model.ExpenseEntity
import com.example.data.model.JobSheetEntity
import com.example.data.model.MachineCategoryEntity
import com.example.data.model.MachineEntity
import com.example.data.model.PaymentEntity
import com.example.data.model.SelectedMachineItem
import com.example.data.model.UserEntity
import com.example.data.repository.BabaRepository
import com.example.data.repository.ReturnCalculation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

data class StaffAccountabilityStat(
    val staffId: String,
    val staffName: String,
    val role: String,
    val checkoutsToday: Int,
    val checkoutsMonth: Int,
    val handledToday: Int,
    val handledMonth: Int
)

data class DashboardStats(
    val todayRevenue: Double = 0.0,
    val todayJobSheetsCount: Int = 0,
    val monthlyRevenue: Double = 0.0,
    val monthlyJobSheetsCount: Int = 0,
    val totalCustomerDues: Double = 0.0,
    val activeRentalsCount: Int = 0,
    val overdueRentalsCount: Int = 0,
    val totalCashToday: Double = 0.0,
    val advanceInToday: Double = 0.0,
    val advanceOutToday: Double = 0.0,
    val staffPerformance: List<StaffAccountabilityStat> = emptyList()
)

data class OwnerAlertNotification(
    val id: String,
    val message: String,
    val timestamp: Long = System.currentTimeMillis()
)

class BabaViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application, viewModelScope)
    val repository = BabaRepository(db)

    val syncService = FirestoreSyncService(application, db, viewModelScope)
    val isSyncing: StateFlow<Boolean> = syncService.isSyncing
    val syncError: StateFlow<String?> = syncService.syncError
    val syncStatus: StateFlow<CloudSyncStatus> = syncService.syncStatus

    private val sessionPrefs = application.getSharedPreferences("baba_session_prefs", Context.MODE_PRIVATE)

    // Current Logged In User
    private val _currentUser = MutableStateFlow<UserEntity?>(null)
    val currentUser: StateFlow<UserEntity?> = _currentUser.asStateFlow()

    // Owner Push / Alert Notification banner
    private val _ownerAlert = MutableStateFlow<OwnerAlertNotification?>(null)
    val ownerAlert: StateFlow<OwnerAlertNotification?> = _ownerAlert.asStateFlow()

    // One-time Tablet Data Migration State (Owner Only)
    private val _migrationProgress = MutableStateFlow<String?>(null)
    val migrationProgress: StateFlow<String?> = _migrationProgress.asStateFlow()

    private val _migrationReport = MutableStateFlow<TabletMigrationReport?>(null)
    val migrationReport: StateFlow<TabletMigrationReport?> = _migrationReport.asStateFlow()

    private val _hasTakenPreMigrationBackup = MutableStateFlow(false)
    val hasTakenPreMigrationBackup: StateFlow<Boolean> = _hasTakenPreMigrationBackup.asStateFlow()

    // Data streams
    val users: StateFlow<List<UserEntity>> = repository.allUsers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeUsers: StateFlow<List<UserEntity>> = repository.activeUsers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val categories: StateFlow<List<MachineCategoryEntity>> = repository.allCategories
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val machines: StateFlow<List<MachineEntity>> = repository.allMachines
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allJobSheets: StateFlow<List<JobSheetEntity>> = repository.allJobSheets
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeJobSheets: StateFlow<List<JobSheetEntity>> = repository.activeJobSheets
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val customers: StateFlow<List<CustomerEntity>> = repository.allCustomers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activityLogs: StateFlow<List<ActivityLogEntity>> = repository.activityLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val payments: StateFlow<List<PaymentEntity>> = repository.allPayments
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val expenses: StateFlow<List<ExpenseEntity>> = repository.allExpenses
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Combined Dashboard Stats with Staff Accountability & Daily vs Monthly split
    val dashboardStats: StateFlow<DashboardStats> = combine(
        allJobSheets,
        payments,
        users
    ) { jobs, allPaymentsList, userList ->
        val now = System.currentTimeMillis()
        val startOfToday = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val startOfMonth = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val nonDeletedJobs = jobs.filter { !it.isDeleted }
        val todayJobs = nonDeletedJobs.filter { it.checkoutTime >= startOfToday }
        val monthJobs = nonDeletedJobs.filter { it.checkoutTime >= startOfMonth }
        val activeJobs = nonDeletedJobs.filter { it.status == "active" || it.status == "overdue" }
        val overdueJobs = nonDeletedJobs.filter { it.status == "overdue" || (it.status == "active" && it.dueTime < now) }

        // 1. Today's Sales (Net Revenue): strictly exclude advance deposits!
        // Only finalized rental fee when machine is returned and rental is closed.
        val returnedTodayJobs = nonDeletedJobs.filter {
            (it.status == "returned" || it.status == "closed") && (it.returnTime ?: 0L) >= startOfToday
        }
        val partialTodaySettlements = allPaymentsList.filter {
            it.timestamp >= startOfToday && it.type == "settlement" && it.remark.contains("Partial return", ignoreCase = true)
        }.sumOf { it.amount }
        val todayRevenue = returnedTodayJobs.sumOf { it.totalRentalFee } + partialTodaySettlements

        // Monthly Sales:
        val returnedMonthJobs = nonDeletedJobs.filter {
            (it.status == "returned" || it.status == "closed") && (it.returnTime ?: 0L) >= startOfMonth
        }
        val partialMonthSettlements = allPaymentsList.filter {
            it.timestamp >= startOfMonth && it.type == "settlement" && it.remark.contains("Partial return", ignoreCase = true)
        }.sumOf { it.amount }
        val monthlyRevenue = returnedMonthJobs.sumOf { it.totalRentalFee } + partialMonthSettlements

        // 2. Total Cash in Today (keep cash advances included):
        val todayPayments = allPaymentsList.filter { it.timestamp >= startOfToday }
        val cashInToday = todayPayments.filter {
            it.method.lowercase() == "cash" && it.type != "refund" && it.type != "return_nil"
        }.sumOf { it.amount }
        val cashOutToday = todayPayments.filter {
            it.method.lowercase() == "cash" && it.type == "refund"
        }.sumOf { it.amount }
        val totalCashToday = maxOf(0.0, cashInToday - cashOutToday)

        // 3. Two separate dashboard counters for Advance In and Advance Out:
        val advanceInToday = todayPayments.filter {
            it.type == "advance" || it.type == "deposit"
        }.sumOf { it.amount }

        val advanceOutToday = todayPayments.filter {
            it.type == "refund"
        }.sumOf { it.amount }

        // 4. Truly pending dues on active/overdue rentals (closed rentals have 0 dues):
        val totalCustomerDues = activeJobs.sumOf { job ->
            val bill = repository.calculateReturnBill(job)
            if (bill.balanceDueOrRefund > 0 && (job.status == "overdue" || now > job.dueTime)) bill.balanceDueOrRefund else 0.0
        }

        val staffStats = userList.map { u ->
            StaffAccountabilityStat(
                staffId = u.id,
                staffName = u.name,
                role = u.role,
                checkoutsToday = todayJobs.count { it.checkoutBy == u.id || it.checkoutByName == u.name || (it.checkoutByName.isEmpty() && it.handledByName == u.name) },
                checkoutsMonth = monthJobs.count { it.checkoutBy == u.id || it.checkoutByName == u.name || (it.checkoutByName.isEmpty() && it.handledByName == u.name) },
                handledToday = todayJobs.count { it.handledBy == u.id || it.handledByName == u.name },
                handledMonth = monthJobs.count { it.handledBy == u.id || it.handledByName == u.name }
            )
        }

        DashboardStats(
            todayRevenue = maxOf(0.0, todayRevenue),
            todayJobSheetsCount = todayJobs.size,
            monthlyRevenue = maxOf(0.0, monthlyRevenue),
            monthlyJobSheetsCount = monthJobs.size,
            totalCustomerDues = totalCustomerDues,
            activeRentalsCount = activeJobs.size,
            overdueRentalsCount = overdueJobs.size,
            totalCashToday = totalCashToday,
            advanceInToday = advanceInToday,
            advanceOutToday = advanceOutToday,
            staffPerformance = staffStats
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardStats())

    init {
        CloudCatchUpSyncWorker.schedulePeriodicAndImmediateSync(application)
        viewModelScope.launch(Dispatchers.IO) {
            AppDatabase.ensureSeedData(db)
            val savedUserId = sessionPrefs.getString("logged_in_user_id", null)
            if (!savedUserId.isNullOrBlank() && _currentUser.value == null) {
                val savedUser = repository.getUserById(savedUserId)
                if (savedUser != null && savedUser.isActive) {
                    _currentUser.value = savedUser
                }
            }
            com.example.util.DueAlarmScheduler.rescheduleAllActiveAlarms(application, db)
            syncService.normalizeAndSyncExistingStaffRecords()
            syncService.startRealtimeListeners()
            syncService.runAutomaticBackgroundSyncCycle()
            // Weekly automated backup (Data + Photos bundled together)
            BackupManager.checkAndRunWeeklyBackup(application, db)
        }

        // Automatically monitor activity logs to trigger Owner Notifications if worker edited or deleted a job sheet
        viewModelScope.launch {
            repository.activityLogs.collect { logs ->
                val latest = logs.firstOrNull()
                if (latest != null && (latest.action == "edited" || latest.action == "deleted") && !latest.performedBy.startsWith("user_owner")) {
                    if (latest.timestamp > System.currentTimeMillis() - 60000) {
                        _ownerAlert.value = OwnerAlertNotification(
                            id = latest.id,
                            message = "Staff Alert: ${latest.changeSummary}",
                            timestamp = latest.timestamp
                        )
                    }
                }
            }
        }
    }

    fun dismissAlert() {
        _ownerAlert.value = null
        syncService.clearSyncError()
    }

    // --- Authentication ---
    fun login(user: UserEntity, enteredPin: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        if (!user.isActive) {
            onError("Account is deactivated. Please contact owner.")
            return
        }
        if (user.pin.isNotBlank() && user.pin == enteredPin) {
            val fbUser = try { com.google.firebase.auth.FirebaseAuth.getInstance().currentUser } catch (_: Exception) { null }
            val fbEmail = fbUser?.email?.trim()?.lowercase().orEmpty()
            if (user.role == "owner" && fbEmail.isNotEmpty() && fbEmail != FirestoreSyncService.OWNER_EMAIL.lowercase()) {
                try { com.google.firebase.auth.FirebaseAuth.getInstance().signOut() } catch (_: Exception) {}
            }
            val normalizedUser = user.copy(phoneOrUsername = user.phoneOrUsername.trim().lowercase())
            _currentUser.value = normalizedUser
            sessionPrefs.edit().putString("logged_in_user_id", normalizedUser.id).apply()
            viewModelScope.launch(Dispatchers.IO) {
                if (repository.getUserById(normalizedUser.id) == null) {
                    repository.insertUser(normalizedUser)
                }
                val resolved = syncService.linkAuthenticatedStaffSession(normalizedUser)
                _currentUser.value = resolved
                sessionPrefs.edit().putString("logged_in_user_id", resolved.id).apply()
                syncService.startRealtimeListeners()
                syncService.runAutomaticBackgroundSyncCycle()
            }
            onSuccess()
        } else {
            onError("Incorrect PIN. Please try again.")
        }
    }

    fun directLoginAs(user: UserEntity) {
        val fbUser = try { com.google.firebase.auth.FirebaseAuth.getInstance().currentUser } catch (_: Exception) { null }
        val fbEmail = fbUser?.email?.trim()?.lowercase().orEmpty()
        if (user.role == "owner" && fbEmail.isNotEmpty() && fbEmail != FirestoreSyncService.OWNER_EMAIL.lowercase()) {
            try { com.google.firebase.auth.FirebaseAuth.getInstance().signOut() } catch (_: Exception) {}
        }
        val normalizedUser = user.copy(phoneOrUsername = user.phoneOrUsername.trim().lowercase())
        _currentUser.value = normalizedUser
        sessionPrefs.edit().putString("logged_in_user_id", normalizedUser.id).apply()
        viewModelScope.launch(Dispatchers.IO) {
            if (repository.getUserById(normalizedUser.id) == null) {
                repository.insertUser(normalizedUser)
            }
            val resolved = syncService.linkAuthenticatedStaffSession(normalizedUser)
            _currentUser.value = resolved
            sessionPrefs.edit().putString("logged_in_user_id", resolved.id).apply()
            syncService.startRealtimeListeners()
            syncService.runAutomaticBackgroundSyncCycle()
        }
    }

    fun logout() {
        _currentUser.value = null
        sessionPrefs.edit().remove("logged_in_user_id").apply()
    }

    // --- Customer Lookup & Manual Customer Management ---
    suspend fun findCustomerByPhone(phone: String): CustomerEntity? {
        return repository.getCustomerByPhone(phone)
    }

    fun saveCustomer(
        existingCustomer: CustomerEntity? = null,
        name: String,
        phone: String,
        address: String,
        onSuccess: (CustomerEntity) -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        val cleanName = name.trim()
        val cleanPhone = phone.trim().filter { it.isDigit() }
        val cleanAddress = address.trim()

        if (cleanName.isEmpty()) {
            onError("Please enter customer name")
            return
        }
        if (cleanPhone.length < 10) {
            onError("Please enter a valid 10-digit phone number")
            return
        }

        viewModelScope.launch {
            try {
                val matchByPhone = repository.getCustomerByPhone(cleanPhone)
                val customer = when {
                    existingCustomer != null -> existingCustomer.copy(
                        name = cleanName,
                        phone = cleanPhone,
                        address = cleanAddress
                    )
                    matchByPhone != null -> matchByPhone.copy(
                        name = cleanName,
                        phone = cleanPhone,
                        address = cleanAddress
                    )
                    else -> CustomerEntity(
                        id = "cust_$cleanPhone",
                        name = cleanName,
                        phone = cleanPhone,
                        address = cleanAddress,
                        createdAt = System.currentTimeMillis()
                    )
                }
                repository.insertCustomer(customer)
                syncService.pushCustomer(customer)
                onSuccess(customer)
            } catch (e: Exception) {
                onError(e.message ?: "Failed to save customer")
            }
        }
    }

    fun deleteCustomer(
        customerId: String,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                repository.deleteCustomer(customerId)
                onSuccess()
            } catch (e: Exception) {
                onError(e.message ?: "Failed to delete customer")
            }
        }
    }

    // --- Job Sheet Actions ---
    fun createJobSheet(
        customer: CustomerEntity,
        items: List<SelectedMachineItem>,
        discount: Double,
        depositAmount: Double,
        depositMethod: String,
        billingCycle: String,
        checkoutUser: UserEntity,
        handledUser: UserEntity,
        notes: String,
        personPhotoPath: String? = null,
        idPhotoPath: String? = null,
        machinePhotoPath: String? = null,
        explicitDueTime: Long? = null,
        context: Context? = null,
        onSuccess: (JobSheetEntity) -> Unit,
        onError: (String) -> Unit
    ) {
        val user = _currentUser.value
        if (user == null) {
            onError("Please log in first")
            return
        }

        if (items.isEmpty()) {
            onError("Please select at least one machine or equipment.")
            return
        }

        // Validate worker discount floor constraint
        if (user.role == "worker") {
            val maxAllowed = user.maxDiscountAmount
            if (discount > maxAllowed) {
                onError("Worker discount cannot exceed ₹$maxAllowed as per owner policy.")
                return
            }
        }

        viewModelScope.launch {
            try {
                val allocatedJobId = kotlinx.coroutines.withContext(Dispatchers.IO) {
                    syncService.allocateNextJobSheetId(repository.getMaxLocalJobNumber())
                }
                val created = repository.createJobSheet(
                    customer = customer,
                    items = items,
                    discount = discount,
                    depositAmount = depositAmount,
                    depositMethod = depositMethod,
                    billingCycle = billingCycle,
                    checkoutUser = user,
                    handledUser = handledUser,
                    notes = notes,
                    personPhotoPath = personPhotoPath,
                    idPhotoPath = idPhotoPath,
                    machinePhotoPath = machinePhotoPath,
                    explicitDueTime = explicitDueTime,
                    context = context,
                    explicitJobId = allocatedJobId
                )
                viewModelScope.launch(Dispatchers.IO) {
                    syncService.pushCustomer(customer)
                    syncService.pushJobSheet(created, isNewCreation = true)
                    syncService.pushPaymentsForJob(created.id)
                    syncService.pushActivityLogsForJob(created.id)
                    items.forEach { item ->
                        syncService.adjustMachineStockAtomically(item.machineId, -item.quantity)
                    }
                }
                onSuccess(created)
            } catch (e: Exception) {
                onError(e.message ?: "Failed to create job sheet")
            }
        }
    }

    fun createJobSheet(
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
        personPhotoPath: String? = null,
        idPhotoPath: String? = null,
        machinePhotoPath: String? = null,
        explicitDueTime: Long? = null,
        context: Context? = null,
        onSuccess: (JobSheetEntity) -> Unit,
        onError: (String) -> Unit
    ) {
        val singleItem = SelectedMachineItem(
            machineId = machine.id,
            machineName = machine.name,
            categoryId = machine.categoryId,
            categoryName = categoryName,
            quantity = quantity,
            rate = rateUsed
        )
        createJobSheet(
            customer = customer,
            items = listOf(singleItem),
            discount = discount,
            depositAmount = depositAmount,
            depositMethod = depositMethod,
            billingCycle = billingCycle,
            checkoutUser = checkoutUser,
            handledUser = handledUser,
            notes = notes,
            personPhotoPath = personPhotoPath,
            idPhotoPath = idPhotoPath,
            machinePhotoPath = machinePhotoPath,
            explicitDueTime = explicitDueTime,
            context = context,
            onSuccess = onSuccess,
            onError = onError
        )
    }

    fun calculateReturnBill(
        jobSheet: JobSheetEntity,
        returningQuantity: Int = jobSheet.remainingActiveQty,
        manualDiscount: Double = 0.0,
        overrideDaysUsed: Int? = null
    ): ReturnCalculation {
        return repository.calculateReturnBill(jobSheet, returningQuantity, manualDiscount, overrideDaysUsed = overrideDaysUsed)
    }

    fun returnJob(
        jobSheetId: String,
        settlementMethod: String,
        manualDiscount: Double = 0.0,
        remark: String = "",
        context: Context? = null,
        overrideDaysUsed: Int? = null,
        onSuccess: (JobSheetEntity) -> Unit,
        onError: (String) -> Unit
    ) {
        val user = _currentUser.value ?: return
        viewModelScope.launch {
            try {
                val priorJob = allJobSheets.value.firstOrNull { it.id == jobSheetId }
                val priorActiveQty = priorJob?.remainingActiveQty ?: 1
                val updated = repository.markReturned(
                    jobSheetId = jobSheetId,
                    settlementMethod = settlementMethod,
                    currentUser = user,
                    manualDiscount = manualDiscount,
                    remark = remark,
                    context = context,
                    overrideDaysUsed = overrideDaysUsed
                )
                if (updated != null) {
                    viewModelScope.launch(Dispatchers.IO) {
                        syncService.pushJobSheet(updated)
                        syncService.pushPaymentsForJob(updated.id)
                        syncService.pushActivityLogsForJob(updated.id)
                        val itemsInJob = SelectedMachineItem.listFromJson(updated.itemsJson)
                        if (itemsInJob.isNotEmpty()) {
                            itemsInJob.forEach { item ->
                                syncService.adjustMachineStockAtomically(item.machineId, item.quantity)
                            }
                        } else {
                            syncService.adjustMachineStockAtomically(updated.machineId, priorActiveQty)
                        }
                    }
                    onSuccess(updated)
                } else {
                    onError("Job sheet not found")
                }
            } catch (e: Exception) {
                onError(e.message ?: "Failed to return job")
            }
        }
    }

    fun returnPartialJob(
        jobSheetId: String,
        returningQuantity: Int,
        settlementMethod: String,
        manualDiscount: Double = 0.0,
        remark: String = "",
        context: Context? = null,
        overrideDaysUsed: Int? = null,
        onSuccess: (JobSheetEntity) -> Unit,
        onError: (String) -> Unit
    ) {
        val user = _currentUser.value ?: return
        viewModelScope.launch {
            try {
                val updated = repository.markPartialReturned(
                    jobSheetId = jobSheetId,
                    returningQuantity = returningQuantity,
                    settlementMethod = settlementMethod,
                    currentUser = user,
                    manualDiscount = manualDiscount,
                    remark = remark,
                    context = context,
                    overrideDaysUsed = overrideDaysUsed
                )
                if (updated != null) {
                    viewModelScope.launch(Dispatchers.IO) {
                        syncService.pushJobSheet(updated)
                        syncService.pushPaymentsForJob(updated.id)
                        syncService.pushActivityLogsForJob(updated.id)
                        val itemsInJob = SelectedMachineItem.listFromJson(updated.itemsJson)
                        if (itemsInJob.isNotEmpty()) {
                            var remainingToRestore = returningQuantity
                            for (item in itemsInJob) {
                                if (remainingToRestore <= 0) break
                                val restoreForThisItem = minOf(item.quantity, remainingToRestore)
                                if (restoreForThisItem > 0) {
                                    syncService.adjustMachineStockAtomically(item.machineId, restoreForThisItem)
                                    remainingToRestore -= restoreForThisItem
                                }
                            }
                        } else {
                            val targetMachineId = updated.machineId.split(",").firstOrNull()?.trim().orEmpty()
                            if (targetMachineId.isNotEmpty()) {
                                syncService.adjustMachineStockAtomically(targetMachineId, returningQuantity)
                            }
                        }
                    }
                    onSuccess(updated)
                } else {
                    onError("Job sheet not found")
                }
            } catch (e: Exception) {
                onError(e.message ?: "Failed to process partial return")
            }
        }
    }

    fun addExtraPayment(
        jobSheetId: String,
        amount: Double,
        paymentMode: String,
        remark: String = "",
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val user = _currentUser.value ?: return
        viewModelScope.launch {
            try {
                val ok = repository.addExtraPayment(
                    jobSheetId = jobSheetId,
                    amount = amount,
                    paymentMode = paymentMode,
                    currentUser = user,
                    remark = remark
                )
                if (ok) {
                    viewModelScope.launch(Dispatchers.IO) {
                        syncService.pushPaymentsForJob(jobSheetId)
                        syncService.pushActivityLogsForJob(jobSheetId)
                        db.jobSheetDao().getJobSheetById(jobSheetId)?.let {
                            syncService.pushJobSheet(it)
                        }
                    }
                    onSuccess()
                } else {
                    onError("Failed to add payment")
                }
            } catch (e: Exception) {
                onError(e.message ?: "Failed to add extra payment")
            }
        }
    }

    fun editJobSheet(
        jobSheetId: String,
        newQuantity: Int,
        newRate: Double,
        newDiscount: Double,
        newNotes: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        onError("Editing job sheets is strictly prohibited. Corrections must be made only by adding payments or remarks.")
    }

    fun softDeleteJobSheet(
        jobSheetId: String,
        reason: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        onError("Deleting job sheets is strictly prohibited by client business policy.")
    }

    // --- Machine Management (Owner Only) ---
    fun addMachine(
        categoryId: String,
        name: String,
        baseRate: Double,
        totalQty: Int,
        modelOrSpecs: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val user = _currentUser.value
        if (user?.role != "owner") {
            onError("Only owner can add machines")
            return
        }
        viewModelScope.launch {
            try {
                val machine = MachineEntity(
                    id = "mach_" + syncService.generateDocumentId("machines"),
                    categoryId = categoryId,
                    name = name,
                    baseRate = baseRate,
                    currentRate = baseRate,
                    availableQty = totalQty,
                    totalQty = totalQty,
                    modelOrSpecs = modelOrSpecs
                )
                repository.insertMachine(machine)
                syncService.pushMachine(machine)
                onSuccess()
            } catch (e: Exception) {
                onError(e.message ?: "Failed to add machine")
            }
        }
    }

    fun updateMachinePricing(
        machineId: String,
        currentRate: Double,
        totalQty: Int,
        maintenanceQty: Int? = null,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val user = _currentUser.value
        if (user?.role != "owner") {
            onError("Only owner can update pricing")
            return
        }
        viewModelScope.launch {
            try {
                val existing = repository.getMachineById(machineId) ?: return@launch
                val rentedOut = (existing.totalQty - existing.availableQty - existing.maintenanceQty).coerceAtLeast(0)
                val safeTotal = totalQty.coerceAtLeast(1)
                val targetMaint = (maintenanceQty ?: existing.maintenanceQty).coerceIn(0, (safeTotal - rentedOut).coerceAtLeast(0))
                val newAvailable = (safeTotal - rentedOut - targetMaint).coerceIn(0, safeTotal)
                val updated = existing.copy(
                    currentRate = currentRate,
                    totalQty = safeTotal,
                    availableQty = newAvailable,
                    maintenanceQty = targetMaint
                )
                repository.updateMachine(updated)
                syncService.pushMachine(updated)
                onSuccess()
            } catch (e: Exception) {
                onError(e.message ?: "Failed to update machine")
            }
        }
    }

    fun updateMachineMaintenance(
        machineId: String,
        newMaintenanceQty: Int,
        onSuccess: (String) -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                val existing = repository.getMachineById(machineId) ?: return@launch
                val rentedOut = (existing.totalQty - existing.availableQty - existing.maintenanceQty).coerceAtLeast(0)
                val maxPossibleMaint = (existing.totalQty - rentedOut).coerceAtLeast(0)
                val safeMaint = newMaintenanceQty.coerceIn(0, maxPossibleMaint)
                val newAvailable = (existing.totalQty - rentedOut - safeMaint).coerceIn(0, existing.totalQty)
                val updated = existing.copy(
                    availableQty = newAvailable,
                    maintenanceQty = safeMaint
                )
                repository.updateMachine(updated)
                syncService.pushMachine(updated)
                val msg = when {
                    safeMaint == 0 -> "${existing.name} is back in stock ($newAvailable/${existing.totalQty} available)"
                    newAvailable == 0 -> "${existing.name} marked Out of Stock ($safeMaint in maintenance)"
                    else -> "${existing.name}: $safeMaint unit(s) in maintenance ($newAvailable/${existing.totalQty} available)"
                }
                onSuccess(msg)
            } catch (e: Exception) {
                onError(e.message ?: "Failed to update maintenance status")
            }
        }
    }

    fun deleteMachine(
        machineId: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                repository.deleteMachine(machineId)
                syncService.deleteMachineFromCloud(machineId)
                onSuccess()
            } catch (e: Exception) {
                onError(e.message ?: "Failed to delete machine")
            }
        }
    }

    fun deleteAllMachines(
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                repository.deleteAllMachines()
                syncService.deleteAllMachinesFromCloud()
                onSuccess()
            } catch (e: Exception) {
                onError(e.message ?: "Failed to delete all machines")
            }
        }
    }

    fun addCategory(
        name: String,
        onSuccess: (MachineCategoryEntity) -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        val cleanName = name.trim()
        if (cleanName.isEmpty()) {
            onError("Please enter a category name")
            return
        }
        viewModelScope.launch {
            try {
                val category = MachineCategoryEntity(
                    id = "cat_" + System.currentTimeMillis(),
                    name = cleanName,
                    iconName = "build"
                )
                repository.insertCategory(category)
                syncService.pushCategory(category)
                onSuccess(category)
            } catch (e: Exception) {
                onError(e.message ?: "Failed to add category")
            }
        }
    }

    fun deleteCategory(
        categoryId: String,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                val machinesInCategory = machines.value.filter { it.categoryId == categoryId }
                repository.deleteCategory(categoryId)
                syncService.deleteCategoryFromCloud(categoryId)
                machinesInCategory.forEach { m ->
                    syncService.deleteMachineFromCloud(m.id)
                }
                onSuccess()
            } catch (e: Exception) {
                onError(e.message ?: "Failed to delete category")
            }
        }
    }

    // --- Staff Management (Owner Only) ---
    fun addWorker(
        name: String,
        phoneOrUsername: String,
        pin: String,
        maxDiscount: Double,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val user = _currentUser.value
        if (user?.role != "owner") {
            onError("Only owner can manage staff")
            return
        }
        viewModelScope.launch {
            try {
                val cleanIdentifier = phoneOrUsername.trim().lowercase()
                val newWorker = UserEntity(
                    id = "user_worker_" + System.currentTimeMillis(),
                    name = name.trim(),
                    phoneOrUsername = cleanIdentifier,
                    pin = pin.trim(),
                    role = "worker",
                    isActive = true,
                    maxDiscountAmount = maxDiscount
                )
                repository.insertUser(newWorker)
                syncService.saveStaffProfile(newWorker)
                onSuccess()
            } catch (e: Exception) {
                onError(e.message ?: "Failed to add worker")
            }
        }
    }

    fun toggleWorkerStatus(worker: UserEntity) {
        if (_currentUser.value?.role != "owner") return
        viewModelScope.launch {
            val updated = worker.copy(
                phoneOrUsername = worker.phoneOrUsername.trim().lowercase(),
                isActive = !worker.isActive
            )
            repository.updateUser(updated)
            syncService.saveStaffProfile(updated)
        }
    }

    fun updateWorkerDiscountFloor(worker: UserEntity, maxDiscountAmount: Double) {
        if (_currentUser.value?.role != "owner") return
        viewModelScope.launch {
            val updated = worker.copy(
                phoneOrUsername = worker.phoneOrUsername.trim().lowercase(),
                maxDiscountAmount = maxDiscountAmount
            )
            repository.updateUser(updated)
            syncService.saveStaffProfile(updated)
        }
    }

    // --- Owner Security & Registered Mobile / PIN ---
    fun updateOwnerRegisteredPhone(
        owner: UserEntity,
        newPhone: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val cleanPhone = newPhone.trim().replace(" ", "").replace("-", "")
        if (cleanPhone.length < 10) {
            onError("Please enter a valid 10-digit mobile number")
            return
        }
        viewModelScope.launch {
            try {
                repository.updateOwnerPhone(owner.id, cleanPhone)
                if (_currentUser.value?.id == owner.id) {
                    _currentUser.value = _currentUser.value?.copy(registeredPhone = cleanPhone)
                }
                syncService.saveStaffProfile(owner.copy(registeredPhone = cleanPhone))
                onSuccess()
            } catch (e: Exception) {
                onError(e.message ?: "Failed to update registered phone")
            }
        }
    }

    fun changeOwnerPin(
        owner: UserEntity,
        newPin: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val cleanPin = newPin.trim()
        if (cleanPin.length != 4 || !cleanPin.all { it.isDigit() }) {
            onError("PIN must be exactly 4 numeric digits")
            return
        }
        viewModelScope.launch {
            try {
                repository.updateOwnerPin(owner.id, cleanPin)
                if (_currentUser.value?.id == owner.id) {
                    _currentUser.value = _currentUser.value?.copy(pin = cleanPin)
                }
                syncService.saveStaffProfile(owner.copy(pin = cleanPin))
                onSuccess()
            } catch (e: Exception) {
                onError(e.message ?: "Failed to update owner PIN")
            }
        }
    }

    // --- CSV Export (Owner Only) ---
    fun exportReport(context: Context, customList: List<JobSheetEntity>? = null) {
        if (_currentUser.value?.role != "owner") return
        val list = customList ?: allJobSheets.value
        val uri = repository.exportReportCsv(context, list)
        if (uri != null) {
            try {
                val clip = android.content.ClipData.newUri(context.contentResolver, "BABA Rental Report CSV", uri)
                val sendIntent: Intent = Intent().apply {
                    action = Intent.ACTION_SEND
                    putExtra(Intent.EXTRA_STREAM, uri)
                    clipData = clip
                    type = "text/csv"
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                val shareIntent = Intent.createChooser(sendIntent, "Export BABA Rental Report").apply {
                    clipData = clip
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(shareIntent)
            } catch (e: Exception) {
                android.widget.Toast.makeText(
                    context,
                    "Could not open CSV share menu: ${e.localizedMessage ?: "No compatible app"}",
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    // --- PDF Export (Owner Only) ---
    fun exportReportPdf(context: Context) {
        if (_currentUser.value?.role != "owner") return
        val list = allJobSheets.value
        val payList = payments.value
        val name = _currentUser.value?.name ?: "Owner"
        com.example.util.PdfGenerator.shareReportPdf(context, list, payList, name)
    }

    fun viewReportPdf(context: Context) {
        if (_currentUser.value?.role != "owner") return
        val list = allJobSheets.value
        val payList = payments.value
        val name = _currentUser.value?.name ?: "Owner"
        com.example.util.PdfGenerator.viewReportPdf(context, list, payList, name)
    }

    // --- Daily Cash Management (Opening & Closing Cash) ---
    val dailyCashList: StateFlow<List<com.example.data.model.DailyCashEntity>> = repository.getAllDailyCashFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun saveOpeningCash(dateStr: String, openingAmount: Double, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val user = _currentUser.value ?: return
        viewModelScope.launch {
            try {
                repository.saveOpeningCash(dateStr, openingAmount, user)
                viewModelScope.launch(Dispatchers.IO) {
                    db.dailyCashDao().getDailyCash(dateStr)?.let { syncService.pushDailyCash(it) }
                    syncService.pushActivityLogsForJob("DAILY_CASH")
                }
                onSuccess()
            } catch (e: Exception) {
                onError(e.message ?: "Failed to save opening cash")
            }
        }
    }

    fun saveClosingCash(dateStr: String, closingAmount: Double, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val user = _currentUser.value ?: return
        viewModelScope.launch {
            try {
                repository.saveClosingCash(dateStr, closingAmount, user)
                viewModelScope.launch(Dispatchers.IO) {
                    db.dailyCashDao().getDailyCash(dateStr)?.let { syncService.pushDailyCash(it) }
                    syncService.pushActivityLogsForJob("DAILY_CASH")
                }
                onSuccess()
            } catch (e: Exception) {
                onError(e.message ?: "Failed to save closing cash")
            }
        }
    }

    suspend fun getOpeningCashForDate(dateStr: String): Double {
        return repository.getOpeningCashForDate(dateStr)
    }

    // --- Expenses Module ---
    fun addExpense(
        date: String,
        timestamp: Long,
        expenseType: String,
        amount: Double,
        remarks: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val user = _currentUser.value
        if (user == null) {
            onError("Please log in first")
            return
        }
        if (expenseType.isBlank()) {
            onError("Please enter Expense Type / Description")
            return
        }
        if (amount <= 0) {
            onError("Please enter a valid expense amount greater than 0")
            return
        }
        viewModelScope.launch {
            try {
                val created = repository.addExpense(
                    date = date,
                    timestamp = timestamp,
                    expenseType = expenseType,
                    amount = amount,
                    remarks = remarks,
                    user = user
                )
                viewModelScope.launch(Dispatchers.IO) {
                    syncService.pushExpense(created)
                    syncService.pushActivityLogsForJob("EXPENSE")
                }
                onSuccess()
            } catch (e: Exception) {
                onError(e.message ?: "Failed to save expense")
            }
        }
    }

    fun deleteExpense(
        expenseId: String,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                repository.deleteExpense(expenseId)
                viewModelScope.launch(Dispatchers.IO) {
                    syncService.deleteExpenseFromCloud(expenseId)
                }
                onSuccess()
            } catch (e: Exception) {
                onError(e.message ?: "Failed to delete expense")
            }
        }
    }

    private val _isManualSyncing = MutableStateFlow(false)
    val isManualSyncing: StateFlow<Boolean> = _isManualSyncing.asStateFlow()

    private val _manualSyncStatus = MutableStateFlow<String?>(null)
    val manualSyncStatus: StateFlow<String?> = _manualSyncStatus.asStateFlow()

    private suspend fun ensureFirebaseSignInForSync(context: Context): Boolean {
        val existingAuth = try { com.google.firebase.auth.FirebaseAuth.getInstance().currentUser } catch (_: Exception) { null }
        if (existingAuth != null) return true

        return try {
            var ctx: Context? = context
            var act: android.app.Activity? = null
            while (ctx != null) {
                if (ctx is android.app.Activity) {
                    act = ctx
                    break
                }
                ctx = (ctx as? android.content.ContextWrapper)?.baseContext
            }
            val targetActivity = act ?: return false
            val webClientId = context.getString(com.example.R.string.default_web_client_id)
            val googleIdOption = com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption.Builder(webClientId).build()
            val request = androidx.credentials.GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()
            val credentialManager = androidx.credentials.CredentialManager.create(context)
            val result = kotlinx.coroutines.withContext(Dispatchers.Main) {
                credentialManager.getCredential(targetActivity, request)
            }
            val credential = result.credential
            if (credential is androidx.credentials.CustomCredential &&
                credential.type == com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val googleIdToken = com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.createFrom(credential.data).idToken
                val authCredential = com.google.firebase.auth.GoogleAuthProvider.getCredential(googleIdToken, null)
                val authResult = com.google.firebase.auth.FirebaseAuth.getInstance().signInWithCredential(authCredential).let { task ->
                    kotlinx.coroutines.suspendCancellableCoroutine { cont ->
                        task.addOnSuccessListener { res -> cont.resume(res, null) }
                        task.addOnFailureListener { err -> cont.cancel(err) }
                    }
                }
                val fbUser = authResult.user
                if (fbUser != null) {
                    val current = _currentUser.value
                    if (current != null) {
                        val linked = syncService.linkAuthenticatedStaffSession(current)
                        _currentUser.value = linked
                    }
                    true
                } else {
                    false
                }
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    fun syncToOtherDevices(
        context: Context,
        onSuccess: (String) -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        if (_isManualSyncing.value) return
        viewModelScope.launch {
            _isManualSyncing.value = true
            _manualSyncStatus.value = "Connecting to Cloud..."
            val signedIn = ensureFirebaseSignInForSync(context)
            if (!signedIn) {
                _isManualSyncing.value = false
                val msg = "Please sign in with your Google account to sync data with other devices."
                _manualSyncStatus.value = msg
                onError(msg)
                return@launch
            }
            val result = kotlinx.coroutines.withContext(Dispatchers.IO) {
                syncService.syncToOtherDevicesNow(_currentUser.value) { step ->
                    _manualSyncStatus.value = step
                }
            }
            _isManualSyncing.value = false
            result.onSuccess { summary ->
                _manualSyncStatus.value = summary
                onSuccess(summary)
            }.onFailure { err ->
                val msg = err.message ?: "Sync failed"
                _manualSyncStatus.value = msg
                onError(msg)
            }
        }
    }

    fun clearManualSyncStatus() {
        _manualSyncStatus.value = null
    }

    // --- STEP 3: One-Time Tablet Data Backup & Cloud Migration (Owner Only) ---
    fun createBackupNowBeforeMigration(
        context: Context,
        onSuccess: (File) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val zipFile = BackupManager.createBackupBundle(context, db)
                _hasTakenPreMigrationBackup.value = true
                _backupsList.value = BackupManager.getBackupsList(context)
                onSuccess(zipFile)
            } catch (e: Exception) {
                onError(e.message ?: "Backup failed")
            }
        }
    }

    fun runOneTimeTabletMigration(context: Context? = null) {
        if (_currentUser.value?.role != "owner") return
        viewModelScope.launch {
            _migrationReport.value = null
            _migrationProgress.value = "Starting migration of existing tablet data..."
            if (context != null) {
                ensureFirebaseSignInForSync(context)
            }
            val report = kotlinx.coroutines.withContext(Dispatchers.IO) {
                syncService.migrateExistingTabletData { step ->
                    _migrationProgress.value = step
                }
            }
            _migrationProgress.value = null
            _migrationReport.value = report
        }
    }

    fun clearMigrationReport() {
        _migrationReport.value = null
    }

    // --- App Control: Total App Data Backup (ZIP) & Restore ---
    private val _backupsList = MutableStateFlow<List<BackupFileInfo>>(emptyList())
    val backupsList: StateFlow<List<BackupFileInfo>> = _backupsList.asStateFlow()

    private val _isBackupRestoreBusy = MutableStateFlow(false)
    val isBackupRestoreBusy: StateFlow<Boolean> = _isBackupRestoreBusy.asStateFlow()

    private val _backupRestoreStatus = MutableStateFlow<String?>(null)
    val backupRestoreStatus: StateFlow<String?> = _backupRestoreStatus.asStateFlow()

    fun refreshBackupsList(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            _backupsList.value = BackupManager.getBackupsList(context)
        }
    }

    fun clearBackupRestoreStatus() {
        _backupRestoreStatus.value = null
    }

    fun createFullBackupZip(
        context: Context,
        onSuccess: (File) -> Unit,
        onError: (String) -> Unit
    ) {
        if (_isBackupRestoreBusy.value) return
        viewModelScope.launch {
            _isBackupRestoreBusy.value = true
            _backupRestoreStatus.value = "Creating full ZIP backup of all app data & photos..."
            try {
                val zipFile = BackupManager.createBackupBundle(context, db)
                _hasTakenPreMigrationBackup.value = true
                _backupsList.value = BackupManager.getBackupsList(context)
                _isBackupRestoreBusy.value = false
                _backupRestoreStatus.value = "Backup created: ${zipFile.name}"
                onSuccess(zipFile)
            } catch (e: Exception) {
                _isBackupRestoreBusy.value = false
                val msg = e.message ?: "Backup failed"
                _backupRestoreStatus.value = "Backup Error: $msg"
                onError(msg)
            }
        }
    }

    fun saveBackupZipToUri(
        context: Context,
        sourceZip: File,
        destUri: Uri,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val saved = BackupManager.copyBackupZipToUri(context, sourceZip, destUri)
                if (saved) {
                    _backupRestoreStatus.value = "Saved ${sourceZip.name} to device storage."
                    onSuccess()
                } else {
                    onError("Could not write backup ZIP to selected location.")
                }
            } catch (e: Exception) {
                onError(e.message ?: "Failed to save backup ZIP")
            }
        }
    }

    fun restoreFromSavedBackupZip(
        context: Context,
        zipFile: File,
        onSuccess: (Int) -> Unit,
        onError: (String) -> Unit
    ) {
        if (_isBackupRestoreBusy.value) return
        viewModelScope.launch {
            _isBackupRestoreBusy.value = true
            _backupRestoreStatus.value = "Restoring app data & photos from ${zipFile.name}..."
            try {
                val restoredCount = BackupManager.restoreFromBackupZip(context, db, zipFile)
                com.example.util.DueAlarmScheduler.rescheduleAllActiveAlarms(context, db)
                viewModelScope.launch(Dispatchers.IO) {
                    syncService.runAutomaticBackgroundSyncCycle()
                }
                _backupsList.value = BackupManager.getBackupsList(context)
                _isBackupRestoreBusy.value = false
                _backupRestoreStatus.value = "Restore complete! $restoredCount new record(s) & local photos restored from ${zipFile.name}."
                onSuccess(restoredCount)
            } catch (e: Exception) {
                _isBackupRestoreBusy.value = false
                val msg = e.message ?: "Restore failed"
                _backupRestoreStatus.value = "Restore Error: $msg"
                onError(msg)
            }
        }
    }

    fun restoreFromExternalBackupUri(
        context: Context,
        uri: Uri,
        onSuccess: (Int) -> Unit,
        onError: (String) -> Unit
    ) {
        if (_isBackupRestoreBusy.value) return
        viewModelScope.launch {
            _isBackupRestoreBusy.value = true
            _backupRestoreStatus.value = "Restoring total app data & photos from selected ZIP file..."
            try {
                val restoredCount = BackupManager.restoreFromBackupUri(context, db, uri)
                com.example.util.DueAlarmScheduler.rescheduleAllActiveAlarms(context, db)
                viewModelScope.launch(Dispatchers.IO) {
                    syncService.runAutomaticBackgroundSyncCycle()
                }
                _backupsList.value = BackupManager.getBackupsList(context)
                _isBackupRestoreBusy.value = false
                _backupRestoreStatus.value = "Restore complete! $restoredCount new record(s) & local photos restored from ZIP."
                onSuccess(restoredCount)
            } catch (e: Exception) {
                _isBackupRestoreBusy.value = false
                val msg = e.message ?: "Restore failed"
                _backupRestoreStatus.value = "Restore Error: $msg"
                onError(msg)
            }
        }
    }
}
