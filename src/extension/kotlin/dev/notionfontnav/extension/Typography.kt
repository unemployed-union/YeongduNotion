package dev.notionfontnav.extension

import android.graphics.Typeface
import android.os.Build

object Typography {
    // One UI maps this alias to the stock sans-serif chain, separate from the FlipFont default.
    private const val STOCK_SANS = "arial"

    @JvmStatic
    fun resourceTypeface(builder: Typeface.CustomFallbackBuilder, resourceId: Int, robotoFlexId: Int): Typeface {
        if (resourceId == robotoFlexId && isSamsung()) builder.setSystemFallback(STOCK_SANS)
        return builder.build()
    }

    @JvmStatic
    fun composeFamily(family: String?): String? {
        if (!isSamsung()) return family
        return if (family == null || family.isEmpty() || family.compareTo("sans-serif") == 0) STOCK_SANS else family
    }

    @JvmStatic
    fun nativeEditorFallback(role: String): Typeface {
        if (isSamsung() && (role.compareTo("CJK-Regular") == 0 || role.compareTo("CJK-Fallback") == 0)) {
            return Typeface.create(STOCK_SANS, Typeface.NORMAL)
        }
        return Typeface.DEFAULT
    }

    private fun isSamsung(): Boolean {
        val manufacturer = Build.MANUFACTURER ?: return false
        return java.lang.String.CASE_INSENSITIVE_ORDER.compare("samsung", manufacturer) == 0
    }
}
