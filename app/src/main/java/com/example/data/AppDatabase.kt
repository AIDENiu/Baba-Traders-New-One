package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.dao.*
import com.example.data.model.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        UserEntity::class,
        MachineCategoryEntity::class,
        MachineEntity::class,
        CustomerEntity::class,
        JobSheetEntity::class,
        ActivityLogEntity::class,
        PaymentEntity::class,
        DailyCashEntity::class,
        ExpenseEntity::class
    ],
    version = 11,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun customerDao(): CustomerDao
    abstract fun machineCategoryDao(): MachineCategoryDao
    abstract fun machineDao(): MachineDao
    abstract fun jobSheetDao(): JobSheetDao
    abstract fun activityLogDao(): ActivityLogDao
    abstract fun paymentDao(): PaymentDao
    abstract fun dailyCashDao(): DailyCashDao
    abstract fun expenseDao(): ExpenseDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private fun hasColumn(db: SupportSQLiteDatabase, tableName: String, columnName: String): Boolean {
            val cursor = db.query("PRAGMA table_info(`$tableName`)")
            cursor.use {
                val nameIndex = it.getColumnIndex("name")
                while (it.moveToNext()) {
                    if (nameIndex >= 0 && it.getString(nameIndex).equals(columnName, ignoreCase = true)) {
                        return true
                    }
                }
            }
            return false
        }

        private fun addColumnIfMissing(
            db: SupportSQLiteDatabase,
            tableName: String,
            columnName: String,
            columnDef: String
        ) {
            if (!hasColumn(db, tableName, columnName)) {
                db.execSQL("ALTER TABLE `$tableName` ADD COLUMN `$columnName` $columnDef")
            }
        }

        private fun migrateNonDestructivelyToV11(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `users` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `phoneOrUsername` TEXT NOT NULL, `pin` TEXT NOT NULL, `role` TEXT NOT NULL, `isActive` INTEGER NOT NULL, `maxDiscountPercent` REAL NOT NULL, `maxDiscountAmount` REAL NOT NULL, `registeredPhone` TEXT NOT NULL, `phone` TEXT NOT NULL, PRIMARY KEY(`id`))")
            db.execSQL("CREATE TABLE IF NOT EXISTS `categories` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `iconName` TEXT NOT NULL, PRIMARY KEY(`id`))")
            db.execSQL("CREATE TABLE IF NOT EXISTS `machines` (`id` TEXT NOT NULL, `categoryId` TEXT NOT NULL, `name` TEXT NOT NULL, `baseRate` REAL NOT NULL, `currentRate` REAL NOT NULL, `totalQty` INTEGER NOT NULL, `availableQty` INTEGER NOT NULL, `modelOrSpecs` TEXT NOT NULL, `maintenanceQty` INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(`id`))")
            db.execSQL("CREATE TABLE IF NOT EXISTS `customers` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `phone` TEXT NOT NULL, `address` TEXT NOT NULL, `idPhotoFrontPath` TEXT, `idPhotoBackPath` TEXT, `personPhotoPath` TEXT, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
            db.execSQL("CREATE TABLE IF NOT EXISTS `job_sheets` (`id` TEXT NOT NULL, `customerId` TEXT NOT NULL, `customerName` TEXT NOT NULL, `customerPhone` TEXT NOT NULL, `customerAddress` TEXT NOT NULL, `machineId` TEXT NOT NULL, `machineName` TEXT NOT NULL, `categoryName` TEXT NOT NULL, `quantity` INTEGER NOT NULL, `rateUsed` REAL NOT NULL, `discount` REAL NOT NULL, `depositAmount` REAL NOT NULL, `billingCycle` TEXT NOT NULL, `checkoutTime` INTEGER NOT NULL, `dueTime` INTEGER NOT NULL, `returnTime` INTEGER, `status` TEXT NOT NULL, `lateFee` REAL NOT NULL, `totalRentalFee` REAL NOT NULL, `finalSettlement` REAL NOT NULL, `checkoutBy` TEXT NOT NULL, `checkoutByName` TEXT NOT NULL, `handledBy` TEXT NOT NULL, `handledByName` TEXT NOT NULL, `returnedBy` TEXT NOT NULL, `returnedByName` TEXT NOT NULL, `closedBy` TEXT NOT NULL, `closedByName` TEXT NOT NULL, `notes` TEXT NOT NULL, `returnRemark` TEXT NOT NULL, `isDeleted` INTEGER NOT NULL, `initialQuantity` INTEGER NOT NULL, `returnedQuantity` INTEGER NOT NULL, `personPhotoPath` TEXT, `idPhotoPath` TEXT, `machinePhotoPath` TEXT, `paymentMode` TEXT NOT NULL, `itemsJson` TEXT NOT NULL, PRIMARY KEY(`id`))")
            db.execSQL("CREATE TABLE IF NOT EXISTS `activity_log` (`id` TEXT NOT NULL, `jobSheetId` TEXT NOT NULL, `action` TEXT NOT NULL, `performedBy` TEXT NOT NULL, `performedByName` TEXT NOT NULL, `changeSummary` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, `handledByUid` TEXT NOT NULL, `handledByName` TEXT NOT NULL, PRIMARY KEY(`id`))")
            db.execSQL("CREATE TABLE IF NOT EXISTS `payments` (`id` TEXT NOT NULL, `jobSheetId` TEXT NOT NULL, `amount` REAL NOT NULL, `method` TEXT NOT NULL, `type` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, `handledBy` TEXT NOT NULL, `handledByName` TEXT NOT NULL, `remark` TEXT NOT NULL, PRIMARY KEY(`id`))")
            db.execSQL("CREATE TABLE IF NOT EXISTS `daily_cash` (`date` TEXT NOT NULL, `openingCash` REAL NOT NULL, `closingCash` REAL NOT NULL, `enteredBy` TEXT NOT NULL, `enteredByName` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL, `openingBalance` REAL NOT NULL, `cashCollected` REAL NOT NULL, `cashRefunded` REAL NOT NULL, `notes` TEXT NOT NULL, PRIMARY KEY(`date`))")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `expenses` (
                    `id` TEXT NOT NULL,
                    `date` TEXT NOT NULL,
                    `timestamp` INTEGER NOT NULL,
                    `expenseType` TEXT NOT NULL,
                    `amount` REAL NOT NULL,
                    `remarks` TEXT NOT NULL,
                    `createdBy` TEXT NOT NULL,
                    `createdByName` TEXT NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )

            addColumnIfMissing(db, "machines", "maintenanceQty", "INTEGER NOT NULL DEFAULT 0")

            addColumnIfMissing(db, "customers", "idPhotoFrontPath", "TEXT")
            addColumnIfMissing(db, "customers", "idPhotoBackPath", "TEXT")
            addColumnIfMissing(db, "customers", "personPhotoPath", "TEXT")
            addColumnIfMissing(db, "customers", "createdAt", "INTEGER NOT NULL DEFAULT 0")

            addColumnIfMissing(db, "job_sheets", "initialQuantity", "INTEGER NOT NULL DEFAULT 0")
            addColumnIfMissing(db, "job_sheets", "returnedQuantity", "INTEGER NOT NULL DEFAULT 0")
            addColumnIfMissing(db, "job_sheets", "personPhotoPath", "TEXT")
            addColumnIfMissing(db, "job_sheets", "idPhotoPath", "TEXT")
            addColumnIfMissing(db, "job_sheets", "machinePhotoPath", "TEXT")
            addColumnIfMissing(db, "job_sheets", "paymentMode", "TEXT NOT NULL DEFAULT 'cash'")
            addColumnIfMissing(db, "job_sheets", "itemsJson", "TEXT NOT NULL DEFAULT ''")

            installImmutabilityTriggers(db)
        }

        fun installImmutabilityTriggers(db: SupportSQLiteDatabase) {
            // 1. Prevent hard DELETE on job_sheets
            db.execSQL(
                """
                CREATE TRIGGER IF NOT EXISTS trg_prevent_jobsheet_delete
                BEFORE DELETE ON job_sheets
                BEGIN
                    SELECT RAISE(ABORT, 'Deleting job sheets is strictly prohibited');
                END
                """.trimIndent()
            )
            // 2. Prevent soft-delete (isDeleted = 1) on job_sheets
            db.execSQL(
                """
                CREATE TRIGGER IF NOT EXISTS trg_prevent_jobsheet_soft_delete
                BEFORE UPDATE ON job_sheets
                WHEN NEW.isDeleted != 0
                BEGIN
                    SELECT RAISE(ABORT, 'Soft-deleting job sheets is strictly prohibited');
                END
                """.trimIndent()
            )
            // 3. Prevent editing immutable core checkout fields on job_sheets
            db.execSQL(
                """
                CREATE TRIGGER IF NOT EXISTS trg_prevent_jobsheet_core_edit
                BEFORE UPDATE ON job_sheets
                WHEN NEW.id != OLD.id
                  OR NEW.customerId != OLD.customerId
                  OR NEW.machineId != OLD.machineId
                  OR NEW.rateUsed != OLD.rateUsed
                  OR NEW.checkoutTime != OLD.checkoutTime
                BEGIN
                    SELECT RAISE(ABORT, 'Editing immutable job sheet core fields is strictly prohibited');
                END
                """.trimIndent()
            )
            // 4. Prevent UPDATE or DELETE on append-only payments ledger
            db.execSQL(
                """
                CREATE TRIGGER IF NOT EXISTS trg_prevent_payment_update
                BEFORE UPDATE ON payments
                BEGIN
                    SELECT RAISE(ABORT, 'Payments are immutable and cannot be updated');
                END
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TRIGGER IF NOT EXISTS trg_prevent_payment_delete
                BEFORE DELETE ON payments
                BEGIN
                    SELECT RAISE(ABORT, 'Payments are immutable and cannot be deleted');
                END
                """.trimIndent()
            )
            // 5. Prevent UPDATE or DELETE on append-only activity_log ledger
            db.execSQL(
                """
                CREATE TRIGGER IF NOT EXISTS trg_prevent_activity_log_update
                BEFORE UPDATE ON activity_log
                BEGIN
                    SELECT RAISE(ABORT, 'Activity logs are immutable and cannot be updated');
                END
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TRIGGER IF NOT EXISTS trg_prevent_activity_log_delete
                BEFORE DELETE ON activity_log
                BEGIN
                    SELECT RAISE(ABORT, 'Activity logs are immutable and cannot be deleted');
                END
                """.trimIndent()
            )
        }

        private val ALL_MIGRATIONS: Array<Migration> = (1..10).map { startVer ->
            object : Migration(startVer, 11) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    migrateNonDestructivelyToV11(db)
                }
            }
        }.toTypedArray()

        fun getDatabase(context: Context, scope: CoroutineScope): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "baba_traders_clean_v2_db"
                )
                    .addMigrations(*ALL_MIGRATIONS)
                    .addCallback(DatabaseCallback(scope))
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private class DatabaseCallback(
            private val scope: CoroutineScope
        ) : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                installImmutabilityTriggers(db)
                INSTANCE?.let { database ->
                    scope.launch(Dispatchers.IO) {
                        populateInitialData(database)
                    }
                }
            }

            override fun onOpen(db: SupportSQLiteDatabase) {
                super.onOpen(db)
                installImmutabilityTriggers(db)
                INSTANCE?.let { database ->
                    scope.launch(Dispatchers.IO) {
                        ensureSeedData(database)
                    }
                }
            }
        }

        suspend fun ensureSeedData(db: AppDatabase) {
            if (db.userDao().getUserCount() == 0) {
                populateInitialData(db)
            }
        }

        suspend fun populateInitialData(db: AppDatabase) {
            val userDao = db.userDao()
            val categoryDao = db.machineCategoryDao()

            userDao.insertUser(
                UserEntity(
                    id = "user_owner_naveen",
                    name = "Naveen",
                    phoneOrUsername = "naveen",
                    role = "owner",
                    phone = "9876543210",
                    registeredPhone = "9876543210",
                    pin = "1234",
                    isActive = true,
                    maxDiscountPercent = 20.0,
                    maxDiscountAmount = 500.0
                )
            )
            userDao.insertUser(
                UserEntity(
                    id = "user_worker_suresh",
                    name = "Suresh",
                    phoneOrUsername = "suresh",
                    role = "worker",
                    phone = "9876543211",
                    registeredPhone = "9876543211",
                    pin = "0000",
                    isActive = true,
                    maxDiscountPercent = 10.0,
                    maxDiscountAmount = 200.0
                )
            )

            categoryDao.insertCategory(MachineCategoryEntity("cat_cutters", "Cutters & Saws", "saw"))
            categoryDao.insertCategory(MachineCategoryEntity("cat_pumps", "Water Pumps", "water_drop"))
            categoryDao.insertCategory(MachineCategoryEntity("cat_motors", "Water Motors", "bolt"))
            categoryDao.insertCategory(MachineCategoryEntity("cat_ladders", "Ladders", "stairs"))
            categoryDao.insertCategory(MachineCategoryEntity("cat_generators", "Generators", "power"))
        }
    }
}
