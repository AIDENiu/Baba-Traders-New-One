package com.example.data.firebase

import android.content.Context
import android.util.Log
import com.example.R
import com.example.data.AppDatabase
import com.example.data.model.ActivityLogEntity
import com.example.data.model.CustomerEntity
import com.example.data.model.JobSheetEntity
import com.example.data.model.MachineEntity
import com.example.data.model.UserEntity
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class FirestoreSyncService(
    private val context: Context,
    private val roomDb: AppDatabase,
    private val scope: CoroutineScope
) {
    val firestore: FirebaseFirestore by lazy {
        val dbId = context.getString(R.string.firestore_database_id)
        FirebaseFirestore.getInstance(dbId)
    }

    private val auth = Firebase.auth

    private var jobSheetsListener: ListenerRegistration? = null
    private var machinesListener: ListenerRegistration? = null
    private var customersListener: ListenerRegistration? = null
    private var activityLogListener: ListenerRegistration? = null

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _syncError = MutableStateFlow<String?>(null)
    val syncError: StateFlow<String?> = _syncError.asStateFlow()

    fun startRealtimeListeners() {
        val currentUser = auth.currentUser ?: return
        stopRealtimeListeners()

        _isSyncing.value = true

        // 1. Real-time Snapshot Listener for Job Sheets
        val jobSheetsPath = "job_sheets"
        jobSheetsListener = firestore.collection(jobSheetsPath)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    val errJson = handleFirestoreError(error, OperationType.LIST, jobSheetsPath)
                    _syncError.value = errJson
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    scope.launch(Dispatchers.IO) {
                        for (doc in snapshot.documents) {
                            try {
                                val id = doc.id
                                val customerId = doc.getString("customerId") ?: ""
                                val customerName = doc.getString("customerName") ?: ""
                                val customerPhone = doc.getString("customerPhone") ?: ""
                                val machineId = doc.getString("machineId") ?: ""
                                val machineName = doc.getString("machineName") ?: ""
                                val categoryName = doc.getString("categoryName") ?: ""
                                val quantity = (doc.get("quantity") as? Number)?.toInt() ?: 1
                                val rateUsed = (doc.get("rateUsed") as? Number)?.toDouble() ?: 0.0
                                val discount = (doc.get("discount") as? Number)?.toDouble() ?: 0.0
                                val depositAmount = (doc.get("deposit") as? Number)?.toDouble()
                                    ?: (doc.get("depositAmount") as? Number)?.toDouble() ?: 0.0
                                val depositMethod = doc.getString("depositMethod") ?: "cash"
                                val billingCycle = doc.getString("billingCycleType")
                                    ?: doc.getString("billingCycle") ?: "24hr"
                                val checkoutTime = (doc.get("startTime") as? Number)?.toLong()
                                    ?: (doc.get("checkoutTime") as? Number)?.toLong() ?: System.currentTimeMillis()
                                val dueTime = (doc.get("dueTime") as? Number)?.toLong()
                                    ?: (checkoutTime + 24 * 3600 * 1000L)
                                val returnTime = (doc.get("returnTime") as? Number)?.toLong()
                                val status = doc.getString("status") ?: "active"
                                val checkoutBy = doc.getString("checkoutByUid") ?: doc.getString("checkoutBy") ?: ""
                                val checkoutByName = doc.getString("checkoutByName") ?: ""
                                val handledBy = doc.getString("handledByUid") ?: doc.getString("handledBy") ?: ""
                                val handledByName = doc.getString("handledByName") ?: ""
                                val returnedBy = doc.getString("returnedByUid") ?: doc.getString("returnedBy") ?: ""
                                val returnedByName = doc.getString("returnedByName") ?: ""
                                val isDeleted = doc.getBoolean("isDeleted") ?: false
                                val lateFee = (doc.get("lateFee") as? Number)?.toDouble() ?: 0.0
                                val totalRentalFee = (doc.get("totalRentalFee") as? Number)?.toDouble() ?: 0.0
                                val finalSettlement = (doc.get("finalSettlement") as? Number)?.toDouble() ?: 0.0
                                val notes = doc.getString("notes") ?: ""

                                val entity = JobSheetEntity(
                                    id = id,
                                    customerId = customerId,
                                    customerName = customerName,
                                    customerPhone = customerPhone,
                                    customerAddress = "",
                                    machineId = machineId,
                                    machineName = machineName,
                                    categoryName = categoryName,
                                    quantity = quantity,
                                    rateUsed = rateUsed,
                                    discount = discount,
                                    depositAmount = depositAmount,
                                    billingCycle = billingCycle,
                                    checkoutTime = checkoutTime,
                                    dueTime = dueTime,
                                    returnTime = returnTime,
                                    status = status,
                                    lateFee = lateFee,
                                    totalRentalFee = totalRentalFee,
                                    finalSettlement = finalSettlement,
                                    checkoutBy = checkoutBy,
                                    checkoutByName = checkoutByName,
                                    handledBy = handledBy,
                                    handledByName = handledByName,
                                    returnedBy = returnedBy,
                                    returnedByName = returnedByName,
                                    notes = notes,
                                    isDeleted = isDeleted
                                )
                                roomDb.jobSheetDao().insertJobSheet(entity)
                            } catch (e: Exception) {
                                Log.e("FirestoreSync", "Error parsing job sheet doc: ${doc.id}", e)
                            }
                        }
                    }
                }
            }

        // 2. Real-time Snapshot Listener for Machines
        val machinesPath = "machines"
        machinesListener = firestore.collection(machinesPath)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    handleFirestoreError(error, OperationType.LIST, machinesPath)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    scope.launch(Dispatchers.IO) {
                        for (doc in snapshot.documents) {
                            try {
                                val id = doc.id
                                val categoryId = doc.getString("categoryId") ?: ""
                                val name = doc.getString("name") ?: ""
                                val baseDailyRate = (doc.get("baseDailyRate") as? Number)?.toDouble() ?: 350.0
                                val dailyRate = (doc.get("dailyRate") as? Number)?.toDouble() ?: baseDailyRate
                                val totalQty = (doc.get("totalQty") as? Number)?.toInt() ?: 5
                                val availableQty = (doc.get("availableQty") as? Number)?.toInt() ?: totalQty
                                val specs = doc.getString("specs") ?: ""

                                val machine = MachineEntity(
                                    id = id,
                                    categoryId = categoryId,
                                    name = name,
                                    baseRate = baseDailyRate,
                                    currentRate = dailyRate,
                                    totalQty = totalQty,
                                    availableQty = availableQty,
                                    modelOrSpecs = specs
                                )
                                roomDb.machineDao().insertMachine(machine)
                            } catch (e: Exception) {
                                Log.e("FirestoreSync", "Error parsing machine doc: ${doc.id}", e)
                            }
                        }
                    }
                }
            }

        // 3. Real-time Snapshot Listener for Customers
        val customersPath = "customers"
        customersListener = firestore.collection(customersPath)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    handleFirestoreError(error, OperationType.LIST, customersPath)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    scope.launch(Dispatchers.IO) {
                        for (doc in snapshot.documents) {
                            try {
                                val id = doc.id
                                val name = doc.getString("name") ?: ""
                                val phone = doc.getString("phone") ?: ""
                                val address = doc.getString("address") ?: ""
                                val existing = roomDb.customerDao().getCustomerById(id)
                                val customer = CustomerEntity(
                                    id = id,
                                    name = name,
                                    phone = phone,
                                    address = address,
                                    // Preserve local device photo paths
                                    idPhotoFrontPath = existing?.idPhotoFrontPath,
                                    idPhotoBackPath = existing?.idPhotoBackPath
                                )
                                roomDb.customerDao().insertCustomer(customer)
                            } catch (e: Exception) {
                                Log.e("FirestoreSync", "Error parsing customer doc: ${doc.id}", e)
                            }
                        }
                    }
                }
            }

        // 4. Real-time Snapshot Listener for Activity Logs (audit trail)
        val activityPath = "activity_log"
        activityLogListener = firestore.collection(activityPath)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    handleFirestoreError(error, OperationType.LIST, activityPath)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    scope.launch(Dispatchers.IO) {
                        for (doc in snapshot.documents) {
                            try {
                                val id = doc.id
                                val jobSheetId = doc.getString("jobSheetId") ?: ""
                                val action = doc.getString("action") ?: "created"
                                val handledByUid = doc.getString("handledByUid") ?: ""
                                val handledByName = doc.getString("handledByName") ?: ""
                                val changeSummary = doc.getString("changeSummary") ?: ""
                                val timestamp = (doc.get("timestamp") as? Number)?.toLong()
                                    ?: System.currentTimeMillis()

                                val log = ActivityLogEntity(
                                    id = id,
                                    jobSheetId = jobSheetId,
                                    action = action,
                                    performedBy = handledByUid,
                                    performedByName = handledByName,
                                    timestamp = timestamp,
                                    changeSummary = changeSummary
                                )
                                roomDb.activityLogDao().insertLog(log)
                            } catch (e: Exception) {
                                Log.e("FirestoreSync", "Error parsing log doc: ${doc.id}", e)
                            }
                        }
                    }
                }
            }
    }

    fun stopRealtimeListeners() {
        jobSheetsListener?.remove()
        jobSheetsListener = null
        machinesListener?.remove()
        machinesListener = null
        customersListener?.remove()
        customersListener = null
        activityLogListener?.remove()
        activityLogListener = null
        _isSyncing.value = false
    }

    // --- Outbound Mutations to Firestore ---

    suspend fun pushJobSheet(jobSheet: JobSheetEntity) {
        val path = "job_sheets/${jobSheet.id}"
        val payload = hashMapOf<String, Any?>(
            "id" to jobSheet.id,
            "customerId" to jobSheet.customerId,
            "customerName" to jobSheet.customerName,
            "customerPhone" to jobSheet.customerPhone,
            "machineId" to jobSheet.machineId,
            "machineName" to jobSheet.machineName,
            "categoryName" to jobSheet.categoryName,
            "quantity" to jobSheet.quantity,
            "rateUsed" to jobSheet.rateUsed,
            "discount" to jobSheet.discount,
            "deposit" to jobSheet.depositAmount,
            "depositMethod" to "cash",
            "billingCycleType" to jobSheet.billingCycle,
            "startTime" to jobSheet.checkoutTime,
            "dueTime" to jobSheet.dueTime,
            "returnTime" to jobSheet.returnTime,
            "status" to jobSheet.status,
            "checkoutByUid" to jobSheet.checkoutBy,
            "checkoutByName" to jobSheet.checkoutByName,
            "handledByUid" to jobSheet.handledBy,
            "handledByName" to jobSheet.handledByName,
            "returnedByUid" to jobSheet.returnedBy,
            "returnedByName" to jobSheet.returnedByName,
            "isDeleted" to jobSheet.isDeleted,
            "lateFee" to jobSheet.lateFee,
            "totalRentalFee" to jobSheet.totalRentalFee,
            "finalSettlement" to jobSheet.finalSettlement,
            "notes" to jobSheet.notes,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            firestore.collection("job_sheets").document(jobSheet.id)
                .set(payload, SetOptions.merge())
                .await()
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
        }
    }

    suspend fun pushCustomer(customer: CustomerEntity) {
        val path = "customers/${customer.id}"
        val payload = hashMapOf<String, Any?>(
            "id" to customer.id,
            "name" to customer.name,
            "phone" to customer.phone,
            "address" to customer.address,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            firestore.collection("customers").document(customer.id)
                .set(payload, SetOptions.merge())
                .await()
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
        }
    }

    suspend fun pushMachineStock(machineId: String, availableQty: Int) {
        val path = "machines/$machineId"
        try {
            firestore.collection("machines").document(machineId)
                .update("availableQty", availableQty)
                .await()
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
        }
    }

    suspend fun pushActivityLog(log: ActivityLogEntity) {
        val path = "activity_log/${log.id}"
        val payload = hashMapOf<String, Any?>(
            "id" to log.id,
            "jobSheetId" to log.jobSheetId,
            "action" to log.action,
            "handledByUid" to log.performedBy,
            "handledByName" to log.performedByName,
            "changeSummary" to log.changeSummary,
            "timestamp" to log.timestamp,
            "serverTimestamp" to FieldValue.serverTimestamp()
        )
        try {
            firestore.collection("activity_log").document(log.id)
                .set(payload)
                .await()
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
        }
    }

    suspend fun getStaffUser(uid: String): UserEntity? {
        val path = "users/$uid"
        return try {
            val doc = firestore.collection("users").document(uid).get().await()
            if (doc.exists()) {
                val name = doc.getString("name") ?: "Staff"
                val role = doc.getString("role") ?: "worker"
                val active = doc.getBoolean("active") ?: true
                val maxDiscount = (doc.get("maxDiscountAmount") as? Number)?.toDouble() ?: 200.0
                UserEntity(
                    id = uid,
                    name = name,
                    phoneOrUsername = doc.getString("phoneOrUsername") ?: name.lowercase(),
                    pin = "",
                    role = role,
                    isActive = active,
                    maxDiscountAmount = maxDiscount
                )
            } else null
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, path)
            null
        }
    }

    suspend fun saveStaffProfile(user: UserEntity) {
        val path = "users/${user.id}"
        val payload = hashMapOf<String, Any?>(
            "uid" to user.id,
            "name" to user.name,
            "phoneOrUsername" to user.phoneOrUsername,
            "role" to user.role,
            "active" to user.isActive,
            "maxDiscountAmount" to user.maxDiscountAmount,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            firestore.collection("users").document(user.id)
                .set(payload, SetOptions.merge())
                .await()
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
        }
    }
}
