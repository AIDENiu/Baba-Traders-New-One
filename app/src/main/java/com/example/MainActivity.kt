package com.example

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.JobSheetEntity
import com.example.data.repository.ReturnCalculation
import com.example.ui.components.AddExtraPaymentDialog
import com.example.ui.components.PaymentHistoryDialog
import com.example.ui.components.ReceiptDialog
import com.example.ui.components.ReturnJobDialog
import com.example.ui.screens.*
import com.example.ui.theme.BabaJobSheetTheme
import com.example.ui.viewmodel.BabaViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: BabaViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            BabaJobSheetTheme {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .imePadding(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    BabaAppContent(viewModel = viewModel)
                }
            }
        }
    }
}

@Composable
fun BabaAppContent(viewModel: BabaViewModel) {
    val context = LocalContext.current

    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val users by viewModel.users.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val machines by viewModel.machines.collectAsStateWithLifecycle()
    val allJobSheets by viewModel.allJobSheets.collectAsStateWithLifecycle()
    val activeJobSheets by viewModel.activeJobSheets.collectAsStateWithLifecycle()
    val customers by viewModel.customers.collectAsStateWithLifecycle()
    val activityLogs by viewModel.activityLogs.collectAsStateWithLifecycle()
    val payments by viewModel.payments.collectAsStateWithLifecycle()
    val dashboardStats by viewModel.dashboardStats.collectAsStateWithLifecycle()
    val alertNotification by viewModel.ownerAlert.collectAsStateWithLifecycle()

    // Navigation Screen State
    var currentScreen by remember { mutableStateOf("auth") }

    // Dialog States
    var jobToReturn by remember { mutableStateOf<JobSheetEntity?>(null) }
    var returnCalculation by remember { mutableStateOf<ReturnCalculation?>(null) }
    var jobToAddPayment by remember { mutableStateOf<JobSheetEntity?>(null) }
    var jobForPaymentHistory by remember { mutableStateOf<JobSheetEntity?>(null) }
    var jobForReceipt by remember { mutableStateOf<JobSheetEntity?>(null) }

    // Synchronize current screen with user role
    LaunchedEffect(currentUser) {
        val user = currentUser
        if (user == null) {
            currentScreen = "auth"
        } else if (currentScreen == "auth") {
            currentScreen = if (user.role == "owner") "owner_dashboard" else "worker_home"
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when (currentScreen) {
            "auth" -> {
                AuthScreen(
                    users = users,
                    onLoginSuccess = { user ->
                        viewModel.directLoginAs(user)
                        currentScreen = if (user.role == "owner") "owner_dashboard" else "worker_home"
                    }
                )
            }

            "worker_home" -> {
                val user = currentUser
                if (user != null) {
                    WorkerHomeScreen(
                        currentUser = user,
                        activeJobs = activeJobSheets,
                        onNewJobSheetClick = { currentScreen = "quick_job_sheet" },
                        onReturnJobClick = { job ->
                            jobToReturn = job
                            returnCalculation = viewModel.calculateReturnBill(job)
                        },
                        onAddPaymentClick = { job -> jobToAddPayment = job },
                        onViewPaymentHistoryClick = { job -> jobForPaymentHistory = job },
                        onViewReceiptClick = { job -> jobForReceipt = job },
                        onSwitchUser = {
                            viewModel.logout()
                            currentScreen = "auth"
                        }
                    )
                }
            }

            "owner_dashboard" -> {
                val user = currentUser
                if (user != null) {
                    OwnerDashboardScreen(
                        currentUser = user,
                        stats = dashboardStats,
                        activeJobs = activeJobSheets,
                        recentLogs = activityLogs,
                        alertNotification = alertNotification,
                        onDismissAlert = { viewModel.dismissAlert() },
                        onNavigateToNewJob = { currentScreen = "quick_job_sheet" },
                        onNavigateToActiveJobs = { currentScreen = "active_jobs" },
                        onNavigateToMachines = { currentScreen = "machines" },
                        onNavigateToCustomers = { currentScreen = "customers" },
                        onNavigateToReports = { currentScreen = "reports" },
                        onNavigateToStaff = { currentScreen = "manage_staff" },
                        onNavigateToActivityLog = { currentScreen = "activity_log" },
                        onReturnJobClick = { job ->
                            jobToReturn = job
                            returnCalculation = viewModel.calculateReturnBill(job)
                        },
                        onViewReceiptClick = { job -> jobForReceipt = job },
                        onSwitchUser = {
                            viewModel.logout()
                            currentScreen = "auth"
                        }
                    )
                }
            }

            "quick_job_sheet" -> {
                val user = currentUser
                if (user != null) {
                    QuickJobSheetScreen(
                        currentUser = user,
                        staffList = users,
                        categories = categories,
                        machines = machines,
                        onFindCustomerByPhone = { phone -> viewModel.findCustomerByPhone(phone) },
                        onCreateJobSheet = { customer, machine, categoryName, qty, rate, discount, deposit, method, cycle, checkoutUser, handledUser, notes, personPhoto, idPhoto, machinePhoto, dueTime, onSuccess, onError ->
                            viewModel.createJobSheet(
                                customer = customer,
                                machine = machine,
                                categoryName = categoryName,
                                quantity = qty,
                                rateUsed = rate,
                                discount = discount,
                                depositAmount = deposit,
                                depositMethod = method,
                                billingCycle = cycle,
                                checkoutUser = checkoutUser,
                                handledUser = handledUser,
                                notes = notes,
                                personPhotoPath = personPhoto,
                                idPhotoPath = idPhoto,
                                machinePhotoPath = machinePhoto,
                                explicitDueTime = dueTime,
                                context = context,
                                onSuccess = onSuccess,
                                onError = onError
                            )
                        },
                        onNavigateBack = {
                            currentScreen = if (user.role == "owner") "owner_dashboard" else "worker_home"
                        }
                    )
                }
            }

            "active_jobs" -> {
                val user = currentUser
                if (user != null) {
                    ActiveJobsScreen(
                        currentUser = user,
                        allJobSheets = allJobSheets,
                        onReturnJob = { job ->
                            jobToReturn = job
                            returnCalculation = viewModel.calculateReturnBill(job)
                        },
                        onAddPayment = { job -> jobToAddPayment = job },
                        onViewPaymentHistory = { job -> jobForPaymentHistory = job },
                        onViewReceipt = { job -> jobForReceipt = job },
                        onNavigateBack = {
                            currentScreen = if (user.role == "owner") "owner_dashboard" else "worker_home"
                        }
                    )
                }
            }

            "machines" -> {
                val user = currentUser
                if (user != null) {
                    MachinesScreen(
                        currentUser = user,
                        categories = categories,
                        machines = machines,
                        onAddMachine = { catId, name, rate, qty, specs ->
                            viewModel.addMachine(
                                categoryId = catId,
                                name = name,
                                baseRate = rate,
                                totalQty = qty,
                                modelOrSpecs = specs,
                                onSuccess = {
                                    Toast.makeText(context, "Machine added to inventory", Toast.LENGTH_SHORT).show()
                                },
                                onError = { err ->
                                    Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                                }
                            )
                        },
                        onUpdatePricing = { machineId, currentRate, totalQty ->
                            viewModel.updateMachinePricing(
                                machineId = machineId,
                                currentRate = currentRate,
                                totalQty = totalQty,
                                onSuccess = {
                                    Toast.makeText(context, "Pricing updated successfully", Toast.LENGTH_SHORT).show()
                                },
                                onError = { err ->
                                    Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                                }
                            )
                        },
                        onNavigateBack = {
                            currentScreen = if (user.role == "owner") "owner_dashboard" else "worker_home"
                        }
                    )
                }
            }

            "customers" -> {
                val user = currentUser
                if (user != null) {
                    CustomersScreen(
                        customers = customers,
                        allJobSheets = allJobSheets,
                        onNavigateBack = {
                            currentScreen = if (user.role == "owner") "owner_dashboard" else "worker_home"
                        }
                    )
                }
            }

            "reports" -> {
                val user = currentUser
                if (user != null && user.role == "owner") {
                    ReportsScreen(
                        currentUser = user,
                        allJobSheets = allJobSheets,
                        payments = payments,
                        onExportCsv = {
                            viewModel.exportReport(context)
                        },
                        onNavigateBack = {
                            currentScreen = "owner_dashboard"
                        }
                    )
                } else {
                    currentScreen = "worker_home"
                }
            }

            "manage_staff" -> {
                val user = currentUser
                if (user != null) {
                    ManageStaffScreen(
                        currentUser = user,
                        users = users,
                        onAddWorker = { name, username, pin, maxDiscount ->
                            viewModel.addWorker(
                                name = name,
                                phoneOrUsername = username,
                                pin = pin,
                                maxDiscount = maxDiscount,
                                onSuccess = {
                                    Toast.makeText(context, "Worker login created", Toast.LENGTH_SHORT).show()
                                },
                                onError = { err ->
                                    Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                                }
                            )
                        },
                        onToggleWorkerStatus = { worker ->
                            viewModel.toggleWorkerStatus(worker)
                        },
                        onUpdateDiscountFloor = { worker, amount ->
                            viewModel.updateWorkerDiscountFloor(worker, amount)
                        },
                        onNavigateBack = {
                            currentScreen = "owner_dashboard"
                        }
                    )
                }
            }

            "activity_log" -> {
                ActivityLogScreen(
                    logs = activityLogs,
                    onNavigateBack = {
                        currentScreen = "owner_dashboard"
                    }
                )
            }
        }

        // Shared Return Dialog
        if (jobToReturn != null && returnCalculation != null) {
            ReturnJobDialog(
                jobSheet = jobToReturn!!,
                calculation = returnCalculation!!,
                onConfirmReturn = { paymentMethod, remark ->
                    viewModel.returnJob(
                        jobSheetId = jobToReturn!!.id,
                        settlementMethod = paymentMethod,
                        remark = remark,
                        context = context,
                        onSuccess = { updated ->
                            jobToReturn = null
                            returnCalculation = null
                            jobForReceipt = updated
                            Toast.makeText(context, "Job closed & machinery restored to stock", Toast.LENGTH_SHORT).show()
                        },
                        onError = { err ->
                            Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                        }
                    )
                },
                onDismiss = {
                    jobToReturn = null
                    returnCalculation = null
                }
            )
        }

        // Shared Add Extra Payment Dialog
        if (jobToAddPayment != null && currentUser != null) {
            AddExtraPaymentDialog(
                jobSheet = jobToAddPayment!!,
                currentUser = currentUser!!,
                onConfirmPayment = { amount, method, remark ->
                    viewModel.addExtraPayment(
                        jobSheetId = jobToAddPayment!!.id,
                        amount = amount,
                        paymentMode = method,
                        remark = remark,
                        onSuccess = {
                            jobToAddPayment = null
                            Toast.makeText(context, "Extra payment recorded successfully", Toast.LENGTH_SHORT).show()
                        },
                        onError = { err ->
                            Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                        }
                    )
                },
                onDismiss = { jobToAddPayment = null }
            )
        }

        // Shared Payment History Dialog
        if (jobForPaymentHistory != null) {
            val jobPayments = remember(payments, jobForPaymentHistory) {
                payments.filter { it.jobSheetId == jobForPaymentHistory!!.id }
            }
            PaymentHistoryDialog(
                jobSheet = jobForPaymentHistory!!,
                payments = jobPayments,
                onDismiss = { jobForPaymentHistory = null }
            )
        }

        // Shared Receipt Dialog
        if (jobForReceipt != null) {
            ReceiptDialog(
                jobSheet = jobForReceipt!!,
                onDismiss = { jobForReceipt = null }
            )
        }
    }
}
