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
    version = 10,
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

        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
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
            }
        }

        fun getDatabase(context: Context, scope: CoroutineScope): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "baba_traders_clean_v2_db"
                )
                    .addMigrations(MIGRATION_9_10)
                    .fallbackToDestructiveMigration()
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
                INSTANCE?.let { database ->
                    scope.launch(Dispatchers.IO) {
                        populateInitialData(database)
                    }
                }
            }

            override fun onOpen(db: SupportSQLiteDatabase) {
                super.onOpen(db)
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
