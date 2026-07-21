package com.golink.busiscoming.ui.main

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.golink.busiscoming.R

enum class TransitCodeShortcutState {
    PINNED,
    NOT_PINNED,
    UNKNOWN
}

enum class TransitCodeShortcutRequestResult {
    ALREADY_PINNED,
    REQUESTED,
    UNSUPPORTED,
    FAILED
}

object TransitCodeShortcutManager {
    private const val SHORTCUT_ID = "transit_code"
    internal const val ACTION_PINNED =
        "com.golink.busiscoming.action.TRANSIT_CODE_SHORTCUT_PINNED"

    fun currentState(context: Context): TransitCodeShortcutState {
        return try {
            val isPinned = ShortcutManagerCompat.getShortcuts(
                context,
                ShortcutManagerCompat.FLAG_MATCH_PINNED
            ).any { it.id == SHORTCUT_ID }
            if (isPinned) TransitCodeShortcutState.PINNED else TransitCodeShortcutState.NOT_PINNED
        } catch (_: RuntimeException) {
            TransitCodeShortcutState.UNKNOWN
        }
    }

    fun requestPinnedShortcut(context: Context): TransitCodeShortcutRequestResult {
        if (currentState(context) == TransitCodeShortcutState.PINNED) {
            return TransitCodeShortcutRequestResult.ALREADY_PINNED
        }
        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(context)) {
            return TransitCodeShortcutRequestResult.UNSUPPORTED
        }
        val shortcut = ShortcutInfoCompat.Builder(context, SHORTCUT_ID)
            .setShortLabel(context.getString(R.string.transit_code))
            .setLongLabel(context.getString(R.string.transit_code_shortcut_long_label))
            .setIcon(IconCompat.createWithResource(context, R.drawable.ic_transit_code))
            .setIntent(TransitCodeEntryPoint.createIntent(context))
            .build()
        val callbackIntent = Intent(context, TransitCodeShortcutPinnedReceiver::class.java)
            .setAction(ACTION_PINNED)
        val callback = PendingIntent.getBroadcast(
            context,
            0,
            callbackIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return try {
            if (ShortcutManagerCompat.requestPinShortcut(context, shortcut, callback.intentSender)) {
                TransitCodeShortcutRequestResult.REQUESTED
            } else {
                TransitCodeShortcutRequestResult.FAILED
            }
        } catch (_: RuntimeException) {
            TransitCodeShortcutRequestResult.FAILED
        }
    }
}

class TransitCodeShortcutPinnedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != TransitCodeShortcutManager.ACTION_PINNED) return
        Toast.makeText(context, R.string.transit_code_shortcut_added, Toast.LENGTH_SHORT).show()
    }
}
