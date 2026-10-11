package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val id: String,
    val name: String,
    val phoneOrUsername: String,
    val pin: String,
    val role: String, // "owner" or "worker"
    val isActive: Boolean = true,
    val maxDiscountPercent: Double = 10.0, // Floor set by owner for workers
    val maxDiscountAmount: Double = 150.0
)

@Entity(tableName = "customers")
data class CustomerEntity(
    @PrimaryKey val id: String,
    val name: String,
    val phone: String,
    val address: String,
    val idPhotoFrontPath: String? = null,
    val idPhotoBackPath: String? = null,
    val personPhotoPath: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "machine_categories")
data class MachineCategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val iconKey: String = "tool"
)

@Entity(tableName = "machines")
data class MachineEntity(
    @PrimaryKey val id: String,
    val categoryId: String,
    val name: String,
    val baseRate: Double,
    val currentRate: Double,
    val availableQty: Int,
    val totalQty: Int,
    val modelOrSpecs: String = ""
)

@Entity(tableName = "job_sheets")
data class JobSheetEntity(
    @PrimaryKey val id: String, // e.g. "JOB-0412"
    val customerId: String,
    val customerName: String,
    val customerPhone: String,
    val customerAddress: String,
    val machineId: String,
    val machineName: String,
    val categoryName: String,
    val quantity: Int,
    val rateUsed: Double,
    val discount: Double = 0.0,
    val depositAmount: Double = 0.0,
    val billingCycle: String = "24hr", // "24hr" or "working_hours"
    val checkoutTime: Long = System.currentTimeMillis(),
    val dueTime: Long,
    val returnTime: Long? = null,
    val status: String = "active", // "active", "returned", "closed", "overdue"
    val checkoutBy: String = "",
    val checkoutByName: String = "",
    val handledBy: String,
    val handledByName: String,
    val returnedBy: String = "",
    val returnedByName: String = "",
    val isDeleted: Boolean = false,
    val lateFee: Double = 0.0,
    val totalRentalFee: Double = 0.0,
    val finalSettlement: Double = 0.0, // balance collected or refund given
    val notes: String = "",
    val personPhotoPath: String? = null,
    val idPhotoPath: String? = null,
    val machinePhotoPath: String? = null,
    val paymentMode: String = "cash", // "cash", "upi", "card", "bank"
    val returnRemark: String = "",
    val closedBy: String = "",
    val closedByName: String = ""
)

@Entity(tableName = "payments")
data class PaymentEntity(
    @PrimaryKey val id: String,
    val jobSheetId: String,
    val amount: Double,
    val method: String, // "cash", "upi", "card", "bank"
    val type: String, // "advance", "deposit", "extra", "settlement", "refund", "return_nil"
    val timestamp: Long = System.currentTimeMillis(),
    val handledBy: String,
    val handledByName: String,
    val remark: String = ""
)

@Entity(tableName = "daily_cash")
data class DailyCashEntity(
    @PrimaryKey val date: String, // "yyyy-MM-dd"
    val openingCash: Double,
    val closingCash: Double = 0.0,
    val enteredBy: String = "",
    val enteredByName: String = "",
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "activity_log")
data class ActivityLogEntity(
    @PrimaryKey val id: String,
    val jobSheetId: String,
    val action: String, // "created", "edited", "deleted", "returned", "payment"
    val performedBy: String,
    val performedByName: String,
    val timestamp: Long = System.currentTimeMillis(),
    val changeSummary: String
)
