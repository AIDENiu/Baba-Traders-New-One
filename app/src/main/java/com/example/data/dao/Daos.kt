package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.ActivityLogEntity
import com.example.data.model.CustomerEntity
import com.example.data.model.JobSheetEntity
import com.example.data.model.MachineCategoryEntity
import com.example.data.model.MachineEntity
import com.example.data.model.PaymentEntity
import com.example.data.model.UserEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Query("SELECT * FROM users ORDER BY role ASC, name ASC")
    fun getAllUsers(): Flow<List<UserEntity>>

    @Query("SELECT * FROM users WHERE isActive = 1 ORDER BY role ASC, name ASC")
    fun getActiveUsers(): Flow<List<UserEntity>>

    @Query("SELECT * FROM users WHERE id = :id LIMIT 1")
    suspend fun getUserById(id: String): UserEntity?

    @Query("SELECT * FROM users WHERE role = 'owner' LIMIT 1")
    suspend fun getOwner(): UserEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserEntity)

    @Update
    suspend fun updateUser(user: UserEntity)
}

@Dao
interface CustomerDao {
    @Query("SELECT * FROM customers ORDER BY createdAt DESC")
    fun getAllCustomers(): Flow<List<CustomerEntity>>

    @Query("SELECT * FROM customers WHERE phone = :phone LIMIT 1")
    suspend fun getCustomerByPhone(phone: String): CustomerEntity?

    @Query("SELECT * FROM customers WHERE id = :id LIMIT 1")
    suspend fun getCustomerById(id: String): CustomerEntity?

    @Query("SELECT * FROM customers WHERE name LIKE '%' || :query || '%' OR phone LIKE '%' || :query || '%'")
    fun searchCustomers(query: String): Flow<List<CustomerEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomer(customer: CustomerEntity)

    @Update
    suspend fun updateCustomer(customer: CustomerEntity)
}

@Dao
interface MachineDao {
    @Query("SELECT * FROM machine_categories ORDER BY name ASC")
    fun getAllCategories(): Flow<List<MachineCategoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(category: MachineCategoryEntity)

    @Query("SELECT * FROM machines ORDER BY name ASC")
    fun getAllMachines(): Flow<List<MachineEntity>>

    @Query("SELECT * FROM machines WHERE categoryId = :categoryId ORDER BY name ASC")
    fun getMachinesByCategory(categoryId: String): Flow<List<MachineEntity>>

    @Query("SELECT * FROM machines WHERE id = :id LIMIT 1")
    suspend fun getMachineById(id: String): MachineEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMachine(machine: MachineEntity)

    @Update
    suspend fun updateMachine(machine: MachineEntity)

    @Query("UPDATE machines SET availableQty = availableQty - :qty WHERE id = :machineId AND availableQty >= :qty")
    suspend fun decrementStock(machineId: String, qty: Int): Int

    @Query("UPDATE machines SET availableQty = MIN(totalQty, availableQty + :qty) WHERE id = :machineId")
    suspend fun incrementStock(machineId: String, qty: Int): Int
}

@Dao
interface JobSheetDao {
    @Query("SELECT * FROM job_sheets WHERE isDeleted = 0 ORDER BY checkoutTime DESC")
    fun getAllJobSheets(): Flow<List<JobSheetEntity>>

    @Query("SELECT * FROM job_sheets WHERE isDeleted = 0 AND status = 'active' ORDER BY dueTime ASC")
    fun getActiveJobSheets(): Flow<List<JobSheetEntity>>

    @Query("SELECT * FROM job_sheets WHERE isDeleted = 0 AND (status = 'overdue' OR (status = 'active' AND dueTime < :currentTime)) ORDER BY dueTime ASC")
    fun getOverdueJobSheets(currentTime: Long): Flow<List<JobSheetEntity>>

    @Query("SELECT * FROM job_sheets WHERE isDeleted = 0 AND status = 'returned' ORDER BY returnTime DESC")
    fun getReturnedJobSheets(): Flow<List<JobSheetEntity>>

    @Query("SELECT * FROM job_sheets WHERE customerId = :customerId AND isDeleted = 0 ORDER BY checkoutTime DESC")
    fun getJobSheetsForCustomer(customerId: String): Flow<List<JobSheetEntity>>

    @Query("SELECT * FROM job_sheets WHERE id = :id LIMIT 1")
    suspend fun getJobSheetById(id: String): JobSheetEntity?

    @Query("SELECT COUNT(*) FROM job_sheets")
    suspend fun getTotalCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertJobSheet(jobSheet: JobSheetEntity)

    @Update
    suspend fun updateJobSheet(jobSheet: JobSheetEntity)

    @Query("UPDATE job_sheets SET isDeleted = 1 WHERE id = :id")
    suspend fun softDeleteJobSheet(id: String)
}

@Dao
interface PaymentDao {
    @Query("SELECT * FROM payments WHERE jobSheetId = :jobSheetId ORDER BY timestamp ASC")
    fun getPaymentsForJobSheet(jobSheetId: String): Flow<List<PaymentEntity>>

    @Query("SELECT * FROM payments ORDER BY timestamp DESC")
    fun getAllPayments(): Flow<List<PaymentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPayment(payment: PaymentEntity)
}

@Dao
interface ActivityLogDao {
    @Query("SELECT * FROM activity_log ORDER BY timestamp DESC")
    fun getAllLogs(): Flow<List<ActivityLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: ActivityLogEntity)
}
