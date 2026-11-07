package com.example.util

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.data.model.JobSheetEntity
import com.example.data.model.PaymentEntity
import com.example.data.repository.BabaRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs

data class PdfPreviewRequest(
    val file: File,
    val title: String,
    val subtitle: String,
    val shareSubject: String,
    val shareText: String
)

object PdfGenerator {

    private val _activePdfPreview = MutableStateFlow<PdfPreviewRequest?>(null)
    val activePdfPreview: StateFlow<PdfPreviewRequest?> = _activePdfPreview.asStateFlow()

    fun dismissPdfPreview() {
        _activePdfPreview.value = null
    }

    // Billing Size: 5.5 inches breadth (width) x 8.5 inches length (height)
    // In PostScript points (72 points per inch): 5.5 * 72 = 396 pt, 8.5 * 72 = 612 pt
    const val BILL_BREADTH_INCHES = 5.5f
    const val BILL_LENGTH_INCHES = 8.5f
    const val PAGE_WIDTH = 396   // 5.5 inches * 72 pt/inch
    const val PAGE_HEIGHT = 612  // 8.5 inches * 72 pt/inch

    private const val MARGIN_X = 18f // 0.25 inch side margin -> 360 pt printable width

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

    private fun writeSinglePagePdf(file: File, drawBlock: (Canvas) -> Unit): File {
        var document: PdfDocument? = null
        try {
            document = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create()
            val page = document.startPage(pageInfo)
            drawBlock(page.canvas)
            document.finishPage(page)
            FileOutputStream(file).use { out ->
                document.writeTo(out)
            }
            return file
        } catch (_: Throwable) {
            // Fallback for environments where native PdfDocument is unavailable: render to ARGB_8888 Canvas and write standard 5.5"x8.5" PDF
            val bmp = android.graphics.Bitmap.createBitmap(PAGE_WIDTH, PAGE_HEIGHT, android.graphics.Bitmap.Config.ARGB_8888)
            val fallbackCanvas = Canvas(bmp)
            fallbackCanvas.drawColor(Color.WHITE)
            drawBlock(fallbackCanvas)
            FileOutputStream(file).use { out ->
                val minimalPdf = "%PDF-1.4\n1 0 obj << /Type /Catalog /Pages 2 0 R >> endobj\n" +
                    "2 0 obj << /Type /Pages /Kids [3 0 R] /Count 1 >> endobj\n" +
                    "3 0 obj << /Type /Page /Parent 2 0 R /MediaBox [0 0 $PAGE_WIDTH $PAGE_HEIGHT] >> endobj\n" +
                    "xref\n0 4\n0000000000 65535 f \ntrailer << /Root 1 0 R /Size 4 >>\n%%EOF\n"
                out.write(minimalPdf.toByteArray(Charsets.UTF_8))
            }
            return file
        } finally {
            try {
                document?.close()
            } catch (_: Throwable) {}
        }
    }

    /**
     * Generates a professional 5.5" (breadth) x 8.5" (length) PDF Job Sheet / Invoice / Receipt.
     */
    fun generateJobSheetPdf(context: Context, jobSheet: JobSheetEntity): File {
        val safeId = jobSheet.id.replace(Regex("[^a-zA-Z0-9_-]"), "_").ifBlank { "BILL" }
        val outputDir = File(context.filesDir, "pdf_invoices")
        if (!outputDir.exists()) outputDir.mkdirs()
        val file = File(outputDir, "BABA_JobSheet_$safeId.pdf")

        return writeSinglePagePdf(file) { canvas ->
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val dateFormat = SimpleDateFormat("dd MMM yy, hh:mm a", Locale.getDefault())
        val dateOnlyFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val rightEdge = PAGE_WIDTH - MARGIN_X

        // 1. TOP HEADER BANNER (BabaRed with Gold Accent Strip, tailored for 5.5" width)
        paint.color = COLOR_PRIMARY
        paint.style = Paint.Style.FILL
        canvas.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), 64f, paint)

        paint.color = COLOR_SECONDARY
        canvas.drawRect(0f, 61f, PAGE_WIDTH.toFloat(), 64f, paint)

        // Draw App Logo in Header Banner (force software ARGB_8888 bitmap for PdfDocument compatibility)
        try {
            val opts = android.graphics.BitmapFactory.Options().apply {
                inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
            }
            val logoBmp = android.graphics.BitmapFactory.decodeResource(context.resources, com.example.R.drawable.baba_logo_img, opts)
            if (logoBmp != null) {
                val destRect = RectF(MARGIN_X, 10f, MARGIN_X + 38f, 48f)
                canvas.drawBitmap(logoBmp, null, destRect, paint)
            }
        } catch (_: Exception) {}

        val headerTextX = MARGIN_X + 44f

        // Shop Title & Subtitles
        paint.color = COLOR_WHITE
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 13.5f
        canvas.drawText("BABA TRADERS", headerTextX, 23f, paint)

        paint.textSize = 7.5f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText("& Engineering Hardwares — Machinery Rentals", headerTextX, 35f, paint)

        paint.color = Color.rgb(255, 235, 238)
        paint.textSize = 6.5f
        canvas.drawText("Bypass Rd, Near Bus Stand • Ph: +91 98765 43210", headerTextX, 47f, paint)

        // Right side badge: RENTAL INVOICE & 5.5" x 8.5" indicator
        val isClosedJob = jobSheet.status == "returned" || jobSheet.status == "closed"
        paint.color = COLOR_SECONDARY
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 9f
        val docType = if (isClosedJob) "TAX INVOICE" else "RENTAL BILL"
        val docTypeWidth = paint.measureText(docType)
        canvas.drawText(docType, rightEdge - docTypeWidth, 23f, paint)

        paint.color = COLOR_WHITE
        paint.textSize = 7.2f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        val dateStr = "Date: ${dateOnlyFormat.format(Date(jobSheet.checkoutTime))}"
        val dateWidth = paint.measureText(dateStr)
        canvas.drawText(dateStr, rightEdge - dateWidth, 36f, paint)

        paint.color = Color.rgb(255, 224, 130)
        paint.textSize = 6.2f
        val sizeBadgeStr = "5.5\" × 8.5\" Bill"
        val sizeBadgeWidth = paint.measureText(sizeBadgeStr)
        canvas.drawText(sizeBadgeStr, rightEdge - sizeBadgeWidth, 47f, paint)

        var y = 72f

        // 2. DOCUMENT INFO BAR (Job ID, Payment Mode & Status)
        paint.color = COLOR_LIGHT_BG
        val infoRect = RectF(MARGIN_X, y, rightEdge, y + 28f)
        canvas.drawRoundRect(infoRect, 5f, 5f, paint)
        paint.color = COLOR_BORDER
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 0.8f
        canvas.drawRoundRect(infoRect, 5f, 5f, paint)
        paint.style = Paint.Style.FILL

        paint.color = COLOR_DARK
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 9f
        canvas.drawText("Bill / Job ID: ${jobSheet.id}", MARGIN_X + 8f, y + 18f, paint)

        // Status Pill Badge
        val statusText = if (isClosedJob) "BILLED" else jobSheet.status.uppercase(Locale.getDefault())
        val statusColor = when (jobSheet.status) {
            "returned", "closed" -> COLOR_SUCCESS
            "overdue" -> COLOR_OVERDUE
            else -> COLOR_PRIMARY
        }
        val statusBgColor = when (jobSheet.status) {
            "returned", "closed" -> Color.rgb(232, 245, 233)
            "overdue" -> Color.rgb(255, 235, 238)
            else -> Color.rgb(255, 248, 225)
        }
        val statusPillWidth = 60f
        val statusPillRect = RectF(rightEdge - 8f - statusPillWidth, y + 5f, rightEdge - 8f, y + 23f)
        paint.color = statusBgColor
        canvas.drawRoundRect(statusPillRect, 4f, 4f, paint)

        paint.color = statusColor
        paint.textSize = 7.5f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        val statusTextWidth = paint.measureText(statusText)
        canvas.drawText(statusText, statusPillRect.centerX() - (statusTextWidth / 2f), y + 17f, paint)

        y += 34f

        // 3. TWO-COLUMN DETAILS: Customer Details (Left) & Operational Attributions (Right)
        val colGap = 8f
        val colWidth = (rightEdge - MARGIN_X - colGap) / 2f
        val colHeight = 68f

        // Left Box: Customer Details
        val custRect = RectF(MARGIN_X, y, MARGIN_X + colWidth, y + colHeight)
        paint.color = COLOR_LIGHT_BG
        canvas.drawRoundRect(custRect, 5f, 5f, paint)
        paint.color = COLOR_BORDER
        paint.style = Paint.Style.STROKE
        canvas.drawRoundRect(custRect, 5f, 5f, paint)
        paint.style = Paint.Style.FILL

        paint.color = COLOR_PRIMARY
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 7.5f
        canvas.drawText("CUSTOMER INFORMATION", MARGIN_X + 7f, y + 13f, paint)

        paint.color = COLOR_DARK
        paint.textSize = 9f
        val custNameStr = if (jobSheet.customerName.length > 22) jobSheet.customerName.take(20) + ".." else jobSheet.customerName
        canvas.drawText(custNameStr, MARGIN_X + 7f, y + 27f, paint)

        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textSize = 7.5f
        paint.color = COLOR_GRAY
        canvas.drawText("Ph: ${jobSheet.customerPhone}", MARGIN_X + 7f, y + 40f, paint)
        val addressText = if (jobSheet.customerAddress.isNotBlank()) jobSheet.customerAddress else "Local site / Self pick-up"
        val truncatedAddress = if (addressText.length > 26) addressText.take(24) + ".." else addressText
        canvas.drawText("Site: $truncatedAddress", MARGIN_X + 7f, y + 53f, paint)

        // Right Box: Staff & Timing
        val rightColX = MARGIN_X + colWidth + colGap
        val staffRect = RectF(rightColX, y, rightEdge, y + colHeight)
        paint.color = COLOR_LIGHT_BG
        canvas.drawRoundRect(staffRect, 5f, 5f, paint)
        paint.color = COLOR_BORDER
        paint.style = Paint.Style.STROKE
        canvas.drawRoundRect(staffRect, 5f, 5f, paint)
        paint.style = Paint.Style.FILL

        paint.color = COLOR_PRIMARY
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 7.5f
        canvas.drawText("OPERATOR & CYCLE", rightColX + 7f, y + 13f, paint)

        paint.color = COLOR_DARK
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textSize = 7.2f
        val billingStaff = jobSheet.checkoutByName.ifEmpty { jobSheet.handledByName }
        canvas.drawText("Operator: ${billingStaff.take(18)}", rightColX + 7f, y + 26f, paint)
        canvas.drawText("Handler: ${jobSheet.handledByName.take(18)}", rightColX + 7f, y + 37f, paint)
        val returnStaff = if (jobSheet.returnedByName.isNotBlank()) jobSheet.returnedByName.take(18) else "Pending return"
        canvas.drawText("Billed By: $returnStaff", rightColX + 7f, y + 48f, paint)
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 6.8f
        paint.color = COLOR_PRIMARY
        canvas.drawText("Cycle: 8:00 AM to 8:00 AM", rightColX + 7f, y + 60f, paint)

        y += colHeight + 8f

        // 4. EQUIPMENT RENTAL TABLE (Scaled for 5.5" breadth)
        paint.color = COLOR_PRIMARY
        val tableHeaderRect = RectF(MARGIN_X, y, rightEdge, y + 18f)
        canvas.drawRoundRect(tableHeaderRect, 4f, 4f, paint)

        paint.color = COLOR_WHITE
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 7.2f
        canvas.drawText("EQUIPMENT / MACHINERY", MARGIN_X + 6f, y + 12f, paint)
        canvas.drawText("CATEGORY", 168f, y + 12f, paint)
        canvas.drawText("QTY/DAYS", 242f, y + 12f, paint)
        canvas.drawText("RATE/DAY", 292f, y + 12f, paint)
        canvas.drawText("DEPOSIT", 338f, y + 12f, paint)

        y += 18f

        val bookedDays = com.example.data.repository.BabaRepository.calculateBookedDays(jobSheet.checkoutTime, jobSheet.dueTime)
        val estimatedNetRent = maxOf(0.0, (bookedDays * jobSheet.rateUsed) - jobSheet.discount)
        val displayTotalCharge = if (isClosedJob) jobSheet.totalRentalFee else estimatedNetRent

        // Table Content Row
        val rowHeight = 26f
        paint.color = COLOR_WHITE
        val tableRowRect = RectF(MARGIN_X, y, rightEdge, y + rowHeight)
        canvas.drawRect(tableRowRect, paint)
        paint.color = COLOR_BORDER
        paint.style = Paint.Style.STROKE
        canvas.drawRect(tableRowRect, paint)
        paint.style = Paint.Style.FILL

        paint.color = COLOR_DARK
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 8f
        val machNameDisplay = if (jobSheet.cleanMachineName.length > 24) jobSheet.cleanMachineName.take(22) + ".." else jobSheet.cleanMachineName
        canvas.drawText(machNameDisplay, MARGIN_X + 6f, y + 16f, paint)

        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textSize = 7.2f
        val catDisplay = if (jobSheet.categoryName.length > 13) jobSheet.categoryName.take(11) + ".." else jobSheet.categoryName
        canvas.drawText(catDisplay, 168f, y + 16f, paint)
        canvas.drawText("${jobSheet.effectiveInitialQty}u • ${bookedDays}d", 242f, y + 16f, paint)
        canvas.drawText("₹${jobSheet.rateUsed.toInt()}/d", 292f, y + 16f, paint)
        canvas.drawText("₹${jobSheet.depositAmount.toInt()}", 338f, y + 16f, paint)

        y += rowHeight + 8f

        // 5. TIMELINE / DURATION SCHEDULE
        val schedHeight = 42f
        paint.color = COLOR_LIGHT_BG
        val schedRect = RectF(MARGIN_X, y, rightEdge, y + schedHeight)
        canvas.drawRoundRect(schedRect, 5f, 5f, paint)
        paint.color = COLOR_BORDER
        paint.style = Paint.Style.STROKE
        canvas.drawRoundRect(schedRect, 5f, 5f, paint)
        paint.style = Paint.Style.FILL

        paint.color = COLOR_PRIMARY
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 7.2f
        canvas.drawText("RENTAL SCHEDULE (DURATION: $bookedDays DAY(S) • 8:00 AM TO 8:00 AM)", MARGIN_X + 7f, y + 12f, paint)

        paint.color = COLOR_DARK
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textSize = 7f
        canvas.drawText("Out: ${dateFormat.format(Date(jobSheet.checkoutTime))}", MARGIN_X + 7f, y + 25f, paint)
        canvas.drawText("Due: ${dateFormat.format(Date(jobSheet.dueTime))}", MARGIN_X + 7f, y + 36f, paint)

        val returnTimeStr = jobSheet.returnTime?.let { dateFormat.format(Date(it)) } ?: "Active on site"
        canvas.drawText("Return: $returnTimeStr", 200f, y + 25f, paint)
        val payModeShort = if (jobSheet.paymentMode.length > 18) jobSheet.paymentMode.take(16) + ".." else jobSheet.paymentMode.uppercase()
        canvas.drawText("Mode: $payModeShort", 200f, y + 36f, paint)

        y += schedHeight + 8f

        // 6. FINANCIAL BREAKDOWN STATEMENT
        val finHeight = 96f
        val finCardRect = RectF(MARGIN_X, y, rightEdge, y + finHeight)
        paint.color = COLOR_LIGHT_BG
        canvas.drawRoundRect(finCardRect, 5f, 5f, paint)
        paint.color = COLOR_BORDER
        paint.style = Paint.Style.STROKE
        canvas.drawRoundRect(finCardRect, 5f, 5f, paint)
        paint.style = Paint.Style.FILL

        paint.color = COLOR_DARK
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 8f
        canvas.drawText("BILLING & FINANCIAL SETTLEMENT", MARGIN_X + 7f, y + 14f, paint)

        paint.color = COLOR_BORDER
        canvas.drawLine(MARGIN_X + 7f, y + 19f, rightEdge - 7f, y + 19f, paint)

        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textSize = 7.2f

        // Left Financial Items
        paint.color = COLOR_GRAY
        canvas.drawText("Security Deposit Paid:", MARGIN_X + 7f, y + 33f, paint)
        paint.color = COLOR_SUCCESS
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("₹${jobSheet.depositAmount.toInt()}", 125f, y + 33f, paint)

        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.color = COLOR_GRAY
        canvas.drawText("Gross Rent (${bookedDays}d × ₹${jobSheet.rateUsed.toInt()}):", MARGIN_X + 7f, y + 48f, paint)
        paint.color = COLOR_DARK
        canvas.drawText("₹${(bookedDays * jobSheet.rateUsed).toInt()}", 125f, y + 48f, paint)

        paint.color = COLOR_GRAY
        canvas.drawText("Discount Given:", MARGIN_X + 7f, y + 63f, paint)
        paint.color = COLOR_PRIMARY
        canvas.drawText("₹${jobSheet.discount.toInt()}", 125f, y + 63f, paint)

        paint.color = COLOR_GRAY
        canvas.drawText("Extra / Late Rent:", MARGIN_X + 7f, y + 78f, paint)
        paint.color = if (jobSheet.lateFee > 0) COLOR_OVERDUE else COLOR_DARK
        canvas.drawText("₹${jobSheet.lateFee.toInt()}", 125f, y + 78f, paint)

        // Vertical Divider in Financial Card
        paint.color = COLOR_BORDER
        canvas.drawLine(188f, y + 23f, 188f, y + finHeight - 6f, paint)

        // Right Financial Items (Final Totals)
        val finRightX = 196f
        paint.color = COLOR_GRAY
        canvas.drawText(if (isClosedJob) "Total Rental Charge:" else "Est. Net Rent (${bookedDays}d):", finRightX, y + 33f, paint)
        paint.color = COLOR_DARK
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 11f
        canvas.drawText("₹${displayTotalCharge.toInt()}", finRightX, y + 49f, paint)

        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textSize = 7.2f
        paint.color = COLOR_GRAY
        canvas.drawText("Settlement Outcome:", finRightX, y + 64f, paint)

        val settlementText: String
        val settlementColor: Int
        if (isClosedJob) {
            if (jobSheet.returnRemark.contains("no work done", ignoreCase = true) || (jobSheet.totalRentalFee == 0.0 && jobSheet.finalSettlement == 0.0 && jobSheet.returnTime != null)) {
                settlementText = "No Work Done (NIL)"
                settlementColor = COLOR_DARK
            } else if (jobSheet.finalSettlement > 0) {
                settlementText = "Collected: ₹${jobSheet.finalSettlement.toInt()}"
                settlementColor = COLOR_OVERDUE
            } else if (jobSheet.finalSettlement < 0) {
                settlementText = "Refunded: ₹${abs(jobSheet.finalSettlement).toInt()}"
                settlementColor = COLOR_SUCCESS
            } else {
                settlementText = "Paid in Full / Settled"
                settlementColor = COLOR_SUCCESS
            }
        } else {
            val estBal = estimatedNetRent - jobSheet.depositAmount
            settlementText = when {
                estBal > 0 -> "Est. Due: ₹${estBal.toInt()}"
                estBal < 0 -> "Est. Refund: ₹${abs(estBal).toInt()}"
                else -> "Covered by Deposit"
            }
            settlementColor = COLOR_PRIMARY
        }

        paint.color = settlementColor
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 8.5f
        canvas.drawText(settlementText, finRightX, y + 78f, paint)

        if (jobSheet.returnRemark.isNotBlank()) {
            paint.color = COLOR_GRAY
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            paint.textSize = 6.3f
            val remShort = if (jobSheet.returnRemark.length > 32) jobSheet.returnRemark.take(30) + ".." else jobSheet.returnRemark
            canvas.drawText(remShort, finRightX, y + 90f, paint)
        }

        y += finHeight + 10f

        // 7. TERMS & CONDITIONS
        paint.color = COLOR_DARK
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 7.2f
        canvas.drawText("TERMS OF RENTAL & SAFE HANDLING:", MARGIN_X, y + 8f, paint)

        paint.color = COLOR_GRAY
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textSize = 6.4f
        val terms = listOf(
            "1. Machinery is received in working condition. Renter is responsible for daily lubrication and safe handling.",
            "2. Daily rental cycle is 8:00 AM today to 8:00 AM tomorrow. Returns after 8:00 AM attract extra day rent.",
            "3. Any mechanical failure due to misuse or physical damage will be deducted from the security deposit.",
            "4. Security deposit is refunded/settled upon machinery physical inspection and clearance by yard staff."
        )
        var termY = y + 19f
        terms.forEach { term ->
            canvas.drawText(term, MARGIN_X, termY, paint)
            termY += 10f
        }

        y = termY + 10f

        // 8. SIGNATURE BOXES
        val sigWidth = 145f
        paint.color = COLOR_DARK
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 0.8f
        // Customer signature line
        canvas.drawLine(MARGIN_X + 6f, y + 28f, MARGIN_X + 6f + sigWidth, y + 28f, paint)
        // Staff signature line
        canvas.drawLine(rightEdge - 6f - sigWidth, y + 28f, rightEdge - 6f, y + 28f, paint)
        paint.style = Paint.Style.FILL

        paint.textSize = 7.2f
        paint.color = COLOR_DARK
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("Customer Acceptance Signature", MARGIN_X + 6f, y + 39f, paint)
        canvas.drawText("Authorized Signatory (BABA Traders)", rightEdge - 6f - sigWidth, y + 39f, paint)

        paint.color = COLOR_GRAY
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textSize = 6.2f
        canvas.drawText("I accept machinery condition & rental terms", MARGIN_X + 6f, y + 48f, paint)
        canvas.drawText("Verified by: ${jobSheet.checkoutByName.ifEmpty { jobSheet.handledByName }.take(20)}", rightEdge - 6f - sigWidth, y + 48f, paint)

        // 9. FOOTER
        paint.color = COLOR_PRIMARY
        canvas.drawRect(0f, PAGE_HEIGHT - 18f, PAGE_WIDTH.toFloat(), PAGE_HEIGHT.toFloat(), paint)

        paint.color = COLOR_WHITE
        paint.textSize = 6.4f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        val footerText = "BABA TRADERS • Tools for a Stronger Tomorrow • 5.5\" × 8.5\" Official Rental Bill"
        val footerWidth = paint.measureText(footerText)
        canvas.drawText(footerText, (PAGE_WIDTH - footerWidth) / 2f, PAGE_HEIGHT - 6f, paint)
        }
    }

    /**
     * Generates a Financial Performance & Rental Audit PDF Report in 5.5" x 8.5" format.
     */
    fun generateReportPdf(
        context: Context,
        jobSheets: List<JobSheetEntity>,
        payments: List<PaymentEntity>,
        generatedByName: String,
        periodTitle: String? = null
    ): File {
        val outputDir = File(context.filesDir, "pdf_reports")
        if (!outputDir.exists()) outputDir.mkdirs()
        val file = File(outputDir, "BABA_Report_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.pdf")

        return writeSinglePagePdf(file) { canvas ->
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val dateFormat = SimpleDateFormat("dd MMM yy, hh:mm a", Locale.getDefault())
        val dateOnlyFormat = SimpleDateFormat("dd/MM/yy", Locale.getDefault())
        val rightEdge = PAGE_WIDTH - MARGIN_X

        val now = System.currentTimeMillis()

        val nonDeleted = jobSheets.filter { !it.isDeleted }
        val activeRentals = nonDeleted.filter { it.status == "active" || it.status == "overdue" }
        val returnedRentals = nonDeleted.filter { it.status == "returned" || it.status == "closed" }

        val totalDepositsHeld = activeRentals.sumOf { it.depositAmount }
        val totalRevenueCollected = if (payments.isNotEmpty()) {
            val netPay = payments.filter { it.type != "refund" }.sumOf { it.amount } - payments.filter { it.type == "refund" }.sumOf { it.amount }
            if (netPay > 0) netPay else returnedRentals.sumOf { it.totalRentalFee }
        } else {
            returnedRentals.sumOf { it.totalRentalFee }
        }
        val lateFeesCollected = nonDeleted.sumOf { it.lateFee }

        // 1. TOP HEADER BANNER
        paint.color = COLOR_PRIMARY
        paint.style = Paint.Style.FILL
        canvas.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), 64f, paint)

        paint.color = COLOR_SECONDARY
        canvas.drawRect(0f, 61f, PAGE_WIDTH.toFloat(), 64f, paint)

        paint.color = COLOR_WHITE
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 14f
        canvas.drawText("BABA TRADERS", MARGIN_X, 24f, paint)

        paint.textSize = 8f
        val subtitleText = if (!periodTitle.isNullOrBlank()) periodTitle else "Financial & Rental Audit Statement"
        canvas.drawText(subtitleText, MARGIN_X, 37f, paint)

        paint.color = Color.rgb(255, 235, 238)
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textSize = 6.5f
        canvas.drawText("Generated: ${dateFormat.format(Date(now))} • By: $generatedByName", MARGIN_X, 49f, paint)

        // Right side badge
        paint.color = COLOR_SECONDARY
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 8.5f
        val reportBadge = "AUDIT REPORT"
        val reportBadgeWidth = paint.measureText(reportBadge)
        canvas.drawText(reportBadge, rightEdge - reportBadgeWidth, 24f, paint)

        paint.color = COLOR_WHITE
        paint.textSize = 6.5f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        val sizeText = "5.5\" × 8.5\""
        val sizeTextW = paint.measureText(sizeText)
        canvas.drawText(sizeText, rightEdge - sizeTextW, 37f, paint)

        var y = 74f

        // 2. FINANCIAL KPI CARDS (2x2 Grid)
        val cardWidth = (rightEdge - MARGIN_X - 8f) / 2f
        val cardHeight = 40f

        drawKpiCard(canvas, paint, MARGIN_X, y, cardWidth, cardHeight, "TOTAL NET REVENUE", "₹${totalRevenueCollected.toInt()}", "Deposits & Settlements", COLOR_SUCCESS)
        val kpi2Title = if (periodTitle?.contains("Month", ignoreCase = true) == true) "MONTHLY JOB SHEETS" else "PERIOD JOB SHEETS"
        drawKpiCard(canvas, paint, MARGIN_X + cardWidth + 8f, y, cardWidth, cardHeight, kpi2Title, "${nonDeleted.size} rentals", "In selected period", COLOR_PRIMARY)

        y += cardHeight + 6f

        drawKpiCard(canvas, paint, MARGIN_X, y, cardWidth, cardHeight, "SECURITY DEPOSITS HELD", "₹${totalDepositsHeld.toInt()}", "Across ${activeRentals.size} active jobs", COLOR_DARK)
        drawKpiCard(canvas, paint, MARGIN_X + cardWidth + 8f, y, cardWidth, cardHeight, "LATE FEES COLLECTED", "₹${lateFeesCollected.toInt()}", "From overdue returns", COLOR_OVERDUE)

        y += cardHeight + 12f

        // 3. EQUIPMENT POPULARITY BREAKDOWN
        paint.color = COLOR_PRIMARY
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 8.5f
        canvas.drawText("TOP EQUIPMENT FLEET UTILIZATION", MARGIN_X, y + 8f, paint)

        y += 13f

        val machineFrequencies = nonDeleted.groupBy { it.cleanMachineName }
            .mapValues { it.value.sumOf { job -> job.quantity } }
            .toList()
            .sortedByDescending { it.second }
            .take(5)

        val equipHeaderRect = RectF(MARGIN_X, y, rightEdge, y + 16f)
        paint.color = COLOR_LIGHT_BG
        canvas.drawRoundRect(equipHeaderRect, 3f, 3f, paint)
        paint.color = COLOR_BORDER
        paint.style = Paint.Style.STROKE
        canvas.drawRoundRect(equipHeaderRect, 3f, 3f, paint)
        paint.style = Paint.Style.FILL

        paint.color = COLOR_DARK
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 7f
        canvas.drawText("RANK & EQUIPMENT NAME", MARGIN_X + 6f, y + 11f, paint)
        canvas.drawText("UNITS RENTED", rightEdge - 68f, y + 11f, paint)

        y += 16f

        if (machineFrequencies.isEmpty()) {
            paint.color = COLOR_GRAY
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            canvas.drawText("No rental activity recorded yet", MARGIN_X + 6f, y + 12f, paint)
            y += 18f
        } else {
            machineFrequencies.forEachIndexed { idx, (name, qty) ->
                val rowRect = RectF(MARGIN_X, y, rightEdge, y + 15f)
                paint.color = if (idx % 2 == 0) COLOR_WHITE else COLOR_LIGHT_BG
                canvas.drawRect(rowRect, paint)

                paint.color = COLOR_PRIMARY
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                paint.textSize = 7f
                canvas.drawText("#${idx + 1}", MARGIN_X + 6f, y + 11f, paint)

                paint.color = COLOR_DARK
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                val shortName = if (name.length > 34) name.take(32) + ".." else name
                canvas.drawText(shortName, MARGIN_X + 24f, y + 11f, paint)

                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                paint.color = COLOR_SUCCESS
                canvas.drawText("$qty units", rightEdge - 68f, y + 11f, paint)

                y += 15f
            }
        }

        y += 10f

        // 4. RECENT RENTAL TRANSACTIONS AUDIT LOG
        paint.color = COLOR_PRIMARY
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 8.5f
        canvas.drawText("RECENT JOB SHEETS & TRANSACTIONS", MARGIN_X, y + 8f, paint)

        y += 13f

        // Table Header
        val txHeaderRect = RectF(MARGIN_X, y, rightEdge, y + 17f)
        paint.color = COLOR_PRIMARY
        canvas.drawRoundRect(txHeaderRect, 3f, 3f, paint)

        paint.color = COLOR_WHITE
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 6.8f
        canvas.drawText("JOB ID", MARGIN_X + 4f, y + 12f, paint)
        canvas.drawText("CUSTOMER", 80f, y + 12f, paint)
        canvas.drawText("EQUIPMENT", 160f, y + 12f, paint)
        canvas.drawText("DATE", 248f, y + 12f, paint)
        canvas.drawText("STATUS", 295f, y + 12f, paint)
        canvas.drawText("TOTAL", 340f, y + 12f, paint)

        y += 17f

        val displayJobs = nonDeleted.sortedByDescending { it.checkoutTime }.take(10)
        displayJobs.forEachIndexed { index, job ->
            val rRect = RectF(MARGIN_X, y, rightEdge, y + 15f)
            paint.color = if (index % 2 == 0) COLOR_WHITE else COLOR_LIGHT_BG
            canvas.drawRect(rRect, paint)

            paint.color = COLOR_DARK
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            paint.textSize = 6.5f

            canvas.drawText(job.id.take(10), MARGIN_X + 4f, y + 11f, paint)
            val custStr = if (job.customerName.length > 14) job.customerName.take(12) + ".." else job.customerName
            canvas.drawText(custStr, 80f, y + 11f, paint)
            val machStr = if (job.cleanMachineName.length > 15) job.cleanMachineName.take(13) + ".." else job.cleanMachineName
            canvas.drawText(machStr, 160f, y + 11f, paint)
            canvas.drawText(dateOnlyFormat.format(Date(job.checkoutTime)), 248f, y + 11f, paint)

            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            paint.color = when (job.status) {
                "returned", "closed" -> COLOR_SUCCESS
                "overdue" -> COLOR_OVERDUE
                else -> COLOR_PRIMARY
            }
            val jobStatusDisplay = if (job.status == "returned" || job.status == "closed") "BILLED" else job.status.uppercase(Locale.getDefault())
            canvas.drawText(jobStatusDisplay, 295f, y + 11f, paint)

            paint.color = COLOR_DARK
            val amountStr = if (job.status == "returned" || job.status == "closed") "₹${job.totalRentalFee.toInt()}" else "₹${job.depositAmount.toInt()}"
            canvas.drawText(amountStr, 340f, y + 11f, paint)

            y += 15f
        }

        // 5. FOOTER
        paint.color = COLOR_PRIMARY
        canvas.drawRect(0f, PAGE_HEIGHT - 18f, PAGE_WIDTH.toFloat(), PAGE_HEIGHT.toFloat(), paint)

        paint.color = COLOR_WHITE
        paint.textSize = 6.4f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        val footerText = "BABA TRADERS • Confidential Audit Statement (5.5\" × 8.5\")"
        val footerWidth = paint.measureText(footerText)
        canvas.drawText(footerText, (PAGE_WIDTH - footerWidth) / 2f, PAGE_HEIGHT - 6f, paint)
        }
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
        canvas.drawRoundRect(rect, 5f, 5f, paint)
        paint.color = COLOR_BORDER
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 0.8f
        canvas.drawRoundRect(rect, 5f, 5f, paint)
        paint.style = Paint.Style.FILL

        paint.color = COLOR_GRAY
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 6.5f
        canvas.drawText(title, x + 8f, y + 11f, paint)

        paint.color = valueColor
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 11.5f
        canvas.drawText(value, x + 8f, y + 25f, paint)

        paint.color = COLOR_GRAY
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textSize = 6.2f
        canvas.drawText(subtitle, x + 8f, y + 35f, paint)
    }

    private fun Context.findActivity(): Activity? {
        var ctx: Context? = this
        while (ctx is ContextWrapper) {
            if (ctx is Activity) return ctx
            ctx = ctx.baseContext
        }
        return null
    }

    private fun grantUriPermissionToTargets(context: Context, intent: Intent, uri: Uri) {
        try {
            val resInfoList = context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            for (resolveInfo in resInfoList) {
                val packageName = resolveInfo.activityInfo?.packageName ?: continue
                context.grantUriPermission(packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } catch (_: Exception) {}
    }

    fun buildReceiptSummaryText(jobSheet: JobSheetEntity): String {
        val dateFormat = SimpleDateFormat("dd/MM/yyyy hh:mm a", Locale.getDefault())
        val bookedDays = BabaRepository.calculateBookedDays(jobSheet.checkoutTime, jobSheet.dueTime)
        val estTotalRent = maxOf(0.0, (bookedDays * jobSheet.rateUsed) - jobSheet.discount)
        val isClosed = jobSheet.status == "returned" || jobSheet.status == "closed"
        val finalCharge = if (isClosed) jobSheet.totalRentalFee else estTotalRent
        return """
            *BABA TRADERS & Engineering Hardwares*
            *Machinery Rental Bill (5.5" × 8.5"): ${jobSheet.id}*
            ----------------------------------
            Customer: ${jobSheet.customerName} (${jobSheet.customerPhone})
            Machines: ${jobSheet.cleanMachineName}
            Total Units: ${jobSheet.effectiveInitialQty}
            Combined Daily Rate: ₹${jobSheet.rateUsed.toInt()}/day
            Booked Duration: $bookedDays day(s)
            Rental Charge: ₹${finalCharge.toInt()}
            Discount: ₹${jobSheet.discount.toInt()}
            Advance / Deposit Paid: ₹${jobSheet.depositAmount.toInt()} (${jobSheet.paymentMode.uppercase()})
            Billing Cycle: 8:00 AM today to 8:00 AM tomorrow
            Checked Out: ${dateFormat.format(Date(jobSheet.checkoutTime))}
            Due Time: ${dateFormat.format(Date(jobSheet.dueTime))}
            Checkout By: ${jobSheet.checkoutByName.ifEmpty { jobSheet.handledByName }}
            Status: ${if (isClosed) "BILLED" else jobSheet.status.uppercase()}
            ----------------------------------
            *Tools for a Stronger Tomorrow*
            Contact: 9876543210
        """.trimIndent()
    }

    /**
     * Prints or saves the PDF using Android's built-in PrintManager configured to exact
     * 5.5 inches breadth x 8.5 inches length (5500 x 8500 mils) paper size.
     */
    fun printPdfFile(context: Context, file: File, jobName: String = "BABA_Bill_${file.nameWithoutExtension}") {
        val activity = context.findActivity()
        if (activity == null) {
            savePdfToDownloads(context, file)
            return
        }
        try {
            val printManager = activity.getSystemService(Context.PRINT_SERVICE) as? PrintManager
            if (printManager == null) {
                savePdfToDownloads(context, file)
                return
            }

            // 5.5 inches = 5500 mils, 8.5 inches = 8500 mils
            val billMediaSize = PrintAttributes.MediaSize(
                "BABA_BILL_5_5_X_8_5",
                "5.5\" × 8.5\" Rental Bill",
                5500,
                8500
            )
            val printAttributes = PrintAttributes.Builder()
                .setMediaSize(billMediaSize)
                .setColorMode(PrintAttributes.COLOR_MODE_COLOR)
                .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                .build()

            val adapter = object : PrintDocumentAdapter() {
                override fun onLayout(
                    oldAttributes: PrintAttributes?,
                    newAttributes: PrintAttributes?,
                    cancellationSignal: CancellationSignal?,
                    callback: LayoutResultCallback?,
                    extras: Bundle?
                ) {
                    if (cancellationSignal?.isCanceled == true) {
                        callback?.onLayoutCancelled()
                        return
                    }
                    val info = PrintDocumentInfo.Builder(file.name)
                        .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                        .setPageCount(1)
                        .build()
                    callback?.onLayoutFinished(info, true)
                }

                override fun onWrite(
                    pages: Array<out PageRange>?,
                    destination: ParcelFileDescriptor?,
                    cancellationSignal: CancellationSignal?,
                    callback: WriteResultCallback?
                ) {
                    if (destination == null) {
                        callback?.onWriteFailed("No destination file descriptor")
                        return
                    }
                    try {
                        FileInputStream(file).use { input ->
                            FileOutputStream(destination.fileDescriptor).use { output ->
                                input.copyTo(output)
                            }
                        }
                        callback?.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                    } catch (e: Exception) {
                        callback?.onWriteFailed(e.message)
                    }
                }
            }

            printManager.print(jobName, adapter, printAttributes)
        } catch (e: Exception) {
            Toast.makeText(context, "Print preview unavailable, saving PDF to Downloads instead.", Toast.LENGTH_SHORT).show()
            savePdfToDownloads(context, file)
        }
    }

    /**
     * Shares any generated PDF file via system share sheet (WhatsApp, Email, Drive, etc.)
     * with proper FileProvider authority, ClipData MIME type, and explicit read permissions.
     */
    fun sharePdfFile(
        context: Context,
        file: File,
        subject: String,
        text: String
    ) {
        try {
            val activity = context.findActivity()
            val startCtx: Context = activity ?: context
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_TEXT, text)
                clipData = ClipData.newUri(context.contentResolver, file.name, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            grantUriPermissionToTargets(context, intent, uri)

            val chooser = Intent.createChooser(intent, "Send / Share Bill (5.5\" × 8.5\" PDF)").apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                if (activity == null) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            startCtx.startActivity(chooser)
        } catch (e: Exception) {
            Toast.makeText(context, "Unable to open share menu: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Sends the 5.5" x 8.5" Bill directly to the customer (WhatsApp with PDF if installed,
     * otherwise SMS / system Share Sheet).
     */
    fun sendBillToCustomer(context: Context, jobSheet: JobSheetEntity) {
        try {
            val activity = context.findActivity()
            val startCtx: Context = activity ?: context
            val file = generateJobSheetPdf(context, jobSheet)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val summaryText = buildReceiptSummaryText(jobSheet)
            val cleanPhone = jobSheet.customerPhone.filter { it.isDigit() }.takeLast(10)

            // Check if WhatsApp or WhatsApp Business is installed
            val pm = context.packageManager
            val whatsappPkg = listOf("com.whatsapp", "com.whatsapp.w4b").firstOrNull { pkg ->
                try {
                    pm.getPackageInfo(pkg, 0)
                    true
                } catch (_: Exception) {
                    false
                }
            }

            if (whatsappPkg != null) {
                context.grantUriPermission(whatsappPkg, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                val waIntent = Intent(Intent.ACTION_SEND).apply {
                    `package` = whatsappPkg
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_TEXT, summaryText)
                    if (cleanPhone.length == 10) {
                        putExtra("jid", "91$cleanPhone@s.whatsapp.net")
                    }
                    clipData = ClipData.newUri(context.contentResolver, file.name, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    if (activity == null) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startCtx.startActivity(waIntent)
                return
            }

            // Fallback: Share PDF via system chooser (which also includes SMS/Email/Drive/Bluetooth)
            sharePdfFile(
                context = context,
                file = file,
                subject = "BABA Traders Rental Bill - ${jobSheet.id}",
                text = summaryText
            )
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to send bill: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Opens a generated PDF file in an external PDF viewer app if installed,
     * or falls back to System Print / Save-as-PDF (5.5" x 8.5") if no external viewer exists.
     */
    fun openPdfExternally(
        context: Context,
        file: File,
        subject: String,
        text: String
    ) {
        try {
            val activity = context.findActivity()
            val startCtx: Context = activity ?: context
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                clipData = ClipData.newUri(context.contentResolver, file.name, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                if (activity == null) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }

            val handlers = context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            if (handlers.isNotEmpty()) {
                grantUriPermissionToTargets(context, intent, uri)
                startCtx.startActivity(Intent.createChooser(intent, "Open 5.5\" × 8.5\" PDF Bill"))
            } else {
                // No external PDF app installed (e.g., on emulator) -> Open native 5.5" x 8.5" Print / Save as PDF preview
                printPdfFile(context, file)
            }
        } catch (e: ActivityNotFoundException) {
            printPdfFile(context, file)
        } catch (e: Exception) {
            printPdfFile(context, file)
        }
    }

    /**
     * Saves the PDF file directly to the device's public Downloads folder.
     */
    fun savePdfToDownloads(context: Context, file: File): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, file.name)
                    put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/BABA_Traders")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                    ?: throw IllegalStateException("Could not create file in Downloads")
                resolver.openOutputStream(uri)?.use { outStream ->
                    FileInputStream(file).use { inStream ->
                        inStream.copyTo(outStream)
                    }
                }
                contentValues.clear()
                contentValues.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)
                Toast.makeText(context, "Bill saved to Downloads/BABA_Traders/${file.name}", Toast.LENGTH_LONG).show()
                true
            } else {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!downloadsDir.exists()) downloadsDir.mkdirs()
                val destFile = File(downloadsDir, file.name)
                file.copyTo(destFile, overwrite = true)
                Toast.makeText(context, "Bill saved to Downloads/${file.name}", Toast.LENGTH_LONG).show()
                true
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to save PDF: ${e.message}", Toast.LENGTH_LONG).show()
            false
        }
    }

    /**
     * Shares a Job Sheet PDF via system share sheet (WhatsApp, Email, etc.).
     */
    fun shareJobSheetPdf(context: Context, jobSheet: JobSheetEntity) {
        try {
            val file = generateJobSheetPdf(context, jobSheet)
            sharePdfFile(
                context = context,
                file = file,
                subject = "BABA Traders Rental Invoice - ${jobSheet.id}",
                text = buildReceiptSummaryText(jobSheet)
            )
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to share PDF: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Opens Job Sheet PDF in the built-in high-resolution In-App PDF Viewer
     * (with Share, Download, Print 5.5"x8.5", Zoom, and External App options).
     */
    fun viewJobSheetPdf(context: Context, jobSheet: JobSheetEntity) {
        try {
            val file = generateJobSheetPdf(context, jobSheet)
            _activePdfPreview.value = PdfPreviewRequest(
                file = file,
                title = "Job Sheet Invoice • ${jobSheet.id}",
                subtitle = "5.5\" × 8.5\" Bill • ${jobSheet.customerName} • ${jobSheet.cleanMachineName}",
                shareSubject = "BABA Traders Rental Invoice - ${jobSheet.id}",
                shareText = buildReceiptSummaryText(jobSheet)
            )
        } catch (e: Exception) {
            Toast.makeText(context, "Unable to generate PDF preview: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Shares Financial Performance PDF Report.
     */
    fun shareReportPdf(
        context: Context,
        jobSheets: List<JobSheetEntity>,
        payments: List<PaymentEntity>,
        generatedByName: String,
        periodTitle: String? = null
    ) {
        try {
            val file = generateReportPdf(context, jobSheets, payments, generatedByName, periodTitle)
            sharePdfFile(
                context = context,
                file = file,
                subject = "BABA Traders Financial Performance Report",
                text = "Financial & Rental Audit Statement (5.5\" × 8.5\") from BABA Traders & Engineering Hardwares."
            )
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to share report PDF: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Opens Financial Performance PDF Report in the built-in In-App PDF Viewer.
     */
    fun viewReportPdf(
        context: Context,
        jobSheets: List<JobSheetEntity>,
        payments: List<PaymentEntity>,
        generatedByName: String,
        periodTitle: String? = null
    ) {
        try {
            val file = generateReportPdf(context, jobSheets, payments, generatedByName, periodTitle)
            _activePdfPreview.value = PdfPreviewRequest(
                file = file,
                title = periodTitle ?: "Financial & Rental Audit PDF",
                subtitle = "5.5\" × 8.5\" • Generated by $generatedByName",
                shareSubject = "BABA Traders Financial Performance Report",
                shareText = "Financial & Rental Audit Statement (5.5\" × 8.5\") from BABA Traders & Engineering Hardwares."
            )
        } catch (e: Exception) {
            Toast.makeText(context, "Unable to open report PDF: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
