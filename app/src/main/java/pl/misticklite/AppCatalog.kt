package pl.misticklite

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager

data class TvApp(val key: AppKey, val component: ComponentName, val activityInfo: ActivityInfo)

class AppCatalog(private val context: Context) {
    private val pm = context.packageManager

    @Suppress("DEPRECATION")
    fun scan(): List<TvApp> {
        val result = LinkedHashMap<String, TvApp>()
        // LEANBACK wins per package; LAUNCHER only fills packages without a TV entry.
        for (category in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
            val matches = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(category), 0)
                .sortedBy { it.activityInfo.name }
            for (resolve in matches) {
                val info = resolve.activityInfo ?: continue
                val pkg = info.packageName
                if (pkg == context.packageName || pkg in result || !info.enabled || !info.exported ||
                    !info.applicationInfo.enabled) continue
                val permission = info.permission
                if (permission != null && pm.checkPermission(permission, context.packageName) != PackageManager.PERMISSION_GRANTED) continue
                try {
                    val installedAt = pm.getPackageInfo(pkg, 0).firstInstallTime
                    val label = resolve.loadLabel(pm).toString().takeIf { it.isNotBlank() } ?: pkg
                    result[pkg] = TvApp(AppKey(pkg, label, installedAt), ComponentName(pkg, info.name), info)
                } catch (_: PackageManager.NameNotFoundException) {
                    // An uninstall can race the scan. The next refresh reconciles the list.
                }
            }
        }
        return result.values.toList()
    }

    fun launch(app: TvApp): Boolean {
        val candidates = listOfNotNull(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER).setComponent(app.component),
            pm.getLeanbackLaunchIntentForPackage(app.key.packageName),
            pm.getLaunchIntentForPackage(app.key.packageName)
        )
        return SafeLaunch.first(context, candidates)
    }
}
