package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.JobSheetEntity
import com.example.data.repository.BabaRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Baba Traders", appName)
    }

    @Test
    fun `launch MainActivity`() {
        val activityController = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = activityController.get()
        assertNotNull(activity)
    }

    @Test
    fun `verify return calculation formula - 1 day rental`() {
        val now = System.currentTimeMillis()
        val jobSheet = JobSheetEntity(
            id = "JOB-0412",
            customerId = "c1",
            customerName = "Ravi",
            customerPhone = "9876543210",
            customerAddress = "Main Road",
            machineId = "m1",
            machineName = "Drill",
            categoryName = "Power Tools",
            quantity = 1,
            rateUsed = 500.0,
            discount = 0.0,
            depositAmount = 500.0,
            checkoutTime = now,
            dueTime = now + 24 * 3600 * 1000L,
            handledBy = "u1",
            handledByName = "Worker"
        )

        // Return within 1 hour: 1 day used
        val daysUsed = BabaRepository.calculateDaysUsed(jobSheet.checkoutTime, now + 3600 * 1000L)
        assertEquals(1, daysUsed)
    }

    @Test
    fun `verify 2 machines for 2 days calculation does not double multiply quantity`() {
        val now = System.currentTimeMillis()
        val twoDaysLater = now + 2 * 24 * 3600 * 1000L
        // 2 machines: e.g. ₹400/day + ₹350/day = ₹750/day combined rateUsed
        val jobSheet = JobSheetEntity(
            id = "JOB-0413",
            customerId = "c1",
            customerName = "Ravi",
            customerPhone = "9876543210",
            customerAddress = "Main Road",
            machineId = "m1,m2",
            machineName = "1x Cutter, 1x Water Motor",
            categoryName = "Cutters + Water Motors",
            quantity = 2,
            initialQuantity = 2,
            returnedQuantity = 0,
            rateUsed = 750.0,
            discount = 50.0,
            depositAmount = 500.0,
            checkoutTime = now,
            dueTime = twoDaysLater,
            handledBy = "u1",
            handledByName = "Worker"
        )

        val bookedDays = BabaRepository.calculateBookedDays(jobSheet.checkoutTime, jobSheet.dueTime)
        assertEquals(2, bookedDays)

        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = com.example.data.AppDatabase.getDatabase(
            context,
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined)
        )
        val repo = BabaRepository(db)

        // Even if returned early, booked duration of 2 days is respected by default, and rateUsed (750) is NOT multiplied by quantity (2) again
        val bill = repo.calculateReturnBill(jobSheet, returningQuantity = 2, manualDiscount = 0.0)
        assertEquals(2, bill.daysUsed)
        assertEquals(750.0, bill.dailyRate, 0.01)
        // Gross = 2 days * 750 = 1500; less 50 discount = 1450 netTotalFee
        assertEquals(1450.0, bill.netTotalFee, 0.01)
        // Balance = 1450 - 500 deposit = 950
        assertEquals(950.0, bill.balanceDueOrRefund, 0.01)
    }
}
