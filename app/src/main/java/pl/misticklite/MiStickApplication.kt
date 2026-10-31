package pl.misticklite

import android.app.Activity
import android.app.Application
import android.os.Bundle

/** Tracks only our own activity lifecycle; no window content or other apps are queried. */
class MiStickApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (!BuildConfig.DEBUG) return
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, state: Bundle?) { if (activity is MainActivity) HomeRedirectControl.created(activity) }
            override fun onActivityResumed(activity: Activity) { if (activity is MainActivity) HomeRedirectControl.resumed(activity) }
            override fun onActivityPaused(activity: Activity) { if (activity is MainActivity) HomeRedirectControl.paused(activity) }
            override fun onActivityDestroyed(activity: Activity) { if (activity is MainActivity) HomeRedirectControl.destroyed(activity) }
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) = Unit
        })
    }
}
