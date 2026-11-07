package com.example.data.firebase

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import com.example.R
import com.example.data.AppDatabase
import com.example.data.model.ActivityLogEntity
import com.example.data.model.CustomerEntity
import com.example.data.model.DailyCashEntity
import com.example.data.model.ExpenseEntity
import com.example.data.model.JobSheetEntity
import com.example.data.model.MachineCategoryEntity
import com.example.data.model.MachineEntity
import com.example.data.model.PaymentEntity
import com.example.data.model.UserEntity
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.PersistentCacheSettings
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import java.io.File
import java.util.Locale
import java.util.UUID

enum class CloudSyncStatus(val label: String) {
    SYNCED("Synced"),
    SYNCING("Syncing"),
    OFFLINE("Offline (will sync later)"),
    ERROR("Error")
}

data class CollectionMigrationCount(
    val collection: String,
    val uploaded: Int,
    val skipped: Int,
    val failed: Int
)

data class TabletMigrationReport(
    val counts: List<CollectionMigrationCount>,
    val totalUploaded: Int,
    val totalSkipped: Int,
    val totalFailed: Int,
    val errorSummary: String? = null
)

class FirestoreSyncService(
    private val context: Context,
    private val roomDb: AppDatabase,
    private val scope: CoroutineScope,
    private val enableBackgroundMonitors: Boolean = true
) {
    val firestore: FirebaseFirestore? by lazy {
        try {
            val dbId = context.getString(R.string.firestore_database_id)
            val instance = FirebaseFirestore.getInstance(dbId)
            try {
                val settings = FirebaseFirestoreSettings.Builder()
                    .setLocalCacheSettings(PersistentCacheSettings.newBuilder().build())
                    .build()
                instance.firestoreSettings = settings
            } catch (_: Exception) {
                // Settings already applied on this instance
            }
            instance
        } catch (e: Exception) {
            Log.d("FirestoreSync", "Firestore instance unavailable: ${e.message}")
            null
        }
    }

    private val auth get() = try { Firebase.auth } catch (e: Exception) { null }
    private val prefs by lazy {
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    private val syncCycleMutex = Mutex()

    private var jobSheetsListener: ListenerRegistration? = null
    private var categoriesListener: ListenerRegistration? = null
    private var machinesListener: ListenerRegistration? = null
    private var customersListener: ListenerRegistration? = null
    private var paymentsListener: ListenerRegistration? = null
    private var usersListener: ListenerRegistration? = null
    private var expensesListener: ListenerRegistration? = null
    private var activityLogListener: ListenerRegistration? = null
    private var dailyCashListener: ListenerRegistration? = null

    private val _isOnline = MutableStateFlow(checkIsDeviceOnline())
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _isActiveSyncOperation = MutableStateFlow(false)

    private val _syncError = MutableStateFlow<String?>(null)
    val syncError: StateFlow<String?> = _syncError.asStateFlow()

    private val _syncStatus = MutableStateFlow(
        if (checkIsDeviceOnline()) CloudSyncStatus.SYNCED else CloudSyncStatus.OFFLINE
    )
    val syncStatus: StateFlow<CloudSyncStatus> = _syncStatus.asStateFlow()

    init {
        if (enableBackgroundMonitors) {
            registerNetworkAndAuthMonitors()
        }
    }

    fun checkIsDeviceOnline(): Boolean {
        return try {
            val cm = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return true
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (_: Exception) {
            true
        }
    }

    private fun refreshSyncStatus() {
        val online = checkIsDeviceOnline()
        _isOnline.value = online
        _syncStatus.value = when {
            !online -> CloudSyncStatus.OFFLINE
            _isActiveSyncOperation.value -> CloudSyncStatus.SYNCING
            _syncError.value != null -> CloudSyncStatus.ERROR
            else -> CloudSyncStatus.SYNCED
        }
    }

    private fun setRealSyncError(errorMsg: String?) {
        if (!checkIsDeviceOnline()) {
            _syncError.value = null
            refreshSyncStatus()
            return
        }
        _syncError.value = errorMsg
        refreshSyncStatus()
    }

    fun clearSyncError() {
        _syncError.value = null
        refreshSyncStatus()
    }

    fun isOneTimeMigrationCompleted(): Boolean {
        return try {
            prefs.getBoolean(KEY_ONE_TIME_MIGRATED, false)
        } catch (_: Exception) {
            false
        }
    }

    private fun markOneTimeMigrationCompleted() {
        try {
            prefs.edit().putBoolean(KEY_ONE_TIME_MIGRATED, true).apply()
        } catch (_: Exception) { }
    }

    private fun registerNetworkAndAuthMonitors() {
        try {
            val cm = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            if (cm != null) {
                val request = NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build()
                cm.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        val wasOffline = !_isOnline.value
                        _isOnline.value = true
                        _syncError.value = null
                        refreshSyncStatus()
                        if (auth?.currentUser != null) {
                            scope.launch(Dispatchers.IO) {
                                startRealtimeListeners()
                                runAutomaticBackgroundSyncCycle()
                            }
                            if (wasOffline) {
                                CloudCatchUpSyncWorker.enqueueImmediateSync(context)
                            }
                        }
                    }

                    override fun onLost(network: Network) {
                        val stillOnline = checkIsDeviceOnline()
                        _isOnline.value = stillOnline
                        if (!stillOnline) {
                            _syncError.value = null
                        }
                        refreshSyncStatus()
                    }
                })
            }
        } catch (e: Exception) {
            Log.d("FirestoreSync", "NetworkCallback registration skipped: ${e.message}")
        }

        try {
            auth?.addAuthStateListener(FirebaseAuth.AuthStateListener { firebaseAuth ->
                if (firebaseAuth.currentUser != null) {
                    refreshSyncStatus()
                    if (checkIsDeviceOnline()) {
                        scope.launch(Dispatchers.IO) {
                            startRealtimeListeners()
                            runAutomaticBackgroundSyncCycle()
                        }
                    }
                } else {
                    stopRealtimeListeners()
                    refreshSyncStatus()
                }
            })
        } catch (e: Exception) {
            Log.d("FirestoreSync", "AuthStateListener registration skipped: ${e.message}")
        }
    }

    fun isCloudAuthenticated(): Boolean {
        return auth?.currentUser != null
    }

    fun generateDocumentId(collection: String): String {
        return try {
            firestore?.collection(collection)?.document()?.id ?: UUID.randomUUID().toString()
        } catch (_: Exception) {
            UUID.randomUUID().toString()
        }
    }

    private fun extractPhotoFileName(path: String?): String? {
        if (path.isNullOrBlank()) return null
        return File(path).name.takeIf { it.isNotBlank() }
    }

    private fun resolveSyncedPhotoPath(existingLocalPath: String?, remoteFileName: String?): String? {
        if (!existingLocalPath.isNullOrBlank() && File(existingLocalPath).exists()) {
            return existingLocalPath
        }
        if (!remoteFileName.isNullOrBlank()) {
            return File(context.filesDir, remoteFileName).absolutePath
        }
        return existingLocalPath
    }

    /**
     * Allocates the next sequential JOB-XXXX number using a Firestore transaction on
     * /counters/job_sheets so two devices never generate duplicate job sheet numbers.
     * Falls back safely to max(localMaxJobNumber) + 1 when offline.
     */
    suspend fun allocateNextJobSheetId(localMaxJobNumber: Int): String {
        val fs = firestore
        val currentUser = auth?.currentUser
        if (fs != null && currentUser != null && checkIsDeviceOnline()) {
            val counterRef = fs.collection("counters").document("job_sheets")
            try {
                val allocatedNum = fs.runTransaction { transaction ->
                    val snap = transaction.get(counterRef)
                    val cloudLast = (snap.get("lastNumber") as? Number)?.toInt() ?: 0
                    val nextNum = maxOf(cloudLast, localMaxJobNumber) + 1
                    transaction.set(
                        counterRef,
                        mapOf(
                            "lastNumber" to nextNum,
                            "updatedAt" to FieldValue.serverTimestamp()
                        ),
                        SetOptions.merge()
                    )
                    nextNum
                }.await()
                return String.format(Locale.US, "JOB-%04d", allocatedNum)
            } catch (e: Exception) {
                Log.d("FirestoreSync", "Offline job ID allocation fallback: ${e.message}")
            }
        }
        val nextOffline = localMaxJobNumber + 1
        return String.format(Locale.US, "JOB-%04d", nextOffline)
    }

    fun startRealtimeListeners() {
        val fs = firestore ?: return
        val currentUser = auth?.currentUser ?: return
        stopRealtimeListeners()

        _isSyncing.value = true
        refreshSyncStatus()

        // 1. Real-time Snapshot Listener for Job Sheets
        val jobSheetsPath = "job_sheets"
        jobSheetsListener = fs.collection(jobSheetsPath)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    setRealSyncError(handleFirestoreError(error, OperationType.LIST, jobSheetsPath))
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    scope.launch(Dispatchers.IO) {
                        for (doc in snapshot.documents) {
                            try {
                                val id = doc.id
                                val existing = roomDb.jobSheetDao().getJobSheetById(id)
                                val customerId = doc.getString("customerId") ?: existing?.customerId ?: ""
                                val customerName = doc.getString("customerName") ?: existing?.customerName ?: ""
                                val customerPhone = doc.getString("customerPhone") ?: existing?.customerPhone ?: ""
                                val customerAddress = doc.getString("customerAddress") ?: existing?.customerAddress ?: ""
                                val machineId = doc.getString("machineId") ?: existing?.machineId ?: ""
                                val machineName = doc.getString("machineName") ?: existing?.machineName ?: ""
                                val categoryName = doc.getString("categoryName") ?: existing?.categoryName ?: ""
                                val quantity = (doc.get("quantity") as? Number)?.toInt() ?: existing?.quantity ?: 1
                                val initialQuantity = (doc.get("initialQuantity") as? Number)?.toInt()
                                    ?: existing?.initialQuantity ?: quantity
                                val returnedQuantity = (doc.get("returnedQuantity") as? Number)?.toInt()
                                    ?: existing?.returnedQuantity ?: 0
                                val rateUsed = (doc.get("rateUsed") as? Number)?.toDouble() ?: existing?.rateUsed ?: 0.0
                                val discount = (doc.get("discount") as? Number)?.toDouble() ?: existing?.discount ?: 0.0
                                val depositAmount = (doc.get("deposit") as? Number)?.toDouble()
                                    ?: (doc.get("depositAmount") as? Number)?.toDouble() ?: existing?.depositAmount ?: 0.0
                                val paymentMode = doc.getString("paymentMode")
                                    ?: doc.getString("depositMethod")
                                    ?: existing?.paymentMode ?: "cash"
                                val billingCycle = doc.getString("billingCycleType")
                                    ?: doc.getString("billingCycle") ?: "working_hours"
                                val checkoutTime = (doc.get("startTime") as? Number)?.toLong()
                                    ?: (doc.get("checkoutTime") as? Number)?.toLong() ?: System.currentTimeMillis()
                                val dueTime = (doc.get("dueTime") as? Number)?.toLong()
                                    ?: (checkoutTime + 24 * 3600 * 1000L)
                                val returnTime = (doc.get("returnTime") as? Number)?.toLong() ?: existing?.returnTime
                                val status = doc.getString("status") ?: existing?.status ?: "active"
                                val checkoutBy = doc.getString("checkoutByUid") ?: doc.getString("checkoutBy") ?: existing?.checkoutBy ?: ""
                                val checkoutByName = doc.getString("checkoutByName") ?: existing?.checkoutByName ?: ""
                                val handledBy = doc.getString("handledByUid") ?: doc.getString("handledBy") ?: existing?.handledBy ?: ""
                                val handledByName = doc.getString("handledByName") ?: existing?.handledByName ?: ""
                                val returnedBy = doc.getString("returnedByUid") ?: doc.getString("returnedBy") ?: existing?.returnedBy ?: ""
                                val returnedByName = doc.getString("returnedByName") ?: existing?.returnedByName ?: ""
                                val closedBy = doc.getString("closedBy") ?: existing?.closedBy ?: returnedBy
                                val closedByName = doc.getString("closedByName") ?: existing?.closedByName ?: returnedByName
                                val isDeleted = doc.getBoolean("isDeleted") ?: false
                                val lateFee = (doc.get("lateFee") as? Number)?.toDouble() ?: 0.0
                                val totalRentalFee = (doc.get("totalRentalFee") as? Number)?.toDouble() ?: 0.0
                                val finalSettlement = (doc.get("finalSettlement") as? Number)?.toDouble() ?: 0.0
                                val notes = doc.getString("notes") ?: existing?.notes ?: ""
                                val returnRemark = doc.getString("returnRemark") ?: existing?.returnRemark ?: ""
                                val itemsJson = doc.getString("itemsJson") ?: existing?.itemsJson ?: ""

                                val personPhotoRef = doc.getString("personPhotoRef")
                                val idPhotoRef = doc.getString("idPhotoRef")
                                val machinePhotoRef = doc.getString("machinePhotoRef")

                                val entity = JobSheetEntity(
                                    id = id,
                                    customerId = customerId,
                                    customerName = customerName,
                                    customerPhone = customerPhone,
                                    customerAddress = customerAddress,
                                    machineId = machineId,
                                    machineName = machineName,
                                    categoryName = categoryName,
                                    quantity = quantity,
                                    initialQuantity = initialQuantity,
                                    returnedQuantity = returnedQuantity,
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
                                    closedBy = closedBy,
                                    closedByName = closedByName,
                                    notes = notes,
                                    returnRemark = returnRemark,
                                    isDeleted = isDeleted,
                                    personPhotoPath = resolveSyncedPhotoPath(existing?.personPhotoPath, personPhotoRef),
                                    idPhotoPath = resolveSyncedPhotoPath(existing?.idPhotoPath, idPhotoRef),
                                    machinePhotoPath = resolveSyncedPhotoPath(existing?.machinePhotoPath, machinePhotoRef),
                                    paymentMode = paymentMode,
                                    itemsJson = itemsJson
                                )
                                roomDb.jobSheetDao().insertJobSheet(entity)
                                com.example.util.DueAlarmScheduler.scheduleAlarms(context, entity)
                            } catch (e: Exception) {
                                Log.d("FirestoreSync", "Skipped job sheet doc: ${doc.id}")
                            }
                        }
                        reconcileMachineStockFromActiveJobs()
                    }
                }
            }

        // 1b. Real-time Snapshot Listener for Machine Categories
        val categoriesPath = "machine_categories"
        categoriesListener = fs.collection(categoriesPath)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    setRealSyncError(handleFirestoreError(error, OperationType.LIST, categoriesPath))
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    scope.launch(Dispatchers.IO) {
                        for (doc in snapshot.documents) {
                            try {
                                val id = doc.id
                                val name = doc.getString("name") ?: continue
                                val iconName = doc.getString("iconName") ?: "build"
                                roomDb.machineCategoryDao().insertCategory(
                                    MachineCategoryEntity(id = id, name = name, iconName = iconName)
                                )
                            } catch (e: Exception) {
                                Log.d("FirestoreSync", "Skipped category doc: ${doc.id}")
                            }
                        }
                    }
                }
            }

        // 2. Real-time Snapshot Listener for Machines
        val machinesPath = "machines"
        val isOwnerListener = currentUser.email?.trim()?.lowercase() == OWNER_EMAIL.trim().lowercase()
        machinesListener = fs.collection(machinesPath)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    setRealSyncError(handleFirestoreError(error, OperationType.LIST, machinesPath))
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    scope.launch(Dispatchers.IO) {
                        for (change in snapshot.documentChanges) {
                            if (change.type == com.google.firebase.firestore.DocumentChange.Type.REMOVED) {
                                try {
                                    roomDb.machineDao().deleteMachineById(change.document.id)
                                } catch (e: Exception) {
                                    Log.d("FirestoreSync", "Skipped deleting removed machine doc: ${change.document.id}")
                                }
                            }
                        }
                        val legacySampleIds = setOf("mach_1", "mach_2", "mach_3", "mach_4", "mach_5", "mach_6")
                        for (doc in snapshot.documents) {
                            try {
                                val id = doc.id
                                if (id in legacySampleIds) {
                                    if (isOwnerListener) {
                                        fs.collection(machinesPath).document(id).delete()
                                    }
                                    roomDb.machineDao().deleteMachineById(id)
                                    continue
                                }
                                val categoryId = doc.getString("categoryId") ?: ""
                                val name = doc.getString("name") ?: ""
                                val baseDailyRate = (doc.get("baseDailyRate") as? Number)?.toDouble() ?: 350.0
                                val dailyRate = (doc.get("dailyRate") as? Number)?.toDouble() ?: baseDailyRate
                                val totalQty = (doc.get("totalQty") as? Number)?.toInt() ?: 5
                                val maintenanceQty = ((doc.get("maintenanceQty") as? Number)?.toInt() ?: 0).coerceIn(0, totalQty)
                                val rawAvailableQty = (doc.get("availableQty") as? Number)?.toInt() ?: (totalQty - maintenanceQty)
                                val availableQty = rawAvailableQty.coerceIn(0, (totalQty - maintenanceQty).coerceAtLeast(0))
                                val specs = doc.getString("specs") ?: ""

                                val machine = MachineEntity(
                                    id = id,
                                    categoryId = categoryId,
                                    name = name,
                                    baseRate = baseDailyRate,
                                    currentRate = dailyRate,
                                    totalQty = totalQty,
                                    availableQty = availableQty,
                                    modelOrSpecs = specs,
                                    maintenanceQty = maintenanceQty
                                )
                                roomDb.machineDao().insertMachine(machine)
                            } catch (e: Exception) {
                                Log.d("FirestoreSync", "Skipped machine doc: ${doc.id}")
                            }
                        }
                        reconcileMachineStockFromActiveJobs()
                    }
                }
            }

        // 3. Real-time Snapshot Listener for Customers
        val customersPath = "customers"
        customersListener = fs.collection(customersPath)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    setRealSyncError(handleFirestoreError(error, OperationType.LIST, customersPath))
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
                                val createdAt = (doc.get("createdAt") as? Number)?.toLong()
                                val existing = roomDb.customerDao().getCustomerById(id)
                                val idPhotoFrontRef = doc.getString("idPhotoFrontRef")
                                val idPhotoBackRef = doc.getString("idPhotoBackRef")
                                val personPhotoRef = doc.getString("personPhotoRef")
                                val customer = CustomerEntity(
                                    id = id,
                                    name = name,
                                    phone = phone,
                                    address = address,
                                    idPhotoFrontPath = resolveSyncedPhotoPath(existing?.idPhotoFrontPath, idPhotoFrontRef),
                                    idPhotoBackPath = resolveSyncedPhotoPath(existing?.idPhotoBackPath, idPhotoBackRef),
                                    personPhotoPath = resolveSyncedPhotoPath(existing?.personPhotoPath, personPhotoRef),
                                    createdAt = createdAt ?: existing?.createdAt ?: System.currentTimeMillis()
                                )
                                roomDb.customerDao().insertCustomer(customer)
                            } catch (e: Exception) {
                                Log.d("FirestoreSync", "Skipped customer doc: ${doc.id}")
                            }
                        }
                    }
                }
            }

        // 3b. Real-time Snapshot Listener for Payments
        val paymentsPath = "payments"
        paymentsListener = fs.collection(paymentsPath)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    setRealSyncError(handleFirestoreError(error, OperationType.LIST, paymentsPath))
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    scope.launch(Dispatchers.IO) {
                        for (doc in snapshot.documents) {
                            try {
                                val id = doc.id
                                val jobSheetId = doc.getString("jobSheetId") ?: continue
                                val amount = (doc.get("amount") as? Number)?.toDouble() ?: 0.0
                                val method = doc.getString("method") ?: "cash"
                                val type = doc.getString("type") ?: "advance"
                                val timestamp = (doc.get("timestamp") as? Number)?.toLong() ?: System.currentTimeMillis()
                                val handledBy = doc.getString("handledBy") ?: ""
                                val handledByName = doc.getString("handledByName") ?: ""
                                val remark = doc.getString("remark") ?: ""
                                roomDb.paymentDao().insertPayment(
                                    PaymentEntity(
                                        id = id,
                                        jobSheetId = jobSheetId,
                                        amount = amount,
                                        method = method,
                                        type = type,
                                        timestamp = timestamp,
                                        handledBy = handledBy,
                                        handledByName = handledByName,
                                        remark = remark
                                    )
                                )
                            } catch (e: Exception) {
                                Log.d("FirestoreSync", "Skipped payment doc: ${doc.id}")
                            }
                        }
                    }
                }
            }

        // 3c. Real-time Snapshot Listener for Staff Profiles (/users)
        val usersPath = "users"
        usersListener = fs.collection(usersPath)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    handleFirestoreError(error, OperationType.LIST, usersPath)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    scope.launch(Dispatchers.IO) {
                        for (doc in snapshot.documents) {
                            try {
                                if (doc.id.contains("@")) continue
                                val uid = doc.id
                                val existing = roomDb.userDao().getUserById(uid)
                                val name = doc.getString("name") ?: existing?.name ?: continue
                                val role = doc.getString("role") ?: existing?.role ?: "worker"
                                val active = doc.getBoolean("active") ?: existing?.isActive ?: true
                                val phoneOrUsername = (doc.getString("phoneOrUsername") ?: existing?.phoneOrUsername ?: name).trim().lowercase()
                                val maxDiscount = (doc.get("maxDiscountAmount") as? Number)?.toDouble() ?: existing?.maxDiscountAmount ?: 200.0
                                val registeredPhone = doc.getString("registeredPhone") ?: existing?.registeredPhone ?: "9876543210"
                                roomDb.userDao().insertUser(
                                    UserEntity(
                                        id = uid,
                                        name = name,
                                        phoneOrUsername = phoneOrUsername,
                                        pin = existing?.pin ?: "",
                                        role = role,
                                        isActive = active,
                                        maxDiscountAmount = maxDiscount,
                                        registeredPhone = registeredPhone
                                    )
                                )
                            } catch (e: Exception) {
                                Log.d("FirestoreSync", "Skipped user doc: ${doc.id}")
                            }
                        }
                    }
                }
            }

        // 4. Real-time Snapshot Listeners for Activity Logs & Expenses (Owner only per strict firestore.rules)
        if (isOwnerListener) {
            val activityPath = "activity_log"
            activityLogListener = fs.collection(activityPath)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.d("FirestoreSync", "Activity log listener warning: ${error.message}")
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
                                    Log.d("FirestoreSync", "Skipped log doc: ${doc.id}")
                                }
                            }
                        }
                    }
                }

            val expensesPath = "expenses"
            expensesListener = fs.collection(expensesPath)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.d("FirestoreSync", "Expenses listener warning: ${error.message}")
                        return@addSnapshotListener
                    }
                    if (snapshot != null) {
                        scope.launch(Dispatchers.IO) {
                            for (doc in snapshot.documents) {
                                try {
                                    val id = doc.id
                                    val date = doc.getString("date") ?: continue
                                    val timestamp = (doc.get("timestamp") as? Number)?.toLong() ?: System.currentTimeMillis()
                                    val expenseType = doc.getString("expenseType") ?: ""
                                    val amount = (doc.get("amount") as? Number)?.toDouble() ?: 0.0
                                    val remarks = doc.getString("remarks") ?: ""
                                    val createdBy = doc.getString("createdBy") ?: ""
                                    val createdByName = doc.getString("createdByName") ?: ""
                                    roomDb.expenseDao().insertExpense(
                                        ExpenseEntity(
                                            id = id,
                                            date = date,
                                            timestamp = timestamp,
                                            expenseType = expenseType,
                                            amount = amount,
                                            remarks = remarks,
                                            createdBy = createdBy,
                                            createdByName = createdByName
                                        )
                                    )
                                } catch (e: Exception) {
                                    Log.d("FirestoreSync", "Skipped expense doc: ${doc.id}")
                                }
                            }
                        }
                    }
                }

            val dailyCashPath = "daily_cash"
            dailyCashListener = fs.collection(dailyCashPath)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.d("FirestoreSync", "Daily cash listener warning: ${error.message}")
                        return@addSnapshotListener
                    }
                    if (snapshot != null) {
                        scope.launch(Dispatchers.IO) {
                            for (doc in snapshot.documents) {
                                try {
                                    val date = doc.getString("date") ?: doc.id
                                    val openingCash = (doc.get("openingCash") as? Number)?.toDouble() ?: 0.0
                                    val closingCash = (doc.get("closingCash") as? Number)?.toDouble() ?: 0.0
                                    val enteredBy = doc.getString("enteredBy") ?: ""
                                    val enteredByName = doc.getString("enteredByName") ?: ""
                                    val updatedAt = (doc.get("updatedAtMillis") as? Number)?.toLong() ?: System.currentTimeMillis()
                                    val openingBalance = (doc.get("openingBalance") as? Number)?.toDouble() ?: openingCash
                                    val cashCollected = (doc.get("cashCollected") as? Number)?.toDouble() ?: 0.0
                                    val cashRefunded = (doc.get("cashRefunded") as? Number)?.toDouble() ?: 0.0
                                    val notes = doc.getString("notes") ?: ""
                                    roomDb.dailyCashDao().insertOrUpdate(
                                        DailyCashEntity(
                                            date = date,
                                            openingCash = openingCash,
                                            closingCash = closingCash,
                                            enteredBy = enteredBy,
                                            enteredByName = enteredByName,
                                            updatedAt = updatedAt,
                                            openingBalance = openingBalance,
                                            cashCollected = cashCollected,
                                            cashRefunded = cashRefunded,
                                            notes = notes
                                        )
                                    )
                                } catch (e: Exception) {
                                    Log.d("FirestoreSync", "Skipped daily_cash doc: ${doc.id}")
                                }
                            }
                        }
                    }
                }
        }
    }

    /**
     * Recalculates each machine's availableQty from active job sheets so two offline phones
     * syncing back online can never corrupt or drift stock below 0 or above totalQty.
     */
    suspend fun reconcileMachineStockFromActiveJobs() {
        try {
            val allJobs = roomDb.jobSheetDao().getAllJobSheetsList()
            val activeJobs = allJobs.filter { !it.isDeleted && (it.status == "active" || it.status == "overdue") }

            val rentedByMachine = mutableMapOf<String, Int>()
            for (job in activeJobs) {
                val remainingOnJob = job.remainingActiveQty.coerceAtLeast(0)
                if (remainingOnJob <= 0) continue

                val items = com.example.data.model.SelectedMachineItem.listFromJson(job.itemsJson)
                if (items.size == 1) {
                    val mId = items.first().machineId
                    rentedByMachine[mId] = (rentedByMachine[mId] ?: 0) + remainingOnJob
                } else if (items.size > 1) {
                    var toAllocate = remainingOnJob
                    for (item in items) {
                        if (toAllocate <= 0) break
                        val allocated = minOf(item.quantity.coerceAtLeast(0), toAllocate)
                        rentedByMachine[item.machineId] = (rentedByMachine[item.machineId] ?: 0) + allocated
                        toAllocate -= allocated
                    }
                } else if (job.machineId.isNotBlank()) {
                    rentedByMachine[job.machineId] = (rentedByMachine[job.machineId] ?: 0) + remainingOnJob
                }
            }

            val machines = roomDb.machineDao().getAllMachinesList()
            for (machine in machines) {
                val rented = rentedByMachine[machine.id] ?: 0
                val maint = machine.maintenanceQty.coerceIn(0, (machine.totalQty - rented).coerceAtLeast(0))
                val expectedAvailable = (machine.totalQty - rented - maint).coerceIn(0, machine.totalQty)
                if (machine.availableQty != expectedAvailable || machine.maintenanceQty != maint) {
                    roomDb.machineDao().updateMachine(machine.copy(availableQty = expectedAvailable, maintenanceQty = maint))
                }
            }
        } catch (e: Exception) {
            Log.d("FirestoreSync", "Stock reconciliation skipped: ${e.message}")
        }
    }

    fun stopRealtimeListeners() {
        jobSheetsListener?.remove()
        jobSheetsListener = null
        categoriesListener?.remove()
        categoriesListener = null
        machinesListener?.remove()
        machinesListener = null
        customersListener?.remove()
        customersListener = null
        paymentsListener?.remove()
        paymentsListener = null
        usersListener?.remove()
        usersListener = null
        expensesListener?.remove()
        expensesListener = null
        activityLogListener?.remove()
        activityLogListener = null
        dailyCashListener?.remove()
        dailyCashListener = null
        _isSyncing.value = false
        refreshSyncStatus()
    }

    // --- Outbound Mutations to Firestore ---

    private fun buildJobSheetPayload(jobSheet: JobSheetEntity): HashMap<String, Any?> {
        return hashMapOf(
            "id" to jobSheet.id,
            "customerId" to jobSheet.customerId,
            "customerName" to jobSheet.customerName,
            "customerPhone" to jobSheet.customerPhone,
            "customerAddress" to jobSheet.customerAddress,
            "machineId" to jobSheet.machineId,
            "machineName" to jobSheet.machineName,
            "categoryName" to jobSheet.categoryName,
            "quantity" to jobSheet.quantity,
            "initialQuantity" to jobSheet.initialQuantity,
            "returnedQuantity" to jobSheet.returnedQuantity,
            "rateUsed" to jobSheet.rateUsed,
            "discount" to jobSheet.discount,
            "deposit" to jobSheet.depositAmount,
            "depositMethod" to jobSheet.paymentMode,
            "paymentMode" to jobSheet.paymentMode,
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
            "closedBy" to jobSheet.closedBy,
            "closedByName" to jobSheet.closedByName,
            "isDeleted" to jobSheet.isDeleted,
            "lateFee" to jobSheet.lateFee,
            "totalRentalFee" to jobSheet.totalRentalFee,
            "finalSettlement" to jobSheet.finalSettlement,
            "notes" to jobSheet.notes,
            "returnRemark" to jobSheet.returnRemark,
            "itemsJson" to jobSheet.itemsJson,
            "personPhotoRef" to extractPhotoFileName(jobSheet.personPhotoPath),
            "idPhotoRef" to extractPhotoFileName(jobSheet.idPhotoPath),
            "machinePhotoRef" to extractPhotoFileName(jobSheet.machinePhotoPath),
            "updatedAt" to FieldValue.serverTimestamp()
        )
    }

    /**
     * Mutable update payload for existing job_sheets documents that strictly avoids touching
     * immutable core billing fields ('id', 'customerId', 'customerName', 'customerPhone',
     * 'machineId', 'rateUsed', 'checkoutTime', 'isDeleted') enforced by firestore.rules.
     */
    private fun buildJobSheetMutableUpdatePayload(jobSheet: JobSheetEntity): Map<String, Any?> {
        return mapOf(
            "customerAddress" to jobSheet.customerAddress,
            "machineName" to jobSheet.machineName,
            "categoryName" to jobSheet.categoryName,
            "quantity" to jobSheet.quantity,
            "initialQuantity" to jobSheet.initialQuantity,
            "returnedQuantity" to jobSheet.returnedQuantity,
            "discount" to jobSheet.discount,
            "deposit" to jobSheet.depositAmount,
            "depositMethod" to jobSheet.paymentMode,
            "paymentMode" to jobSheet.paymentMode,
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
            "closedBy" to jobSheet.closedBy,
            "closedByName" to jobSheet.closedByName,
            "lateFee" to jobSheet.lateFee,
            "totalRentalFee" to jobSheet.totalRentalFee,
            "finalSettlement" to jobSheet.finalSettlement,
            "notes" to jobSheet.notes,
            "returnRemark" to jobSheet.returnRemark,
            "itemsJson" to jobSheet.itemsJson,
            "personPhotoRef" to extractPhotoFileName(jobSheet.personPhotoPath),
            "idPhotoRef" to extractPhotoFileName(jobSheet.idPhotoPath),
            "machinePhotoRef" to extractPhotoFileName(jobSheet.machinePhotoPath),
            "updatedAt" to FieldValue.serverTimestamp()
        )
    }

    suspend fun pushJobSheet(jobSheet: JobSheetEntity, isNewCreation: Boolean = false) {
        if (auth?.currentUser == null) return
        val fs = firestore ?: return
        val path = "job_sheets/${jobSheet.id}"
        if (!checkIsDeviceOnline()) {
            val docRef = fs.collection("job_sheets").document(jobSheet.id)
            if (isNewCreation) {
                docRef.set(buildJobSheetPayload(jobSheet))
            } else {
                docRef.update(buildJobSheetMutableUpdatePayload(jobSheet))
            }
            refreshSyncStatus()
            return
        }
        try {
            val docRef = fs.collection("job_sheets").document(jobSheet.id)
            val existing = docRef.get().await()
            if (!existing.exists()) {
                docRef.set(buildJobSheetPayload(jobSheet)).await()
            } else {
                docRef.update(buildJobSheetMutableUpdatePayload(jobSheet)).await()
            }
            refreshSyncStatus()
        } catch (e: Exception) {
            setRealSyncError(handleFirestoreError(e, OperationType.WRITE, path))
        }
    }

    private fun buildCustomerPayload(customer: CustomerEntity): HashMap<String, Any?> {
        return hashMapOf(
            "id" to customer.id,
            "name" to customer.name,
            "phone" to customer.phone,
            "address" to customer.address,
            "createdAt" to customer.createdAt,
            "idPhotoFrontRef" to extractPhotoFileName(customer.idPhotoFrontPath),
            "idPhotoBackRef" to extractPhotoFileName(customer.idPhotoBackPath),
            "personPhotoRef" to extractPhotoFileName(customer.personPhotoPath),
            "updatedAt" to FieldValue.serverTimestamp()
        )
    }

    suspend fun pushCustomer(customer: CustomerEntity) {
        if (auth?.currentUser == null) return
        val fs = firestore ?: return
        val path = "customers/${customer.id}"
        val payload = buildCustomerPayload(customer)
        if (!checkIsDeviceOnline()) {
            fs.collection("customers").document(customer.id).set(payload, SetOptions.merge())
            refreshSyncStatus()
            return
        }
        try {
            fs.collection("customers").document(customer.id)
                .set(payload, SetOptions.merge())
                .await()
            refreshSyncStatus()
        } catch (e: Exception) {
            setRealSyncError(handleFirestoreError(e, OperationType.WRITE, path))
        }
    }

    private fun buildMachinePayload(machine: MachineEntity): HashMap<String, Any?> {
        return hashMapOf(
            "id" to machine.id,
            "categoryId" to machine.categoryId,
            "name" to machine.name,
            "baseDailyRate" to machine.baseRate,
            "dailyRate" to machine.currentRate,
            "totalQty" to machine.totalQty,
            "availableQty" to machine.availableQty,
            "maintenanceQty" to machine.maintenanceQty,
            "specs" to machine.modelOrSpecs,
            "updatedAt" to FieldValue.serverTimestamp()
        )
    }

    suspend fun pushMachine(machine: MachineEntity) {
        if (auth?.currentUser == null) return
        val fs = firestore ?: return
        val path = "machines/${machine.id}"
        val payload = buildMachinePayload(machine)
        if (!checkIsDeviceOnline()) {
            fs.collection("machines").document(machine.id).set(payload, SetOptions.merge())
            refreshSyncStatus()
            return
        }
        try {
            fs.collection("machines").document(machine.id)
                .set(payload, SetOptions.merge())
                .await()
            refreshSyncStatus()
        } catch (e: Exception) {
            setRealSyncError(handleFirestoreError(e, OperationType.WRITE, path))
        }
    }

    suspend fun deleteMachineFromCloud(machineId: String) {
        if (auth?.currentUser == null) return
        val fs = firestore ?: return
        val path = "machines/$machineId"
        if (!checkIsDeviceOnline()) {
            fs.collection("machines").document(machineId).delete()
            refreshSyncStatus()
            return
        }
        try {
            fs.collection("machines").document(machineId)
                .delete()
                .await()
            refreshSyncStatus()
        } catch (e: Exception) {
            setRealSyncError(handleFirestoreError(e, OperationType.DELETE, path))
        }
    }

    suspend fun deleteAllMachinesFromCloud() {
        if (auth?.currentUser == null) return
        val fs = firestore ?: return
        val path = "machines"
        if (!checkIsDeviceOnline()) return
        try {
            val docs = fs.collection("machines").get().await()
            for (doc in docs.documents) {
                fs.collection("machines").document(doc.id).delete().await()
            }
            refreshSyncStatus()
        } catch (e: Exception) {
            setRealSyncError(handleFirestoreError(e, OperationType.DELETE, path))
        }
    }

    /**
     * Atomically increments or decrements a machine's availableQty in Firestore using
     * FieldValue.increment(delta) so concurrent offline updates from two phones never
     * overwrite each other's stock delta.
     */
    suspend fun adjustMachineStockAtomically(machineId: String, delta: Int) {
        if (auth?.currentUser == null || machineId.isBlank() || delta == 0) return
        val fs = firestore ?: return
        val path = "machines/$machineId"
        val updateMap = mapOf(
            "availableQty" to FieldValue.increment(delta.toLong()),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (!checkIsDeviceOnline()) {
            fs.collection("machines").document(machineId).update(updateMap)
            refreshSyncStatus()
            return
        }
        try {
            fs.collection("machines").document(machineId)
                .update(updateMap)
                .await()
            refreshSyncStatus()
        } catch (e: Exception) {
            setRealSyncError(handleFirestoreError(e, OperationType.UPDATE, path))
        }
    }

    suspend fun pushMachineStock(machineId: String, availableQty: Int) {
        if (auth?.currentUser == null) return
        val fs = firestore ?: return
        val path = "machines/$machineId"
        val updateMap = mapOf(
            "availableQty" to availableQty,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (!checkIsDeviceOnline()) {
            fs.collection("machines").document(machineId).update(updateMap)
            refreshSyncStatus()
            return
        }
        try {
            fs.collection("machines").document(machineId)
                .update(updateMap)
                .await()
            refreshSyncStatus()
        } catch (e: Exception) {
            setRealSyncError(handleFirestoreError(e, OperationType.UPDATE, path))
        }
    }

    private fun buildActivityLogPayload(log: ActivityLogEntity): HashMap<String, Any?> {
        return hashMapOf(
            "id" to log.id,
            "jobSheetId" to log.jobSheetId,
            "action" to log.action,
            "handledByUid" to log.performedBy,
            "handledByName" to log.performedByName,
            "changeSummary" to log.changeSummary,
            "timestamp" to log.timestamp,
            "serverTimestamp" to FieldValue.serverTimestamp()
        )
    }

    suspend fun pushActivityLog(log: ActivityLogEntity) {
        if (auth?.currentUser == null) return
        val fs = firestore ?: return
        val path = "activity_log/${log.id}"
        val payload = buildActivityLogPayload(log)
        if (!checkIsDeviceOnline()) {
            fs.collection("activity_log").document(log.id).set(payload)
            refreshSyncStatus()
            return
        }
        try {
            val existing = fs.collection("activity_log").document(log.id).get().await()
            if (!existing.exists()) {
                fs.collection("activity_log").document(log.id)
                    .set(payload)
                    .await()
            }
            refreshSyncStatus()
        } catch (e: Exception) {
            setRealSyncError(handleFirestoreError(e, OperationType.CREATE, path))
        }
    }

    suspend fun pushActivityLogsForJob(jobSheetId: String) {
        if (auth?.currentUser == null) return
        try {
            val logs = roomDb.activityLogDao().getAllLogsList()
            for (log in logs.filter { it.jobSheetId == jobSheetId }) {
                pushActivityLog(log)
            }
        } catch (e: Exception) {
            Log.d("FirestoreSync", "Skipped pushing activity logs for job $jobSheetId: ${e.message}")
        }
    }

    suspend fun pushCategory(category: MachineCategoryEntity) {
        if (auth?.currentUser == null) return
        val fs = firestore ?: return
        val path = "machine_categories/${category.id}"
        val payload = hashMapOf<String, Any?>(
            "id" to category.id,
            "name" to category.name,
            "iconName" to category.iconName,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (!checkIsDeviceOnline()) {
            fs.collection("machine_categories").document(category.id).set(payload, SetOptions.merge())
            refreshSyncStatus()
            return
        }
        try {
            fs.collection("machine_categories").document(category.id)
                .set(payload, SetOptions.merge())
                .await()
            refreshSyncStatus()
        } catch (e: Exception) {
            setRealSyncError(handleFirestoreError(e, OperationType.WRITE, path))
        }
    }

    suspend fun deleteCategoryFromCloud(categoryId: String) {
        if (auth?.currentUser == null) return
        val fs = firestore ?: return
        val path = "machine_categories/$categoryId"
        if (!checkIsDeviceOnline()) {
            fs.collection("machine_categories").document(categoryId).delete()
            refreshSyncStatus()
            return
        }
        try {
            fs.collection("machine_categories").document(categoryId)
                .delete()
                .await()
            refreshSyncStatus()
        } catch (e: Exception) {
            setRealSyncError(handleFirestoreError(e, OperationType.DELETE, path))
        }
    }

    private fun buildPaymentPayload(payment: PaymentEntity): HashMap<String, Any?> {
        return hashMapOf(
            "id" to payment.id,
            "jobSheetId" to payment.jobSheetId,
            "amount" to payment.amount,
            "method" to payment.method,
            "type" to payment.type,
            "timestamp" to payment.timestamp,
            "handledBy" to payment.handledBy,
            "handledByName" to payment.handledByName,
            "remark" to payment.remark,
            "serverTimestamp" to FieldValue.serverTimestamp()
        )
    }

    suspend fun pushPayment(payment: PaymentEntity) {
        if (auth?.currentUser == null) return
        val fs = firestore ?: return
        val path = "payments/${payment.id}"
        val payload = buildPaymentPayload(payment)
        if (!checkIsDeviceOnline()) {
            fs.collection("payments").document(payment.id).set(payload)
            refreshSyncStatus()
            return
        }
        try {
            val existing = fs.collection("payments").document(payment.id).get().await()
            if (!existing.exists()) {
                fs.collection("payments").document(payment.id)
                    .set(payload)
                    .await()
            }
            refreshSyncStatus()
        } catch (e: Exception) {
            setRealSyncError(handleFirestoreError(e, OperationType.CREATE, path))
        }
    }

    suspend fun pushPaymentsForJob(jobSheetId: String) {
        if (auth?.currentUser == null) return
        try {
            val allPayments = roomDb.paymentDao().getAllPaymentsList()
            for (payment in allPayments.filter { it.jobSheetId == jobSheetId }) {
                pushPayment(payment)
            }
        } catch (e: Exception) {
            Log.d("FirestoreSync", "Skipped pushing payments for job $jobSheetId: ${e.message}")
        }
    }

    suspend fun pushExpense(expense: ExpenseEntity) {
        if (auth?.currentUser == null) return
        val fs = firestore ?: return
        val path = "expenses/${expense.id}"
        val payload = hashMapOf<String, Any?>(
            "id" to expense.id,
            "date" to expense.date,
            "timestamp" to expense.timestamp,
            "expenseType" to expense.expenseType,
            "amount" to expense.amount,
            "remarks" to expense.remarks,
            "createdBy" to expense.createdBy,
            "createdByName" to expense.createdByName,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (!checkIsDeviceOnline()) {
            fs.collection("expenses").document(expense.id).set(payload)
            refreshSyncStatus()
            return
        }
        try {
            val docRef = fs.collection("expenses").document(expense.id)
            val snap = docRef.get().await()
            if (!snap.exists()) {
                docRef.set(payload).await()
            } else if (auth?.currentUser?.email?.trim()?.lowercase() == OWNER_EMAIL.trim().lowercase()) {
                docRef.set(payload, SetOptions.merge()).await()
            }
            refreshSyncStatus()
        } catch (e: Exception) {
            setRealSyncError(handleFirestoreError(e, OperationType.WRITE, path))
        }
    }

    suspend fun deleteExpenseFromCloud(expenseId: String) {
        if (auth?.currentUser == null) return
        val fs = firestore ?: return
        val path = "expenses/$expenseId"
        if (!checkIsDeviceOnline()) {
            fs.collection("expenses").document(expenseId).delete()
            refreshSyncStatus()
            return
        }
        try {
            fs.collection("expenses").document(expenseId)
                .delete()
                .await()
            refreshSyncStatus()
        } catch (e: Exception) {
            setRealSyncError(handleFirestoreError(e, OperationType.DELETE, path))
        }
    }

    suspend fun pushDailyCash(dailyCash: DailyCashEntity) {
        if (auth?.currentUser == null) return
        val fs = firestore ?: return
        val docId = dailyCash.date.replace("/", "-")
        val path = "daily_cash/$docId"
        val payload = hashMapOf<String, Any?>(
            "date" to dailyCash.date,
            "openingCash" to dailyCash.openingCash,
            "closingCash" to dailyCash.closingCash,
            "enteredBy" to dailyCash.enteredBy,
            "enteredByName" to dailyCash.enteredByName,
            "updatedAtMillis" to dailyCash.updatedAt,
            "openingBalance" to dailyCash.openingBalance,
            "cashCollected" to dailyCash.cashCollected,
            "cashRefunded" to dailyCash.cashRefunded,
            "notes" to dailyCash.notes,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (!checkIsDeviceOnline()) {
            fs.collection("daily_cash").document(docId).set(payload, SetOptions.merge())
            refreshSyncStatus()
            return
        }
        try {
            fs.collection("daily_cash").document(docId)
                .set(payload, SetOptions.merge())
                .await()
            refreshSyncStatus()
        } catch (e: Exception) {
            setRealSyncError(handleFirestoreError(e, OperationType.WRITE, path))
        }
    }

    suspend fun getStaffUser(uid: String): UserEntity? {
        if (auth?.currentUser == null) return null
        val fs = firestore ?: return null
        val path = "users/$uid"
        return try {
            val doc = fs.collection("users").document(uid).get().await()
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
                    maxDiscountAmount = maxDiscount,
                    registeredPhone = doc.getString("registeredPhone") ?: "9876543210"
                )
            } else null
        } catch (e: Exception) {
            setRealSyncError(handleFirestoreError(e, OperationType.GET, path))
            null
        }
    }

    /**
     * Owner-only: creates or updates a staff profile in Firestore.
     * If phoneOrUsername is a Google email address, also writes /users/{email} (trimmed & lowercase)
     * so that when that staff member signs in with Google on their phone, Firestore rules
     * recognize their pre-authorized email and allow linking their UID as a worker.
     */
    suspend fun saveStaffProfile(user: UserEntity) {
        if (auth?.currentUser == null) return
        val fs = firestore ?: return
        val cleanIdentifier = user.phoneOrUsername.trim().lowercase()
        val path = "users/${user.id}"
        val payload = hashMapOf<String, Any?>(
            "uid" to user.id,
            "name" to user.name.trim(),
            "phoneOrUsername" to cleanIdentifier,
            "role" to user.role,
            "active" to user.isActive,
            "maxDiscountAmount" to user.maxDiscountAmount,
            "registeredPhone" to user.registeredPhone.trim(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (!checkIsDeviceOnline()) {
            fs.collection("users").document(user.id).set(payload, SetOptions.merge())
            if (cleanIdentifier.contains("@")) {
                fs.collection("users").document(cleanIdentifier).set(payload, SetOptions.merge())
            }
            refreshSyncStatus()
            return
        }
        try {
            fs.collection("users").document(user.id)
                .set(payload, SetOptions.merge())
                .await()
            if (cleanIdentifier.contains("@")) {
                fs.collection("users").document(cleanIdentifier)
                    .set(payload, SetOptions.merge())
                    .await()
            }
            refreshSyncStatus()
        } catch (e: Exception) {
            setRealSyncError(handleFirestoreError(e, OperationType.WRITE, path))
        }
    }

    /**
     * Requirement 1 & 2: Runs the one-time tablet-to-cloud migration (if not yet marked migrated)
     * followed by an automatic catch-up sync (pushing any unsynced local records and pulling
     * cloud records into Room). Synchronized via syncCycleMutex so concurrent triggers never collide.
     */
    suspend fun runAutomaticBackgroundSyncCycle() {
        if (!checkIsDeviceOnline() || auth?.currentUser == null) {
            refreshSyncStatus()
            return
        }
        syncCycleMutex.withLock {
            if (!checkIsDeviceOnline() || auth?.currentUser == null) {
                refreshSyncStatus()
                return
            }
            _isActiveSyncOperation.value = true
            refreshSyncStatus()
            try {
                runAutomaticOneTimeMigrationIfNeeded()
                runAutomaticCatchUpSync()
            } finally {
                _isActiveSyncOperation.value = false
                refreshSyncStatus()
            }
        }
    }

    /**
     * Requirement 1: Runs the existing one-time tablet-to-cloud migration automatically once,
     * on first launch after the update, when the user is signed in with Google and online.
     * Stores a persistent "migrated" flag so it never runs twice.
     */
    suspend fun runAutomaticOneTimeMigrationIfNeeded(): Boolean {
        if (isOneTimeMigrationCompleted()) return false
        val currentUser = auth?.currentUser ?: return false
        if (!checkIsDeviceOnline()) return false
        val signedInEmail = currentUser.email?.trim()?.lowercase().orEmpty()
        if (signedInEmail != OWNER_EMAIL.trim().lowercase()) return false

        val report = migrateExistingTabletData()
        if (report.errorSummary == null && report.totalFailed == 0) {
            markOneTimeMigrationCompleted()
            return true
        }
        return false
    }

    /**
     * STEP 3: One-Time, Owner-only idempotent migration of all existing local Room data
     * (customers, machines, job_sheets, payments, activity_log) to Firestore.
     * Preserves existing IDs, job sheet numbers, and original timestamps without creating duplicates.
     */
    suspend fun migrateExistingTabletData(
        onProgress: (String) -> Unit = {}
    ): TabletMigrationReport {
        val currentUser = auth?.currentUser
        val fs = firestore
        if (currentUser == null || fs == null) {
            return TabletMigrationReport(
                counts = emptyList(),
                totalUploaded = 0,
                totalSkipped = 0,
                totalFailed = 0,
                errorSummary = "Please sign in with Google first to run cloud migration."
            )
        }

        if (!checkIsDeviceOnline()) {
            refreshSyncStatus()
            return TabletMigrationReport(
                counts = emptyList(),
                totalUploaded = 0,
                totalSkipped = 0,
                totalFailed = 0,
                errorSummary = "Offline (will sync later)"
            )
        }

        val signedInEmail = currentUser.email?.trim()?.lowercase().orEmpty()
        val isOwnerSession = signedInEmail == OWNER_EMAIL.trim().lowercase()
        if (!isOwnerSession) {
            return TabletMigrationReport(
                counts = emptyList(),
                totalUploaded = 0,
                totalSkipped = 0,
                totalFailed = 0,
                errorSummary = "Only the Owner account ($OWNER_EMAIL) can run tablet migration."
            )
        }

        // Ensure Owner's /users/{uid} document exists
        try {
            fs.collection("users").document(currentUser.uid).set(
                hashMapOf(
                    "uid" to currentUser.uid,
                    "name" to (currentUser.displayName ?: "Owner"),
                    "phoneOrUsername" to signedInEmail,
                    "role" to "owner",
                    "active" to true,
                    "updatedAt" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            ).await()
        } catch (_: Exception) { }

        val counts = mutableListOf<CollectionMigrationCount>()

        // 0. Ensure machine_categories are present in Firestore so machines reference valid categories
        try {
            val localCategories = roomDb.machineCategoryDao().getAllCategoriesList()
            for (cat in localCategories) {
                val docRef = fs.collection("machine_categories").document(cat.id)
                if (!docRef.get().await().exists()) {
                    docRef.set(
                        hashMapOf(
                            "id" to cat.id,
                            "name" to cat.name,
                            "iconName" to cat.iconName,
                            "updatedAt" to FieldValue.serverTimestamp()
                        ),
                        SetOptions.merge()
                    ).await()
                }
            }
        } catch (_: Exception) { }

        // 1. Migrate Customers
        val localCustomers = roomDb.customerDao().getAllCustomersList()
        var custUploaded = 0
        var custSkipped = 0
        var custFailed = 0
        localCustomers.forEachIndexed { idx, customer ->
            onProgress("Migrating customers (${idx + 1}/${localCustomers.size})...")
            try {
                val docRef = fs.collection("customers").document(customer.id)
                val snap = docRef.get().await()
                if (snap.exists()) {
                    custSkipped++
                } else {
                    docRef.set(buildCustomerPayload(customer), SetOptions.merge()).await()
                    custUploaded++
                }
            } catch (e: Exception) {
                custFailed++
                setRealSyncError(handleFirestoreError(e, OperationType.WRITE, "customers/${customer.id}"))
            }
        }
        counts.add(CollectionMigrationCount("customers", custUploaded, custSkipped, custFailed))

        // 2. Migrate Machines
        val localMachines = roomDb.machineDao().getAllMachinesList()
        var machUploaded = 0
        var machSkipped = 0
        var machFailed = 0
        localMachines.forEachIndexed { idx, machine ->
            onProgress("Migrating machines (${idx + 1}/${localMachines.size})...")
            try {
                val docRef = fs.collection("machines").document(machine.id)
                val snap = docRef.get().await()
                if (snap.exists()) {
                    machSkipped++
                } else {
                    docRef.set(buildMachinePayload(machine), SetOptions.merge()).await()
                    machUploaded++
                }
            } catch (e: Exception) {
                machFailed++
                setRealSyncError(handleFirestoreError(e, OperationType.WRITE, "machines/${machine.id}"))
            }
        }
        counts.add(CollectionMigrationCount("machines", machUploaded, machSkipped, machFailed))

        // 3. Migrate Job Sheets & sync transactional job counter
        val localJobSheets = roomDb.jobSheetDao().getAllJobSheetsList()
        var jobUploaded = 0
        var jobSkipped = 0
        var jobFailed = 0
        var maxJobNum = 0
        localJobSheets.forEachIndexed { idx, job ->
            onProgress("Migrating job_sheets (${idx + 1}/${localJobSheets.size})...")
            val parsedNum = job.id.removePrefix("JOB-").trim().toIntOrNull() ?: 0
            if (parsedNum > maxJobNum) maxJobNum = parsedNum
            try {
                val docRef = fs.collection("job_sheets").document(job.id)
                val snap = docRef.get().await()
                if (snap.exists()) {
                    jobSkipped++
                } else {
                    docRef.set(buildJobSheetPayload(job)).await()
                    jobUploaded++
                }
            } catch (e: Exception) {
                jobFailed++
                setRealSyncError(handleFirestoreError(e, OperationType.WRITE, "job_sheets/${job.id}"))
            }
        }
        if (maxJobNum > 0) {
            try {
                val counterRef = fs.collection("counters").document("job_sheets")
                fs.runTransaction { txn ->
                    val snap = txn.get(counterRef)
                    val cloudLast = (snap.get("lastNumber") as? Number)?.toInt() ?: 0
                    if (maxJobNum > cloudLast) {
                        txn.set(
                            counterRef,
                            mapOf("lastNumber" to maxJobNum, "updatedAt" to FieldValue.serverTimestamp()),
                            SetOptions.merge()
                        )
                    }
                }.await()
            } catch (_: Exception) { }
        }
        counts.add(CollectionMigrationCount("job_sheets", jobUploaded, jobSkipped, jobFailed))

        // 4. Migrate Payments (Immutable financial ledger - skip existing)
        val localPayments = roomDb.paymentDao().getAllPaymentsList()
        var payUploaded = 0
        var paySkipped = 0
        var payFailed = 0
        localPayments.forEachIndexed { idx, payment ->
            onProgress("Migrating payments (${idx + 1}/${localPayments.size})...")
            try {
                val docRef = fs.collection("payments").document(payment.id)
                val snap = docRef.get().await()
                if (snap.exists()) {
                    paySkipped++
                } else {
                    docRef.set(buildPaymentPayload(payment)).await()
                    payUploaded++
                }
            } catch (e: Exception) {
                payFailed++
                setRealSyncError(handleFirestoreError(e, OperationType.CREATE, "payments/${payment.id}"))
            }
        }
        counts.add(CollectionMigrationCount("payments", payUploaded, paySkipped, payFailed))

        // 5. Migrate Activity Log (Write-once audit ledger - skip existing)
        val localLogs = roomDb.activityLogDao().getAllLogsList()
        var logUploaded = 0
        var logSkipped = 0
        var logFailed = 0
        localLogs.forEachIndexed { idx, log ->
            onProgress("Migrating activity_log (${idx + 1}/${localLogs.size})...")
            try {
                val docRef = fs.collection("activity_log").document(log.id)
                val snap = docRef.get().await()
                if (snap.exists()) {
                    logSkipped++
                } else {
                    docRef.set(buildActivityLogPayload(log)).await()
                    logUploaded++
                }
            } catch (e: Exception) {
                logFailed++
                setRealSyncError(handleFirestoreError(e, OperationType.CREATE, "activity_log/${log.id}"))
            }
        }
        counts.add(CollectionMigrationCount("activity_log", logUploaded, logSkipped, logFailed))

        val totalFailed = counts.sumOf { it.failed }
        if (totalFailed == 0) {
            markOneTimeMigrationCompleted()
            _syncError.value = null
        }
        refreshSyncStatus()
        onProgress("Migration complete.")
        return TabletMigrationReport(
            counts = counts,
            totalUploaded = counts.sumOf { it.uploaded },
            totalSkipped = counts.sumOf { it.skipped },
            totalFailed = totalFailed
        )
    }

    /**
     * Requirement 2: Automatic idempotent catch-up sync that runs on app start,
     * whenever the device regains internet, and periodically via WorkManager.
     * Pushes any unsynced local records and pulls all latest cloud records into Room.
     */
    suspend fun runAutomaticCatchUpSync() {
        val currentUser = auth?.currentUser ?: return
        val fs = firestore ?: return
        if (!checkIsDeviceOnline()) {
            refreshSyncStatus()
            return
        }
        val signedInEmail = currentUser.email?.trim()?.lowercase().orEmpty()
        val isOwnerAccount = signedInEmail == OWNER_EMAIL.trim().lowercase()

        // EnsureStaff/Owner session document exists if authorized
        try {
            if (isOwnerAccount) {
                fs.collection("users").document(currentUser.uid).set(
                    hashMapOf(
                        "uid" to currentUser.uid,
                        "name" to (currentUser.displayName ?: "Owner"),
                        "phoneOrUsername" to signedInEmail,
                        "role" to "owner",
                        "active" to true,
                        "updatedAt" to FieldValue.serverTimestamp()
                    ),
                    SetOptions.merge()
                ).await()
            } else {
                val uidDoc = fs.collection("users").document(currentUser.uid).get().await()
                if (!uidDoc.exists() && signedInEmail.contains("@")) {
                    val preAuth = fs.collection("users").document(signedInEmail).get().await()
                    if (preAuth.exists() && preAuth.getString("role") == "worker") {
                        fs.collection("users").document(currentUser.uid).set(
                            hashMapOf(
                                "uid" to currentUser.uid,
                                "name" to (preAuth.getString("name") ?: currentUser.displayName ?: "Worker"),
                                "phoneOrUsername" to signedInEmail,
                                "role" to "worker",
                                "active" to true,
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        ).await()
                    }
                }
            }
        } catch (_: Exception) { }

        // 1. Push local records according to strict security rules
        if (isOwnerAccount) {
            for (cat in roomDb.machineCategoryDao().getAllCategoriesList()) {
                try {
                    fs.collection("machine_categories").document(cat.id).set(
                        hashMapOf(
                            "id" to cat.id,
                            "name" to cat.name,
                            "iconName" to cat.iconName,
                            "updatedAt" to FieldValue.serverTimestamp()
                        ),
                        SetOptions.merge()
                    ).await()
                } catch (_: Exception) { }
            }
            for (machine in roomDb.machineDao().getAllMachinesList()) {
                try {
                    fs.collection("machines").document(machine.id)
                        .set(buildMachinePayload(machine), SetOptions.merge())
                        .await()
                } catch (_: Exception) { }
            }
        }

        for (customer in roomDb.customerDao().getAllCustomersList()) {
            try {
                fs.collection("customers").document(customer.id)
                    .set(buildCustomerPayload(customer), SetOptions.merge())
                    .await()
            } catch (_: Exception) { }
        }

        var maxJobNum = 0
        for (job in roomDb.jobSheetDao().getAllJobSheetsList()) {
            val parsedNum = job.id.removePrefix("JOB-").trim().toIntOrNull() ?: 0
            if (parsedNum > maxJobNum) maxJobNum = parsedNum
            try {
                val docRef = fs.collection("job_sheets").document(job.id)
                val snap = docRef.get().await()
                if (!snap.exists()) {
                    if (isOwnerAccount || job.discount <= 200.0) {
                        docRef.set(buildJobSheetPayload(job)).await()
                    }
                } else {
                    docRef.update(buildJobSheetMutableUpdatePayload(job)).await()
                }
            } catch (_: Exception) { }
        }

        if (maxJobNum > 0) {
            try {
                val counterRef = fs.collection("counters").document("job_sheets")
                fs.runTransaction { txn ->
                    val snap = txn.get(counterRef)
                    val cloudLast = (snap.get("lastNumber") as? Number)?.toInt() ?: 0
                    if (maxJobNum > cloudLast) {
                        txn.set(
                            counterRef,
                            mapOf("lastNumber" to maxJobNum, "updatedAt" to FieldValue.serverTimestamp()),
                            SetOptions.merge()
                        )
                    }
                }.await()
            } catch (_: Exception) { }
        }

        // Write-once collections: only create if document does not exist yet
        for (payment in roomDb.paymentDao().getAllPaymentsList()) {
            try {
                val docRef = fs.collection("payments").document(payment.id)
                if (!docRef.get().await().exists()) {
                    docRef.set(buildPaymentPayload(payment)).await()
                }
            } catch (_: Exception) { }
        }

        for (log in roomDb.activityLogDao().getAllLogsList()) {
            try {
                val docRef = fs.collection("activity_log").document(log.id)
                if (!docRef.get().await().exists()) {
                    docRef.set(buildActivityLogPayload(log)).await()
                }
            } catch (_: Exception) { }
        }

        for (expense in roomDb.expenseDao().getAllExpensesList()) {
            try {
                val docRef = fs.collection("expenses").document(expense.id)
                if (isOwnerAccount || !docRef.get().await().exists()) {
                    docRef.set(
                        hashMapOf(
                            "id" to expense.id,
                            "date" to expense.date,
                            "timestamp" to expense.timestamp,
                            "expenseType" to expense.expenseType,
                            "amount" to expense.amount,
                            "remarks" to expense.remarks,
                            "createdBy" to expense.createdBy,
                            "createdByName" to expense.createdByName,
                            "updatedAt" to FieldValue.serverTimestamp()
                        ),
                        SetOptions.merge()
                    ).await()
                }
            } catch (_: Exception) { }
        }

        for (dailyCash in roomDb.dailyCashDao().getAllDailyCashList()) {
            try {
                pushDailyCash(dailyCash)
            } catch (_: Exception) { }
        }

        // 2. Pull latest cloud records into local Room
        pullAllCloudCollectionsToRoom(fs, isOwnerAccount)
        reconcileMachineStockFromActiveJobs()
    }

    private suspend fun pullAllCloudCollectionsToRoom(fs: FirebaseFirestore, isOwnerAccount: Boolean) {
        try {
            val cloudCategories = fs.collection("machine_categories").get().await()
            for (doc in cloudCategories.documents) {
                val name = doc.getString("name") ?: continue
                val iconName = doc.getString("iconName") ?: "build"
                roomDb.machineCategoryDao().insertCategory(
                    MachineCategoryEntity(id = doc.id, name = name, iconName = iconName)
                )
            }
        } catch (_: Exception) { }

        try {
            val cloudMachines = fs.collection("machines").get().await()
            for (doc in cloudMachines.documents) {
                val id = doc.id
                val categoryId = doc.getString("categoryId") ?: ""
                val name = doc.getString("name") ?: ""
                val baseDailyRate = (doc.get("baseDailyRate") as? Number)?.toDouble() ?: 350.0
                val dailyRate = (doc.get("dailyRate") as? Number)?.toDouble() ?: baseDailyRate
                val totalQty = (doc.get("totalQty") as? Number)?.toInt() ?: 5
                val maintenanceQty = ((doc.get("maintenanceQty") as? Number)?.toInt() ?: 0).coerceIn(0, totalQty)
                val rawAvailableQty = (doc.get("availableQty") as? Number)?.toInt() ?: (totalQty - maintenanceQty)
                val availableQty = rawAvailableQty.coerceIn(0, (totalQty - maintenanceQty).coerceAtLeast(0))
                val specs = doc.getString("specs") ?: ""
                roomDb.machineDao().insertMachine(
                    MachineEntity(
                        id = id,
                        categoryId = categoryId,
                        name = name,
                        baseRate = baseDailyRate,
                        currentRate = dailyRate,
                        totalQty = totalQty,
                        availableQty = availableQty,
                        modelOrSpecs = specs,
                        maintenanceQty = maintenanceQty
                    )
                )
            }
        } catch (_: Exception) { }

        try {
            val cloudCustomers = fs.collection("customers").get().await()
            for (doc in cloudCustomers.documents) {
                val id = doc.id
                val existing = roomDb.customerDao().getCustomerById(id)
                val customer = CustomerEntity(
                    id = id,
                    name = doc.getString("name") ?: "",
                    phone = doc.getString("phone") ?: "",
                    address = doc.getString("address") ?: "",
                    idPhotoFrontPath = resolveSyncedPhotoPath(existing?.idPhotoFrontPath, doc.getString("idPhotoFrontRef")),
                    idPhotoBackPath = resolveSyncedPhotoPath(existing?.idPhotoBackPath, doc.getString("idPhotoBackRef")),
                    personPhotoPath = resolveSyncedPhotoPath(existing?.personPhotoPath, doc.getString("personPhotoRef")),
                    createdAt = (doc.get("createdAt") as? Number)?.toLong() ?: existing?.createdAt ?: System.currentTimeMillis()
                )
                roomDb.customerDao().insertCustomer(customer)
            }
        } catch (_: Exception) { }

        try {
            val cloudJobs = fs.collection("job_sheets").get().await()
            for (doc in cloudJobs.documents) {
                val id = doc.id
                val existing = roomDb.jobSheetDao().getJobSheetById(id)
                val quantity = (doc.get("quantity") as? Number)?.toInt() ?: existing?.quantity ?: 1
                val checkoutTime = (doc.get("startTime") as? Number)?.toLong()
                    ?: (doc.get("checkoutTime") as? Number)?.toLong() ?: System.currentTimeMillis()
                val returnedBy = doc.getString("returnedByUid") ?: doc.getString("returnedBy") ?: existing?.returnedBy ?: ""
                val returnedByName = doc.getString("returnedByName") ?: existing?.returnedByName ?: ""
                val entity = JobSheetEntity(
                    id = id,
                    customerId = doc.getString("customerId") ?: existing?.customerId ?: "",
                    customerName = doc.getString("customerName") ?: existing?.customerName ?: "",
                    customerPhone = doc.getString("customerPhone") ?: existing?.customerPhone ?: "",
                    customerAddress = doc.getString("customerAddress") ?: existing?.customerAddress ?: "",
                    machineId = doc.getString("machineId") ?: existing?.machineId ?: "",
                    machineName = doc.getString("machineName") ?: existing?.machineName ?: "",
                    categoryName = doc.getString("categoryName") ?: existing?.categoryName ?: "",
                    quantity = quantity,
                    initialQuantity = (doc.get("initialQuantity") as? Number)?.toInt() ?: existing?.initialQuantity ?: quantity,
                    returnedQuantity = (doc.get("returnedQuantity") as? Number)?.toInt() ?: existing?.returnedQuantity ?: 0,
                    rateUsed = (doc.get("rateUsed") as? Number)?.toDouble() ?: existing?.rateUsed ?: 0.0,
                    discount = (doc.get("discount") as? Number)?.toDouble() ?: existing?.discount ?: 0.0,
                    depositAmount = (doc.get("deposit") as? Number)?.toDouble()
                        ?: (doc.get("depositAmount") as? Number)?.toDouble() ?: existing?.depositAmount ?: 0.0,
                    billingCycle = doc.getString("billingCycleType") ?: doc.getString("billingCycle") ?: "working_hours",
                    checkoutTime = checkoutTime,
                    dueTime = (doc.get("dueTime") as? Number)?.toLong() ?: (checkoutTime + 24 * 3600 * 1000L),
                    returnTime = (doc.get("returnTime") as? Number)?.toLong() ?: existing?.returnTime,
                    status = doc.getString("status") ?: existing?.status ?: "active",
                    lateFee = (doc.get("lateFee") as? Number)?.toDouble() ?: 0.0,
                    totalRentalFee = (doc.get("totalRentalFee") as? Number)?.toDouble() ?: 0.0,
                    finalSettlement = (doc.get("finalSettlement") as? Number)?.toDouble() ?: 0.0,
                    checkoutBy = doc.getString("checkoutByUid") ?: doc.getString("checkoutBy") ?: existing?.checkoutBy ?: "",
                    checkoutByName = doc.getString("checkoutByName") ?: existing?.checkoutByName ?: "",
                    handledBy = doc.getString("handledByUid") ?: doc.getString("handledBy") ?: existing?.handledBy ?: "",
                    handledByName = doc.getString("handledByName") ?: existing?.handledByName ?: "",
                    returnedBy = returnedBy,
                    returnedByName = returnedByName,
                    closedBy = doc.getString("closedBy") ?: existing?.closedBy ?: returnedBy,
                    closedByName = doc.getString("closedByName") ?: existing?.closedByName ?: returnedByName,
                    notes = doc.getString("notes") ?: existing?.notes ?: "",
                    returnRemark = doc.getString("returnRemark") ?: existing?.returnRemark ?: "",
                    isDeleted = doc.getBoolean("isDeleted") ?: false,
                    personPhotoPath = resolveSyncedPhotoPath(existing?.personPhotoPath, doc.getString("personPhotoRef")),
                    idPhotoPath = resolveSyncedPhotoPath(existing?.idPhotoPath, doc.getString("idPhotoRef")),
                    machinePhotoPath = resolveSyncedPhotoPath(existing?.machinePhotoPath, doc.getString("machinePhotoRef")),
                    paymentMode = doc.getString("paymentMode") ?: doc.getString("depositMethod") ?: existing?.paymentMode ?: "cash",
                    itemsJson = doc.getString("itemsJson") ?: existing?.itemsJson ?: ""
                )
                roomDb.jobSheetDao().insertJobSheet(entity)
                com.example.util.DueAlarmScheduler.scheduleAlarms(context, entity)
            }
        } catch (_: Exception) { }

        try {
            val cloudPayments = fs.collection("payments").get().await()
            for (doc in cloudPayments.documents) {
                val jobSheetId = doc.getString("jobSheetId") ?: continue
                roomDb.paymentDao().insertPayment(
                    PaymentEntity(
                        id = doc.id,
                        jobSheetId = jobSheetId,
                        amount = (doc.get("amount") as? Number)?.toDouble() ?: 0.0,
                        method = doc.getString("method") ?: "cash",
                        type = doc.getString("type") ?: "advance",
                        timestamp = (doc.get("timestamp") as? Number)?.toLong() ?: System.currentTimeMillis(),
                        handledBy = doc.getString("handledBy") ?: "",
                        handledByName = doc.getString("handledByName") ?: "",
                        remark = doc.getString("remark") ?: ""
                    )
                )
            }
        } catch (_: Exception) { }

        if (isOwnerAccount) {
            try {
                val cloudLogs = fs.collection("activity_log").get().await()
                for (doc in cloudLogs.documents) {
                    val jobSheetId = doc.getString("jobSheetId") ?: continue
                    roomDb.activityLogDao().insertLog(
                        ActivityLogEntity(
                            id = doc.id,
                            jobSheetId = jobSheetId,
                            action = doc.getString("action") ?: "updated",
                            performedBy = doc.getString("handledByUid") ?: doc.getString("performedBy") ?: "",
                            performedByName = doc.getString("handledByName") ?: doc.getString("performedByName") ?: "",
                            timestamp = (doc.get("timestamp") as? Number)?.toLong() ?: System.currentTimeMillis(),
                            changeSummary = doc.getString("changeSummary") ?: ""
                        )
                    )
                }
            } catch (_: Exception) { }

            try {
                val cloudExpenses = fs.collection("expenses").get().await()
                for (doc in cloudExpenses.documents) {
                    val date = doc.getString("date") ?: continue
                    roomDb.expenseDao().insertExpense(
                        ExpenseEntity(
                            id = doc.id,
                            date = date,
                            timestamp = (doc.get("timestamp") as? Number)?.toLong() ?: System.currentTimeMillis(),
                            expenseType = doc.getString("expenseType") ?: "",
                            amount = (doc.get("amount") as? Number)?.toDouble() ?: 0.0,
                            remarks = doc.getString("remarks") ?: "",
                            createdBy = doc.getString("createdBy") ?: "",
                            createdByName = doc.getString("createdByName") ?: ""
                        )
                    )
                }
            } catch (_: Exception) { }

            try {
                val cloudDailyCash = fs.collection("daily_cash").get().await()
                for (doc in cloudDailyCash.documents) {
                    val date = doc.getString("date") ?: doc.id
                    val openingCash = (doc.get("openingCash") as? Number)?.toDouble() ?: 0.0
                    val closingCash = (doc.get("closingCash") as? Number)?.toDouble() ?: 0.0
                    roomDb.dailyCashDao().insertOrUpdate(
                        DailyCashEntity(
                            date = date,
                            openingCash = openingCash,
                            closingCash = closingCash,
                            enteredBy = doc.getString("enteredBy") ?: "",
                            enteredByName = doc.getString("enteredByName") ?: "",
                            updatedAt = (doc.get("updatedAtMillis") as? Number)?.toLong() ?: System.currentTimeMillis(),
                            openingBalance = (doc.get("openingBalance") as? Number)?.toDouble() ?: openingCash,
                            cashCollected = (doc.get("cashCollected") as? Number)?.toDouble() ?: 0.0,
                            cashRefunded = (doc.get("cashRefunded") as? Number)?.toDouble() ?: 0.0,
                            notes = doc.getString("notes") ?: ""
                        )
                    )
                }
            } catch (_: Exception) { }
        }
    }

    /**
     * Requirement 5: Owner-only manual "Sync Devices" button handler.
     */
    suspend fun syncToOtherDevicesNow(
        selectedUser: UserEntity?,
        onProgress: (String) -> Unit = {}
    ): Result<String> {
        val currentUser = auth?.currentUser
        val fs = firestore
        if (currentUser == null || fs == null) {
            val msg = "Please sign in with Google to sync data with other devices."
            setRealSyncError(msg)
            return Result.failure(IllegalStateException(msg))
        }

        if (!checkIsDeviceOnline()) {
            refreshSyncStatus()
            return Result.failure(IllegalStateException("Offline (will sync later)"))
        }

        _isActiveSyncOperation.value = true
        refreshSyncStatus()
        return try {
            _syncError.value = null
            onProgress("Syncing with cloud...")
            if (selectedUser != null) {
                linkAuthenticatedStaffSession(selectedUser)
            }
            runAutomaticOneTimeMigrationIfNeeded()
            runAutomaticCatchUpSync()
            startRealtimeListeners()

            val totalJobsNow = roomDb.jobSheetDao().getAllJobSheetsList().size
            val totalCustNow = roomDb.customerDao().getAllCustomersList().size
            val totalMachNow = roomDb.machineDao().getAllMachinesList().size
            val summary = "Synced $totalJobsNow Job Sheets, $totalCustNow Customers & $totalMachNow Machines across all devices!"
            onProgress(summary)
            Result.success(summary)
        } catch (e: Exception) {
            val err = handleFirestoreError(e, OperationType.WRITE, "syncToOtherDevices")
            setRealSyncError(err)
            Result.failure(IllegalStateException(err, e))
        } finally {
            _isActiveSyncOperation.value = false
            refreshSyncStatus()
        }
    }

    /**
     * Normalizes all existing staff records in Room and Firestore (trimming spaces and converting
     * emails/usernames to lowercase) so staff added earlier with capital letters or spaces work
     * automatically without re-adding them.
     */
    suspend fun normalizeAndSyncExistingStaffRecords() {
        try {
            val localUsers = roomDb.userDao().getAllUsersList()
            for (u in localUsers) {
                val cleaned = u.phoneOrUsername.trim().lowercase()
                if (u.phoneOrUsername != cleaned) {
                    roomDb.userDao().updateUser(u.copy(phoneOrUsername = cleaned))
                }
            }

            if (!checkIsDeviceOnline()) return
            val currentUser = auth?.currentUser ?: return
            val fs = firestore ?: return
            val signedInEmail = currentUser.email?.trim()?.lowercase().orEmpty()
            val ownerEmailLower = OWNER_EMAIL.trim().lowercase()

            if (signedInEmail == ownerEmailLower) {
                val updatedLocalUsers = roomDb.userDao().getAllUsersList()
                for (u in updatedLocalUsers) {
                    if (u.role == "worker") {
                        saveStaffProfile(u.copy(phoneOrUsername = u.phoneOrUsername.trim().lowercase()))
                    }
                }

                val existingCloudUsers = fs.collection("users").get().await()
                for (doc in existingCloudUsers.documents) {
                    val docId = doc.id
                    val role = doc.getString("role") ?: "worker"
                    val rawIdentifier = doc.getString("phoneOrUsername").orEmpty()
                    val cleanIdentifier = rawIdentifier.trim().lowercase()

                    if (role == "worker") {
                        val normalizedPayload = hashMapOf<String, Any?>(
                            "uid" to (doc.getString("uid") ?: docId),
                            "name" to (doc.getString("name")?.trim() ?: "Worker"),
                            "phoneOrUsername" to cleanIdentifier,
                            "role" to "worker",
                            "active" to (doc.getBoolean("active") ?: true),
                            "maxDiscountAmount" to ((doc.get("maxDiscountAmount") as? Number)?.toDouble() ?: 200.0),
                            "registeredPhone" to (doc.getString("registeredPhone") ?: ""),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                        if (rawIdentifier != cleanIdentifier) {
                            fs.collection("users").document(docId)
                                .set(normalizedPayload, SetOptions.merge())
                                .await()
                        }
                        if (cleanIdentifier.contains("@")) {
                            fs.collection("users").document(cleanIdentifier)
                                .set(normalizedPayload, SetOptions.merge())
                                .await()
                            if (docId.contains("@") && docId != cleanIdentifier) {
                                fs.collection("users").document(docId).delete().await()
                            }
                        }
                        if (docId.contains("@")) {
                            val cleanDocId = docId.trim().lowercase()
                            if (docId != cleanDocId) {
                                val fixedPayload = HashMap(normalizedPayload)
                                if (cleanIdentifier.isEmpty()) {
                                    fixedPayload["phoneOrUsername"] = cleanDocId
                                }
                                fs.collection("users").document(cleanDocId)
                                    .set(fixedPayload, SetOptions.merge())
                                    .await()
                                fs.collection("users").document(docId).delete().await()
                            }
                        }
                    }
                }

                runAutomaticBackgroundSyncCycle()
            }
        } catch (e: Exception) {
            Log.d("FirestoreSync", "Staff normalization skipped: ${e.message}")
        }
    }

    /**
     * Safely links a signed-in Google user's Firebase UID to their pre-authorized staff profile
     * while strictly respecting firestore.rules role and update constraints.
     */
    suspend fun linkAuthenticatedStaffSession(selectedUser: UserEntity): UserEntity {
        val currentUser = auth?.currentUser ?: return selectedUser
        val fs = firestore ?: return selectedUser
        val email = currentUser.email?.trim()?.lowercase().orEmpty()
        val ownerEmailLower = OWNER_EMAIL.trim().lowercase()
        try {
            if (email == ownerEmailLower) {
                val resolvedOwner = selectedUser.copy(
                    id = currentUser.uid,
                    phoneOrUsername = ownerEmailLower,
                    role = "owner",
                    isActive = true
                )
                roomDb.userDao().insertUser(resolvedOwner)
                if (checkIsDeviceOnline()) {
                    val ownerPayload = hashMapOf<String, Any?>(
                        "uid" to currentUser.uid,
                        "name" to resolvedOwner.name.trim(),
                        "phoneOrUsername" to ownerEmailLower,
                        "role" to "owner",
                        "active" to true,
                        "maxDiscountAmount" to resolvedOwner.maxDiscountAmount,
                        "registeredPhone" to resolvedOwner.registeredPhone.trim(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                    fs.collection("users").document(currentUser.uid)
                        .set(ownerPayload, SetOptions.merge())
                        .await()
                    fs.collection("users").document(ownerEmailLower)
                        .set(ownerPayload, SetOptions.merge())
                        .await()
                    normalizeAndSyncExistingStaffRecords()
                }
                _syncError.value = null
                refreshSyncStatus()
                return resolvedOwner
            }

            if (!checkIsDeviceOnline()) {
                refreshSyncStatus()
                return selectedUser
            }

            // Worker session linking: check /users/{uid} first so we use create vs update properly
            val existingUidDoc = fs.collection("users").document(currentUser.uid).get().await()
            val preAuthDoc = if (email.isNotEmpty()) {
                fs.collection("users").document(email).get().await()
            } else null

            val resolvedDiscount = (existingUidDoc.get("maxDiscountAmount") as? Number)?.toDouble()
                ?: (preAuthDoc?.get("maxDiscountAmount") as? Number)?.toDouble()
                ?: selectedUser.maxDiscountAmount
            val resolvedName = existingUidDoc.getString("name")
                ?: preAuthDoc?.getString("name")
                ?: selectedUser.name

            val resolvedWorker = selectedUser.copy(
                id = currentUser.uid,
                name = resolvedName.trim(),
                phoneOrUsername = email.ifEmpty { selectedUser.phoneOrUsername.trim().lowercase() },
                role = "worker",
                isActive = true,
                maxDiscountAmount = resolvedDiscount
            )

            if (!existingUidDoc.exists()) {
                // Create worker doc (allowed when pre-authorized by email)
                val createPayload = hashMapOf<String, Any?>(
                    "uid" to currentUser.uid,
                    "name" to resolvedWorker.name,
                    "phoneOrUsername" to resolvedWorker.phoneOrUsername,
                    "role" to "worker",
                    "active" to true,
                    "maxDiscountAmount" to resolvedDiscount,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                fs.collection("users").document(currentUser.uid)
                    .set(createPayload)
                    .await()
            } else {
                // Update only non-privileged fields on existing worker doc
                fs.collection("users").document(currentUser.uid)
                    .update(
                        mapOf(
                            "name" to resolvedWorker.name,
                            "phoneOrUsername" to resolvedWorker.phoneOrUsername,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                    .await()
            }

            roomDb.userDao().insertUser(resolvedWorker)
            _syncError.value = null
            refreshSyncStatus()
            return resolvedWorker
        } catch (e: Exception) {
            Log.d("FirestoreSync", "Staff UID link skipped: ${e.message}")
        }
        return selectedUser
    }

    companion object {
        const val OWNER_EMAIL = "rupak2472@gmail.com"
        private const val PREFS_NAME = "baba_cloud_sync_prefs"
        private const val KEY_ONE_TIME_MIGRATED = "one_time_tablet_migrated_v1"
    }
}

