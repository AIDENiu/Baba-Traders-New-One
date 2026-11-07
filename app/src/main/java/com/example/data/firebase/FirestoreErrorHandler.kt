package com.example.data.firebase

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import org.json.JSONArray
import org.json.JSONObject

enum class OperationType(val value: String) {
    CREATE("create"),
    UPDATE("update"),
    DELETE("delete"),
    LIST("list"),
    GET("get"),
    WRITE("write"),
}

fun handleFirestoreError(exception: Exception, operationType: OperationType, path: String?): String {
    val auth = try { FirebaseAuth.getInstance() } catch (e: Exception) { null }
    val currentUser = auth?.currentUser
    val collectionName = path?.substringBefore("/") ?: "unknown"
    val rawMessage = exception.message ?: exception.toString()

    // Sanitize log output: never log personal data (emails, phone numbers, or customer names)
    val errorInfoJson = JSONObject().apply {
        put("error", rawMessage)
        put("operationType", operationType.value)
        put("collection", collectionName)
        put("isAuthenticated", currentUser != null)
    }

    val jsonString = errorInfoJson.toString()
    Log.e("FirestoreSync", "Cloud sync error: $jsonString")
    return "Sync Error (${operationType.value.uppercase()} on $collectionName): $rawMessage"
}
