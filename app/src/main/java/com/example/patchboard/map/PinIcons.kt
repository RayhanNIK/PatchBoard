package com.example.patchboard.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory

/**
 * Round map pins drawn in code, so a pin can use the exact priority colour of the app.
 * Only call [pin] once a map is ready (the Maps SDK must be initialised).
 */
object PinIcons {
    private val cache = HashMap<Int, BitmapDescriptor>()

    fun pin(context: Context, color: Int): BitmapDescriptor =
        cache.getOrPut(color) { BitmapDescriptorFactory.fromBitmap(draw(context, color)) }

    private fun draw(context: Context, color: Int): Bitmap {
        val d = context.resources.displayMetrics.density
        val size = (32 * d).toInt()
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val cx = size / 2f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        paint.color = Color.argb(70, 0, 0, 0)            // soft shadow
        c.drawCircle(cx, cx + 1.5f * d, 12 * d, paint)
        paint.color = Color.WHITE                        // white ring
        c.drawCircle(cx, cx, 12 * d, paint)
        paint.color = color                              // priority colour
        c.drawCircle(cx, cx, 9.5f * d, paint)
        paint.color = Color.WHITE                        // centre dot
        c.drawCircle(cx, cx, 3.2f * d, paint)
        return bmp
    }
}
