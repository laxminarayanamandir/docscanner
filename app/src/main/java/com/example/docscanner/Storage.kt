package com.example.docscanner

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.pdf.PdfDocument
import android.provider.MediaStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Storage {
    private fun stamp() = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())

    /** Saves to Pictures/DocScanner. */
    fun saveJpeg(ctx: Context, bmp: Bitmap): Boolean = runCatching {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "Scan_${stamp()}.jpg")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/DocScanner")
        }
        val uri = ctx.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)!!
        ctx.contentResolver.openOutputStream(uri)!!.use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
    }.isSuccess

    /** Saves a single-page PDF to Download/DocScanner. */
    fun savePdf(ctx: Context, bmp: Bitmap): Boolean = runCatching {
        val pageW = 595
        val pageH = (pageW.toFloat() * bmp.height / bmp.width).toInt().coerceAtLeast(1)
        val pdf = PdfDocument()
        val page = pdf.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, 1).create())
        page.canvas.drawBitmap(bmp, null, Rect(0, 0, pageW, pageH), Paint(Paint.FILTER_BITMAP_FLAG))
        pdf.finishPage(page)

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "Scan_${stamp()}.pdf")
            put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/DocScanner")
        }
        val uri = ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)!!
        ctx.contentResolver.openOutputStream(uri)!!.use { pdf.writeTo(it) }
        pdf.close()
    }.isSuccess
}
