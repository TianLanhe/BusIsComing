package com.golink.busiscoming.ui.main

import android.content.Context
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.golink.busiscoming.R

enum class TransitCodeShortcutRequestResult {
    REQUESTED,
    UNSUPPORTED,
    FAILED
}

object TransitCodeShortcutManager {
    private const val SHORTCUT_ID = "transit_code"

    fun requestPinnedShortcut(context: Context): TransitCodeShortcutRequestResult {
        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(context)) {
            return TransitCodeShortcutRequestResult.UNSUPPORTED
        }
        val shortcut = ShortcutInfoCompat.Builder(context, SHORTCUT_ID)
            .setShortLabel(context.getString(R.string.transit_code))
            .setLongLabel(context.getString(R.string.transit_code_shortcut_long_label))
            .setIcon(IconCompat.createWithResource(context, R.drawable.ic_transit_code))
            .setIntent(TransitCodeEntryPoint.createIntent(context))
            .build()
        return try {
            if (ShortcutManagerCompat.requestPinShortcut(context, shortcut, null)) {
                TransitCodeShortcutRequestResult.REQUESTED
            } else {
                TransitCodeShortcutRequestResult.FAILED
            }
        } catch (_: RuntimeException) {
            TransitCodeShortcutRequestResult.FAILED
        }
    }
}
