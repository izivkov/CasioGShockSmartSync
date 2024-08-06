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
import org.avmedia.gShockPhoneSync.MainActivity.Companion.api
import org.avmedia.gShockPhoneSync.utils.IHideableLayout
import org.avmedia.gshockapi.EventAction
import org.avmedia.gshockapi.ProgressEvents

class ActionRunnerLayout @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr), IHideableLayout {

    init {
        hide()
        createAppEventsSubscription()
    }

    private fun createAppEventsSubscription() {

        val eventActions = arrayOf(
            EventAction("RunActions") {
                ActionsModel.loadData(context)
                ActionsModel.runActionsForActionButton(context)
            },

            /*
            1. If we pressed the Action Button, run just the selected actions.
            2. If we connected via AutoTime (without pressing a button), run TimeSet action, plus OnConnectActions actions:
               currently, Google Calender Events, and Prayer Alarms.
            3. We we connected by long-pressing the lower-left button, run OnConnectActions, and SetTime if this is always-connected watch
               like the ECB-30.
            **/
            EventAction("ButtonPressedInfoReceived") {
                ActionsModel.loadData(context)

                if (api().isActionButtonPressed()) {
                    show()
                    ActionsModel.runActionsForActionButton(context)
                } else if (api().isAutoTimeStarted()) {
                    ActionsModel.runActionsForAutoTimeSetting(context)
                } else if (api().isFindPhoneButtonPressed()) {
                    show()
                    ActionsModel.runActionFindPhone(context)
                } else if (api().isNormalButtonPressed()) {
                    ActionsModel.runActionForConnection(context)
                }
            },
            EventAction("Disconnect") {
                hide()
            },
        )

        ProgressEvents.runEventActions(this.javaClass.name, eventActions)
    }

    override fun show() {
        visibility = View.VISIBLE
    }

    override fun hide() {
        visibility = View.GONE
    }
}
