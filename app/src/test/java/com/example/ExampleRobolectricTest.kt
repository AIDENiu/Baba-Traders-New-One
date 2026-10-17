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
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("BABA Job Sheet", appName)
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
}
