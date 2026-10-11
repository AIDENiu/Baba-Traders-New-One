package com.example.data.backup

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.example.data.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
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
    private const val MAX_RETAINED_BACKUPS = 4

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
        val dateDisplayFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
        val now = System.currentTimeMillis()
        val timestampStr = dateFormat.format(Date(now))
        val zipFileName = "BABA_Backup_$timestampStr.zip"
        val zipFile = File(backupDir, zipFileName)

        val tempDir = File(context.cacheDir, "backup_temp_$now")
        tempDir.mkdirs()

        try {
            // 1. Export Customers JSON
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
                        put("createdAt", it.createdAt)
                    })
                }
            }
            File(tempDir, "customers.json").writeText(customersJson.toString(2))

            // 2. Export Machines JSON
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
                        put("modelOrSpecs", it.modelOrSpecs)
                    })
                }
            }
            File(tempDir, "machines.json").writeText(machinesJson.toString(2))

            // 3. Export Job Sheets JSON & CSV
            val jobSheets = db.jobSheetDao().getAllJobSheetsList()
            val jobSheetsJson = JSONArray().apply {
                jobSheets.forEach {
                    put(JSONObject().apply {
                        put("id", it.id)
                        put("customerId", it.customerId)
                        put("customerName", it.customerName)
                        put("customerPhone", it.customerPhone)
                        put("machineId", it.machineId)
                        put("machineName", it.machineName)
                        put("categoryName", it.categoryName)
                        put("quantity", it.quantity)
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
                        put("lateFee", it.lateFee)
                        put("totalRentalFee", it.totalRentalFee)
                        put("finalSettlement", it.finalSettlement)
                        put("notes", it.notes)
                        put("isDeleted", it.isDeleted)
                    })
                }
            }
            File(tempDir, "job_sheets.json").writeText(jobSheetsJson.toString(2))

            // CSV Export
            val csvBuilder = StringBuilder()
            csvBuilder.append("JobSheet_ID,Customer_Name,Customer_Phone,Machine_Name,Qty,Daily_Rate,Discount,Deposit,Checkout_Date,Due_Date,Return_Date,Status,Checked_Out_By,Returned_To,Total_Fee,Final_Settlement\n")
            val dateFmt = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
            jobSheets.forEach { job ->
                val checkoutStr = dateFmt.format(Date(job.checkoutTime))
                val dueStr = dateFmt.format(Date(job.dueTime))
                val returnStr = job.returnTime?.let { dateFmt.format(Date(it)) } ?: "N/A"
                csvBuilder.append("\"${job.id}\",\"${job.customerName}\",\"${job.customerPhone}\",\"${job.machineName}\",${job.quantity},${job.rateUsed},${job.discount},${job.depositAmount},\"$checkoutStr\",\"$dueStr\",\"$returnStr\",${job.status},\"${job.checkoutByName}\",\"${job.returnedByName}\",${job.totalRentalFee},${job.finalSettlement}\n")
            }
            File(tempDir, "rental_records.csv").writeText(csvBuilder.toString())

            // 4. Export Payments JSON
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
                    })
                }
            }
            File(tempDir, "payments.json").writeText(paymentsJson.toString(2))

            // 5. Export Activity Log JSON
            val logs = db.activityLogDao().getAllLogsList()
            val logsJson = JSONArray().apply {
                logs.forEach {
                    put(JSONObject().apply {
                        put("id", it.id)
                        put("jobSheetId", it.jobSheetId)
                        put("action", it.action)
                        put("performedBy", it.performedBy)
                        put("performedByName", it.performedByName)
                        put("timestamp", it.timestamp)
                        put("changeSummary", it.changeSummary)
                    })
                }
            }
            File(tempDir, "activity_log.json").writeText(logsJson.toString(2))

            // 6. Copy Local ID Proof Photos into tempDir/id_photos
            val idPhotosDir = File(tempDir, "id_photos")
            idPhotosDir.mkdirs()
            val filesInStorage = context.filesDir.listFiles() ?: emptyArray()
            for (file in filesInStorage) {
                if (file.isFile && (file.name.endsWith(".jpg") || file.name.endsWith(".jpeg") || file.name.endsWith(".png"))) {
                    file.copyTo(File(idPhotosDir, file.name), overwrite = true)
                }
            }

            // 7. Zip all files into BABA_Backup_*.zip
            ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use { outZip ->
                zipDirectory(tempDir, "", outZip)
            }

            // 8. Update last backup timestamp
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putLong(KEY_LAST_BACKUP, now)
                .apply()

            // 9. Rolling cleanup: keep only last MAX_RETAINED_BACKUPS (4)
            cleanRollingBackups(backupDir)

        } finally {
            tempDir.deleteRecursively()
        }

        zipFile
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
            // Sort oldest first
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
