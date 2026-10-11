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
import com.example.data.firebase.FirestoreSyncService
import java.io.File
import com.example.data.model.ActivityLogEntity
import com.example.data.model.CustomerEntity
import com.example.data.model.JobSheetEntity
import com.example.data.model.MachineCategoryEntity
import com.example.data.model.MachineEntity
import com.example.data.model.PaymentEntity
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

    // Current Logged In User
    private val _currentUser = MutableStateFlow<UserEntity?>(null)
    val currentUser: StateFlow<UserEntity?> = _currentUser.asStateFlow()

    // Owner Push / Alert Notification banner
    private val _ownerAlert = MutableStateFlow<OwnerAlertNotification?>(null)
    val ownerAlert: StateFlow<OwnerAlertNotification?> = _ownerAlert.asStateFlow()

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

        val todayRevenue = allPaymentsList.filter { it.timestamp >= startOfToday && it.type != "refund" }
            .sumOf { it.amount } - allPaymentsList.filter { it.timestamp >= startOfToday && it.type == "refund" }
            .sumOf { it.amount }

        val monthlyRevenue = allPaymentsList.filter { it.timestamp >= startOfMonth && it.type != "refund" }
            .sumOf { it.amount } - allPaymentsList.filter { it.timestamp >= startOfMonth && it.type == "refund" }
            .sumOf { it.amount }

        val totalCustomerDues = nonDeletedJobs.filter { it.finalSettlement > 0 }.sumOf { it.finalSettlement }

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
            staffPerformance = staffStats
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardStats())

    init {
        viewModelScope.launch(Dispatchers.IO) {
            AppDatabase.ensureSeedData(db)
            syncService.startRealtimeListeners()
            // Weekly automated backup (Data + Photos bundled together)
            BackupManager.checkAndRunWeeklyBackup(application, db)
        }

        // Automatically monitor activity logs to trigger Owner Notifications if worker edited or deleted a job sheet
        viewModelScope.launch {
            repository.activityLogs.collect { logs ->
                val latest = logs.firstOrNull()
                if (latest != null && (latest.action == "edited" || latest.action == "deleted")) {
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
    }

    // --- Authentication ---
    fun login(user: UserEntity, enteredPin: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        if (!user.isActive) {
            onError("Account is deactivated. Please contact owner.")
            return
        }
        if (user.pin.isEmpty() || user.pin == enteredPin) {
            _currentUser.value = user
            viewModelScope.launch(Dispatchers.IO) {
                if (repository.getUserById(user.id) == null) {
                    repository.insertUser(user)
                }
            }
            onSuccess()
        } else {
            onError("Incorrect PIN. Please try again.")
        }
    }

    fun directLoginAs(user: UserEntity) {
        _currentUser.value = user
        viewModelScope.launch(Dispatchers.IO) {
            if (repository.getUserById(user.id) == null) {
                repository.insertUser(user)
            }
        }
    }

    fun logout() {
        _currentUser.value = null
    }

    // --- Customer Lookup ---
    suspend fun findCustomerByPhone(phone: String): CustomerEntity? {
        return repository.getCustomerByPhone(phone)
    }

    // --- Job Sheet Actions ---
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
        val user = _currentUser.value
        if (user == null) {
            onError("Please log in first")
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

        if (machine.availableQty < quantity) {
            onError("Only ${machine.availableQty} units available in stock.")
            return
        }

        viewModelScope.launch {
            try {
                val created = repository.createJobSheet(
                    customer = customer,
                    machine = machine,
                    categoryName = categoryName,
                    quantity = quantity,
                    rateUsed = rateUsed,
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
                    context = context
                )
                onSuccess(created)
            } catch (e: Exception) {
                onError(e.message ?: "Failed to create job sheet")
            }
        }
    }

    fun calculateReturnBill(jobSheet: JobSheetEntity): ReturnCalculation {
        return repository.calculateReturnBill(jobSheet)
    }

    fun returnJob(
        jobSheetId: String,
        settlementMethod: String,
        remark: String = "",
        context: Context? = null,
        onSuccess: (JobSheetEntity) -> Unit,
        onError: (String) -> Unit
    ) {
        val user = _currentUser.value ?: return
        viewModelScope.launch {
            try {
                val updated = repository.markReturned(
                    jobSheetId = jobSheetId,
                    settlementMethod = settlementMethod,
                    currentUser = user,
                    remark = remark,
                    context = context
                )
                if (updated != null) {
                    onSuccess(updated)
                } else {
                    onError("Job sheet not found")
                }
            } catch (e: Exception) {
                onError(e.message ?: "Failed to return job")
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
                if (ok) onSuccess() else onError("Failed to add payment")
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
                    id = "mach_" + System.currentTimeMillis(),
                    categoryId = categoryId,
                    name = name,
                    baseRate = baseRate,
                    currentRate = baseRate,
                    availableQty = totalQty,
                    totalQty = totalQty,
                    modelOrSpecs = modelOrSpecs
                )
                repository.insertMachine(machine)
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
                val diffQty = totalQty - existing.totalQty
                val newAvailable = (existing.availableQty + diffQty).coerceAtLeast(0)
                val updated = existing.copy(
                    currentRate = currentRate,
                    totalQty = totalQty,
                    availableQty = newAvailable
                )
                repository.updateMachine(updated)
                onSuccess()
            } catch (e: Exception) {
                onError(e.message ?: "Failed to update machine")
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
                val newWorker = UserEntity(
                    id = "user_worker_" + System.currentTimeMillis(),
                    name = name,
                    phoneOrUsername = phoneOrUsername,
                    pin = pin,
                    role = "worker",
                    isActive = true,
                    maxDiscountAmount = maxDiscount
                )
                repository.insertUser(newWorker)
                onSuccess()
            } catch (e: Exception) {
                onError(e.message ?: "Failed to add worker")
            }
        }
    }

    fun toggleWorkerStatus(worker: UserEntity) {
        if (_currentUser.value?.role != "owner") return
        viewModelScope.launch {
            repository.updateUser(worker.copy(isActive = !worker.isActive))
        }
    }

    fun updateWorkerDiscountFloor(worker: UserEntity, maxDiscountAmount: Double) {
        if (_currentUser.value?.role != "owner") return
        viewModelScope.launch {
            repository.updateUser(worker.copy(maxDiscountAmount = maxDiscountAmount))
        }
    }

    // --- CSV Export (Owner Only) ---
    fun exportReport(context: Context) {
        if (_currentUser.value?.role != "owner") return
        val list = allJobSheets.value
        val uri = repository.exportReportCsv(context, list)
        if (uri != null) {
            val sendIntent: Intent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_STREAM, uri)
                type = "text/csv"
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val shareIntent = Intent.createChooser(sendIntent, "Export BABA Rental Report")
            shareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(shareIntent)
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
                onSuccess()
            } catch (e: Exception) {
                onError(e.message ?: "Failed to save closing cash")
            }
        }
    }

    suspend fun getOpeningCashForDate(dateStr: String): Double {
        return repository.getOpeningCashForDate(dateStr)
    }
}
