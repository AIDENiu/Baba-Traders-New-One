package com.example.data.repository

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.data.AppDatabase
import com.example.data.model.ActivityLogEntity
import com.example.data.model.CustomerEntity
import com.example.data.model.DailyCashEntity
import com.example.data.model.JobSheetEntity
import com.example.data.model.MachineCategoryEntity
import com.example.data.model.MachineEntity
import com.example.data.model.PaymentEntity
import com.example.data.model.SelectedMachineItem
import com.example.data.model.UserEntity
import kotlinx.coroutines.flow.Flow
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.ceil
import kotlin.math.max

data class ReturnCalculation(
    val baseDays: Int = 1,
    val daysLate: Int = 0,
    val baseRentalFee: Double,
    val lateFee: Double = 0.0,
    val discount: Double,
    val depositAmount: Double,
    val netTotalFee: Double,
    val balanceDueOrRefund: Double, // > 0: customer owes balance; < 0: shop refunds customer; == 0: balanced
    val daysUsed: Int = baseDays + daysLate,
    val returningQuantity: Int = 1,
    val dailyRate: Double = 0.0
)

class BabaRepository(private val db: AppDatabase) {

    companion object {
        fun calculateDaysUsed(checkoutTime: Long, returnTime: Long = System.currentTimeMillis()): Int {
            val totalMsPerDay = 24 * 3600 * 1000.0
            val durationMs = maxOf(0L, returnTime - checkoutTime)
            return ceil(durationMs.toDouble() / totalMsPerDay).toInt().coerceAtLeast(1)
        }
    }

    // --- Users ---
    val allUsers: Flow<List<UserEntity>> = db.userDao().getAllUsers()
    val activeUsers: Flow<List<UserEntity>> = db.userDao().getActiveUsers()

    suspend fun getUserById(id: String) = db.userDao().getUserById(id)
    suspend fun insertUser(user: UserEntity) = db.userDao().insertUser(user)
    suspend fun updateUser(user: UserEntity) = db.userDao().updateUser(user)

    // --- Customers ---
    val allCustomers: Flow<List<CustomerEntity>> = db.customerDao().getAllCustomers()

    suspend fun getCustomerByPhone(phone: String) = db.customerDao().getCustomerByPhone(phone)
    suspend fun getCustomerById(id: String) = db.customerDao().getCustomerById(id)
    fun searchCustomers(query: String) = db.customerDao().searchCustomers(query)
    suspend fun insertCustomer(customer: CustomerEntity) = db.customerDao().insertCustomer(customer)
    suspend fun updateCustomer(customer: CustomerEntity) = db.customerDao().updateCustomer(customer)

    // --- Categories & Machines ---
    val allCategories: Flow<List<MachineCategoryEntity>> = db.machineCategoryDao().getAllCategories()
    val allMachines: Flow<List<MachineEntity>> = db.machineDao().getAllMachines()

    fun getMachinesByCategory(categoryId: String) = db.machineDao().getMachinesByCategory(categoryId)
    suspend fun getMachineById(id: String) = db.machineDao().getMachineById(id)
    suspend fun insertMachine(machine: MachineEntity) = db.machineDao().insertMachine(machine)
    suspend fun updateMachine(machine: MachineEntity) = db.machineDao().updateMachine(machine)

    // --- Job Sheets ---
    val allJobSheets: Flow<List<JobSheetEntity>> = db.jobSheetDao().getAllJobSheets()
    val activeJobSheets: Flow<List<JobSheetEntity>> = db.jobSheetDao().getActiveJobSheets()
    val overdueJobSheets: Flow<List<JobSheetEntity>> = db.jobSheetDao().getOverdueJobSheets()
    fun getOverdueJobSheets(now: Long = System.currentTimeMillis()) = db.jobSheetDao().getOverdueJobSheets()
    val returnedJobSheets: Flow<List<JobSheetEntity>> = db.jobSheetDao().getReturnedJobSheets()
    fun getJobSheetsForCustomer(customerId: String) = db.jobSheetDao().getJobSheetsForCustomer(customerId)

    // --- Activity Log & Payments ---
    val activityLogs: Flow<List<ActivityLogEntity>> = db.activityLogDao().getAllLogs()
    val allPayments: Flow<List<PaymentEntity>> = db.paymentDao().getAllPayments()

    suspend fun generateNextJobId(): String {
        val count = db.jobSheetDao().getTotalCount() + 413
        return String.format(Locale.getDefault(), "JOB-%04d", count)
    }

    // Billing Due Date calculation - Rolling 24-hour window from exact checkout time
    fun computeDueTime(checkoutTime: Long, billingCycle: String = "24hr"): Long {
        return checkoutTime + 24 * 3600 * 1000L
    }

    suspend fun createJobSheet(
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
        context: Context? = null
    ): JobSheetEntity {
        // 1. Ensure customer is persisted
        db.customerDao().insertCustomer(customer.copy(personPhotoPath = personPhotoPath ?: customer.personPhotoPath))

        // 2. Generate unique display ID
        val jobId = generateNextJobId()
        val now = System.currentTimeMillis()
        val dueTime = explicitDueTime ?: computeDueTime(now, billingCycle)

        val primaryMachineId = items.firstOrNull()?.machineId ?: ""
        val combinedMachineNames = items.joinToString(", ") { "${it.quantity}x ${it.machineName}" }
        val uniqueCategories = items.map { it.categoryName }.distinct()
        val combinedCategoryNames = if (uniqueCategories.size > 1) {
            "Multi-Category (${uniqueCategories.joinToString(", ")})"
        } else {
            uniqueCategories.firstOrNull() ?: "Hardware"
        }
        val totalQty = items.sumOf { it.quantity }
        val combinedRate = items.sumOf { it.quantity * it.rate }
        val itemsJsonStr = SelectedMachineItem.listToJson(items)

        val jobSheet = JobSheetEntity(
            id = jobId,
            customerId = customer.id,
            customerName = customer.name,
            customerPhone = customer.phone,
            customerAddress = customer.address,
            machineId = primaryMachineId,
            machineName = combinedMachineNames,
            categoryName = combinedCategoryNames,
            quantity = totalQty,
            initialQuantity = totalQty,
            returnedQuantity = 0,
            rateUsed = combinedRate,
            discount = discount,
            depositAmount = depositAmount,
            billingCycle = billingCycle,
            checkoutTime = now,
            dueTime = dueTime,
            status = "active",
            checkoutBy = checkoutUser.id,
            checkoutByName = checkoutUser.name,
            handledBy = handledUser.id,
            handledByName = handledUser.name,
            isDeleted = false,
            notes = notes,
            personPhotoPath = personPhotoPath,
            idPhotoPath = idPhotoPath,
            machinePhotoPath = machinePhotoPath,
            paymentMode = depositMethod,
            itemsJson = itemsJsonStr
        )

        // 3. Save job sheet and decrement stock for ALL selected items
        db.jobSheetDao().insertJobSheet(jobSheet)
        items.forEach { item ->
            db.machineDao().decrementStock(item.machineId, item.quantity)
        }

        // 4. Record advance received as the first payment entry with its mode of payment
        if (depositAmount > 0) {
            db.paymentDao().insertPayment(
                PaymentEntity(
                    id = UUID.randomUUID().toString(),
                    jobSheetId = jobId,
                    amount = depositAmount,
                    method = depositMethod,
                    type = "advance",
                    timestamp = now,
                    handledBy = checkoutUser.id,
                    handledByName = checkoutUser.name,
                    remark = "Advance received at checkout"
                )
            )
        }

        // 5. Activity log with checkout & handling accountability
        db.activityLogDao().insertLog(
            ActivityLogEntity(
                id = UUID.randomUUID().toString(),
                jobSheetId = jobId,
                action = "created",
                performedBy = checkoutUser.id,
                performedByName = checkoutUser.name,
                timestamp = now,
                changeSummary = "${checkoutUser.name} billed job sheet $jobId for ${customer.name}: $combinedMachineNames ($totalQty items, Rate: ₹$combinedRate, Advance: ₹$depositAmount via $depositMethod)"
            )
        )

        // 6. Schedule offline alarms for due date & overdue alert
        if (context != null) {
            com.example.util.DueAlarmScheduler.scheduleAlarms(context, jobSheet)
        }

        return jobSheet
    }

    suspend fun createJobSheet(
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
        context: Context? = null
    ): JobSheetEntity {
        val singleItem = SelectedMachineItem(
            machineId = machine.id,
            machineName = machine.name,
            categoryId = machine.categoryId,
            categoryName = categoryName,
            quantity = quantity,
            rate = rateUsed
        )
        return createJobSheet(
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
            context = context
        )
    }

    fun calculateDaysUsed(checkoutTime: Long, returnTime: Long = System.currentTimeMillis()): Int {
        val totalMsPerDay = 24 * 3600 * 1000.0
        val durationMs = maxOf(0L, returnTime - checkoutTime)
        return ceil(durationMs.toDouble() / totalMsPerDay).toInt().coerceAtLeast(1)
    }

    fun calculateReturnBill(
        jobSheet: JobSheetEntity,
        returningQuantity: Int = jobSheet.remainingActiveQty,
        manualDiscount: Double = 0.0,
        returnTime: Long = System.currentTimeMillis()
    ): ReturnCalculation {
        val daysUsed = calculateDaysUsed(jobSheet.checkoutTime, returnTime)
        val dailyRate = jobSheet.rateUsed
        val qty = returningQuantity.coerceAtLeast(1)

        // Formula: (Days Used × Daily Rate) - Initial Advance Deposit (and manual discount)
        val baseRentalFee = daysUsed * dailyRate * qty
        val initialDiscountProrated = if (jobSheet.effectiveInitialQty > 0) {
            (jobSheet.discount * qty) / jobSheet.effectiveInitialQty
        } else {
            jobSheet.discount
        }
        val totalDiscount = initialDiscountProrated + manualDiscount
        val netTotalFee = max(0.0, baseRentalFee - totalDiscount)

        val depositAllocated = if (jobSheet.remainingActiveQty > 0) {
            (jobSheet.depositAmount * qty) / jobSheet.remainingActiveQty
        } else {
            jobSheet.depositAmount
        }

        val balanceDueOrRefund = netTotalFee - depositAllocated

        return ReturnCalculation(
            baseDays = daysUsed,
            daysLate = maxOf(0, daysUsed - 1),
            baseRentalFee = baseRentalFee,
            lateFee = 0.0,
            discount = totalDiscount,
            depositAmount = depositAllocated,
            netTotalFee = netTotalFee,
            balanceDueOrRefund = balanceDueOrRefund,
            daysUsed = daysUsed,
            returningQuantity = qty,
            dailyRate = dailyRate
        )
    }

    suspend fun addExtraPayment(
        jobSheetId: String,
        amount: Double,
        paymentMode: String,
        currentUser: UserEntity,
        remark: String = ""
    ): Boolean {
        val jobSheet = db.jobSheetDao().getJobSheetById(jobSheetId) ?: return false
        val now = System.currentTimeMillis()

        // Insert payment entry
        db.paymentDao().insertPayment(
            PaymentEntity(
                id = UUID.randomUUID().toString(),
                jobSheetId = jobSheet.id,
                amount = amount,
                method = paymentMode,
                type = "extra",
                timestamp = now,
                handledBy = currentUser.id,
                handledByName = currentUser.name,
                remark = remark.ifEmpty { "Extra payment added" }
            )
        )

        // Update depositAmount to immediately reflect updated balance
        val updatedJob = jobSheet.copy(
            depositAmount = jobSheet.depositAmount + amount,
            finalSettlement = jobSheet.finalSettlement - amount
        )
        db.jobSheetDao().updateJobSheet(updatedJob)

        db.activityLogDao().insertLog(
            ActivityLogEntity(
                id = UUID.randomUUID().toString(),
                jobSheetId = jobSheet.id,
                action = "payment",
                performedBy = currentUser.id,
                performedByName = currentUser.name,
                timestamp = now,
                changeSummary = "${currentUser.name} received extra payment ₹$amount ($paymentMode) for job sheet ${jobSheet.id}. Remark: $remark"
            )
        )

        return true
    }

    suspend fun markReturned(
        jobSheetId: String,
        settlementMethod: String,
        currentUser: UserEntity,
        manualDiscount: Double = 0.0,
        returnTime: Long = System.currentTimeMillis(),
        remark: String = "",
        context: Context? = null
    ): JobSheetEntity? {
        val jobSheet = db.jobSheetDao().getJobSheetById(jobSheetId) ?: return null
        val calc = calculateReturnBill(
            jobSheet = jobSheet,
            returningQuantity = jobSheet.remainingActiveQty,
            manualDiscount = manualDiscount,
            returnTime = returnTime
        )
        val isNoWorkDone = settlementMethod == "no_work_done"

        val updatedJob = jobSheet.copy(
            returnTime = returnTime,
            returnedBy = currentUser.id,
            returnedByName = currentUser.name,
            closedBy = currentUser.id,
            closedByName = currentUser.name,
            status = "closed",
            returnedQuantity = jobSheet.effectiveInitialQty,
            quantity = 0,
            discount = calc.discount,
            lateFee = 0.0,
            totalRentalFee = if (isNoWorkDone) 0.0 else calc.netTotalFee,
            finalSettlement = 0.0, // Fully settled upon return; strictly no false balance due!
            returnRemark = remark
        )

        db.jobSheetDao().updateJobSheet(updatedJob)
        val itemsInJob = SelectedMachineItem.listFromJson(jobSheet.itemsJson)
        if (itemsInJob.isNotEmpty()) {
            itemsInJob.forEach { item ->
                db.machineDao().incrementStock(item.machineId, item.quantity)
            }
        } else {
            db.machineDao().incrementStock(jobSheet.machineId, jobSheet.remainingActiveQty)
        }

        if (context != null) {
            com.example.util.DueAlarmScheduler.cancelAlarms(context, jobSheet.id)
        }

        // Record settlement, refund, or NIL return entry
        if (isNoWorkDone) {
            if (jobSheet.depositAmount > 0) {
                db.paymentDao().insertPayment(
                    PaymentEntity(
                        id = UUID.randomUUID().toString(),
                        jobSheetId = jobSheet.id,
                        amount = jobSheet.depositAmount,
                        method = if (settlementMethod == "no_work_done") "cash" else settlementMethod,
                        type = "refund",
                        timestamp = returnTime,
                        handledBy = currentUser.id,
                        handledByName = currentUser.name,
                        remark = "Full deposit refund - No work done"
                    )
                )
            }
            db.paymentDao().insertPayment(
                PaymentEntity(
                    id = UUID.randomUUID().toString(),
                    jobSheetId = jobSheet.id,
                    amount = 0.0,
                    method = "no_work_done",
                    type = "return_nil",
                    timestamp = returnTime,
                    handledBy = currentUser.id,
                    handledByName = currentUser.name,
                    remark = if (remark.isNotBlank()) remark else "Closed with NIL payment (No work done)"
                )
            )
        } else if (calc.balanceDueOrRefund > 0) {
            db.paymentDao().insertPayment(
                PaymentEntity(
                    id = UUID.randomUUID().toString(),
                    jobSheetId = jobSheet.id,
                    amount = calc.balanceDueOrRefund,
                    method = settlementMethod,
                    type = "settlement",
                    timestamp = returnTime,
                    handledBy = currentUser.id,
                    handledByName = currentUser.name,
                    remark = remark.ifEmpty { "Final settlement collected (${calc.daysUsed} days used)" }
                )
            )
        } else if (calc.balanceDueOrRefund < 0) {
            db.paymentDao().insertPayment(
                PaymentEntity(
                    id = UUID.randomUUID().toString(),
                    jobSheetId = jobSheet.id,
                    amount = kotlin.math.abs(calc.balanceDueOrRefund),
                    method = settlementMethod,
                    type = "refund",
                    timestamp = returnTime,
                    handledBy = currentUser.id,
                    handledByName = currentUser.name,
                    remark = remark.ifEmpty { "Deposit refund returned (${calc.daysUsed} days used)" }
                )
            )
        } else {
            db.paymentDao().insertPayment(
                PaymentEntity(
                    id = UUID.randomUUID().toString(),
                    jobSheetId = jobSheet.id,
                    amount = 0.0,
                    method = settlementMethod,
                    type = "return_nil",
                    timestamp = returnTime,
                    handledBy = currentUser.id,
                    handledByName = currentUser.name,
                    remark = if (remark.isNotBlank()) remark else "Closed with NIL payment (Account balanced)"
                )
            )
        }

        // Log
        val summary = if (isNoWorkDone) {
            "${currentUser.name} closed job sheet ${jobSheet.id} with NO WORK DONE (All payments NIL). Full deposit ₹${jobSheet.depositAmount} refunded."
        } else {
            "${currentUser.name} closed job sheet ${jobSheet.id}. Days used: ${calc.daysUsed}, Total fee: ₹${calc.netTotalFee}, Settled: ₹${calc.balanceDueOrRefund} via $settlementMethod. Remark: ${remark.ifEmpty { "None" }}"
        }
        db.activityLogDao().insertLog(
            ActivityLogEntity(
                id = UUID.randomUUID().toString(),
                jobSheetId = jobSheet.id,
                action = "returned",
                performedBy = currentUser.id,
                performedByName = currentUser.name,
                timestamp = returnTime,
                changeSummary = summary
            )
        )

        return updatedJob
    }

    suspend fun markPartialReturned(
        jobSheetId: String,
        returningQuantity: Int,
        settlementMethod: String,
        currentUser: UserEntity,
        manualDiscount: Double = 0.0,
        returnTime: Long = System.currentTimeMillis(),
        remark: String = "",
        context: Context? = null
    ): JobSheetEntity? {
        val jobSheet = db.jobSheetDao().getJobSheetById(jobSheetId) ?: return null
        val calc = calculateReturnBill(
            jobSheet = jobSheet,
            returningQuantity = returningQuantity,
            manualDiscount = manualDiscount,
            returnTime = returnTime
        )

        val currentActiveQty = jobSheet.remainingActiveQty
        val newRemainingQty = (currentActiveQty - returningQuantity).coerceAtLeast(0)
        val newReturnedQty = jobSheet.returnedQuantity + returningQuantity
        val isFullyClosed = newRemainingQty == 0

        val remainingDeposit = maxOf(0.0, jobSheet.depositAmount - calc.depositAmount)

        val updatedJob = jobSheet.copy(
            quantity = newRemainingQty,
            initialQuantity = jobSheet.effectiveInitialQty,
            returnedQuantity = newReturnedQty,
            depositAmount = remainingDeposit,
            totalRentalFee = jobSheet.totalRentalFee + calc.netTotalFee,
            finalSettlement = 0.0,
            status = if (isFullyClosed) "closed" else "active",
            returnTime = if (isFullyClosed) returnTime else jobSheet.returnTime,
            returnedBy = currentUser.id,
            returnedByName = currentUser.name,
            closedBy = if (isFullyClosed) currentUser.id else jobSheet.closedBy,
            closedByName = if (isFullyClosed) currentUser.name else jobSheet.closedByName,
            returnRemark = if (isFullyClosed) remark else jobSheet.returnRemark
        )

        db.jobSheetDao().updateJobSheet(updatedJob)
        db.machineDao().incrementStock(jobSheet.machineId, returningQuantity)

        if (isFullyClosed && context != null) {
            com.example.util.DueAlarmScheduler.cancelAlarms(context, jobSheet.id)
        }

        // Record payment for this partial return
        if (calc.balanceDueOrRefund > 0) {
            db.paymentDao().insertPayment(
                PaymentEntity(
                    id = UUID.randomUUID().toString(),
                    jobSheetId = jobSheet.id,
                    amount = calc.balanceDueOrRefund,
                    method = settlementMethod,
                    type = "settlement",
                    timestamp = returnTime,
                    handledBy = currentUser.id,
                    handledByName = currentUser.name,
                    remark = "Partial return of $returningQuantity machine(s). ${remark.ifEmpty { "Rent settled" }}"
                )
            )
        } else if (calc.balanceDueOrRefund < 0) {
            db.paymentDao().insertPayment(
                PaymentEntity(
                    id = UUID.randomUUID().toString(),
                    jobSheetId = jobSheet.id,
                    amount = kotlin.math.abs(calc.balanceDueOrRefund),
                    method = settlementMethod,
                    type = "refund",
                    timestamp = returnTime,
                    handledBy = currentUser.id,
                    handledByName = currentUser.name,
                    remark = "Partial return deposit refund for $returningQuantity machine(s). ${remark.ifEmpty { "Refunded" }}"
                )
            )
        } else {
            db.paymentDao().insertPayment(
                PaymentEntity(
                    id = UUID.randomUUID().toString(),
                    jobSheetId = jobSheet.id,
                    amount = 0.0,
                    method = settlementMethod,
                    type = "return_nil",
                    timestamp = returnTime,
                    handledBy = currentUser.id,
                    handledByName = currentUser.name,
                    remark = "Partial return of $returningQuantity machine(s) balanced (NIL). $remark"
                )
            )
        }

        val summary = "${currentUser.name} processed partial return: $returningQuantity units of ${jobSheet.machineName} returned ($newRemainingQty still active on ${jobSheet.id}). Net rent: ₹${calc.netTotalFee}, Settled: ₹${calc.balanceDueOrRefund} via $settlementMethod. Remark: $remark"
        db.activityLogDao().insertLog(
            ActivityLogEntity(
                id = UUID.randomUUID().toString(),
                jobSheetId = jobSheet.id,
                action = if (isFullyClosed) "returned" else "partial_returned",
                performedBy = currentUser.id,
                performedByName = currentUser.name,
                timestamp = returnTime,
                changeSummary = summary
            )
        )

        return updatedJob
    }

    suspend fun editJobSheet(
        jobSheetId: String,
        newQuantity: Int,
        newRate: Double,
        newDiscount: Double,
        newNotes: String,
        currentUser: UserEntity
    ) {
        throw IllegalStateException("Editing job sheets is strictly prohibited. Corrections must be made only by adding payments or remarks.")
    }

    suspend fun softDeleteJobSheet(
        jobSheetId: String,
        reason: String,
        currentUser: UserEntity
    ) {
        throw IllegalStateException("Deleting job sheets is strictly prohibited by client business policy.")
    }

    // --- Daily Cash Management (Opening & Closing Cash) ---
    suspend fun getOpeningCashForDate(dateStr: String): Double {
        val existing = db.dailyCashDao().getDailyCash(dateStr)
        if (existing != null) return existing.openingCash
        val previous = db.dailyCashDao().getPreviousDailyCash(dateStr)
        return previous?.closingCash ?: 0.0
    }

    suspend fun saveOpeningCash(dateStr: String, openingAmount: Double, user: UserEntity) {
        val existing = db.dailyCashDao().getDailyCash(dateStr)
        val entity = DailyCashEntity(
            date = dateStr,
            openingCash = openingAmount,
            closingCash = existing?.closingCash ?: 0.0,
            enteredBy = user.id,
            enteredByName = user.name,
            updatedAt = System.currentTimeMillis()
        )
        db.dailyCashDao().insertOrUpdate(entity)
    }

    suspend fun saveClosingCash(dateStr: String, closingAmount: Double, user: UserEntity) {
        val existing = db.dailyCashDao().getDailyCash(dateStr)
        val opening = existing?.openingCash ?: getOpeningCashForDate(dateStr)
        val entity = DailyCashEntity(
            date = dateStr,
            openingCash = opening,
            closingCash = closingAmount,
            enteredBy = user.id,
            enteredByName = user.name,
            updatedAt = System.currentTimeMillis()
        )
        db.dailyCashDao().insertOrUpdate(entity)
    }

    fun getDailyCashFlow(dateStr: String): Flow<DailyCashEntity?> = db.dailyCashDao().getDailyCashFlow(dateStr)
    fun getAllDailyCashFlow(): Flow<List<DailyCashEntity>> = db.dailyCashDao().getAllDailyCash()
    fun getPaymentsForJobSheet(jobSheetId: String): Flow<List<PaymentEntity>> = db.paymentDao().getPaymentsForJobSheet(jobSheetId)

    // CSV Export functionality for Owner reports
    fun exportReportCsv(context: Context, jobSheets: List<JobSheetEntity>): Uri? {
        try {
            val fileName = "baba_rental_report_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.csv"
            val file = File(context.cacheDir, fileName)
            val writer = FileWriter(file)

            val dateFilterFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val dateTimeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

            val startOfToday = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }.timeInMillis

            val startOfMonth = Calendar.getInstance().apply {
                set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }.timeInMillis

            // Net Revenue context calculations (strictly excludes advance deposits, only includes finalized rental fee when closed)
            val todayTotalRevenue = jobSheets.filter { (it.status == "returned" || it.status == "closed") && (it.returnTime ?: 0L) >= startOfToday && !it.isDeleted }
                .sumOf { it.totalRentalFee }
            val monthlyTotalRevenue = jobSheets.filter { (it.status == "returned" || it.status == "closed") && (it.returnTime ?: 0L) >= startOfMonth && !it.isDeleted }
                .sumOf { it.totalRentalFee }

            val todayJobCount = jobSheets.count { it.checkoutTime >= startOfToday && !it.isDeleted }
            val monthJobCount = jobSheets.count { it.checkoutTime >= startOfMonth && !it.isDeleted }

            // CSV Columns requested
            writer.append("Job_ID,Date_of_Transaction,Checkout_By,Handled_By,Customer_Name,Phone,Address,Machine_Name,Category,Quantity,Rate_Used,Discount,Deposit,Billing_Cycle,Checkout_Time,Due_Date,Return_Date,Status,Late_Fee,Total_Rental_Fee,Final_Settlement,Daily_Total_Context,Monthly_Total_Context,Notes\n")

            for (job in jobSheets) {
                val dateOfTxn = dateFilterFormat.format(Date(job.checkoutTime))
                val checkoutStr = dateTimeFormat.format(Date(job.checkoutTime))
                val dueStr = dateTimeFormat.format(Date(job.dueTime))
                val returnStr = job.returnTime?.let { dateTimeFormat.format(Date(it)) } ?: "Active"
                val checkoutByStr = job.checkoutByName.ifEmpty { job.handledByName }
                val handledByStr = job.handledByName

                writer.append("\"${job.id}\",")
                writer.append("\"$dateOfTxn\",")
                writer.append("\"$checkoutByStr\",")
                writer.append("\"$handledByStr\",")
                writer.append("\"${job.customerName}\",")
                writer.append("\"${job.customerPhone}\",")
                writer.append("\"${job.customerAddress.replace("\"", "\"\"")}\",")
                writer.append("\"${job.machineName.replace("\"", "\"\"")}\",")
                writer.append("\"${job.categoryName}\",")
                writer.append("${job.quantity},")
                writer.append("${job.rateUsed},")
                writer.append("${job.discount},")
                writer.append("${job.depositAmount},")
                writer.append("\"${job.billingCycle}\",")
                writer.append("\"$checkoutStr\",")
                writer.append("\"$dueStr\",")
                writer.append("\"$returnStr\",")
                writer.append("\"${job.status}\",")
                writer.append("${job.lateFee},")
                writer.append("${job.totalRentalFee},")
                writer.append("${job.finalSettlement},")
                writer.append("\"Rs ${todayTotalRevenue.toInt()}\",")
                writer.append("\"Rs ${monthlyTotalRevenue.toInt()}\",")
                writer.append("\"${job.notes.replace("\"", "\"\"")}\"\n")
            }

            val periodTotalRevenue = jobSheets.filter { (it.status == "returned" || it.status == "closed") && !it.isDeleted }
                .sumOf { it.totalRentalFee }
            val periodJobCount = jobSheets.count { !it.isDeleted }

            // Summary Context for Accountant convenience
            writer.append("\n\"--- FINANCIAL SUMMARY CONTEXT ---\",,,,,,,,,,,,,,,,,,,,\n")
            writer.append("\"Selected Period Job Sheets\",\"$periodJobCount rentals\",,,,,,,,,,,,,,,,,,,,\n")
            writer.append("\"Selected Period Net Revenue\",\"Rs ${periodTotalRevenue.toInt()}\",,,,,,,,,,,,,,,,,,,,\n")
            writer.append("\"Today's Sales (Net Revenue)\",\"Rs ${todayTotalRevenue.toInt()}\",,,,,,,,,,,,,,,,,,,,\n")
            writer.append("\"Today's Job Sheets\",\"$todayJobCount rentals\",,,,,,,,,,,,,,,,,,,,\n")
            writer.append("\"Monthly Sales (Net Revenue)\",\"Rs ${monthlyTotalRevenue.toInt()}\",,,,,,,,,,,,,,,,,,,,\n")
            writer.append("\"Monthly Job Sheets\",\"$monthJobCount rentals\",,,,,,,,,,,,,,,,,,,,\n")

            writer.flush()
            writer.close()

            return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    suspend fun updateOwnerPhone(ownerId: String, newPhone: String) {
        val user = db.userDao().getUserById(ownerId) ?: return
        db.userDao().updateUser(user.copy(registeredPhone = newPhone))
        db.activityLogDao().insertLog(
            ActivityLogEntity(
                id = "log_${System.currentTimeMillis()}",
                jobSheetId = "OWNER_SECURITY",
                action = "phone_updated",
                performedBy = user.id,
                performedByName = user.name,
                timestamp = System.currentTimeMillis(),
                changeSummary = "Registered security mobile number updated to +91 $newPhone for OTP verification"
            )
        )
    }

    suspend fun updateOwnerPin(ownerId: String, newPin: String) {
        val user = db.userDao().getUserById(ownerId) ?: return
        db.userDao().updateUser(user.copy(pin = newPin))
        db.activityLogDao().insertLog(
            ActivityLogEntity(
                id = "log_${System.currentTimeMillis()}",
                jobSheetId = "OWNER_SECURITY",
                action = "pin_changed",
                performedBy = user.id,
                performedByName = user.name,
                timestamp = System.currentTimeMillis(),
                changeSummary = "Owner PIN was successfully changed via OTP verification"
            )
        )
    }
}
