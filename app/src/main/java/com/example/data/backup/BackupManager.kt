package com.example.data.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.data.AppDatabase
import com.example.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class BackupFileInfo(
    val fileName: String,
    val file: File,
    val sizeBytes: Long,
    val timestamp: Long,
    val formattedDate: String
)

object BackupManager {

    private const val PREFS_NAME = "baba_backup_prefs"
    private const val KEY_LAST_BACKUP = "last_weekly_backup_timestamp"
    private const val BACKUP_DIR_NAME = "baba_backups"
    private const val MAX_RETAINED_BACKUPS = 20

    fun getBackupDirectory(context: Context): File {
        val dir = File(context.filesDir, BACKUP_DIR_NAME)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    suspend fun createBackupBundle(context: Context, db: AppDatabase): File = withContext(Dispatchers.IO) {
        val backupDir = getBackupDirectory(context)
        val dateFormat = SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.getDefault())
        val now = System.currentTimeMillis()
        val timestampStr = dateFormat.format(Date(now))
        val zipFileName = "BABA_Backup_$timestampStr.zip"
        val zipFile = File(backupDir, zipFileName)

        val tempDir = File(context.cacheDir, "backup_temp_$now")
        tempDir.mkdirs()

        try {
            // 0. Export Staff & Owner Users JSON
            val users = db.userDao().getAllUsersList()
            val usersJson = JSONArray().apply {
                users.forEach {
                    put(JSONObject().apply {
                        put("id", it.id)
                        put("name", it.name)
                        put("phoneOrUsername", it.phoneOrUsername)
                        put("pin", it.pin)
                        put("role", it.role)
                        put("isActive", it.isActive)
                        put("maxDiscountPercent", it.maxDiscountPercent)
                        put("maxDiscountAmount", it.maxDiscountAmount)
                        put("registeredPhone", it.registeredPhone)
                        put("phone", it.phone)
                    })
                }
            }
            File(tempDir, "users.json").writeText(usersJson.toString(2))

            // 1. Export Customers JSON (including personPhotoPath, idPhotoFrontPath, idPhotoBackPath)
            val customers = db.customerDao().getAllCustomersList()
            val customersJson = JSONArray().apply {
                customers.forEach {
                    put(JSONObject().apply {
                        put("id", it.id)
                        put("name", it.name)
                        put("phone", it.phone)
                        put("address", it.address)
                        put("idPhotoFrontPath", it.idPhotoFrontPath ?: "")
                        put("idPhotoBackPath", it.idPhotoBackPath ?: "")
                        put("personPhotoPath", it.personPhotoPath ?: "")
                        put("createdAt", it.createdAt)
                    })
                }
            }
            File(tempDir, "customers.json").writeText(customersJson.toString(2))

            // 2. Export Categories JSON
            val categories = db.machineCategoryDao().getAllCategoriesList()
            val categoriesJson = JSONArray().apply {
                categories.forEach {
                    put(JSONObject().apply {
                        put("id", it.id)
                        put("name", it.name)
                        put("iconName", it.iconName)
                    })
                }
            }
            File(tempDir, "categories.json").writeText(categoriesJson.toString(2))

            // 3. Export Machines JSON
            val machines = db.machineDao().getAllMachinesList()
            val machinesJson = JSONArray().apply {
                machines.forEach {
                    put(JSONObject().apply {
                        put("id", it.id)
                        put("categoryId", it.categoryId)
                        put("name", it.name)
                        put("baseRate", it.baseRate)
                        put("currentRate", it.currentRate)
                        put("availableQty", it.availableQty)
                        put("totalQty", it.totalQty)
                        put("maintenanceQty", it.maintenanceQty)
                        put("modelOrSpecs", it.modelOrSpecs)
                    })
                }
            }
            File(tempDir, "machines.json").writeText(machinesJson.toString(2))

            // 4. Export Job Sheets JSON & CSV (with all photo references, closedBy, returnRemark, paymentMode, itemsJson)
            val jobSheets = db.jobSheetDao().getAllJobSheetsList()
            val jobSheetsJson = JSONArray().apply {
                jobSheets.forEach {
                    put(JSONObject().apply {
                        put("id", it.id)
                        put("customerId", it.customerId)
                        put("customerName", it.customerName)
                        put("customerPhone", it.customerPhone)
                        put("customerAddress", it.customerAddress)
                        put("machineId", it.machineId)
                        put("machineName", it.machineName)
                        put("categoryName", it.categoryName)
                        put("quantity", it.quantity)
                        put("initialQuantity", it.initialQuantity)
                        put("returnedQuantity", it.returnedQuantity)
                        put("rateUsed", it.rateUsed)
                        put("discount", it.discount)
                        put("depositAmount", it.depositAmount)
                        put("billingCycle", it.billingCycle)
                        put("checkoutTime", it.checkoutTime)
                        put("dueTime", it.dueTime)
                        put("returnTime", it.returnTime ?: JSONObject.NULL)
                        put("status", it.status)
                        put("checkoutBy", it.checkoutBy)
                        put("checkoutByName", it.checkoutByName)
                        put("handledBy", it.handledBy)
                        put("handledByName", it.handledByName)
                        put("returnedBy", it.returnedBy)
                        put("returnedByName", it.returnedByName)
                        put("closedBy", it.closedBy)
                        put("closedByName", it.closedByName)
                        put("lateFee", it.lateFee)
                        put("totalRentalFee", it.totalRentalFee)
                        put("finalSettlement", it.finalSettlement)
                        put("notes", it.notes)
                        put("returnRemark", it.returnRemark)
                        put("paymentMode", it.paymentMode)
                        put("itemsJson", it.itemsJson)
                        put("personPhotoPath", it.personPhotoPath ?: "")
                        put("idPhotoPath", it.idPhotoPath ?: "")
                        put("machinePhotoPath", it.machinePhotoPath ?: "")
                        put("isDeleted", it.isDeleted)
                    })
                }
            }
            File(tempDir, "job_sheets.json").writeText(jobSheetsJson.toString(2))

            // CSV Export
            val csvBuilder = StringBuilder()
            csvBuilder.append("JobSheet_ID,Customer_Name,Customer_Phone,Machine_Name,Qty,Daily_Rate,Discount,Deposit,Payment_Mode,Checkout_Date,Due_Date,Return_Date,Status,Created_By,Closed_By,Return_Remark,Late_Fee,Total_Fee,Final_Settlement\n")
            val dateFmt = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
            jobSheets.forEach { job ->
                val checkoutStr = dateFmt.format(Date(job.checkoutTime))
                val dueStr = dateFmt.format(Date(job.dueTime))
                val returnStr = job.returnTime?.let { dateFmt.format(Date(it)) } ?: "N/A"
                val closedByDisplay = job.closedByName.ifBlank { job.returnedByName }
                csvBuilder.append("\"${job.id}\",\"${job.customerName}\",\"${job.customerPhone}\",\"${job.machineName}\",${job.quantity},${job.rateUsed},${job.discount},${job.depositAmount},\"${job.paymentMode}\",\"$checkoutStr\",\"$dueStr\",\"$returnStr\",${job.status},\"${job.checkoutByName}\",\"$closedByDisplay\",\"${job.returnRemark.replace("\"", "'")}\",${job.lateFee},${job.totalRentalFee},${job.finalSettlement}\n")
            }
            File(tempDir, "rental_records.csv").writeText(csvBuilder.toString())

            // 5. Export Payments JSON
            val payments = db.paymentDao().getAllPaymentsList()
            val paymentsJson = JSONArray().apply {
                payments.forEach {
                    put(JSONObject().apply {
                        put("id", it.id)
                        put("jobSheetId", it.jobSheetId)
                        put("amount", it.amount)
                        put("method", it.method)
                        put("type", it.type)
                        put("timestamp", it.timestamp)
                        put("handledBy", it.handledBy)
                        put("handledByName", it.handledByName)
                        put("remark", it.remark)
                    })
                }
            }
            File(tempDir, "payments.json").writeText(paymentsJson.toString(2))

            // 6. Export Activity Log JSON
            val logs = db.activityLogDao().getAllLogsList()
            val logsJson = JSONArray().apply {
                logs.forEach {
                    put(JSONObject().apply {
                        put("id", it.id)
                        put("jobSheetId", it.jobSheetId)
                        put("action", it.action)
                        put("performedBy", it.performedBy)
                        put("performedByName", it.performedByName)
                        put("handledByUid", it.handledByUid)
                        put("handledByName", it.handledByName)
                        put("timestamp", it.timestamp)
                        put("changeSummary", it.changeSummary)
                    })
                }
            }
            File(tempDir, "activity_log.json").writeText(logsJson.toString(2))

            // 7. Export Expenses & Daily Cash JSON
            val expenses = db.expenseDao().getAllExpensesList()
            val expensesJson = JSONArray().apply {
                expenses.forEach {
                    put(JSONObject().apply {
                        put("id", it.id)
                        put("date", it.date)
                        put("timestamp", it.timestamp)
                        put("expenseType", it.expenseType)
                        put("amount", it.amount)
                        put("remarks", it.remarks)
                        put("createdBy", it.createdBy)
                        put("createdByName", it.createdByName)
                    })
                }
            }
            File(tempDir, "expenses.json").writeText(expensesJson.toString(2))

            val dailyCashList = db.dailyCashDao().getAllDailyCashList()
            val dailyCashJson = JSONArray().apply {
                dailyCashList.forEach {
                    put(JSONObject().apply {
                        put("date", it.date)
                        put("openingCash", it.openingCash)
                        put("closingCash", it.closingCash)
                        put("enteredBy", it.enteredBy)
                        put("enteredByName", it.enteredByName)
                        put("updatedAt", it.updatedAt)
                        put("openingBalance", it.openingBalance)
                        put("cashCollected", it.cashCollected)
                        put("cashRefunded", it.cashRefunded)
                        put("notes", it.notes)
                    })
                }
            }
            File(tempDir, "daily_cash.json").writeText(dailyCashJson.toString(2))

            // 8. Copy ALL Local Photos (Person, ID Front/Back, Machine) into tempDir/id_photos
            val idPhotosDir = File(tempDir, "id_photos")
            idPhotosDir.mkdirs()
            val candidatePhotoFiles = LinkedHashMap<String, File>()

            val filesInStorage = context.filesDir.listFiles() ?: emptyArray()
            for (file in filesInStorage) {
                if (file.isFile && isImageFileName(file.name)) {
                    candidatePhotoFiles[file.name] = file
                }
            }
            // Also check any explicit photo paths referenced in customers and job_sheets
            customers.forEach { cust ->
                listOfNotNull(cust.idPhotoFrontPath, cust.idPhotoBackPath, cust.personPhotoPath).forEach { path ->
                    resolveLocalPhotoFile(context, path)?.let { f -> candidatePhotoFiles[f.name] = f }
                }
            }
            jobSheets.forEach { job ->
                listOfNotNull(job.personPhotoPath, job.idPhotoPath, job.machinePhotoPath).forEach { path ->
                    resolveLocalPhotoFile(context, path)?.let { f -> candidatePhotoFiles[f.name] = f }
                }
            }

            for ((name, file) in candidatePhotoFiles) {
                if (file.exists() && file.length() > 0L) {
                    file.copyTo(File(idPhotosDir, name), overwrite = true)
                }
            }

            // 9. Zip all files into BABA_Backup_*.zip
            ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use { outZip ->
                zipDirectory(tempDir, "", outZip)
            }

            // 10. Update last backup timestamp
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putLong(KEY_LAST_BACKUP, now)
                .apply()

            // 11. Rolling cleanup: keep only last MAX_RETAINED_BACKUPS
            cleanRollingBackups(backupDir)

        } finally {
            tempDir.deleteRecursively()
        }

        zipFile
    }

    private fun findFileInRestoreDir(rootDir: File, fileName: String): File? {
        val direct = File(rootDir, fileName)
        if (direct.exists()) return direct
        return rootDir.walkTopDown().firstOrNull { it.name.equals(fileName, ignoreCase = true) }
    }

    /**
     * Non-destructive restore of total app data and photos from a BABA Backup ZIP.
     */
    suspend fun restoreFromBackupZip(context: Context, db: AppDatabase, zipFile: File): Int = withContext(Dispatchers.IO) {
        if (!zipFile.exists()) return@withContext 0
        val tempRestoreDir = File(context.cacheDir, "restore_temp_${System.currentTimeMillis()}")
        tempRestoreDir.mkdirs()
        var restoredRecords = 0

        try {
            ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zis ->
                var entry: ZipEntry? = zis.nextEntry
                val buffer = ByteArray(4096)
                while (entry != null) {
                    val outFile = File(tempRestoreDir, entry.name)
                    if (!outFile.canonicalPath.startsWith(tempRestoreDir.canonicalPath)) {
                        zis.closeEntry()
                        entry = zis.nextEntry
                        continue
                    }
                    if (entry.isDirectory) {
                        outFile.mkdirs()
                    } else {
                        outFile.parentFile?.mkdirs()
                        FileOutputStream(outFile).use { fos ->
                            var len: Int
                            while (zis.read(buffer).also { len = it } > 0) {
                                fos.write(buffer, 0, len)
                            }
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }

            // 1. Restore photos to context.filesDir
            tempRestoreDir.walkTopDown().forEach { photo ->
                if (photo.isFile && isImageFileName(photo.name)) {
                    val target = File(context.filesDir, photo.name)
                    if (!target.exists() || target.length() == 0L) {
                        photo.copyTo(target, overwrite = true)
                    }
                }
            }

            // 2. Restore users (non-destructive: only add missing staff/users)
            findFileInRestoreDir(tempRestoreDir, "users.json")?.let { usersFile ->
                val arr = JSONArray(usersFile.readText())
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val id = obj.optString("id")
                    if (id.isNotBlank() && db.userDao().getUserById(id) == null) {
                        db.userDao().insertUser(
                            UserEntity(
                                id = id,
                                name = obj.optString("name"),
                                phoneOrUsername = obj.optString("phoneOrUsername"),
                                pin = obj.optString("pin"),
                                role = obj.optString("role", "worker"),
                                isActive = obj.optBoolean("isActive", true),
                                maxDiscountPercent = obj.optDouble("maxDiscountPercent", 0.0),
                                maxDiscountAmount = obj.optDouble("maxDiscountAmount", 0.0),
                                registeredPhone = obj.optString("registeredPhone", ""),
                                phone = obj.optString("phone", "")
                            )
                        )
                        restoredRecords++
                    }
                }
            }

            // 3. Restore categories
            findFileInRestoreDir(tempRestoreDir, "categories.json")?.let { categoriesFile ->
                val existingCategoryIds = db.machineCategoryDao().getAllCategoriesList().map { it.id }.toHashSet()
                val arr = JSONArray(categoriesFile.readText())
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val id = obj.optString("id")
                    if (id.isNotBlank() && !existingCategoryIds.contains(id)) {
                        db.machineCategoryDao().insertCategory(
                            MachineCategoryEntity(
                                id = id,
                                name = obj.optString("name"),
                                iconName = obj.optString("iconName", "build")
                            )
                        )
                        existingCategoryIds.add(id)
                        restoredRecords++
                    }
                }
            }

            // 4. Restore machines
            findFileInRestoreDir(tempRestoreDir, "machines.json")?.let { machinesFile ->
                val arr = JSONArray(machinesFile.readText())
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val id = obj.optString("id")
                    if (id.isNotBlank() && db.machineDao().getMachineById(id) == null) {
                        val totalQty = obj.optInt("totalQty", 1)
                        db.machineDao().insertMachine(
                            MachineEntity(
                                id = id,
                                categoryId = obj.optString("categoryId"),
                                name = obj.optString("name"),
                                baseRate = obj.optDouble("baseRate", 0.0),
                                currentRate = obj.optDouble("currentRate", obj.optDouble("baseRate", 0.0)),
                                totalQty = totalQty,
                                availableQty = obj.optInt("availableQty", totalQty),
                                maintenanceQty = obj.optInt("maintenanceQty", 0),
                                modelOrSpecs = obj.optString("modelOrSpecs", "")
                            )
                        )
                        restoredRecords++
                    }
                }
            }

            // 5. Restore customers
            findFileInRestoreDir(tempRestoreDir, "customers.json")?.let { customersFile ->
                val arr = JSONArray(customersFile.readText())
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val id = obj.optString("id")
                    if (id.isNotBlank() && db.customerDao().getCustomerById(id) == null) {
                        db.customerDao().insertCustomer(
                            CustomerEntity(
                                id = id,
                                name = obj.optString("name"),
                                phone = obj.optString("phone"),
                                address = obj.optString("address"),
                                idPhotoFrontPath = obj.optString("idPhotoFrontPath").takeIf { it.isNotBlank() }?.let { File(context.filesDir, File(it).name).absolutePath },
                                idPhotoBackPath = obj.optString("idPhotoBackPath").takeIf { it.isNotBlank() }?.let { File(context.filesDir, File(it).name).absolutePath },
                                personPhotoPath = obj.optString("personPhotoPath").takeIf { it.isNotBlank() }?.let { File(context.filesDir, File(it).name).absolutePath },
                                createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                            )
                        )
                        restoredRecords++
                    }
                }
            }

            // 6. Restore job sheets (non-destructive, respecting SQLite immutability triggers)
            findFileInRestoreDir(tempRestoreDir, "job_sheets.json")?.let { jobsFile ->
                val arr = JSONArray(jobsFile.readText())
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val id = obj.optString("id")
                    if (id.isNotBlank()) {
                        val existingJob = db.jobSheetDao().getJobSheetById(id)
                        val qty = obj.optInt("quantity", 1)
                        val restoredPersonPhoto = obj.optString("personPhotoPath").takeIf { it.isNotBlank() }?.let { File(context.filesDir, File(it).name).absolutePath }
                        val restoredIdPhoto = obj.optString("idPhotoPath").takeIf { it.isNotBlank() }?.let { File(context.filesDir, File(it).name).absolutePath }
                        val restoredMachinePhoto = obj.optString("machinePhotoPath").takeIf { it.isNotBlank() }?.let { File(context.filesDir, File(it).name).absolutePath }

                        if (existingJob == null) {
                            db.jobSheetDao().insertJobSheet(
                                JobSheetEntity(
                                    id = id,
                                    customerId = obj.optString("customerId"),
                                    customerName = obj.optString("customerName"),
                                    customerPhone = obj.optString("customerPhone"),
                                    customerAddress = obj.optString("customerAddress"),
                                    machineId = obj.optString("machineId"),
                                    machineName = obj.optString("machineName"),
                                    categoryName = obj.optString("categoryName"),
                                    quantity = qty,
                                    initialQuantity = obj.optInt("initialQuantity", qty),
                                    returnedQuantity = obj.optInt("returnedQuantity", 0),
                                    rateUsed = obj.optDouble("rateUsed", 0.0),
                                    discount = obj.optDouble("discount", 0.0),
                                    depositAmount = obj.optDouble("depositAmount", 0.0),
                                    billingCycle = obj.optString("billingCycle", "working_hours"),
                                    checkoutTime = obj.optLong("checkoutTime"),
                                    dueTime = obj.optLong("dueTime"),
                                    returnTime = if (obj.isNull("returnTime")) null else obj.optLong("returnTime"),
                                    status = obj.optString("status", "active"),
                                    lateFee = obj.optDouble("lateFee", 0.0),
                                    totalRentalFee = obj.optDouble("totalRentalFee", 0.0),
                                    finalSettlement = obj.optDouble("finalSettlement", 0.0),
                                    checkoutBy = obj.optString("checkoutBy"),
                                    checkoutByName = obj.optString("checkoutByName"),
                                    handledBy = obj.optString("handledBy"),
                                    handledByName = obj.optString("handledByName"),
                                    returnedBy = obj.optString("returnedBy"),
                                    returnedByName = obj.optString("returnedByName"),
                                    closedBy = obj.optString("closedBy"),
                                    closedByName = obj.optString("closedByName"),
                                    notes = obj.optString("notes"),
                                    returnRemark = obj.optString("returnRemark"),
                                    isDeleted = false,
                                    personPhotoPath = restoredPersonPhoto,
                                    idPhotoPath = restoredIdPhoto,
                                    machinePhotoPath = restoredMachinePhoto,
                                    paymentMode = obj.optString("paymentMode", "cash"),
                                    itemsJson = obj.optString("itemsJson", "")
                                )
                            )
                            restoredRecords++
                        } else if (existingJob.personPhotoPath.isNullOrBlank() && !restoredPersonPhoto.isNullOrBlank() ||
                            existingJob.idPhotoPath.isNullOrBlank() && !restoredIdPhoto.isNullOrBlank() ||
                            existingJob.machinePhotoPath.isNullOrBlank() && !restoredMachinePhoto.isNullOrBlank()
                        ) {
                            // Update only photo paths if missing on this device without touching immutable core fields
                            db.jobSheetDao().updateJobSheet(
                                existingJob.copy(
                                    personPhotoPath = existingJob.personPhotoPath ?: restoredPersonPhoto,
                                    idPhotoPath = existingJob.idPhotoPath ?: restoredIdPhoto,
                                    machinePhotoPath = existingJob.machinePhotoPath ?: restoredMachinePhoto,
                                    isDeleted = false
                                )
                            )
                        }
                    }
                }
            }

            // 7. Restore payments (append-only)
            findFileInRestoreDir(tempRestoreDir, "payments.json")?.let { paymentsFile ->
                val existingPaymentIds = db.paymentDao().getAllPaymentsList().map { it.id }.toHashSet()
                val arr = JSONArray(paymentsFile.readText())
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val id = obj.optString("id")
                    if (id.isNotBlank() && !existingPaymentIds.contains(id)) {
                        db.paymentDao().insertPayment(
                            PaymentEntity(
                                id = id,
                                jobSheetId = obj.optString("jobSheetId"),
                                amount = obj.optDouble("amount", 0.0),
                                method = obj.optString("method", "cash"),
                                type = obj.optString("type", "advance"),
                                timestamp = obj.optLong("timestamp"),
                                handledBy = obj.optString("handledBy"),
                                handledByName = obj.optString("handledByName"),
                                remark = obj.optString("remark", "")
                            )
                        )
                        existingPaymentIds.add(id)
                        restoredRecords++
                    }
                }
            }

            // 8. Restore activity log (append-only)
            findFileInRestoreDir(tempRestoreDir, "activity_log.json")?.let { logsFile ->
                val existingLogIds = db.activityLogDao().getAllLogsList().map { it.id }.toHashSet()
                val arr = JSONArray(logsFile.readText())
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val id = obj.optString("id")
                    if (id.isNotBlank() && !existingLogIds.contains(id)) {
                        db.activityLogDao().insertLog(
                            ActivityLogEntity(
                                id = id,
                                jobSheetId = obj.optString("jobSheetId"),
                                action = obj.optString("action"),
                                performedBy = obj.optString("performedBy"),
                                performedByName = obj.optString("performedByName"),
                                handledByUid = obj.optString("handledByUid", ""),
                                handledByName = obj.optString("handledByName", ""),
                                timestamp = obj.optLong("timestamp"),
                                changeSummary = obj.optString("changeSummary")
                            )
                        )
                        existingLogIds.add(id)
                        restoredRecords++
                    }
                }
            }

            // 9. Restore expenses
            findFileInRestoreDir(tempRestoreDir, "expenses.json")?.let { expensesFile ->
                val existingExpenseIds = db.expenseDao().getAllExpensesList().map { it.id }.toHashSet()
                val arr = JSONArray(expensesFile.readText())
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val id = obj.optString("id")
                    if (id.isNotBlank() && !existingExpenseIds.contains(id)) {
                        db.expenseDao().insertExpense(
                            ExpenseEntity(
                                id = id,
                                date = obj.optString("date"),
                                timestamp = obj.optLong("timestamp"),
                                expenseType = obj.optString("expenseType"),
                                amount = obj.optDouble("amount", 0.0),
                                remarks = obj.optString("remarks", ""),
                                createdBy = obj.optString("createdBy", ""),
                                createdByName = obj.optString("createdByName", "")
                            )
                        )
                        existingExpenseIds.add(id)
                        restoredRecords++
                    }
                }
            }

            // 10. Restore daily cash
            findFileInRestoreDir(tempRestoreDir, "daily_cash.json")?.let { cashFile ->
                val arr = JSONArray(cashFile.readText())
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val date = obj.optString("date")
                    if (date.isNotBlank() && db.dailyCashDao().getDailyCash(date) == null) {
                        db.dailyCashDao().insertDailyCash(
                            DailyCashEntity(
                                date = date,
                                openingCash = obj.optDouble("openingCash", 0.0),
                                closingCash = obj.optDouble("closingCash", 0.0),
                                enteredBy = obj.optString("enteredBy", ""),
                                enteredByName = obj.optString("enteredByName", ""),
                                updatedAt = obj.optLong("updatedAt", System.currentTimeMillis()),
                                openingBalance = obj.optDouble("openingBalance", 0.0),
                                cashCollected = obj.optDouble("cashCollected", 0.0),
                                cashRefunded = obj.optDouble("cashRefunded", 0.0),
                                notes = obj.optString("notes", "")
                            )
                        )
                        restoredRecords++
                    }
                }
            }
        } finally {
            tempRestoreDir.deleteRecursively()
        }
        restoredRecords
    }

    /**
     * Restores app data & photos from an external ZIP file Uri selected via Android's system file picker.
     */
    suspend fun restoreFromBackupUri(context: Context, db: AppDatabase, uri: Uri): Int = withContext(Dispatchers.IO) {
        val tempZipFile = File(context.cacheDir, "picked_restore_${System.currentTimeMillis()}.zip")
        try {
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: throw IllegalArgumentException("Unable to read selected backup ZIP file.")
            inputStream.use { input ->
                FileOutputStream(tempZipFile).use { output ->
                    input.copyTo(output)
                }
            }
            // Also save a copy into the app's local backups directory so it appears in Backup History
            val backupDir = getBackupDirectory(context)
            val savedCopyName = "BABA_Imported_${SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.getDefault()).format(Date())}.zip"
            tempZipFile.copyTo(File(backupDir, savedCopyName), overwrite = true)

            restoreFromBackupZip(context, db, tempZipFile)
        } finally {
            if (tempZipFile.exists()) {
                tempZipFile.delete()
            }
        }
    }

    /**
     * Copies a generated backup ZIP file to a user-selected destination Uri (e.g., Downloads folder via CreateDocument).
     */
    suspend fun copyBackupZipToUri(context: Context, sourceZip: File, destUri: Uri): Boolean = withContext(Dispatchers.IO) {
        if (!sourceZip.exists()) return@withContext false
        val outStream = context.contentResolver.openOutputStream(destUri) ?: return@withContext false
        outStream.use { output ->
            FileInputStream(sourceZip).use { input ->
                input.copyTo(output)
            }
        }
        true
    }

    private fun isImageFileName(name: String): Boolean {
        val lower = name.lowercase(Locale.ROOT)
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png")
    }

    private fun resolveLocalPhotoFile(context: Context, path: String): File? {
        if (path.isBlank()) return null
        val direct = File(path)
        if (direct.exists() && direct.isFile) return direct
        val inFilesDir = File(context.filesDir, direct.name)
        if (inFilesDir.exists() && inFilesDir.isFile) return inFilesDir
        return null
    }

    private fun zipDirectory(folder: File, parentFolder: String, zos: ZipOutputStream) {
        val files = folder.listFiles() ?: return
        val buffer = ByteArray(4096)
        for (file in files) {
            val entryName = if (parentFolder.isEmpty()) file.name else "$parentFolder/${file.name}"
            if (file.isDirectory) {
                zipDirectory(file, entryName, zos)
            } else {
                FileInputStream(file).use { fis ->
                    zos.putNextEntry(ZipEntry(entryName))
                    var length: Int
                    while (fis.read(buffer).also { length = it } > 0) {
                        zos.write(buffer, 0, length)
                    }
                    zos.closeEntry()
                }
            }
        }
    }

    private fun cleanRollingBackups(backupDir: File) {
        val backups = backupDir.listFiles { file -> file.isFile && file.name.endsWith(".zip") }
            ?: return
        if (backups.size > MAX_RETAINED_BACKUPS) {
            val sorted = backups.sortedBy { it.lastModified() }
            val deleteCount = backups.size - MAX_RETAINED_BACKUPS
            for (i in 0 until deleteCount) {
                sorted[i].delete()
            }
        }
    }

    fun getBackupsList(context: Context): List<BackupFileInfo> {
        val backupDir = getBackupDirectory(context)
        val files = backupDir.listFiles { file -> file.isFile && file.name.endsWith(".zip") }
            ?: return emptyList()
        val displayFmt = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
        return files.sortedByDescending { it.lastModified() }.map { file ->
            BackupFileInfo(
                fileName = file.name,
                file = file,
                sizeBytes = file.length(),
                timestamp = file.lastModified(),
                formattedDate = displayFmt.format(Date(file.lastModified()))
            )
        }
    }

    suspend fun checkAndRunWeeklyBackup(context: Context, db: AppDatabase): Boolean = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastBackup = prefs.getLong(KEY_LAST_BACKUP, 0L)
        val oneWeekMs = 7 * 24 * 3600 * 1000L
        val now = System.currentTimeMillis()
        if (lastBackup == 0L || (now - lastBackup) >= oneWeekMs) {
            try {
                createBackupBundle(context, db)
                true
            } catch (e: Exception) {
                false
            }
        } else {
            false
        }
    }

    fun shareBackupZip(context: Context, zipFile: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            zipFile
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "BABA Traders Backup - ${zipFile.name}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Save or Export BABA Backup ZIP"))
    }
}
