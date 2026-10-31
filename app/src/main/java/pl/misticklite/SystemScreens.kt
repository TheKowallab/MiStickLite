package pl.misticklite

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.widget.Toast

object SafeLaunch {
    @Suppress("DEPRECATION")
    fun first(context: Context, candidates: List<Intent>): Boolean {
        for (candidate in candidates) {
            try {
                val resolved = context.packageManager.resolveActivity(candidate, PackageManager.MATCH_DEFAULT_ONLY) ?: continue
                val info = resolved.activityInfo ?: continue
                if (!info.exported || !info.enabled || !info.applicationInfo.enabled) continue
                context.startActivity(Intent(candidate).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return true
            } catch (_: ActivityNotFoundException) {
                // Firmware variants and package changes are expected.
            } catch (_: SecurityException) {
                // Some OEM activities resolve but are protected from third party apps.
            }
        }
        return false
    }
}

class SystemScreens(private val context: Context) {
    fun account() {
        val candidates = mutableListOf(Intent(Settings.ACTION_SYNC_SETTINGS))
        for (pkg in listOf("com.android.tv.settings", "com.google.android.tv.settings")) {
            for (name in listOf("com.android.tv.settings.accounts.AccountsActivity",
                "com.google.android.tv.settings.AccountActivity")) {
                candidates += Intent().setComponent(ComponentName(pkg, name))
            }
        }
        // USER_SETTINGS is used for accounts on some TV settings implementations.
        candidates += Intent("android.settings.USER_SETTINGS")
        if (SafeLaunch.first(context, candidates)) return
        toast(R.string.settings_fallback)
        settings()
    }

    fun settings() {
        val candidates = mutableListOf(Intent(Settings.ACTION_SETTINGS),
            Intent(Intent.ACTION_MAIN).addCategory("android.intent.category.LEANBACK_SETTINGS"))
        for (pkg in listOf("com.android.tv.settings", "com.google.android.tv.settings", "com.android.settings")) {
            context.packageManager.getLeanbackLaunchIntentForPackage(pkg)?.let(candidates::add)
            context.packageManager.getLaunchIntentForPackage(pkg)?.let(candidates::add)
        }
        if (!SafeLaunch.first(context, candidates)) toast(R.string.screen_unavailable)
    }

    fun store() {
        val pm = context.packageManager
        val candidates = listOfNotNull(
            pm.getLeanbackLaunchIntentForPackage("com.android.vending"),
            pm.getLaunchIntentForPackage("com.android.vending"),
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MARKET).setPackage("com.android.vending"),
            Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=streaming&c=apps")).setPackage("com.android.vending")
        )
        if (SafeLaunch.first(context, candidates)) return
        toast(R.string.store_fallback)
        if (!SafeLaunch.first(context, listOf(Intent(Settings.ACTION_APPLICATION_SETTINGS)))) settings()
    }

    private fun toast(message: Int) = Toast.makeText(context, message, Toast.LENGTH_LONG).show()
}
