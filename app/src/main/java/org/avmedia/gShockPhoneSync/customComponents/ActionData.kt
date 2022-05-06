/*
 * Created by Ivo Zivkov (izivkov@gmail.com) on 2022-03-30, 12:06 a.m.
 * Copyright (c) 2022 . All rights reserved.
 * Last modified 2022-03-20, 7:47 p.m.
 */

package org.avmedia.gShockPhoneSync.customComponents

import com.google.gson.Gson
import timber.log.Timber

object ActionData {

    open class Action(open override var title: String, open override var enabled: Boolean) : IAction {
        override fun run() {
            TODO("Not yet implemented")
        }

        override fun getId(): Int {
            TODO("Not yet implemented")
        }
    }

    class PhoneDialAction(
        override var title: String,
        override var enabled: Boolean,
        val phoneNumber: String
    ) : Action(title, enabled) {
        init {
            Timber.d("PhoneDialAction")
        }
    }

    val actions = ArrayList<IAction>()

    init {
        actions.add(Action("Set Time", true))
        actions.add(Action("Save current location to Google maps", false))
        actions.add(Action("Take a photo", true))
        actions.add(PhoneDialAction("Make a phone call", true, "416-555-3045"))
    }

    fun clear() {
        actions.clear()
    }

    fun isEmpty(): Boolean {
        return actions.size == 0
    }

    @Synchronized
    fun fromJson(jsonStr: String) {
        val gson = Gson()
        val actionStr = gson.fromJson(jsonStr, Array<Action>::class.java)
        actions.addAll(actionStr)
    }

    @Synchronized
    fun toJson(): String {
        val gson = Gson()
        return gson.toJson(actions)
    }
}