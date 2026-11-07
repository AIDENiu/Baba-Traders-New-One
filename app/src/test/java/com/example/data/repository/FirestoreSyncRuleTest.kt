package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class FirestoreSyncRuleTest : FirestoreEmulatorTestBase() {

  @Test
  fun testUnauthenticatedAccessFails() = runTest {
    auth.signOut()
    try {
      firestore.collection("job_sheets").document("test_unauth").get().await()
      fail("Expected unauthenticated read to fail")
    } catch (e: FirebaseFirestoreException) {
      assertTrue(e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED)
    }
  }

  @Test
  fun testNonStaffCannotReadOrSelfAssignRole() = runTest {
    val strangerUid = signInTestUser("stranger@test.com")
    // Cannot self-assign worker or owner role
    try {
      firestore.collection("users").document(strangerUid).set(
        mapOf("uid" to strangerUid, "name" to "Stranger", "role" to "worker", "active" to true)
      ).await()
      fail("Expected non-owner self-assigning role to fail")
    } catch (e: FirebaseFirestoreException) {
      assertTrue(e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED)
    }

    // Cannot read job_sheets, machines, or customers
    try {
      firestore.collection("job_sheets").document("any_job").get().await()
      fail("Expected non-staff read of job_sheets to fail")
    } catch (e: FirebaseFirestoreException) {
      assertTrue(e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED)
    }
  }

  @Test
  fun testWorkerJobSheetDiscountFloorEnforced() = runTest {
    val workerUid = signInTestUser("worker_suresh@test.com")
    // Sign in as Owner (rupak2472@gmail.com) to provision worker role in users collection
    signInTestUser("rupak2472@gmail.com")
    val userDoc = mapOf(
      "uid" to workerUid,
      "name" to "Suresh",
      "role" to "worker",
      "active" to true,
      "maxDiscountAmount" to 200
    )
    firestore.collection("users").document(workerUid).set(userDoc).await()

    // Switch back to worker account
    signInTestUser("worker_suresh@test.com")

    // 1. Discount <= 200 should succeed
    val validJobId = "job_valid_${System.currentTimeMillis()}"
    val validJob = mapOf(
      "id" to validJobId,
      "customerId" to "c1",
      "machineId" to "m1",
      "quantity" to 1,
      "rateUsed" to 350.0,
      "discount" to 150.0,
      "deposit" to 500.0,
      "status" to "active",
      "handledByUid" to workerUid,
      "isDeleted" to false
    )
    firestore.collection("job_sheets").document(validJobId).set(validJob).await()
    val readJob = firestore.collection("job_sheets").document(validJobId).get().await()
    assertNotNull(readJob.data)

    // 2. Discount > 200 should fail
    val invalidJobId = "job_invalid_${System.currentTimeMillis()}"
    val invalidJob = mapOf(
      "id" to invalidJobId,
      "customerId" to "c1",
      "machineId" to "m1",
      "quantity" to 1,
      "rateUsed" to 350.0,
      "discount" to 300.0, // Exceeds 200 floor
      "deposit" to 500.0,
      "status" to "active",
      "handledByUid" to workerUid,
      "isDeleted" to false
    )
    try {
      firestore.collection("job_sheets").document(invalidJobId).set(invalidJob).await()
      fail("Expected discount > 200 to fail with PERMISSION_DENIED")
    } catch (e: FirebaseFirestoreException) {
      assertTrue(e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED)
    }
  }

  @Test
  fun testActivityLogWriteOnceEnforced() = runTest {
    val workerUid = signInTestUser("worker_audit@test.com")
    // Owner provisions the worker profile
    signInTestUser("rupak2472@gmail.com")
    val userDoc = mapOf(
      "uid" to workerUid,
      "name" to "Worker",
      "role" to "worker",
      "active" to true
    )
    firestore.collection("users").document(workerUid).set(userDoc).await()

    // Switch back to worker
    signInTestUser("worker_audit@test.com")

    val logId = "log_${System.currentTimeMillis()}"
    val log = mapOf(
      "id" to logId,
      "jobSheetId" to "job_1",
      "action" to "created",
      "handledByUid" to workerUid
    )
    // Create succeeds
    firestore.collection("activity_log").document(logId).set(log).await()

    // Update fails
    try {
      firestore.collection("activity_log").document(logId).update("action", "tampered").await()
      fail("Expected update on activity_log to fail")
    } catch (e: FirebaseFirestoreException) {
      assertTrue(e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED)
    }

    // Delete fails
    try {
      firestore.collection("activity_log").document(logId).delete().await()
      fail("Expected delete on activity_log to fail")
    } catch (e: FirebaseFirestoreException) {
      assertTrue(e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED)
    }
  }
}
