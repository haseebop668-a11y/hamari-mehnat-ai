package com.hamari.mehenat.jobhunter.data

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import java.io.OutputStream

/**
 * Creates a simple, readable, multi-page PDF from user-reviewed application text.
 * It does not certify the truth or completeness of AI-generated content.
 */
object ApplicationPdfGenerator {
    fun write(
        context: Context,
        destination: Uri,
        documentTitle: String,
        bodyText: String,
        documentLabel: String = "USER-REVIEWED APPLICATION DRAFT"
    ) {
        require(documentTitle.isNotBlank()) { "Document title is required." }
        require(bodyText.isNotBlank()) { "There is no text to export." }
        require(bodyText.length <= MAX_CHARS) { "Draft is too long to export safely." }

        val document = PdfDocument()
        try {
            val output = context.contentResolver.openOutputStream(destination)
                ?: error("Android could not open the selected PDF destination.")
            output.use { stream ->
                val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = 0xFF18212B.toInt()
                    textSize = 22f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                }
                val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = 0xFF8A6A2D.toInt()
                    textSize = 8f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                }
                val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = 0xFF202832.toInt()
                    textSize = 10.5f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                }
                val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = 0xFF7B8794.toInt()
                    textSize = 8f
                }
                val headingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = 0xFF18212B.toInt()
                    textSize = 12f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                }

                val pageWidth = 595
                val pageHeight = 842
                val left = 48f
                val right = 547f
                val top = 52f
                val bottom = 790f
                val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = 0xFFD6B36A.toInt()
                    strokeWidth = 1.5f
                }

                var pageNumber = 0
                var page: PdfDocument.Page? = null
                var canvas: android.graphics.Canvas? = null
                var y = top

                fun startPage() {
                    y = top
                    pageNumber += 1
                    page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
                    canvas = page!!.canvas
                    canvas!!.drawText(documentLabel.uppercase(), left, y, labelPaint)
                    y += 30f
                    canvas!!.drawText(documentTitle.take(90), left, y, titlePaint)
                    y += 18f
                    canvas!!.drawLine(left, y, right, y, linePaint)
                    y += 28f
                }

                fun finishPage() {
                    val active = canvas ?: return
                    active.drawLine(left, 812f, right, 812f, linePaint)
                    active.drawText("Prepared on this device • Review every detail before sending", left, 827f, footerPaint)
                    active.drawText("Page $pageNumber", right - 42f, 827f, footerPaint)
                    document.finishPage(page!!)
                    page = null
                    canvas = null
                }

                fun ensureSpace(required: Float) {
                    if (page == null) startPage()
                    if (y + required > bottom) {
                        finishPage()
                        startPage()
                    }
                }

                fun drawWrapped(text: String, paint: Paint, lineHeight: Float, indent: Float = 0f) {
                    val available = right - left - indent
                    val words = text.trim().split(Regex("\\s+")).filter(String::isNotBlank)
                    if (words.isEmpty()) {
                        y += lineHeight
                        return
                    }
                    var line = StringBuilder()
                    for (word in words) {
                        val candidate = if (line.isEmpty()) word else "$line $word"
                        if (paint.measureText(candidate) > available && line.isNotEmpty()) {
                            ensureSpace(lineHeight)
                            canvas!!.drawText(line.toString(), left + indent, y, paint)
                            y += lineHeight
                            line = StringBuilder(word)
                        } else {
                            line = StringBuilder(candidate)
                        }
                    }
                    if (line.isNotEmpty()) {
                        ensureSpace(lineHeight)
                        canvas!!.drawText(line.toString(), left + indent, y, paint)
                        y += lineHeight
                    }
                }

                startPage()
                bodyText.replace("\r", "").split("\n").forEach { rawLine ->
                    val line = rawLine.trim()
                    if (line.isBlank()) {
                        y += 7f
                    } else if (line.startsWith("# ")) {
                        ensureSpace(25f)
                        y += 8f
                        drawWrapped(line.removePrefix("# ").uppercase(), headingPaint, 17f)
                        y += 3f
                    } else if (line.startsWith("## ")) {
                        ensureSpace(22f)
                        y += 5f
                        drawWrapped(line.removePrefix("## "), headingPaint, 16f)
                        y += 2f
                    } else {
                        val paragraph = line.removePrefix("- ").removePrefix("• ")
                        drawWrapped(paragraph, bodyPaint, 15f, if (line.startsWith("- ") || line.startsWith("• ")) 10f else 0f)
                    }
                }
                finishPage()
                document.writeTo(stream)
            }
        } finally {
            document.close()
        }
    }

    private const val MAX_CHARS = 60_000
}
