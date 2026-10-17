package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
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
        DailyCashEntity::class
    ],
    version = 8,
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

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context, scope: CoroutineScope): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "baba_traders_db"
                )
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
            val machineDao = db.machineDao()

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

            machineDao.insertMachine(MachineEntity("mach_1", "cat_cutters", "Concrete Cutter Pro", 500.0, 500.0, 8, 8, "Heavy duty 14-inch blade"))
            machineDao.insertMachine(MachineEntity("mach_2", "cat_cutters", "Circular Saw Heavy", 350.0, 350.0, 10, 10, "1800W motor"))
            machineDao.insertMachine(MachineEntity("mach_3", "cat_pumps", "Submersible Pump 2HP", 400.0, 400.0, 6, 6, "High head water discharge"))
            machineDao.insertMachine(MachineEntity("mach_4", "cat_motors", "Water Motor 1HP", 300.0, 300.0, 12, 12, "Copper winding"))
            machineDao.insertMachine(MachineEntity("mach_5", "cat_ladders", "Aluminum Ladder 20ft", 250.0, 250.0, 15, 15, "Extension ladder"))
            machineDao.insertMachine(MachineEntity("mach_6", "cat_generators", "Diesel Generator 5KVA", 1200.0, 1200.0, 4, 4, "Silent canopy"))
        }
    }
}
