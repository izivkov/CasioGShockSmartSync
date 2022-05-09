/*
 * Created by Ivo Zivkov (izivkov@gmail.com) on 2022-03-30, 12:06 a.m.
 * Copyright (c) 2022 . All rights reserved.
 * Last modified 2022-03-20, 7:47 p.m.
 */

package org.avmedia.gShockPhoneSync.customComponents

import android.content.Intent
import androidx.core.content.ContextCompat.startActivity
import com.google.gson.Gson
import timber.log.Timber

object ActionData {

    abstract class Action(open var title: String, open var enabled: Boolean) {
        abstract fun run()
    }

    class SetTimeAction (override var title: String, override var enabled: Boolean) : Action (title, enabled) {
        override fun run() {
            TODO("Not yet implemented")
        }
    }

    class SetLocationAction (override var title: String, override var enabled: Boolean) : Action (title, enabled) {
        override fun run() {
            TODO("Not yet implemented")
        }
    }

    class StartVoiceAssistAction (override var title: String, override var enabled: Boolean) : Action (title, enabled) {
        override fun run() {
        }
    }

    class Separator (override var title: String, override var enabled: Boolean) : Action (title, enabled) {
        override fun run() {
            TODO("Not yet implemented")
        }
    }

    class MapAction (override var title: String, override var enabled: Boolean) : Action (title, enabled) {
        override fun run() {
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

        override fun run() {
            TODO("Not yet implemented")
        }
    }

    enum class CAMERA_ORIENTATION(val orientation: String) {
        FRONT("FRONT"), BACK("BACK")
    }

    class PhotoAction (
        override var title: String,
        override var enabled: Boolean,
        private val cameraOrientation: CAMERA_ORIENTATION
    ) : Action(title, enabled) {
        init {
            Timber.d("PhotoAction: orientation: $cameraOrientation")
        }

        override fun run() {
            TODO("Not yet implemented")
        }
    }

    class EmailLocationAction (
        override var title: String,
        override var enabled: Boolean,
        var emailAddress: String,
        var extraText: String
    ) : Action(title, enabled) {
        init {
            Timber.d("EmailLocationAction: emailAddress: $emailAddress")
            Timber.d("EmailLocationAction: extraText: $extraText")
        }

        override fun run() {
            TODO("Not yet implemented")
        }
    }

    val actions = ArrayList<ActionData.Action>()

    init {
        actions.add(MapAction("Map", false))
        actions.add(SetLocationAction("Save location to G-maps", false))
        actions.add(SetTimeAction("Set Time", true))
        actions.add(PhotoAction("Take a photo", false, CAMERA_ORIENTATION.FRONT))
        actions.add(StartVoiceAssistAction("Start Voice Assist", true))

        actions.add(Separator("Emergency Actions:", false))

        actions.add(PhoneDialAction("Make a phone call", true, ""))
        actions.add(EmailLocationAction("Send my location by email", true, "", "Come get me"))
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