/*
 * Created by Ivo Zivkov (izivkov@gmail.com) on 2022-03-30, 12:06 a.m.
 * Copyright (c) 2022 . All rights reserved.
 * Last modified 2022-03-29, 11:56 a.m.
 */

/*
 * Developed by:
 *
 * Ivo Zivkov
 * izivkov@gmail.com
 *
 * Date: 2020-12-27, 10:58 p.m.
 */

package org.avmedia.gShockPhoneSync.customComponents

import android.app.Activity
import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.navigation.findNavController
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import org.avmedia.gShockPhoneSync.IHideableLayout
import org.avmedia.gShockPhoneSync.R
import org.avmedia.gShockPhoneSync.casioB5600.CasioSupport
import org.avmedia.gShockPhoneSync.utils.ProgressEvents
import timber.log.Timber

class MainLayout @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr), IHideableLayout {

    init {
        // INZ temp
        // hide()
        show()
        // INZ end

        createAppEventsSubscription()
    }

    private fun createAppEventsSubscription(): Disposable =
        ProgressEvents.connectionEventFlowable
            .observeOn(AndroidSchedulers.mainThread())
            .doOnNext {
                when (it) {
                    ProgressEvents.Events.WatchInitializationCompleted -> {
                        val navController =
                            (context as Activity).findNavController(R.id.nav_host_fragment_activity_gshock_screens)
                        val watchButtonPressed = CasioSupport.getPressedWatchButton()
                        if (watchButtonPressed == CasioSupport.WATCH_BUTTON.LOWER_RIGHT) {
                            navController.navigate(org.avmedia.gShockPhoneSync.R.id.navigation_actions)
                        } else {
                            navController.navigate(org.avmedia.gShockPhoneSync.R.id.navigation_home)
                        }

                        show()
                    }
                    ProgressEvents.Events.Disconnect -> {
                        hide()
                    }
                }
            }
            .subscribe(
                { },
                { throwable -> Timber.i("Got error on subscribe: $throwable") })

    override fun show() {
        visibility = View.VISIBLE
    }

    override fun hide() {
        visibility = View.GONE
    }
}
