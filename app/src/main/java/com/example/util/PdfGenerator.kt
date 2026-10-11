package com.example.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.data.model.JobSheetEntity
import com.example.data.model.PaymentEntity
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs

object PdfGenerator {

    // Standard A4 dimensions in PostScript points (72 points per inch)
    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842

    // Brand Palette
    private val COLOR_PRIMARY = Color.rgb(183, 28, 28)       // BabaRed #B71C1C
    private val COLOR_SECONDARY = Color.rgb(251, 192, 45)    // BabaYellow #FBC02D
    private val COLOR_DARK = Color.rgb(33, 33, 33)           // BabaDark #212121
    private val COLOR_GRAY = Color.rgb(117, 117, 117)        // BabaGray #757575
    private val COLOR_LIGHT_BG = Color.rgb(245, 245, 245)    // BabaLightGray #F5F5F5
    private val COLOR_BORDER = Color.rgb(224, 224, 224)      // BabaBorder #E0E0E0
    private val COLOR_SUCCESS = Color.rgb(46, 125, 50)       // Green #2E7D32
    private val COLOR_OVERDUE = Color.rgb(198, 40, 40)       // Red #C62828
    private val COLOR_WHITE = Color.WHITE

    /**
     * Generates a professional PDF Job Sheet / Invoice / Receipt for rental transactions.
     */
    fun generateJobSheetPdf(context: Context, jobSheet: JobSheetEntity): File {
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create()
        val page = document.startPage(pageInfo)
        val canvas = page.canvas

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val dateFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
        val dateOnlyFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

        // 1. TOP HEADER BANNER (BabaRed with Gold Accent Strip)
        paint.color = COLOR_PRIMARY
        paint.style = Paint.Style.FILL
        canvas.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), 95f, paint)

        paint.color = COLOR_SECONDARY
        canvas.drawRect(0f, 92f, PAGE_WIDTH.toFloat(), 96f, paint)

        // Shop Title & Subtitles
        paint.color = COLOR_WHITE
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 20f
        canvas.drawText("BABA TRADERS", 36f, 38f, paint)

        paint.textSize = 10f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText("& Engineering Hardwares — Machinery & Tool Rentals", 36f, 54f, paint)

        paint.color = Color.rgb(255, 235, 238)
        paint.textSize = 8.5f
        canvas.drawText("Bypass Road, Near Bus Stand • Helpline: +91 98765 43210 • Reg No: BT-ENG-2024", 36f, 70f, paint)

        // Right side badge: RENTAL INVOICE
        paint.color = COLOR_SECONDARY
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 13f
        val docType = if (jobSheet.status == "returned") "TAX INVOICE & RECEIPT" else "RENTAL JOB SHEET"
        val docTypeWidth = paint.measureText(docType)
        canvas.drawText(docType, PAGE_WIDTH - 36f - docTypeWidth, 42f, paint)

        paint.color = COLOR_WHITE
        paint.textSize = 9.5f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        val dateStr = "Date: ${dateOnlyFormat.format(Date(jobSheet.checkoutTime))}"
        val dateWidth = paint.measureText(dateStr)
        canvas.drawText(dateStr, PAGE_WIDTH - 36f - dateWidth, 60f, paint)

        var y = 115f

        // 2. DOCUMENT INFO BAR (Job ID & Status)
        // Background card for order info
        paint.color = COLOR_LIGHT_BG
        val infoRect = RectF(36f, y, PAGE_WIDTH - 36f, y + 42f)
        canvas.drawRoundRect(infoRect, 8f, 8f, paint)
        paint.color = COLOR_BORDER
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        canvas.drawRoundRect(infoRect, 8f, 8f, paint)
        paint.style = Paint.Style.FILL

        paint.color = COLOR_DARK
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 12f
        canvas.drawText("Job Sheet ID: ${jobSheet.id}", 48f, y + 26f, paint)

        // Status Pill Badge
        val statusText = jobSheet.status.uppercase(Locale.getDefault())
        val statusColor = when (jobSheet.status) {
            "returned" -> COLOR_SUCCESS
            "overdue" -> COLOR_OVERDUE
            else -> COLOR_PRIMARY
        }
        val statusBgColor = when (jobSheet.status) {
            "returned" -> Color.rgb(232, 245, 233)
            "overdue" -> Color.rgb(255, 235, 238)
            else -> Color.rgb(255, 248, 225)
        }
        val statusPillWidth = 85f
        val statusPillRect = RectF(PAGE_WIDTH - 48f - statusPillWidth, y + 10f, PAGE_WIDTH - 48f, y + 32f)
        paint.color = statusBgColor
        canvas.drawRoundRect(statusPillRect, 6f, 6f, paint)

        paint.color = statusColor
        paint.textSize = 9.5f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        val statusTextWidth = paint.measureText(statusText)
        canvas.drawText(statusText, statusPillRect.centerX() - (statusTextWidth / 2f), y + 24f, paint)

        y += 56f

        // 3. TWO-COLUMN DETAILS: Customer Details (Left) & Operational Attributions (Right)
        val colWidth = (PAGE_WIDTH - 72f - 16f) / 2f
        val colHeight = 90f

        // Left Box: Customer Details
        val custRect = RectF(36f, y, 36f + colWidth, y + colHeight)
        paint.color = COLOR_LIGHT_BG
        canvas.drawRoundRect(custRect, 6f, 6f, paint)
        paint.color = COLOR_BORDER
        paint.style = Paint.Style.STROKE
        canvas.drawRoundRect(custRect, 6f, 6f, paint)
        paint.style = Paint.Style.FILL

        paint.color = COLOR_PRIMARY
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 9.5f
        canvas.drawText("CUSTOMER INFORMATION", 46f, y + 18f, paint)

        paint.color = COLOR_DARK
        paint.textSize = 11.5f
        canvas.drawText(jobSheet.customerName, 46f, y + 36f, paint)

        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textSize = 9f
        paint.color = COLOR_GRAY
        canvas.drawText("Phone: ${jobSheet.customerPhone}", 46f, y + 52f, paint)
        val addressText = if (jobSheet.customerAddress.isNotBlank()) jobSheet.customerAddress else "Local site / Self pick-up"
        val truncatedAddress = if (addressText.length > 38) addressText.take(35) + "..." else addressText
        canvas.drawText("Address: $truncatedAddress", 46f, y + 68f, paint)

        // Right Box: Staff & Timing
        val staffRect = RectF(36f + colWidth + 16f, y, PAGE_WIDTH - 36f, y + colHeight)
        paint.color = COLOR_LIGHT_BG
        canvas.drawRoundRect(staffRect, 6f, 6f, paint)
        paint.color = COLOR_BORDER
        paint.style = Paint.Style.STROKE
        canvas.drawRoundRect(staffRect, 6f, 6f, paint)
        paint.style = Paint.Style.FILL

        paint.color = COLOR_PRIMARY
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 9.5f
        canvas.drawText("OPERATOR & TIMING", 36f + colWidth + 26f, y + 18f, paint)

        paint.color = COLOR_DARK
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textSize = 8.5f
        val billingStaff = jobSheet.checkoutByName.ifEmpty { jobSheet.handledByName }
        canvas.drawText("Billing Operator: $billingStaff", 36f + colWidth + 26f, y + 34f, paint)
        canvas.drawText("Yard Handler: ${jobSheet.handledByName}", 36f + colWidth + 26f, y + 48f, paint)
        val returnStaff = if (jobSheet.returnedByName.isNotBlank()) jobSheet.returnedByName else "Pending return"
        canvas.drawText("Returned To: $returnStaff", 36f + colWidth + 26f, y + 62f, paint)
        val cycleDesc = if (jobSheet.billingCycle == "24hr") "24-Hour Rolling Cycle" else "24-Hour Operation"
        canvas.drawText("Policy: $cycleDesc", 36f + colWidth + 26f, y + 76f, paint)

        y += colHeight + 16f

        // 4. EQUIPMENT RENTAL TABLE
        paint.color = COLOR_PRIMARY
        val tableHeaderRect = RectF(36f, y, PAGE_WIDTH - 36f, y + 24f)
        canvas.drawRoundRect(tableHeaderRect, 4f, 4f, paint)

        paint.color = COLOR_WHITE
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 9f
        canvas.drawText("EQUIPMENT / MACHINERY", 46f, y + 16f, paint)
        canvas.drawText("CATEGORY", 240f, y + 16f, paint)
        canvas.drawText("QTY", 330f, y + 16f, paint)
        canvas.drawText("RATE / DAY", 380f, y + 16f, paint)
        canvas.drawText("DEPOSIT", 460f, y + 16f, paint)

        y += 24f

        // Table Content Row
        paint.color = COLOR_WHITE
        val tableRowRect = RectF(36f, y, PAGE_WIDTH - 36f, y + 36f)
        canvas.drawRect(tableRowRect, paint)
        paint.color = COLOR_BORDER
        paint.style = Paint.Style.STROKE
        canvas.drawRect(tableRowRect, paint)
        paint.style = Paint.Style.FILL

        paint.color = COLOR_DARK
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 10f
        canvas.drawText(jobSheet.machineName, 46f, y + 22f, paint)

        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textSize = 9f
        canvas.drawText(jobSheet.categoryName, 240f, y + 22f, paint)
        canvas.drawText("${jobSheet.quantity} unit", 330f, y + 22f, paint)
        canvas.drawText("₹${jobSheet.rateUsed.toInt()}", 380f, y + 22f, paint)
        canvas.drawText("₹${jobSheet.depositAmount.toInt()}", 460f, y + 22f, paint)

        y += 46f

        // 5. TIMELINE / DURATION SCHEDULE
        paint.color = COLOR_LIGHT_BG
        val schedRect = RectF(36f, y, PAGE_WIDTH - 36f, y + 54f)
        canvas.drawRoundRect(schedRect, 6f, 6f, paint)
        paint.color = COLOR_BORDER
        paint.style = Paint.Style.STROKE
        canvas.drawRoundRect(schedRect, 6f, 6f, paint)
        paint.style = Paint.Style.FILL

        paint.color = COLOR_PRIMARY
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 9f
        canvas.drawText("RENTAL SCHEDULE", 46f, y + 18f, paint)

        paint.color = COLOR_DARK
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textSize = 8.5f
        canvas.drawText("Checked Out: ${dateFormat.format(Date(jobSheet.checkoutTime))}", 46f, y + 34f, paint)
        canvas.drawText("Return Due:  ${dateFormat.format(Date(jobSheet.dueTime))}", 46f, y + 46f, paint)

        val returnTimeStr = jobSheet.returnTime?.let { dateFormat.format(Date(it)) } ?: "Active in field"
        canvas.drawText("Actual Return: $returnTimeStr", 300f, y + 34f, paint)
        val depositMethodStr = "Deposit Paid: ₹${jobSheet.depositAmount.toInt()}"
        canvas.drawText(depositMethodStr, 300f, y + 46f, paint)

        y += 66f

        // 6. FINANCIAL BREAKDOWN STATEMENT (Two columns / structured totals)
        val finCardRect = RectF(36f, y, PAGE_WIDTH - 36f, y + 120f)
        paint.color = COLOR_LIGHT_BG
        canvas.drawRoundRect(finCardRect, 6f, 6f, paint)
        paint.color = COLOR_BORDER
        paint.style = Paint.Style.STROKE
        canvas.drawRoundRect(finCardRect, 6f, 6f, paint)
        paint.style = Paint.Style.FILL

        paint.color = COLOR_DARK
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 10f
        canvas.drawText("BILLING & FINANCIAL SETTLEMENT", 46f, y + 20f, paint)

        paint.color = COLOR_BORDER
        canvas.drawLine(46f, y + 26f, PAGE_WIDTH - 46f, y + 26f, paint)

        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textSize = 9f

        // Left Financial Items
        paint.color = COLOR_GRAY
        canvas.drawText("Security Deposit Held:", 46f, y + 44f, paint)
        paint.color = COLOR_SUCCESS
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("₹${jobSheet.depositAmount.toInt()}", 180f, y + 44f, paint)

        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.color = COLOR_GRAY
        canvas.drawText("Discount Given:", 46f, y + 62f, paint)
        paint.color = COLOR_PRIMARY
        canvas.drawText("₹${jobSheet.discount.toInt()}", 180f, y + 62f, paint)

        paint.color = COLOR_GRAY
        canvas.drawText("Late Fee Charged:", 46f, y + 80f, paint)
        paint.color = if (jobSheet.lateFee > 0) COLOR_OVERDUE else COLOR_DARK
        canvas.drawText("₹${jobSheet.lateFee.toInt()}", 180f, y + 80f, paint)

        // Right Financial Items (Final Totals)
        paint.color = COLOR_GRAY
        canvas.drawText("Total Rental Charge:", 300f, y + 44f, paint)
        paint.color = COLOR_DARK
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 10f
        canvas.drawText("₹${jobSheet.totalRentalFee.toInt()}", 450f, y + 44f, paint)

        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textSize = 9f
        paint.color = COLOR_GRAY
        canvas.drawText("Settlement Outcome:", 300f, y + 68f, paint)

        val settlementText: String
        val settlementColor: Int
        if (jobSheet.status == "returned") {
            if (jobSheet.finalSettlement > 0) {
                settlementText = "Balance Collected: ₹${jobSheet.finalSettlement.toInt()}"
                settlementColor = COLOR_OVERDUE
            } else if (jobSheet.finalSettlement < 0) {
                settlementText = "Deposit Refunded: ₹${abs(jobSheet.finalSettlement).toInt()}"
                settlementColor = COLOR_SUCCESS
            } else {
                settlementText = "Paid in Full / Settled"
                settlementColor = COLOR_SUCCESS
            }
        } else {
            settlementText = "Active Job (Pending Return)"
            settlementColor = COLOR_PRIMARY
        }

        paint.color = settlementColor
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 10.5f
        canvas.drawText(settlementText, 300f, y + 88f, paint)

        y += 134f

        // 7. TERMS & CONDITIONS
        paint.color = COLOR_DARK
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 8.5f
        canvas.drawText("TERMS OF RENTAL & SAFE HANDLING:", 36f, y + 10f, paint)

        paint.color = COLOR_GRAY
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textSize = 7.5f
        val terms = listOf(
            "1. Machinery is received in verified working condition. Renter is responsible for daily lubrication and fuel.",
            "2. Billing applies on 24-hour cycle. Extensions or overstays roll over into the next day daily fee.",
            "3. Any mechanical failure due to misuse or physical damage will be deducted from the security deposit.",
            "4. Deposit will be refunded upon machinery physical inspection and return clearance by yard staff."
        )
        var termY = y + 24f
        terms.forEach { term ->
            canvas.drawText(term, 36f, termY, paint)
            termY += 12f
        }

        y = termY + 20f

        // 8. SIGNATURE BOXES
        val sigWidth = 190f
        paint.color = COLOR_DARK
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 0.8f
        // Customer signature line
        canvas.drawLine(48f, y + 40f, 48f + sigWidth, y + 40f, paint)
        // Staff signature line
        canvas.drawLine(PAGE_WIDTH - 48f - sigWidth, y + 40f, PAGE_WIDTH - 48f, y + 40f, paint)
        paint.style = Paint.Style.FILL

        paint.textSize = 8.5f
        paint.color = COLOR_DARK
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("Customer Acceptance Signature", 48f, y + 54f, paint)
        canvas.drawText("Authorized Signatory (BABA Traders)", PAGE_WIDTH - 48f - sigWidth, y + 54f, paint)

        paint.color = COLOR_GRAY
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textSize = 7f
        canvas.drawText("I accept the machinery condition & rental terms", 48f, y + 64f, paint)
        canvas.drawText("Verified by: ${jobSheet.checkoutByName.ifEmpty { jobSheet.handledByName }}", PAGE_WIDTH - 48f - sigWidth, y + 64f, paint)

        // 9. FOOTER
        paint.color = COLOR_PRIMARY
        canvas.drawRect(0f, PAGE_HEIGHT - 22f, PAGE_WIDTH.toFloat(), PAGE_HEIGHT.toFloat(), paint)

        paint.color = COLOR_WHITE
        paint.textSize = 7.5f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        val footerText = "BABA TRADERS — Tools for a Stronger Tomorrow • Generated electronically via BABA Job Sheet App"
        val footerWidth = paint.measureText(footerText)
        canvas.drawText(footerText, (PAGE_WIDTH - footerWidth) / 2f, PAGE_HEIGHT - 8f, paint)

        document.finishPage(page)

        // Write to cache file
        val outputDir = File(context.cacheDir, "pdf_invoices")
        if (!outputDir.exists()) outputDir.mkdirs()
        val file = File(outputDir, "BABA_JobSheet_${jobSheet.id}.pdf")
        FileOutputStream(file).use { out ->
            document.writeTo(out)
        }
        document.close()

        return file
    }

    /**
     * Generates a comprehensive Financial Performance & Rental Audit PDF Report.
     */
    fun generateReportPdf(
        context: Context,
        jobSheets: List<JobSheetEntity>,
        payments: List<PaymentEntity>,
        generatedByName: String
    ): File {
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create()
        val page = document.startPage(pageInfo)
        val canvas = page.canvas

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val dateFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
        val dateOnlyFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

        val now = System.currentTimeMillis()
        val startOfMonth = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val nonDeleted = jobSheets.filter { !it.isDeleted }
        val thisMonthJobs = nonDeleted.filter { it.checkoutTime >= startOfMonth }
        val activeRentals = nonDeleted.filter { it.status == "active" || it.status == "overdue" }
        val returnedRentals = nonDeleted.filter { it.status == "returned" }

        val totalDepositsHeld = activeRentals.sumOf { it.depositAmount }
        val totalRevenueCollected = payments.filter { it.type != "refund" }.sumOf { it.amount } - payments.filter { it.type == "refund" }.sumOf { it.amount }
        val lateFeesCollected = nonDeleted.sumOf { it.lateFee }

        // 1. TOP HEADER BANNER
        paint.color = COLOR_PRIMARY
        paint.style = Paint.Style.FILL
        canvas.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), 95f, paint)

        paint.color = COLOR_SECONDARY
        canvas.drawRect(0f, 92f, PAGE_WIDTH.toFloat(), 96f, paint)

        paint.color = COLOR_WHITE
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 20f
        canvas.drawText("BABA TRADERS", 36f, 38f, paint)

        paint.textSize = 10f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText("Monthly Financial & Rental Audit Statement", 36f, 54f, paint)

        paint.color = Color.rgb(255, 235, 238)
        paint.textSize = 8.5f
        canvas.drawText("Generated on: ${dateFormat.format(Date(now))} • Generated by: $generatedByName", 36f, 70f, paint)

        // Right side badge
        paint.color = COLOR_SECONDARY
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 13f
        val reportBadge = "EXECUTIVE REPORT"
        val reportBadgeWidth = paint.measureText(reportBadge)
        canvas.drawText(reportBadge, PAGE_WIDTH - 36f - reportBadgeWidth, 42f, paint)

        var y = 115f

        // 2. FINANCIAL KPI CARDS (2x2 Grid)
        val cardWidth = (PAGE_WIDTH - 72f - 12f) / 2f
        val cardHeight = 48f

        // Card 1: Total Net Revenue
        drawKpiCard(canvas, paint, 36f, y, cardWidth, cardHeight, "TOTAL NET REVENUE", "₹${totalRevenueCollected.toInt()}", "Deposits & Settlements", COLOR_SUCCESS)
        // Card 2: Monthly Job Sheets
        drawKpiCard(canvas, paint, 36f + cardWidth + 12f, y, cardWidth, cardHeight, "MONTHLY JOB SHEETS", "${thisMonthJobs.size} rentals", "Created this calendar month", COLOR_PRIMARY)

        y += cardHeight + 10f

        // Card 3: Security Deposits Held
        drawKpiCard(canvas, paint, 36f, y, cardWidth, cardHeight, "SECURITY DEPOSITS HELD", "₹${totalDepositsHeld.toInt()}", "Across ${activeRentals.size} active jobs", COLOR_DARK)
        // Card 4: Late Fees Collected
        drawKpiCard(canvas, paint, 36f + cardWidth + 12f, y, cardWidth, cardHeight, "LATE FEES COLLECTED", "₹${lateFeesCollected.toInt()}", "From overdue returns", COLOR_OVERDUE)

        y += cardHeight + 18f

        // 3. EQUIPMENT POPULARITY BREAKDOWN
        paint.color = COLOR_PRIMARY
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 11f
        canvas.drawText("TOP EQUIPMENT FLEET UTILIZATION", 36f, y + 10f, paint)

        y += 18f

        val machineFrequencies = nonDeleted.groupBy { it.machineName }
            .mapValues { it.value.sumOf { job -> job.quantity } }
            .toList()
            .sortedByDescending { it.second }
            .take(5)

        // Table header for equipment
        val equipHeaderRect = RectF(36f, y, PAGE_WIDTH - 36f, y + 20f)
        paint.color = COLOR_LIGHT_BG
        canvas.drawRoundRect(equipHeaderRect, 4f, 4f, paint)
        paint.color = COLOR_BORDER
        paint.style = Paint.Style.STROKE
        canvas.drawRoundRect(equipHeaderRect, 4f, 4f, paint)
        paint.style = Paint.Style.FILL

        paint.color = COLOR_DARK
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 8.5f
        canvas.drawText("RANK & EQUIPMENT NAME", 46f, y + 14f, paint)
        canvas.drawText("TOTAL UNITS RENTED", PAGE_WIDTH - 150f, y + 14f, paint)

        y += 20f

        if (machineFrequencies.isEmpty()) {
            paint.color = COLOR_GRAY
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            canvas.drawText("No rental activity recorded yet", 46f, y + 16f, paint)
            y += 24f
        } else {
            machineFrequencies.forEachIndexed { idx, (name, qty) ->
                val rowRect = RectF(36f, y, PAGE_WIDTH - 36f, y + 20f)
                paint.color = if (idx % 2 == 0) COLOR_WHITE else COLOR_LIGHT_BG
                canvas.drawRect(rowRect, paint)

                paint.color = COLOR_PRIMARY
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                paint.textSize = 8.5f
                canvas.drawText("#${idx + 1}", 46f, y + 14f, paint)

                paint.color = COLOR_DARK
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                canvas.drawText(name, 70f, y + 14f, paint)

                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                paint.color = COLOR_SUCCESS
                canvas.drawText("$qty units", PAGE_WIDTH - 150f, y + 14f, paint)

                y += 20f
            }
        }

        y += 16f

        // 4. RECENT RENTAL TRANSACTIONS AUDIT LOG
        paint.color = COLOR_PRIMARY
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 11f
        canvas.drawText("RECENT JOB SHEETS & TRANSACTION RECORDS", 36f, y + 10f, paint)

        y += 18f

        // Table Header
        val txHeaderRect = RectF(36f, y, PAGE_WIDTH - 36f, y + 22f)
        paint.color = COLOR_PRIMARY
        canvas.drawRoundRect(txHeaderRect, 4f, 4f, paint)

        paint.color = COLOR_WHITE
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 8f
        canvas.drawText("JOB ID", 44f, y + 15f, paint)
        canvas.drawText("CUSTOMER", 120f, y + 15f, paint)
        canvas.drawText("EQUIPMENT", 230f, y + 15f, paint)
        canvas.drawText("DATE", 350f, y + 15f, paint)
        canvas.drawText("STATUS", 420f, y + 15f, paint)
        canvas.drawText("TOTAL / DEP", 480f, y + 15f, paint)

        y += 22f

        val displayJobs = nonDeleted.sortedByDescending { it.checkoutTime }.take(10)
        displayJobs.forEachIndexed { index, job ->
            val rRect = RectF(36f, y, PAGE_WIDTH - 36f, y + 20f)
            paint.color = if (index % 2 == 0) COLOR_WHITE else COLOR_LIGHT_BG
            canvas.drawRect(rRect, paint)

            paint.color = COLOR_DARK
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            paint.textSize = 7.5f

            canvas.drawText(job.id.take(11), 44f, y + 14f, paint)
            val custStr = if (job.customerName.length > 18) job.customerName.take(16) + ".." else job.customerName
            canvas.drawText(custStr, 120f, y + 14f, paint)
            val machStr = if (job.machineName.length > 20) job.machineName.take(18) + ".." else job.machineName
            canvas.drawText(machStr, 230f, y + 14f, paint)
            canvas.drawText(dateOnlyFormat.format(Date(job.checkoutTime)), 350f, y + 14f, paint)

            // Status Badge
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            paint.color = when (job.status) {
                "returned" -> COLOR_SUCCESS
                "overdue" -> COLOR_OVERDUE
                else -> COLOR_PRIMARY
            }
            canvas.drawText(job.status.uppercase(Locale.getDefault()), 420f, y + 14f, paint)

            paint.color = COLOR_DARK
            val amountStr = if (job.status == "returned") "₹${job.totalRentalFee.toInt()}" else "₹${job.depositAmount.toInt()}"
            canvas.drawText(amountStr, 480f, y + 14f, paint)

            y += 20f
        }

        // 5. FOOTER
        paint.color = COLOR_PRIMARY
        canvas.drawRect(0f, PAGE_HEIGHT - 22f, PAGE_WIDTH.toFloat(), PAGE_HEIGHT.toFloat(), paint)

        paint.color = COLOR_WHITE
        paint.textSize = 7.5f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        val footerText = "BABA TRADERS & Engineering Hardwares • Confidential Internal Audit Report • Generated electronically"
        val footerWidth = paint.measureText(footerText)
        canvas.drawText(footerText, (PAGE_WIDTH - footerWidth) / 2f, PAGE_HEIGHT - 8f, paint)

        document.finishPage(page)

        val outputDir = File(context.cacheDir, "pdf_reports")
        if (!outputDir.exists()) outputDir.mkdirs()
        val file = File(outputDir, "BABA_Report_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.pdf")
        FileOutputStream(file).use { out ->
            document.writeTo(out)
        }
        document.close()

        return file
    }

    private fun drawKpiCard(
        canvas: Canvas,
        paint: Paint,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        title: String,
        value: String,
        subtitle: String,
        valueColor: Int
    ) {
        val rect = RectF(x, y, x + width, y + height)
        paint.color = COLOR_LIGHT_BG
        canvas.drawRoundRect(rect, 6f, 6f, paint)
        paint.color = COLOR_BORDER
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        canvas.drawRoundRect(rect, 6f, 6f, paint)
        paint.style = Paint.Style.FILL

        paint.color = COLOR_GRAY
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 7.5f
        canvas.drawText(title, x + 10f, y + 14f, paint)

        paint.color = valueColor
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 14f
        canvas.drawText(value, x + 10f, y + 32f, paint)

        paint.color = COLOR_GRAY
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textSize = 7f
        canvas.drawText(subtitle, x + 10f, y + 43f, paint)
    }

    /**
     * Shares a Job Sheet PDF via system share sheet (WhatsApp, Email, etc.).
     */
    fun shareJobSheetPdf(context: Context, jobSheet: JobSheetEntity) {
        try {
            val file = generateJobSheetPdf(context, jobSheet)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "BABA Traders Rental Invoice - ${jobSheet.id}")
                putExtra(
                    Intent.EXTRA_TEXT,
                    "Rental Job Sheet & Invoice for ${jobSheet.customerName} (${jobSheet.machineName}) from BABA Traders."
                )
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Share Job Sheet PDF"))
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to share PDF: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Opens Job Sheet PDF in system PDF viewer.
     */
    fun viewJobSheetPdf(context: Context, jobSheet: JobSheetEntity) {
        try {
            val file = generateJobSheetPdf(context, jobSheet)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            // Fallback: no dedicated PDF viewer installed, offer share chooser
            shareJobSheetPdf(context, jobSheet)
            Toast.makeText(context, "No PDF viewer found. Opening share menu.", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Unable to open PDF: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Shares Financial Performance PDF Report.
     */
    fun shareReportPdf(
        context: Context,
        jobSheets: List<JobSheetEntity>,
        payments: List<PaymentEntity>,
        generatedByName: String
    ) {
        try {
            val file = generateReportPdf(context, jobSheets, payments, generatedByName)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "BABA Traders Financial Performance Report")
                putExtra(
                    Intent.EXTRA_TEXT,
                    "Monthly Financial & Rental Audit Statement from BABA Traders & Engineering Hardwares."
                )
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Share Report PDF"))
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to share report PDF: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Views Financial Performance PDF Report.
     */
    fun viewReportPdf(
        context: Context,
        jobSheets: List<JobSheetEntity>,
        payments: List<PaymentEntity>,
        generatedByName: String
    ) {
        try {
            val file = generateReportPdf(context, jobSheets, payments, generatedByName)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            shareReportPdf(context, jobSheets, payments, generatedByName)
            Toast.makeText(context, "No PDF viewer found. Opening share menu.", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Unable to open report PDF: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
