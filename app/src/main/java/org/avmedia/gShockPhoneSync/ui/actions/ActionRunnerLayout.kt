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

package org.avmedia.gShockPhoneSync.ui.actions

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import org.avmedia.gShockPhoneSync.IHideableLayout
import org.avmedia.gShockPhoneSync.MainActivity.Companion.api
import org.avmedia.gshockapi.ProgressEvents
import timber.log.Timber

class ActionRunnerLayout @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr), IHideableLayout {

    init {
        hide()
        createAppEventsSubscription()
    }

    private fun createAppEventsSubscription(): Disposable =
        ProgressEvents.connectionEventsFlowable
            .observeOn(AndroidSchedulers.mainThread())
            .doOnNext {
                when (it) {
                    ProgressEvents["ButtonPressedInfoReceived"] -> {
                        ActionsModel.loadData(context)

                        if (api().isActionButtonPressed()) {
                            show()
                            ActionsModel.runActions(context)
                        } else if (api().isAutoTimeStarted()) {
                            ActionsModel.runActionsForAutoTimeSetting(context)
                        }
                    }
                    ProgressEvents["Disconnect"] -> {
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
