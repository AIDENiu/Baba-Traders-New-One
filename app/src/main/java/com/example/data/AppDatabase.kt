package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.dao.ActivityLogDao
import com.example.data.dao.CustomerDao
import com.example.data.dao.JobSheetDao
import com.example.data.dao.MachineDao
import com.example.data.dao.PaymentDao
import com.example.data.dao.UserDao
import com.example.data.model.ActivityLogEntity
import com.example.data.model.CustomerEntity
import com.example.data.model.JobSheetEntity
import com.example.data.model.MachineCategoryEntity
import com.example.data.model.MachineEntity
import com.example.data.model.PaymentEntity
import com.example.data.model.UserEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        UserEntity::class,
        CustomerEntity::class,
        MachineCategoryEntity::class,
        MachineEntity::class,
        JobSheetEntity::class,
        PaymentEntity::class,
        ActivityLogEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun customerDao(): CustomerDao
    abstract fun machineDao(): MachineDao
    abstract fun jobSheetDao(): JobSheetDao
    abstract fun paymentDao(): PaymentDao
    abstract fun activityLogDao(): ActivityLogDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context, scope: CoroutineScope): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "baba_job_sheet.db"
                )
                    .addCallback(DatabaseCallback(scope))
                    .fallbackToDestructiveMigration()
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
                scope.launch(Dispatchers.IO) {
                    db.execSQL("UPDATE users SET name = 'Naveen', phoneOrUsername = 'naveen' WHERE name = 'Navin Anna' OR id = 'user_owner_navin'")
                }
            }

            suspend fun populateInitialData(db: AppDatabase) {
                // 1. Initial Users (Owner Naveen and Worker Suresh)
                db.userDao().insertUser(
                    UserEntity(
                        id = "user_owner_naveen",
                        name = "Naveen",
                        phoneOrUsername = "naveen",
                        pin = "1234",
                        role = "owner",
                        isActive = true,
                        maxDiscountPercent = 15.0,
                        maxDiscountAmount = 200.0
                    )
                )
                db.userDao().insertUser(
                    UserEntity(
                        id = "user_worker_suresh",
                        name = "Suresh",
                        phoneOrUsername = "suresh",
                        pin = "0000",
                        role = "worker",
                        isActive = true
                    )
                )

                // 2. Categories
                val categories = listOf(
                    MachineCategoryEntity("cat_drilling", "Drilling Machines", "drill"),
                    MachineCategoryEntity("cat_ladders", "Ladders", "ladder"),
                    MachineCategoryEntity("cat_motors", "Water Motors", "motor"),
                    MachineCategoryEntity("cat_welding", "Welding Machines", "welder"),
                    MachineCategoryEntity("cat_generators", "Generators & Power", "generator"),
                    MachineCategoryEntity("cat_cutters", "Cutters & Saws", "saw")
                )
                categories.forEach { db.machineDao().insertCategory(it) }

                // 3. Machines with Base and Current rates, stock
                val machines = listOf(
                    MachineEntity("m_1", "cat_drilling", "Heavy Duty Hammer Drill (Rotary)", 350.0, 350.0, 5, 5, "Bosch 800W SDS+"),
                    MachineEntity("m_2", "cat_drilling", "Core Cutting Drill Machine", 650.0, 650.0, 3, 3, "Dongcheng 190mm Diamond"),
                    MachineEntity("m_3", "cat_ladders", "Aluminum Telescopic Ladder 20ft", 250.0, 250.0, 6, 6, "Heavy 150kg load capacity"),
                    MachineEntity("m_4", "cat_ladders", "A-Frame Dual Step Ladder 12ft", 180.0, 180.0, 4, 4, "Aluminum safety lock"),
                    MachineEntity("m_5", "cat_motors", "1.5 HP Submersible Mud/Slurry Pump", 450.0, 450.0, 4, 4, "Kirloskar 2 inch outlet"),
                    MachineEntity("m_6", "cat_motors", "2.0 HP High Flow Dewatering Pump", 550.0, 550.0, 2, 2, "Honda Engine Petrol driven"),
                    MachineEntity("m_7", "cat_welding", "Inverter ARC Welding Machine 250A", 400.0, 400.0, 4, 4, "Single Phase with cables & mask"),
                    MachineEntity("m_8", "cat_generators", "Portable Silent Generator 3.5kVA", 850.0, 850.0, 3, 3, "Honda 4-stroke Petrol"),
                    MachineEntity("m_9", "cat_generators", "Concrete Needle Vibrator 2HP", 300.0, 300.0, 5, 5, "Electric motor + 5m needle"),
                    MachineEntity("m_10", "cat_cutters", "14-inch Heavy Metal Cut-Off Saw", 350.0, 350.0, 4, 4, "Dewalt 2200W")
                )
                machines.forEach { db.machineDao().insertMachine(it) }

                // 4. Sample Customers
                val customers = listOf(
                    CustomerEntity("c_1", "Rathinavel", "9876543210", "12, Gandhi Nagar, Bypass Road"),
                    CustomerEntity("c_2", "Murugan Contractor", "9840112233", "45, East Coast Road, Near Bus Stand"),
                    CustomerEntity("c_3", "Karthik Builders", "9789012345", "8, Industrial Estate, Phase 2")
                )
                customers.forEach { db.customerDao().insertCustomer(it) }

                // 5. Seed an initial active job sheet to demonstrate order management
                val now = System.currentTimeMillis()
                val oneDayAgo = now - 26 * 3600 * 1000L // Overdue by 2 hours
                val sampleJob = JobSheetEntity(
                    id = "JOB-0412",
                    customerId = "c_1",
                    customerName = "Rathinavel",
                    customerPhone = "9876543210",
                    customerAddress = "12, Gandhi Nagar, Bypass Road",
                    machineId = "m_1",
                    machineName = "Heavy Duty Hammer Drill (Rotary)",
                    categoryName = "Drilling Machines",
                    quantity = 1,
                    rateUsed = 350.0,
                    discount = 0.0,
                    depositAmount = 500.0,
                    billingCycle = "24hr",
                    checkoutTime = oneDayAgo,
                    dueTime = oneDayAgo + 24 * 3600 * 1000L,
                    status = "overdue",
                    checkoutBy = "user_owner_naveen",
                    checkoutByName = "Naveen",
                    handledBy = "user_worker_suresh",
                    handledByName = "Suresh",
                    notes = "Rented for house renovation"
                )
                db.jobSheetDao().insertJobSheet(sampleJob)
                db.machineDao().decrementStock("m_1", 1)

                db.activityLogDao().insertLog(
                    ActivityLogEntity(
                        id = "log_init_1",
                        jobSheetId = "JOB-0412",
                        action = "created",
                        performedBy = "user_worker_suresh",
                        performedByName = "Suresh",
                        timestamp = oneDayAgo,
                        changeSummary = "Suresh created job sheet #0412 for Rathinavel (Heavy Duty Hammer Drill, 1 qty, ₹500 deposit)"
                    )
                )
            }
        }
    }
}
