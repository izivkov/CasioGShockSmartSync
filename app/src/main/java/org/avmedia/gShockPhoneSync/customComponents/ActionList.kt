/*
 * Created by Ivo Zivkov (izivkov@gmail.com) on 2022-05-06, 7:04 p.m.
 * Copyright (c) 2022 . All rights reserved.
 * Last modified 2022-05-06, 7:00 p.m.
 */
package org.avmedia.gShockPhoneSync.customComponents

import android.content.Context
import android.util.AttributeSet
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.avmedia.gShockPhoneSync.casioB5600.CasioSupport
import org.avmedia.gShockPhoneSync.utils.Utils

class ActionList @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : RecyclerView(context, attrs, defStyleAttr) {

    init {
        adapter = ActionAdapter(ActionsModel.actions)
        layoutManager = LinearLayoutManager(context)
    }

    fun init() {
        super.onAttachedToWindow()
        loadData(context)

        if (Utils.isDebugMode() || CasioSupport.isActionButtonPressed()) {
            runActions()
        }
    }

    fun shutdown() {
        super.onDetachedFromWindow()
        saveData(context)
    }

    private fun runActions() {
        ActionsModel.actions.forEach {
            if (it.enabled) {
                // Run in background for speed
                GlobalScope.launch {
                    try {
                        it.run(context)
                    } catch (e: SecurityException) {
                        Utils.snackBar(context, "You have not given permission to to run action ${it.title}.")
                    } catch (e: Exception) {
                        Utils.snackBar(context, "Could not run action ${it.title}.")
                    }
                }
            }
        }
    }

    private fun loadData(context: Context) {
        ActionsModel.actions.forEach {
            it.load(context)
        }
    }

    private fun saveData(context: Context) {
        ActionsModel.actions.forEach {
            it.save(context)
        }
    }
}
