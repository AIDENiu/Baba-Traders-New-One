package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.*
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Query("SELECT * FROM users")
    fun getAllUsers(): Flow<List<UserEntity>>

    @Query("SELECT * FROM users WHERE isActive = 1")
    fun getActiveUsers(): Flow<List<UserEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUsers(users: List<UserEntity>)

    @Update
    suspend fun updateUser(user: UserEntity)

    @Query("SELECT * FROM users WHERE id = :id")
    suspend fun getUserById(id: String): UserEntity?

    @Query("SELECT COUNT(*) FROM users")
    suspend fun getUserCount(): Int
}

@Dao
interface CustomerDao {
    @Query("SELECT * FROM customers WHERE name LIKE '%' || :query || '%' OR phone LIKE '%' || :query || '%'")
    fun searchCustomers(query: String): Flow<List<CustomerEntity>>

    @Query("SELECT * FROM customers WHERE phone = :phone LIMIT 1")
    suspend fun getCustomerByPhone(phone: String): CustomerEntity?

    @Query("SELECT * FROM customers WHERE id = :id")
    suspend fun getCustomerById(id: String): CustomerEntity?

    @Query("SELECT * FROM customers ORDER BY createdAt DESC")
    fun getAllCustomers(): Flow<List<CustomerEntity>>

    @Query("SELECT * FROM customers ORDER BY createdAt DESC")
    suspend fun getAllCustomersList(): List<CustomerEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomer(customer: CustomerEntity)

    @Update
    suspend fun updateCustomer(customer: CustomerEntity)
}

@Dao
interface MachineCategoryDao {
    @Query("SELECT * FROM categories")
    fun getAllCategories(): Flow<List<MachineCategoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(category: MachineCategoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategories(categories: List<MachineCategoryEntity>)
}

@Dao
interface MachineDao {
    @Query("SELECT * FROM categories")
    fun getAllCategories(): Flow<List<MachineCategoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(category: MachineCategoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategories(categories: List<MachineCategoryEntity>)

    @Query("SELECT * FROM machines")
    fun getAllMachines(): Flow<List<MachineEntity>>

    @Query("SELECT * FROM machines")
    suspend fun getAllMachinesList(): List<MachineEntity>

    @Query("SELECT * FROM machines WHERE categoryId = :categoryId")
    fun getMachinesByCategory(categoryId: String): Flow<List<MachineEntity>>

    @Query("SELECT * FROM machines WHERE id = :id")
    suspend fun getMachineById(id: String): MachineEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMachine(machine: MachineEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMachines(machines: List<MachineEntity>)

    @Update
    suspend fun updateMachine(machine: MachineEntity)

    @Query("UPDATE machines SET availableQty = MAX(0, availableQty - :qty) WHERE id = :id")
    suspend fun decrementStock(id: String, qty: Int)

    @Query("UPDATE machines SET availableQty = MIN(totalQty, availableQty + :qty) WHERE id = :id")
    suspend fun incrementStock(id: String, qty: Int)
}

@Dao
interface JobSheetDao {
    @Query("SELECT * FROM job_sheets WHERE isDeleted = 0 ORDER BY checkoutTime DESC")
    fun getAllJobSheets(): Flow<List<JobSheetEntity>>

    @Query("SELECT * FROM job_sheets ORDER BY checkoutTime DESC")
    suspend fun getAllJobSheetsList(): List<JobSheetEntity>

    @Query("SELECT * FROM job_sheets WHERE isDeleted = 0 AND (status = 'active' OR status = 'overdue') ORDER BY checkoutTime DESC")
    fun getActiveJobSheets(): Flow<List<JobSheetEntity>>

    @Query("SELECT * FROM job_sheets WHERE isDeleted = 0 AND (status = 'overdue' OR (status = 'active' AND dueTime < :now)) ORDER BY dueTime ASC")
    fun getOverdueJobSheets(now: Long): Flow<List<JobSheetEntity>>

    @Query("SELECT * FROM job_sheets WHERE isDeleted = 0 AND (status = 'returned' OR status = 'closed') ORDER BY returnTime DESC")
    fun getReturnedJobSheets(): Flow<List<JobSheetEntity>>

    @Query("SELECT * FROM job_sheets WHERE isDeleted = 0 AND customerId = :customerId ORDER BY checkoutTime DESC")
    fun getJobSheetsForCustomer(customerId: String): Flow<List<JobSheetEntity>>

    @Query("SELECT * FROM job_sheets WHERE id = :id")
    suspend fun getJobSheetById(id: String): JobSheetEntity?

    @Query("SELECT COUNT(*) FROM job_sheets")
    suspend fun getTotalCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertJobSheet(jobSheet: JobSheetEntity)

    @Update
    suspend fun updateJobSheet(jobSheet: JobSheetEntity)
}

@Dao
interface ActivityLogDao {
    @Query("SELECT * FROM activity_log ORDER BY timestamp DESC")
    fun getAllLogs(): Flow<List<ActivityLogEntity>>

    @Query("SELECT * FROM activity_log ORDER BY timestamp DESC")
    suspend fun getAllLogsList(): List<ActivityLogEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: ActivityLogEntity)
}

@Dao
interface PaymentDao {
    @Query("SELECT * FROM payments ORDER BY timestamp DESC")
    fun getAllPayments(): Flow<List<PaymentEntity>>

    @Query("SELECT * FROM payments ORDER BY timestamp DESC")
    suspend fun getAllPaymentsList(): List<PaymentEntity>

    @Query("SELECT * FROM payments WHERE jobSheetId = :jobSheetId ORDER BY timestamp DESC")
    fun getPaymentsForJob(jobSheetId: String): Flow<List<PaymentEntity>>

    @Query("SELECT * FROM payments WHERE jobSheetId = :jobSheetId ORDER BY timestamp DESC")
    fun getPaymentsForJobSheet(jobSheetId: String): Flow<List<PaymentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPayment(payment: PaymentEntity)
}

@Dao
interface DailyCashDao {
    @Query("SELECT * FROM daily_cash WHERE date = :date")
    suspend fun getDailyCash(date: String): DailyCashEntity?

    @Query("SELECT * FROM daily_cash WHERE date < :date ORDER BY date DESC LIMIT 1")
    suspend fun getPreviousDailyCash(date: String): DailyCashEntity?

    @Query("SELECT * FROM daily_cash WHERE date = :date")
    fun getDailyCashFlow(date: String): Flow<DailyCashEntity?>

    @Query("SELECT * FROM daily_cash ORDER BY date DESC")
    fun getAllDailyCash(): Flow<List<DailyCashEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDailyCash(dailyCash: DailyCashEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(dailyCash: DailyCashEntity)
}
