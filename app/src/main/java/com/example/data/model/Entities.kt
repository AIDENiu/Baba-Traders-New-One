package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import org.json.JSONArray
import org.json.JSONObject
import java.io.Serializable
import java.util.UUID

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val phoneOrUsername: String = "",
    val pin: String = "1234",
    val role: String = "worker", // "owner", "worker", "manager"
    val isActive: Boolean = true,
    val maxDiscountPercent: Double = 10.0,
    val maxDiscountAmount: Double = 200.0,
    val registeredPhone: String = "9876543210",
    val phone: String = ""
) : Serializable

@Entity(tableName = "categories")
data class MachineCategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val iconName: String
) : Serializable

@Entity(tableName = "machines")
data class MachineEntity(
    @PrimaryKey val id: String,
    val categoryId: String,
    val name: String,
    val baseRate: Double = 350.0,
    val currentRate: Double = 350.0,
    val totalQty: Int = 10,
    val availableQty: Int = 10,
    val modelOrSpecs: String = ""
) : Serializable

@Entity(tableName = "customers")
data class CustomerEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val phone: String,
    val address: String,
    val idPhotoFrontPath: String? = null,
    val idPhotoBackPath: String? = null,
    val personPhotoPath: String? = null,
    val createdAt: Long = System.currentTimeMillis()
) : Serializable

data class SelectedMachineItem(
    val machineId: String,
    val machineName: String,
    val categoryId: String,
    val categoryName: String,
    var quantity: Int,
    var rate: Double
) : Serializable {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("machineId", machineId)
            put("machineName", machineName)
            put("categoryId", categoryId)
            put("categoryName", categoryName)
            put("quantity", quantity)
            put("rate", rate)
        }
    }

    companion object {
        fun fromJson(json: JSONObject): SelectedMachineItem {
            return SelectedMachineItem(
                machineId = json.optString("machineId"),
                machineName = json.optString("machineName"),
                categoryId = json.optString("categoryId"),
                categoryName = json.optString("categoryName"),
                quantity = json.optInt("quantity", 1),
                rate = json.optDouble("rate", 0.0)
            )
        }

        fun listFromJson(jsonString: String?): List<SelectedMachineItem> {
            val list = mutableListOf<SelectedMachineItem>()
            if (jsonString.isNullOrBlank()) return list
            try {
                val jsonArray = JSONArray(jsonString)
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.optJSONObject(i)
                    if (obj != null) {
                        list.add(fromJson(obj))
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return list
        }

        fun listToJson(list: List<SelectedMachineItem>): String {
            val arr = JSONArray()
            list.forEach { arr.put(it.toJson()) }
            return arr.toString()
        }
    }
}

@Entity(tableName = "job_sheets")
data class JobSheetEntity(
    @PrimaryKey val id: String = "JOB-" + (1000..9999).random(),
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
    val billingCycle: String = "24hr",
    val checkoutTime: Long = System.currentTimeMillis(),
    val dueTime: Long = System.currentTimeMillis() + (24 * 3600 * 1000L),
    val returnTime: Long? = null,
    val status: String = "active",
    val lateFee: Double = 0.0,
    val totalRentalFee: Double = 0.0,
    val finalSettlement: Double = 0.0,
    val checkoutBy: String = "",
    val checkoutByName: String = "",
    val handledBy: String = "",
    val handledByName: String = "",
    val returnedBy: String = "",
    val returnedByName: String = "",
    val closedBy: String = "",
    val closedByName: String = "",
    val notes: String = "",
    val returnRemark: String = "",
    val isDeleted: Boolean = false,
    val initialQuantity: Int = 0,
    val returnedQuantity: Int = 0,
    val personPhotoPath: String? = null,
    val idPhotoPath: String? = null,
    val machinePhotoPath: String? = null,
    val paymentMode: String = "cash",
    val itemsJson: String = ""
) : Serializable {
    val effectiveInitialQty: Int get() = if (initialQuantity > 0) initialQuantity else quantity
    val remainingActiveQty: Int get() = effectiveInitialQty - returnedQuantity
}

@Entity(tableName = "activity_log")
data class ActivityLogEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val jobSheetId: String,
    val action: String,
    val performedBy: String = "",
    val performedByName: String = "",
    val changeSummary: String,
    val timestamp: Long = System.currentTimeMillis(),
    val handledByUid: String = performedBy,
    val handledByName: String = performedByName
) : Serializable

@Entity(tableName = "payments")
data class PaymentEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val jobSheetId: String,
    val amount: Double,
    val method: String,
    val type: String,
    val timestamp: Long = System.currentTimeMillis(),
    val handledBy: String = "",
    val handledByName: String = "",
    val remark: String = ""
) : Serializable

@Entity(tableName = "daily_cash")
data class DailyCashEntity(
    @PrimaryKey val date: String,
    val openingCash: Double = 0.0,
    val closingCash: Double = 0.0,
    val enteredBy: String = "",
    val enteredByName: String = "",
    val updatedAt: Long = System.currentTimeMillis(),
    val openingBalance: Double = 0.0,
    val cashCollected: Double = 0.0,
    val cashRefunded: Double = 0.0,
    val notes: String = ""
) : Serializable
