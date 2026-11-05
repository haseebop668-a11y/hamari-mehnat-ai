package com.hamari.mehenat.jobhunter.data

import android.content.Context
import android.util.Xml
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

data class TextExtractionResult(
    val text: String,
    val format: String,
    val warning: String? = null
)

class DocumentTextExtractor(context: Context) {
    init { PDFBoxResourceLoader.init(context.applicationContext) }

    fun extract(fileName: String, bytes: ByteArray): TextExtractionResult {
        require(bytes.isNotEmpty()) { "Document is empty." }
        require(bytes.size <= 25 * 1024 * 1024) { "Document exceeds the 25 MB limit." }
        val lower = fileName.lowercase()
        val text = when {
            lower.endsWith(".pdf") -> extractPdf(bytes)
            lower.endsWith(".docx") -> extractDocx(bytes)
            lower.endsWith(".txt") -> String(bytes, Charsets.UTF_8)
            else -> error("Text extraction supports PDF, DOCX and TXT. Images are stored securely but OCR is not enabled.")
        }.replace(Regex("[\\t\\r ]+"), " ")
            .replace(Regex("\\n{3,}"), "\\n\\n")
            .trim()
            .take(MAX_TEXT_CHARS)

        require(text.isNotBlank()) {
            "No selectable text found. This may be a scanned PDF; OCR is not enabled in this build."
        }
        return TextExtractionResult(text, lower.substringAfterLast('.').uppercase())
    }

    private fun extractPdf(bytes: ByteArray): String =
        PDDocument.load(ByteArrayInputStream(bytes)).use { document ->
            PDFTextStripper().getText(document)
        }

    private fun extractDocx(bytes: ByteArray): String {
        var documentXml: ByteArray? = null
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.name == "word/document.xml") {
                    documentXml = zip.readBytes()
                    break
                }
                zip.closeEntry()
            }
        }
        val xml = documentXml ?: error("DOCX package does not contain word/document.xml.")
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(ByteArrayInputStream(xml), "UTF-8")
        val output = StringBuilder()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name == "t") {
                output.append(parser.nextText()).append(' ')
            } else if (event == XmlPullParser.END_TAG && parser.name == "p") {
                output.append('\n')
            }
            event = parser.next()
        }
        return output.toString()
    }

    companion object { private const val MAX_TEXT_CHARS = 50_000 }
}
