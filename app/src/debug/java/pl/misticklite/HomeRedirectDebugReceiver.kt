package pl.misticklite

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Explicit ordered ADB broadcast; never enables accessibility or changes default HOME. */
class HomeRedirectDebugReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!BuildConfig.DEBUG || intent.action != HomeRedirectControl.ACTION) {
            resultCode = 1
            resultData = "ok=false error=invalid_action"
            return
        }
        val (success, status) = HomeRedirectControl.command(intent.getStringExtra("command") ?: "status")
        resultCode = if (success) 0 else 1
        resultData = "ok=$success $status"
    }
}
