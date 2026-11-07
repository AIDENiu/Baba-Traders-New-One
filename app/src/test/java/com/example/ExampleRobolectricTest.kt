package com.example

import android.app.AlarmManager
import android.content.Context
import android.database.sqlite.SQLiteException
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.backup.BackupManager
import com.example.data.model.CustomerEntity
import com.example.data.model.JobSheetEntity
import com.example.data.model.MachineCategoryEntity
import com.example.data.model.MachineEntity
import com.example.data.model.SelectedMachineItem
import com.example.data.model.UserEntity
import com.example.data.repository.BabaRepository
import com.example.util.DueAlarmScheduler
import com.example.util.PdfGenerator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.io.File
import java.util.zip.ZipFile

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Baba Traders", appName)
    }

    @Test
    fun `launch MainActivity`() {
        val activityController = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = activityController.get()
        assertNotNull(activity)
    }

    @Test
    fun `verify return calculation formula - 1 day rental`() {
        val now = System.currentTimeMillis()
        val jobSheet = JobSheetEntity(
            id = "JOB-0412",
            customerId = "c1",
            customerName = "Ravi",
            customerPhone = "9876543210",
            customerAddress = "Main Road",
            machineId = "m1",
            machineName = "Drill",
            categoryName = "Power Tools",
            quantity = 1,
            rateUsed = 500.0,
            discount = 0.0,
            depositAmount = 500.0,
            checkoutTime = now,
            dueTime = now + 24 * 3600 * 1000L,
            handledBy = "u1",
            handledByName = "Worker"
        )

        val daysUsed = BabaRepository.calculateDaysUsed(jobSheet.checkoutTime, now + 3600 * 1000L)
        assertEquals(1, daysUsed)
    }

    @Test
    fun `verify 2 machines for 2 days calculation does not double multiply quantity`() {
        val now = System.currentTimeMillis()
        val twoDaysLater = now + 2 * 24 * 3600 * 1000L
        val jobSheet = JobSheetEntity(
            id = "JOB-0413",
            customerId = "c1",
            customerName = "Ravi",
            customerPhone = "9876543210",
            customerAddress = "Main Road",
            machineId = "m1,m2",
            machineName = "1x Cutter, 1x Water Motor",
            categoryName = "Cutters + Water Motors",
            quantity = 2,
            initialQuantity = 2,
            returnedQuantity = 0,
            rateUsed = 750.0,
            discount = 50.0,
            depositAmount = 500.0,
            checkoutTime = now,
            dueTime = twoDaysLater,
            handledBy = "u1",
            handledByName = "Worker"
        )

        val bookedDays = BabaRepository.calculateBookedDays(jobSheet.checkoutTime, jobSheet.dueTime)
        assertEquals(2, bookedDays)

        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = AppDatabase.getDatabase(context, CoroutineScope(Dispatchers.Unconfined))
        val repo = BabaRepository(db)

        val bill = repo.calculateReturnBill(jobSheet, returningQuantity = 2, manualDiscount = 0.0)
        assertEquals(2, bill.daysUsed)
        assertEquals(750.0, bill.dailyRate, 0.01)
        assertEquals(1450.0, bill.netTotalFee, 0.01)
        assertEquals(950.0, bill.balanceDueOrRefund, 0.01)
    }

    @Test
    fun `verify 5_5 by 8_5 inch PDF invoice generation and 8 AM cycle cutoff`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val cal = java.util.Calendar.getInstance().apply {
            set(2026, java.util.Calendar.MARCH, 10, 9, 30, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val checkout = cal.timeInMillis
        val next8Am = BabaRepository.computeCutoffTimeForDays(checkout, 1)
        assertEquals(1, BabaRepository.calculateDaysUsed(checkout, next8Am - 30 * 60 * 1000L))
        assertEquals(2, BabaRepository.calculateDaysUsed(checkout, next8Am + 30 * 60 * 1000L))

        val splitParsed = BabaRepository.parsePaymentMethod("split:1000:2000", 3000.0)
        assertTrue(splitParsed.isSplit)
        assertEquals(1000.0, splitParsed.cashAmount, 0.01)
        assertEquals(2000.0, splitParsed.gpayAmount, 0.01)

        val jobSheet = JobSheetEntity(
            id = "JOB-5585",
            customerId = "c1",
            customerName = "Suresh Kumar",
            customerPhone = "9876543210",
            customerAddress = "Industrial Area",
            machineId = "m1",
            machineName = "Concrete Mixer",
            categoryName = "Mixers",
            quantity = 1,
            rateUsed = 3000.0,
            discount = 0.0,
            depositAmount = 3000.0,
            paymentMode = splitParsed.displayMode,
            checkoutTime = checkout,
            dueTime = next8Am,
            handledBy = "u1",
            handledByName = "Naveen"
        )

        val pdfFile = PdfGenerator.generateJobSheetPdf(context, jobSheet)
        assertNotNull(pdfFile)
        assertTrue(pdfFile.exists())
        assertTrue(pdfFile.length() > 0)
        assertEquals(396, PdfGenerator.PAGE_WIDTH)
        assertEquals(612, PdfGenerator.PAGE_HEIGHT)
    }

    @Test
    fun `verify full 12-item business checklist, SQLite immutability triggers, alarms, and backup ZIP with photos`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = AppDatabase.getDatabase(context, CoroutineScope(Dispatchers.Unconfined))
        AppDatabase.ensureSeedData(db)
        val repo = BabaRepository(db)

        val owner = UserEntity(
            id = "user_owner_naveen",
            name = "Naveen",
            phoneOrUsername = "rupak2472@gmail.com",
            pin = "1234",
            role = "owner",
            isActive = true,
            maxDiscountAmount = 1000.0
        )
        val worker = UserEntity(
            id = "user_worker_suresh",
            name = "Suresh",
            phoneOrUsername = "suresh@gmail.com",
            pin = "0000",
            role = "worker",
            isActive = true,
            maxDiscountAmount = 200.0
        )
        repo.insertUser(owner)
        repo.insertUser(worker)

        // Setup Category & 2 Machines (Stock in Hand test)
        val cat = MachineCategoryEntity(id = "cat_power", name = "Power Tools", iconName = "build")
        repo.insertCategory(cat)
        val mach1 = MachineEntity(
            id = "mach_breaker_1",
            categoryId = "cat_power",
            name = "16kg Breaker",
            baseRate = 600.0,
            currentRate = 600.0,
            totalQty = 5,
            availableQty = 5
        )
        val mach2 = MachineEntity(
            id = "mach_cutter_2",
            categoryId = "cat_power",
            name = "Marble Cutter",
            baseRate = 300.0,
            currentRate = 300.0,
            totalQty = 4,
            availableQty = 4
        )
        repo.insertMachine(mach1)
        repo.insertMachine(mach2)

        // Create local photo files on device to verify photo handling & Backup ZIP inclusion
        val photosDir = File(context.filesDir, "photos").apply { mkdirs() }
        val personPhoto = File(photosDir, "person_test_1.jpg").apply { writeBytes(ByteArray(128) { 0x42 }) }
        val idPhoto = File(photosDir, "id_test_1.jpg").apply { writeBytes(ByteArray(128) { 0x43 }) }
        val machinePhoto = File(photosDir, "machine_test_1.jpg").apply { writeBytes(ByteArray(128) { 0x44 }) }

        // ITEM 1 & 6 & 9 & 10: Create Job Sheet with customer, photos, estimate, advance, payment mode, due date, created-by
        val customer = CustomerEntity(
            id = "cust_9876543210",
            name = "Ramesh Babu",
            phone = "9876543210",
            address = "MG Road, Bengaluru",
            personPhotoPath = personPhoto.absolutePath,
            idPhotoFrontPath = idPhoto.absolutePath
        )
        val items = listOf(
            SelectedMachineItem(
                machineId = mach1.id,
                machineName = mach1.name,
                categoryId = cat.id,
                categoryName = cat.name,
                quantity = 2,
                rate = 600.0
            ),
            SelectedMachineItem(
                machineId = mach2.id,
                machineName = mach2.name,
                categoryId = cat.id,
                categoryName = cat.name,
                quantity = 1,
                rate = 300.0
            )
        )
        val now = System.currentTimeMillis()
        val dueTime = now + 24 * 3600 * 1000L

        val createdJob = repo.createJobSheet(
            customer = customer,
            items = items,
            discount = 100.0,
            depositAmount = 500.0,
            depositMethod = "cash",
            billingCycle = "daily",
            checkoutUser = worker,
            handledUser = worker,
            notes = "Handle with care",
            personPhotoPath = personPhoto.absolutePath,
            idPhotoPath = idPhoto.absolutePath,
            machinePhotoPath = machinePhoto.absolutePath,
            explicitDueTime = dueTime,
            context = context,
            explicitJobId = "JOB-1001"
        )
        assertEquals("JOB-1001", createdJob.id)
        assertEquals("Suresh", createdJob.checkoutByName)
        assertEquals("cash", createdJob.paymentMode.lowercase())
        assertEquals(personPhoto.absolutePath, createdJob.personPhotoPath)
        assertEquals(idPhoto.absolutePath, createdJob.idPhotoPath)
        assertEquals(machinePhoto.absolutePath, createdJob.machinePhotoPath)

        // ITEM 9: Verify Stock in Hand decremented accurately (mach1: 5 -> 3, mach2: 4 -> 3)
        assertEquals(3, repo.getMachineById(mach1.id)?.availableQty)
        assertEquals(3, repo.getMachineById(mach2.id)?.availableQty)

        // ITEM 10: Verify Due-date Alarm scheduled in AlarmManager (works offline & reschedules on boot)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val shadowAlarmManager = Shadows.shadowOf(alarmManager)
        assertTrue("Expected scheduled due-date alarms", shadowAlarmManager.scheduledAlarms.isNotEmpty())
        DueAlarmScheduler.rescheduleAllActiveAlarms(context, db)
        assertTrue("Expected alarms after boot reschedule", shadowAlarmManager.scheduledAlarms.isNotEmpty())

        // ITEM 2: Add extra payment (₹300 GPay) -> depositAmount becomes 500 + 300 = 800
        val extraOk = repo.addExtraPayment(
            jobSheetId = createdJob.id,
            amount = 300.0,
            paymentMode = "gpay",
            currentUser = worker,
            remark = "Mid-rental top-up"
        )
        assertTrue(extraOk)
        val afterExtra = db.jobSheetDao().getJobSheetById(createdJob.id)!!
        assertEquals(800.0, afterExtra.depositAmount, 0.01)

        // Worker discount cap enforcement on return: worker cannot apply additional ₹150 when ₹100 already given (total 250 > 200 cap)
        try {
            repo.markReturned(
                jobSheetId = createdJob.id,
                settlementMethod = "cash",
                currentUser = worker,
                manualDiscount = 150.0,
                remark = "Try excessive discount",
                context = context,
                overrideDaysUsed = 1
            )
            fail("Expected IllegalArgumentException when worker cumulative discount exceeds ₹200 cap")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("cannot exceed"))
        }

        // ITEM 6: Close/return job sheet by Owner and verify closedByName, paymentMode, discount, and stock restoration
        val returnedJob = repo.markReturned(
            jobSheetId = createdJob.id,
            settlementMethod = "cash",
            currentUser = owner,
            manualDiscount = 50.0, // Total discount = 100 + 50 = 150; 1 day * 1500 = 1500 - 150 = 1350 net
            remark = "Returned in good condition",
            context = context,
            overrideDaysUsed = 1
        )!!
        assertEquals("closed", returnedJob.status)
        assertEquals("Naveen", returnedJob.closedByName)
        assertEquals("Suresh", returnedJob.checkoutByName)
        assertEquals(150.0, returnedJob.discount, 0.01)
        assertEquals(1350.0, returnedJob.totalRentalFee, 0.01)
        assertEquals(5, repo.getMachineById(mach1.id)?.availableQty)
        assertEquals(4, repo.getMachineById(mach2.id)?.availableQty)

        // ITEM 3: Return with remark & NIL in sales report when no payment (0 days / no work done)
        val nilJob = repo.createJobSheet(
            customer = customer,
            items = listOf(items.first()),
            discount = 0.0,
            depositAmount = 0.0,
            depositMethod = "cash",
            billingCycle = "daily",
            checkoutUser = worker,
            handledUser = worker,
            notes = "",
            explicitDueTime = dueTime,
            context = context,
            explicitJobId = "JOB-1002"
        )
        val closedNilJob = repo.markReturned(
            jobSheetId = nilJob.id,
            settlementMethod = "cash",
            currentUser = worker,
            manualDiscount = 0.0,
            remark = "Site power failure - machine unused",
            context = context,
            overrideDaysUsed = 0
        )!!
        assertEquals("closed", closedNilJob.status)
        assertEquals(0.0, closedNilJob.totalRentalFee, 0.01)
        assertEquals("Site power failure - machine unused", closedNilJob.returnRemark)

        // ITEM 4: No edit and no delete of job sheets, even by direct database / SQLite calls!
        try {
            repo.editJobSheet("JOB-1001", 5, 100.0, 0.0, "Tampered", owner)
            fail("Expected IllegalStateException on editJobSheet")
        } catch (expected: IllegalStateException) {
            assertTrue(expected.message!!.contains("strictly prohibited"))
        }
        try {
            repo.softDeleteJobSheet("JOB-1001", "Try delete", owner)
            fail("Expected IllegalStateException on softDeleteJobSheet")
        } catch (expected: IllegalStateException) {
            assertTrue(expected.message!!.contains("strictly prohibited"))
        }

        val sqliteDb = db.openHelper.writableDatabase
        try {
            sqliteDb.execSQL("DELETE FROM job_sheets WHERE id = 'JOB-1001'")
            fail("Expected SQLite trigger to block DELETE on job_sheets")
        } catch (expected: SQLiteException) {
            assertTrue(expected.message!!.contains("Deleting job sheets is strictly prohibited"))
        }
        try {
            sqliteDb.execSQL("UPDATE job_sheets SET isDeleted = 1 WHERE id = 'JOB-1001'")
            fail("Expected SQLite trigger to block soft-delete on job_sheets")
        } catch (expected: SQLiteException) {
            assertTrue(expected.message!!.contains("Soft-deleting job sheets is strictly prohibited"))
        }
        try {
            sqliteDb.execSQL("UPDATE job_sheets SET rateUsed = 50.0 WHERE id = 'JOB-1001'")
            fail("Expected SQLite trigger to block editing core fields on job_sheets")
        } catch (expected: SQLiteException) {
            assertTrue(expected.message!!.contains("Editing immutable job sheet core fields is strictly prohibited"))
        }
        try {
            sqliteDb.execSQL("UPDATE payments SET amount = 1.0")
            fail("Expected SQLite trigger to block UPDATE on payments")
        } catch (expected: SQLiteException) {
            assertTrue(expected.message!!.contains("Payments are immutable and cannot be updated"))
        }
        try {
            sqliteDb.execSQL("UPDATE activity_log SET action = 'tampered'")
            fail("Expected SQLite trigger to block UPDATE on activity_log")
        } catch (expected: SQLiteException) {
            assertTrue(expected.message!!.contains("Activity logs are immutable and cannot be updated"))
        }

        // ITEM 12: Opening and Closing Cash Report
        repo.saveOpeningCash("2026-11-07", 2000.0, owner)
        repo.saveClosingCash("2026-11-07", 3550.0, owner)
        assertEquals(2000.0, repo.getOpeningCashForDate("2026-11-07"), 0.01)
        val cashRec = db.dailyCashDao().getDailyCash("2026-11-07")
        assertNotNull(cashRec)
        assertEquals(3550.0, cashRec!!.closingCash, 0.01)

        // BACKUP ZIP & RESTORE VERIFICATION: Verify Backup ZIP contains all JSON tables + all local photos, and restores cleanly into a fresh DB
        val backupZip = BackupManager.createBackupBundle(context, db)
        assertTrue(backupZip.exists())
        ZipFile(backupZip).use { zip ->
            val entryNames = zip.entries().asSequence().map { it.name }.toSet()
            assertTrue(entryNames.contains("users.json"))
            assertTrue(entryNames.contains("customers.json"))
            assertTrue(entryNames.contains("categories.json"))
            assertTrue(entryNames.contains("machines.json"))
            assertTrue(entryNames.contains("job_sheets.json"))
            assertTrue(entryNames.contains("payments.json"))
            assertTrue(entryNames.contains("activity_log.json"))
            assertTrue(entryNames.contains("expenses.json"))
            assertTrue(entryNames.contains("daily_cash.json"))
            assertTrue(entryNames.contains("id_photos/person_test_1.jpg"))
            assertTrue(entryNames.contains("id_photos/id_test_1.jpg"))
            assertTrue(entryNames.contains("id_photos/machine_test_1.jpg"))
        }

        // Delete local photo files and restore into a brand-new database to prove full ZIP restore
        personPhoto.delete()
        idPhoto.delete()
        machinePhoto.delete()

        val restoredDb = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .addCallback(object : RoomDatabase.Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    super.onCreate(db)
                    AppDatabase.installImmutabilityTriggers(db)
                }
            })
            .build()

        try {
            val restoredCount = BackupManager.restoreFromBackupZip(context, restoredDb, backupZip)
            assertTrue("Expected restoredCount > 0", restoredCount > 0)
            assertNotNull(restoredDb.customerDao().getCustomerById("cust_9876543210"))
            assertNotNull(restoredDb.machineDao().getMachineById("mach_breaker_1"))
            assertNotNull(restoredDb.jobSheetDao().getJobSheetById("JOB-1001"))
            assertNotNull(restoredDb.jobSheetDao().getJobSheetById("JOB-1002"))
            assertTrue(restoredDb.paymentDao().getAllPaymentsList().isNotEmpty())
            assertTrue(restoredDb.activityLogDao().getAllLogsList().isNotEmpty())
            assertNotNull(restoredDb.dailyCashDao().getDailyCash("2026-11-07"))
            assertTrue("Expected person photo restored to filesDir", File(context.filesDir, "person_test_1.jpg").exists())
            assertTrue("Expected ID photo restored to filesDir", File(context.filesDir, "id_test_1.jpg").exists())
            assertTrue("Expected machine photo restored to filesDir", File(context.filesDir, "machine_test_1.jpg").exists())

            // Verify idempotency: running restore a second time adds 0 duplicate records and does not violate triggers
            val secondRestoreCount = BackupManager.restoreFromBackupZip(context, restoredDb, backupZip)
            assertEquals(0, secondRestoreCount)
        } finally {
            restoredDb.close()
        }
    }
}
