package com.survival.ruler

import android.content.Context
import android.util.DisplayMetrics

object DisplayMetricsHelper {
    /**
     * Get the actual DPI of the device screen
     */
    fun getScreenDpi(context: Context): Float {
        val displayMetrics = context.resources.displayMetrics
        return displayMetrics.densityDpi.toFloat()
    }

    /**
     * Get the screen width in pixels
     */
    fun getScreenWidthPx(context: Context): Int {
        return context.resources.displayMetrics.widthPixels
    }

    /**
     * Get the screen height in pixels
     */
    fun getScreenHeightPx(context: Context): Int {
        return context.resources.displayMetrics.heightPixels
    }

    /**
     * Convert centimeters to pixels based on actual screen DPI
     * 1 inch = 2.54 cm
     * 1 inch = dpi pixels
     */
    fun cmToPx(cm: Float, dpi: Float): Float {
        return (cm / 2.54f) * (dpi / 160f)
    }

    /**
     * Convert inches to pixels based on actual screen DPI
     * 1 inch = dpi/160 * 160 = dpi pixels (at 160dpi baseline)
     */
    fun inchToPx(inches: Float, dpi: Float): Float {
        return inches * (dpi / 160f)
    }

    /**
     * Get pixels per centimeter
     */
    fun getPxPerCm(dpi: Float): Float {
        return cmToPx(1f, dpi)
    }

    /**
     * Get pixels per inch
     */
    fun getPxPerInch(dpi: Float): Float {
        return inchToPx(1f, dpi)
    }
}
