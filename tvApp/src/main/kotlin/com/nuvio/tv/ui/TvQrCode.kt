package com.nuvio.tv.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

/** QR code with rounded modules on a white rounded background (ported from NuvioTV). */
object TvQrCodeGenerator {
    fun generate(content: String, size: Int = 512, margin: Int = 2): Bitmap {
        val bitMatrix = QRCodeWriter().encode(
            content,
            BarcodeFormat.QR_CODE,
            size,
            size,
            mapOf(EncodeHintType.MARGIN to margin.coerceAtLeast(0)),
        )
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val background = RectF(0f, 0f, size.toFloat(), size.toFloat())
        val cornerRadius = size * 0.06f
        canvas.drawRoundRect(
            background,
            cornerRadius,
            cornerRadius,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE },
        )

        // The matrix is scaled to `size`: find the module size from the first dark run.
        var firstDark = -1
        var moduleSize = 1
        loop@ for (y in 0 until size) {
            for (x in 0 until size) {
                if (bitMatrix[x, y]) {
                    firstDark = x
                    var end = x
                    while (end < size && bitMatrix[end, y]) end++
                    // Finder patterns start with a 7-module dark run.
                    moduleSize = ((end - x) / 7).coerceAtLeast(1)
                    break@loop
                }
            }
        }
        val offset = if (firstDark >= 0) firstDark % moduleSize else 0
        val foreground = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }
        val moduleRadius = moduleSize * 0.3f
        var x = offset
        while (x < size) {
            var y = offset
            while (y < size) {
                if (bitMatrix[x, y]) {
                    canvas.drawRoundRect(
                        RectF(
                            x.toFloat(),
                            y.toFloat(),
                            (x + moduleSize).toFloat().coerceAtMost(size.toFloat()),
                            (y + moduleSize).toFloat().coerceAtMost(size.toFloat()),
                        ),
                        moduleRadius,
                        moduleRadius,
                        foreground,
                    )
                }
                y += moduleSize
            }
            x += moduleSize
        }
        return bitmap
    }
}

@Composable
fun TvQrCode(content: String, contentDescription: String?, modifier: Modifier = Modifier) {
    val image = remember(content) { TvQrCodeGenerator.generate(content).asImageBitmap() }
    Image(bitmap = image, contentDescription = contentDescription, modifier = modifier)
}
