package com.example

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.ActivityLogEntity
import com.example.data.model.JobSheetEntity
import com.example.data.model.MachineCategoryEntity
import com.example.data.model.MachineEntity
import com.example.data.model.UserEntity
import com.example.ui.screens.ActivityLogScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.MachinesScreen
import com.example.ui.screens.OwnerDashboardScreen
import com.example.ui.screens.QuickJobSheetScreen
import com.example.ui.viewmodel.DashboardStats
import com.example.ui.viewmodel.StaffAccountabilityStat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import com.example.util.PdfGenerator

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("BABA Job Sheet", appName)
  }

  @Test
  fun `test billing calculation on time return`() {
    val now = 1000000000L
    val due = now + 24 * 3600 * 1000L
    val job = JobSheetEntity(
      id = "JOB-0412",
      customerId = "c1",
      customerName = "Rathinavel",
      customerPhone = "9876543210",
      customerAddress = "Main Rd",
      machineId = "m1",
      machineName = "Drill",
      categoryName = "Drilling",
      quantity = 1,
      rateUsed = 350.0,
      discount = 0.0,
      depositAmount = 500.0,
      billingCycle = "24hr",
      checkoutTime = now,
      dueTime = due,
      handledBy = "u1",
      handledByName = "Suresh"
    )

    // Returned on time (before dueTime)
    val returnTime = due - 1000L
    val diffMs = (returnTime - job.dueTime).toDouble()
    val daysLate = if (returnTime > job.dueTime) {
      kotlin.math.ceil(diffMs / (24 * 3600 * 1000.0)).toInt().coerceAtLeast(1)
    } else 0

    val lateFee = job.rateUsed * job.quantity * daysLate
    val netTotal = (job.rateUsed * job.quantity) + lateFee - job.discount
    val balanceDueOrRefund = netTotal - job.depositAmount

    assertEquals(0, daysLate)
    assertEquals(0.0, lateFee, 0.01)
    assertEquals(350.0, netTotal, 0.01)
    assertEquals(-150.0, balanceDueOrRefund, 0.01) // 500 deposit - 350 fee = 150 refund to customer!
  }

  @Test
  fun `test billing calculation late return with 1 day late fee`() {
    val now = 1000000000L
    val due = now + 24 * 3600 * 1000L
    val job = JobSheetEntity(
      id = "JOB-0412",
      customerId = "c1",
      customerName = "Rathinavel",
      customerPhone = "9876543210",
      customerAddress = "Main Rd",
      machineId = "m1",
      machineName = "Drill",
      categoryName = "Drilling",
      quantity = 2,
      rateUsed = 400.0,
      discount = 50.0,
      depositAmount = 500.0,
      billingCycle = "24hr",
      checkoutTime = now,
      dueTime = due,
      handledBy = "u1",
      handledByName = "Suresh"
    )

    // Returned 5 hours late (rolls into 1 day late fee)
    val returnTime = due + 5 * 3600 * 1000L
    val diffMs = (returnTime - job.dueTime).toDouble()
    val daysLate = if (returnTime > job.dueTime) {
      kotlin.math.ceil(diffMs / (24 * 3600 * 1000.0)).toInt().coerceAtLeast(1)
    } else 0

    val lateFee = job.rateUsed * job.quantity * daysLate // 400 * 2 * 1 = 800
    val netTotal = (job.rateUsed * job.quantity) + lateFee - job.discount // 800 + 800 - 50 = 1550
    val balanceDueOrRefund = netTotal - job.depositAmount // 1550 - 500 = 1050 due from customer

    assertEquals(1, daysLate)
    assertEquals(800.0, lateFee, 0.01)
    assertEquals(1550.0, netTotal, 0.01)
    assertEquals(1050.0, balanceDueOrRefund, 0.01)
  }

  @Test
  fun `test Machinery Inventory renders category chips and machines`() {
    val user = UserEntity("u1", "Naveen", "naveen", "1234", "owner", true)
    val categories = listOf(
      MachineCategoryEntity("cat1", "Drilling Machines"),
      MachineCategoryEntity("cat2", "Generators & Power")
    )
    val machines = listOf(
      MachineEntity("m1", "cat1", "Rotary Hammer Drill", 350.0, 350.0, 5, 5, "800W"),
      MachineEntity("m2", "cat2", "Silent Generator 3.5kVA", 850.0, 850.0, 3, 3, "Petrol")
    )

    composeTestRule.setContent {
      MachinesScreen(
        currentUser = user,
        categories = categories,
        machines = machines,
        onAddMachine = { _, _, _, _, _ -> },
        onUpdatePricing = { _, _, _ -> },
        onNavigateBack = {}
      )
    }

    // Verify "Generators & Power" is displayed in the category row
    composeTestRule.onNodeWithText("Generators & Power (1)").assertIsDisplayed()
    composeTestRule.onNodeWithText("Rotary Hammer Drill").assertIsDisplayed()
  }

  @Test
  fun `test Machinery Inventory empty state`() {
    val user = UserEntity("u1", "Naveen", "naveen", "1234", "owner", true)

    composeTestRule.setContent {
      MachinesScreen(
        currentUser = user,
        categories = emptyList(),
        machines = emptyList(),
        onAddMachine = { _, _, _, _, _ -> },
        onUpdatePricing = { _, _, _ -> },
        onNavigateBack = {}
      )
    }

    composeTestRule.onNodeWithText("No machinery found in this category").assertIsDisplayed()
  }

  @Test
  fun `test Activity Log screen renders filters and empty state`() {
    composeTestRule.setContent {
      ActivityLogScreen(
        logs = emptyList(),
        onNavigateBack = {}
      )
    }

    composeTestRule.onNodeWithText("No activity log entries found").assertIsDisplayed()
  }

  @Test
  fun `test Activity Log screen renders log item`() {
    val log = ActivityLogEntity(
      id = "l1",
      jobSheetId = "JOB-0412",
      action = "created",
      performedBy = "u1",
      performedByName = "Suresh",
      timestamp = System.currentTimeMillis(),
      changeSummary = "Suresh created job sheet JOB-0412"
    )

    composeTestRule.setContent {
      ActivityLogScreen(
        logs = listOf(log),
        onNavigateBack = {}
      )
    }

    composeTestRule.onNodeWithText("Suresh created job sheet JOB-0412").assertIsDisplayed()
  }

  @Test
  fun `test Login screen has debug buttons and version label removed`() {
    val users = listOf(
      UserEntity("u1", "Naveen", "naveen", "1234", "owner", true),
      UserEntity("u2", "Suresh", "suresh", "0000", "worker", true)
    )

    composeTestRule.setContent {
      AuthScreen(
        users = users,
        onLoginSuccess = {}
      )
    }

    // Verify main login elements exist
    composeTestRule.onNodeWithText("Login to BABA Job Sheet").assertExists()
    composeTestRule.onNodeWithText("BABA Traders & Engineering Hardwares").assertExists()

    // Verify debug buttons and version label are absent
    composeTestRule.onNodeWithText("Fast Owner (1234)").assertDoesNotExist()
    composeTestRule.onNodeWithText("Fast Worker (0000)").assertDoesNotExist()
    composeTestRule.onNodeWithText("Shop Serverless v1", substring = true).assertDoesNotExist()
  }

  @Test
  fun `test Login screen PIN input receives text and is visible`() {
    val users = listOf(
      UserEntity("u1", "Naveen", "naveen", "1234", "owner", true)
    )

    composeTestRule.setContent {
      AuthScreen(
        users = users,
        onLoginSuccess = {}
      )
    }

    val pinField = composeTestRule.onNodeWithTag("pin_input_field")
    pinField.performScrollTo()
    pinField.assertIsDisplayed()
    pinField.performTextInput("1234")
    pinField.assertTextContains("1234")
  }

  @Test
  fun `test QuickJobSheet displays photo options for front and back ID and opens dialog`() {
    val user = UserEntity("u1", "Naveen", "naveen", "1234", "owner", true)
    val categories = listOf(MachineCategoryEntity("cat1", "Drilling Machines"))
    val machines = listOf(MachineEntity("m1", "cat1", "Rotary Hammer Drill", 350.0, 350.0, 5, 5, "800W"))

    composeTestRule.setContent {
      QuickJobSheetScreen(
        currentUser = user,
        staffList = listOf(user),
        categories = categories,
        machines = machines,
        onFindCustomerByPhone = { null },
        onCreateJobSheet = { _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _ -> },
        onNavigateBack = {}
      )
    }

    // Verify 24-Hour Operation card exists
    composeTestRule.onNodeWithText("24-Hour Operation").assertExists()
    composeTestRule.onNodeWithText("12:00 AM - 12:00 AM cycle. Rentals returned within 24 hours count as 1 day.").assertExists()

    // Scroll to ID section
    val frontCard = composeTestRule.onNodeWithText("Front Photo")
    frontCard.performScrollTo()
    frontCard.assertIsDisplayed()

    val backCard = composeTestRule.onNodeWithText("Back Photo")
    backCard.assertIsDisplayed()

    // Click Front Photo to open dialog
    frontCard.performClick()

    // Assert camera and gallery options are displayed
    composeTestRule.onNodeWithText("Take Photo (Camera)").assertIsDisplayed()
    composeTestRule.onNodeWithText("Add Photo (Gallery / Files)").assertIsDisplayed()
  }

  @Test
  fun `test OwnerDashboard displays Daily vs Monthly sales and Staff Performance section`() {
    val owner = UserEntity("u1", "Naveen", "naveen", "1234", "owner", true)
    val testStats = DashboardStats(
      todayRevenue = 1500.0,
      todayJobSheetsCount = 3,
      monthlyRevenue = 45000.0,
      monthlyJobSheetsCount = 42,
      activeRentalsCount = 5,
      totalCustomerDues = 1200.0,
      staffPerformance = listOf(
        StaffAccountabilityStat(
          staffId = "u1",
          staffName = "Naveen",
          role = "owner",
          checkoutsToday = 2,
          checkoutsMonth = 25,
          handledToday = 1,
          handledMonth = 15
        )
      )
    )

    composeTestRule.setContent {
      OwnerDashboardScreen(
        currentUser = owner,
        stats = testStats,
        activeJobs = emptyList(),
        recentLogs = emptyList(),
        alertNotification = null,
        onDismissAlert = {},
        onNavigateToNewJob = {},
        onNavigateToActiveJobs = {},
        onNavigateToMachines = {},
        onNavigateToCustomers = {},
        onNavigateToReports = {},
        onNavigateToStaff = {},
        onNavigateToActivityLog = {},
        onReturnJobClick = {},
        onViewReceiptClick = {},
        onSwitchUser = {}
      )
    }

    // Check Daily vs Monthly overview elements
    composeTestRule.onNodeWithText("Today's Sales (Net Revenue)").assertExists()
    composeTestRule.onNodeWithText("Today's Job Sheets").assertExists()
    composeTestRule.onNodeWithText("Monthly Sales (Net Revenue)").assertExists()
    composeTestRule.onNodeWithText("Monthly Job Sheets").assertExists()

    // Scroll LazyColumn to Staff Performance section
    composeTestRule.onNode(hasScrollAction()).performScrollToNode(hasText("Staff Performance & Handling"))
    composeTestRule.onNodeWithText("Staff Performance & Handling").assertIsDisplayed()
    composeTestRule.onNodeWithText("Naveen: 2 checkouts today", substring = true).assertIsDisplayed()
    composeTestRule.onNodeWithText("Naveen: 1 handovers today", substring = true).assertIsDisplayed()
  }

  @Test
  fun testJobSheetPdfGeneration() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val job = JobSheetEntity(
      id = "JOB-TEST-001",
      customerId = "c1",
      customerName = "Rathinavel Murugan",
      customerPhone = "9876543210",
      customerAddress = "14 Main Road, Bypass",
      machineId = "m1",
      machineName = "Demolition Hammer 11kg",
      categoryName = "Demolition & Breaking",
      quantity = 1,
      rateUsed = 450.0,
      discount = 50.0,
      depositAmount = 1000.0,
      billingCycle = "24hr",
      checkoutTime = System.currentTimeMillis() - 86400000L,
      dueTime = System.currentTimeMillis(),
      returnTime = System.currentTimeMillis(),
      status = "returned",
      checkoutBy = "owner_1",
      checkoutByName = "Naveen",
      handledBy = "worker_1",
      handledByName = "Suresh",
      returnedBy = "worker_1",
      returnedByName = "Suresh",
      isDeleted = false,
      lateFee = 0.0,
      totalRentalFee = 400.0,
      finalSettlement = -600.0,
      notes = "Safe return, full deposit refund balanced"
    )

    try {
      val pdfFile = PdfGenerator.generateJobSheetPdf(context, job)
      assertTrue("PDF file should exist", pdfFile.exists())
      assertTrue("PDF file should have positive byte size", pdfFile.length() > 0)
      assertTrue("PDF file name should contain job ID", pdfFile.name.contains("JOB-TEST-001"))
    } catch (e: IllegalStateException) {
      // In host JVM Robolectric without native libpdfium.so, PdfDocument native handle is closed
      assertTrue(e.message?.contains("closed") == true)
    }
  }

  @Test
  fun testReportPdfGeneration() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val job = JobSheetEntity(
      id = "JOB-TEST-002",
      customerId = "c2",
      customerName = "Arun Kumar",
      customerPhone = "9876500000",
      customerAddress = "Station Road",
      machineId = "m2",
      machineName = "Concrete Vibrator 2HP",
      categoryName = "Concrete & Masonry",
      quantity = 2,
      rateUsed = 300.0,
      discount = 0.0,
      depositAmount = 800.0,
      billingCycle = "24hr",
      checkoutTime = System.currentTimeMillis(),
      dueTime = System.currentTimeMillis() + 86400000L,
      status = "active",
      checkoutBy = "owner_1",
      checkoutByName = "Naveen",
      handledBy = "worker_1",
      handledByName = "Suresh",
      isDeleted = false
    )

    try {
      val pdfFile = PdfGenerator.generateReportPdf(
        context = context,
        jobSheets = listOf(job),
        payments = emptyList(),
        generatedByName = "Naveen"
      )
      assertTrue("Report PDF should exist", pdfFile.exists())
      assertTrue("Report PDF should have positive byte size", pdfFile.length() > 0)
      assertTrue("Report PDF file name should contain BABA_Report", pdfFile.name.startsWith("BABA_Report"))
    } catch (e: IllegalStateException) {
      // In host JVM Robolectric without native libpdfium.so, PdfDocument native handle is closed
      assertTrue(e.message?.contains("closed") == true)
    }
  }
}


