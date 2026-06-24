package com.example.busiscoming.ui.main

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import com.example.busiscoming.data.model.TransitCodeLaunchTarget

class TransitCodeLauncher(
    private val startUri: (String) -> Unit
) {
    fun launch(target: TransitCodeLaunchTarget): TransitCodeLaunchResult {
        return try {
            startUri(target.uri)
            TransitCodeLaunchResult.SUCCESS
        } catch (_: ActivityNotFoundException) {
            TransitCodeLaunchResult.UNAVAILABLE
        } catch (_: SecurityException) {
            TransitCodeLaunchResult.UNAVAILABLE
        } catch (_: Throwable) {
            TransitCodeLaunchResult.UNEXPECTED_ERROR
        }
    }

    companion object {
        fun forActivity(activity: Activity): TransitCodeLauncher {
            return TransitCodeLauncher { uri ->
                activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)))
            }
        }
    }
}

enum class TransitCodeLaunchResult {
    SUCCESS,
    UNAVAILABLE,
    UNEXPECTED_ERROR
}
