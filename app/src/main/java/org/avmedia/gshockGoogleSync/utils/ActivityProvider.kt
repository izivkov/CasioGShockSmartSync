package org.avmedia.gshockGoogleSync.utils

import android.app.Activity
import android.app.Application
import android.os.Bundle
import java.lang.ref.WeakReference

object ActivityProvider {
    private var currentActivity: WeakReference<Activity>? = null

    fun initialize(application: Application) {
        application.registerActivityLifecycleCallbacks(object :
            Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                currentActivity = WeakReference(activity)
            }

            override fun onActivityStarted(activity: Activity) {
                currentActivity = WeakReference(activity)
            }

            override fun onActivityResumed(activity: Activity) {
                currentActivity = WeakReference(activity)
            }

            override fun onActivityPaused(activity: Activity) {
                // Needed to satisfy Interface, but not used here
            }
            override fun onActivityStopped(activity: Activity) {
                // Needed to satisfy Interface, but not used here
            }

            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
                // Needed to satisfy Interface, but not used here
            }

            override fun onActivityDestroyed(activity: Activity) {
                if (currentActivity?.get() == activity) {
                    currentActivity = null
                }
            }
        })
    }

    fun getCurrentActivity(): Activity? = currentActivity?.get()
}