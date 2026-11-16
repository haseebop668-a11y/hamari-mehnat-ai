package com.hamari.mehenat.jobhunter.data

import android.content.Context
import android.util.Xml
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

data class TextExtractionResult(
    val text: String,
    val format: String,
    val warning: String? = null
)

class DocumentTextExtractor(private val context: Context) {
    init { PDFBoxResourceLoader.init(context.applicationContext) }

    fun extract(fileName: String, bytes: ByteArray): TextExtractionResult {
        require(bytes.isNotEmpty()) { "Document is empty." }
        require(bytes.size <= 25 * 1024 * 1024) { "Document exceeds the 25 MB limit." }
        val lower = fileName.lowercase()
        var warning: String? = null
        val extracted = when {
            lower.endsWith(".pdf") -> {
                val selectable = extractPdf(bytes)
                if (selectable.isNotBlank()) selectable else {
                    warning = "Scanned PDF: on-device Latin-script OCR was used. Review names, numbers and dates carefully."
                    extractScannedPdf(bytes)
                }
            }
            lower.endsWith(".docx") -> extractDocx(bytes)
            lower.endsWith(".txt") -> String(bytes, Charsets.UTF_8)
            lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") -> {
                warning = "On-device Latin-script OCR was used for this image. Review names, numbers and dates carefully."
                extractImage(bytes)
            }
            else -> error("Text extraction supports PDF, DOCX, TXT, PNG and JPEG.")
        }
        val text = extracted.replace(Regex("[\\t\\r ]+"), " ")
            .replace(Regex("\\n{3,}"), "\\n\\n")
            .trim()
            .take(MAX_TEXT_CHARS)
        require(text.isNotBlank()) { "No text could be extracted or recognized from this document." }
        return TextExtractionResult(text, lower.substringAfterLast('.').uppercase(), warning)
    }

    private fun extractPdf(bytes: ByteArray): String =
        PDDocument.load(ByteArrayInputStream(bytes)).use { document ->
            PDFTextStripper().getText(document)
        }

    private fun extractScannedPdf(bytes: ByteArray): String {
        val temp = File.createTempFile("hm-ocr-", ".pdf", context.cacheDir)
        return try {
            temp.writeBytes(bytes)
            ParcelFileDescriptor.open(temp, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                PdfRenderer(descriptor).use { renderer ->
                    require(renderer.pageCount <= MAX_OCR_PAGES) {
                        "Scanned PDF has ${renderer.pageCount} pages. OCR is limited to $MAX_OCR_PAGES pages per document."
                    }
                    buildString {
                        for (index in 0 until renderer.pageCount) {
                            renderer.openPage(index).use { page ->
                                val scale = minOf(2.0f, MAX_OCR_DIMENSION.toFloat() / maxOf(page.width, page.height))
                                val width = (page.width * scale).toInt().coerceAtLeast(1)
                                val height = (page.height * scale).toInt().coerceAtLeast(1)
                                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                                try {
                                    page.render(bitmap, Rect(0, 0, width, height), null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                    append(recognize(bitmap)).append('\\n')
                                } finally { bitmap.recycle() }
                            }
                        }
                    }
                }
            }
        } finally { temp.delete() }
    }

    private fun extractImage(bytes: ByteArray): String {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Image could not be decoded." }
        var sample = 1
        while (maxOf(bounds.outWidth / sample, bounds.outHeight / sample) > MAX_OCR_DIMENSION) sample *= 2
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: error("Image could not be decoded for OCR.")
        return try { recognize(bitmap) } finally { bitmap.recycle() }
    }

    private fun recognize(bitmap: Bitmap): String {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            val latch = CountDownLatch(1)
            var recognized = ""
            var failure: Exception? = null
            recognizer.process(InputImage.fromBitmap(bitmap, 0))
                .addOnSuccessListener { result -> recognized = result.text }
                .addOnFailureListener { error -> failure = error }
                .addOnCompleteListener { latch.countDown() }
            check(latch.await(OCR_TIMEOUT_SECONDS, TimeUnit.SECONDS)) { "On-device OCR timed out." }
            failure?.let { throw IllegalStateException("On-device OCR failed: ${it.message}", it) }
            return recognized
        } finally { recognizer.close() }
    }

    private fun extractDocx(bytes: ByteArray): String {
        var documentXml: ByteArray? = null
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entriesSeen = 0
            while (true) {
                val entry = zip.nextEntry ?: break
                entriesSeen += 1
                require(entriesSeen <= MAX_DOCX_ENTRIES) { "DOCX contains too many ZIP entries." }
                if (entry.name == "word/document.xml") {
                    val output = ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    var total = 0
                    while (true) {
                        val count = zip.read(buffer)
                        if (count < 0) break
                        total += count
                        require(total <= MAX_DOCX_XML_BYTES) { "DOCX document XML exceeds the safe extraction limit." }
                        output.write(buffer, 0, count)
                    }
                    documentXml = output.toByteArray()
                    break
                }
                zip.closeEntry()
            }
        }
        val xml = documentXml ?: error("DOCX package does not contain word/document.xml.")
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL, false)
        parser.setInput(ByteArrayInputStream(xml), "UTF-8")
        val output = StringBuilder()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT && output.length < MAX_TEXT_CHARS) {
            if (event == XmlPullParser.START_TAG && parser.name.substringAfter(':') == "t") {
                output.append(parser.nextText()).append(' ')
            } else if (event == XmlPullParser.END_TAG && parser.name.substringAfter(':') == "p") {
                output.append('\n')
            }
            event = parser.next()
        }
        return output.toString().take(MAX_TEXT_CHARS)
    }

    companion object {
        private const val MAX_TEXT_CHARS = 50_000
        private const val MAX_DOCX_XML_BYTES = 8 * 1024 * 1024
        private const val MAX_DOCX_ENTRIES = 1_000
        private const val MAX_OCR_PAGES = 10
        private const val MAX_OCR_DIMENSION = 2200
        private const val OCR_TIMEOUT_SECONDS = 30L
    }
}
