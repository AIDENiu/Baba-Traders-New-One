package com.example.data.repository

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.data.AppDatabase
import com.example.data.model.ActivityLogEntity
import com.example.data.model.CustomerEntity
import com.example.data.model.JobSheetEntity
import com.example.data.model.MachineCategoryEntity
import com.example.data.model.MachineEntity
import com.example.data.model.PaymentEntity
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
    val baseDays: Int,
    val daysLate: Int,
    val baseRentalFee: Double,
    val lateFee: Double,
    val discount: Double,
    val depositAmount: Double,
    val netTotalFee: Double,
    val balanceDueOrRefund: Double // > 0: customer owes balance; < 0: shop refunds customer
)

class BabaRepository(private val db: AppDatabase) {

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
    val allCategories: Flow<List<MachineCategoryEntity>> = db.machineDao().getAllCategories()
    val allMachines: Flow<List<MachineEntity>> = db.machineDao().getAllMachines()

    fun getMachinesByCategory(categoryId: String) = db.machineDao().getMachinesByCategory(categoryId)
    suspend fun getMachineById(id: String) = db.machineDao().getMachineById(id)
    suspend fun insertMachine(machine: MachineEntity) = db.machineDao().insertMachine(machine)
    suspend fun updateMachine(machine: MachineEntity) = db.machineDao().updateMachine(machine)

    // --- Job Sheets ---
    val allJobSheets: Flow<List<JobSheetEntity>> = db.jobSheetDao().getAllJobSheets()
    val activeJobSheets: Flow<List<JobSheetEntity>> = db.jobSheetDao().getActiveJobSheets()
    fun getOverdueJobSheets(now: Long) = db.jobSheetDao().getOverdueJobSheets(now)
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
        notes: String
    ): JobSheetEntity {
        // 1. Ensure customer is persisted
        db.customerDao().insertCustomer(customer)

        // 2. Generate unique display ID
        val jobId = generateNextJobId()
        val now = System.currentTimeMillis()
        val dueTime = computeDueTime(now, billingCycle)

        val jobSheet = JobSheetEntity(
            id = jobId,
            customerId = customer.id,
            customerName = customer.name,
            customerPhone = customer.phone,
            customerAddress = customer.address,
            machineId = machine.id,
            machineName = machine.name,
            categoryName = categoryName,
            quantity = quantity,
            rateUsed = rateUsed,
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
            notes = notes
        )

        // 3. Save job sheet and decrement stock
        db.jobSheetDao().insertJobSheet(jobSheet)
        db.machineDao().decrementStock(machine.id, quantity)

        // 4. Record deposit payment if amount > 0
        if (depositAmount > 0) {
            db.paymentDao().insertPayment(
                PaymentEntity(
                    id = UUID.randomUUID().toString(),
                    jobSheetId = jobId,
                    amount = depositAmount,
                    method = depositMethod,
                    type = "deposit",
                    timestamp = now,
                    handledBy = checkoutUser.id,
                    handledByName = checkoutUser.name
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
                changeSummary = "${checkoutUser.name} billed job sheet $jobId (Handled by ${handledUser.name}) for ${customer.name} (${machine.name}, Qty: $quantity, Rate: ₹$rateUsed, Deposit: ₹$depositAmount)"
            )
        )

        return jobSheet
    }

    fun calculateReturnBill(jobSheet: JobSheetEntity, returnTime: Long = System.currentTimeMillis()): ReturnCalculation {
        val totalMsPerDay = 24 * 3600 * 1000.0
        val durationMs = maxOf(0L, returnTime - jobSheet.checkoutTime)
        // Rolling 24-hour window:
        // Returns within 24 hours (e.g. 1 hour) are billed as exactly 1 day.
        val totalDays = ceil(durationMs.toDouble() / totalMsPerDay).toInt().coerceAtLeast(1)
        val baseDays = 1
        val daysLate = (totalDays - baseDays).coerceAtLeast(0)

        val baseRentalFee = jobSheet.rateUsed * jobSheet.quantity * baseDays
        val lateFee = jobSheet.rateUsed * jobSheet.quantity * daysLate
        val netTotalFee = max(0.0, (baseRentalFee + lateFee) - jobSheet.discount)
        val balanceDueOrRefund = netTotalFee - jobSheet.depositAmount

        return ReturnCalculation(
            baseDays = baseDays,
            daysLate = daysLate,
            baseRentalFee = baseRentalFee,
            lateFee = lateFee,
            discount = jobSheet.discount,
            depositAmount = jobSheet.depositAmount,
            netTotalFee = netTotalFee,
            balanceDueOrRefund = balanceDueOrRefund
        )
    }

    suspend fun markReturned(
        jobSheetId: String,
        settlementMethod: String,
        currentUser: UserEntity,
        returnTime: Long = System.currentTimeMillis()
    ): JobSheetEntity? {
        val jobSheet = db.jobSheetDao().getJobSheetById(jobSheetId) ?: return null
        val calc = calculateReturnBill(jobSheet, returnTime)

        val updatedJob = jobSheet.copy(
            returnTime = returnTime,
            status = "returned",
            lateFee = calc.lateFee,
            totalRentalFee = calc.netTotalFee,
            finalSettlement = calc.balanceDueOrRefund
        )

        db.jobSheetDao().updateJobSheet(updatedJob)
        db.machineDao().incrementStock(jobSheet.machineId, jobSheet.quantity)

        // Record settlement payment if non-zero
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
                    handledByName = currentUser.name
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
                    handledByName = currentUser.name
                )
            )
        }

        // Log
        val summary = if (calc.daysLate > 0) {
            "${currentUser.name} marked ${jobSheet.id} returned (LATE by ${calc.daysLate}d, late fee: ₹${calc.lateFee}, net: ₹${calc.netTotalFee})"
        } else {
            "${currentUser.name} marked ${jobSheet.id} returned (On time, total: ₹${calc.netTotalFee})"
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

    suspend fun editJobSheet(
        jobSheetId: String,
        newQuantity: Int,
        newRate: Double,
        newDiscount: Double,
        newNotes: String,
        currentUser: UserEntity
    ) {
        val job = db.jobSheetDao().getJobSheetById(jobSheetId) ?: return

        // Adjust stock if quantity changed and still active
        if (job.status == "active" && newQuantity != job.quantity) {
            val diff = newQuantity - job.quantity
            if (diff > 0) {
                db.machineDao().decrementStock(job.machineId, diff)
            } else {
                db.machineDao().incrementStock(job.machineId, kotlin.math.abs(diff))
            }
        }

        val updated = job.copy(
            quantity = newQuantity,
            rateUsed = newRate,
            discount = newDiscount,
            notes = newNotes
        )
        db.jobSheetDao().updateJobSheet(updated)

        db.activityLogDao().insertLog(
            ActivityLogEntity(
                id = UUID.randomUUID().toString(),
                jobSheetId = job.id,
                action = "edited",
                performedBy = currentUser.id,
                performedByName = currentUser.name,
                timestamp = System.currentTimeMillis(),
                changeSummary = "${currentUser.name} edited job sheet ${job.id} (Qty: ${job.quantity}->${newQuantity}, Rate: ₹${job.rateUsed}->₹${newRate}, Discount: ₹${job.discount}->₹${newDiscount})"
            )
        )
    }

    suspend fun softDeleteJobSheet(
        jobSheetId: String,
        reason: String,
        currentUser: UserEntity
    ) {
        val job = db.jobSheetDao().getJobSheetById(jobSheetId) ?: return

        // Restore stock if it was active
        if (job.status == "active" || job.status == "overdue") {
            db.machineDao().incrementStock(job.machineId, job.quantity)
        }

        db.jobSheetDao().softDeleteJobSheet(jobSheetId)

        db.activityLogDao().insertLog(
            ActivityLogEntity(
                id = UUID.randomUUID().toString(),
                jobSheetId = job.id,
                action = "deleted",
                performedBy = currentUser.id,
                performedByName = currentUser.name,
                timestamp = System.currentTimeMillis(),
                changeSummary = "${currentUser.name} deleted job sheet ${job.id} (${job.customerName}, ${job.machineName}). Reason: $reason"
            )
        )
    }

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

            // Net Revenue context calculations
            val todayTotalRevenue = jobSheets.filter { it.checkoutTime >= startOfToday && !it.isDeleted }
                .sumOf { if (it.status == "returned") it.totalRentalFee else it.depositAmount }
            val monthlyTotalRevenue = jobSheets.filter { it.checkoutTime >= startOfMonth && !it.isDeleted }
                .sumOf { if (it.status == "returned") it.totalRentalFee else it.depositAmount }

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

            // Summary Context for Accountant convenience
            writer.append("\n\"--- FINANCIAL SUMMARY CONTEXT ---\",,,,,,,,,,,,,,,,,,,,\n")
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
}
