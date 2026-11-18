package dev.notionfontnav.extension

import android.os.Build
import android.view.View
import android.view.WindowInsets

object BottomSafeArea {
    // The reference Galaxy's navigation hint reserves 15 dp (53 px at 560 dpi).
    private const val HINT_INSET_DP = 15f

    @JvmStatic
    fun toolbarPadding(view: View?, originalDp: Float): Float {
        if (view == null || !isSamsung()) return originalDp
        val insets = view.rootWindowInsets ?: return originalDp
        val navigation = WindowInsets.Type.navigationBars()
        val visible = insets.getInsets(navigation)
        val stable = insets.getInsetsIgnoringVisibility(navigation)
        val ime = WindowInsets.Type.ime()
        if (visible.bottom > 0 || stable.left > 0 || stable.right > 0 ||
            insets.isVisible(ime) || insets.getInsets(ime).bottom > 0
        ) return originalDp

        val gestures = WindowInsets.Type.systemGestures() or WindowInsets.Type.mandatorySystemGestures()
        val gestureBottom = insets.getInsets(gestures).bottom
        if (gestureBottom <= 0) return originalDp

        val density = view.resources.displayMetrics.density
        val hintPixels = Math.round(HINT_INSET_DP * density)
        val restore = if (stable.bottom > 0) stable.bottom else hintPixels
        // Round both terms separately to preserve Notion's existing 4 dp padding at fractional densities.
        return (Math.round(originalDp * density) + minOf(restore, gestureBottom)) / density
    }

    private fun isSamsung(): Boolean {
        val manufacturer = Build.MANUFACTURER ?: return false
        return java.lang.String.CASE_INSENSITIVE_ORDER.compare("samsung", manufacturer) == 0
    }
}
