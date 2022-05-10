/*
 * Created by Ivo Zivkov (izivkov@gmail.com) on 2022-03-30, 12:06 a.m.
 * Copyright (c) 2022 . All rights reserved.
 * Last modified 2022-03-20, 7:47 p.m.
 */

package org.avmedia.gShockPhoneSync.customComponents

import android.content.Context
import com.google.gson.Gson
import org.avmedia.gShockPhoneSync.utils.LocalDataStorage
import timber.log.Timber

object ActionsModel {

    abstract class Action(open var title: String, open var enabled: Boolean) {
        abstract fun run()

        open fun save(context: Context) {
            val key = this.javaClass.simpleName+".enabled"
            val value = enabled
            LocalDataStorage.put(key, value.toString(), context)
        }

        open fun load (context: Context) {
            val key = this.javaClass.simpleName+".enabled"
            enabled = LocalDataStorage.get(key, "false", context).toBoolean()
        }
    }

    class SetTimeAction (override var title: String, override var enabled: Boolean) : Action (title, enabled) {
        override fun run() {
            TODO("Not yet implemented")
        }

        override fun save(context: Context) {
            super.save (context)
        }

        override fun load(context: Context) {
            val key = this.javaClass.simpleName+".enabled"
            enabled = LocalDataStorage.get(key, "true", context).toBoolean()
        }
    }

    class SetLocationAction (override var title: String, override var enabled: Boolean) : Action (title, enabled) {
        override fun run() {
            TODO("Not yet implemented")
        }

        override fun save(context: Context) {
            super.save (context)
        }

        override fun load(context: Context) {
            super.load(context)
        }
    }

    class StartVoiceAssistAction (override var title: String, override var enabled: Boolean) : Action (title, enabled) {
        override fun run() {
        }

        override fun save(context: Context) {
            super.save (context)
        }

        override fun load(context: Context) {
            super.load(context)
        }
    }

    class Separator (override var title: String, override var enabled: Boolean) : Action (title, enabled) {
        override fun run() {
            TODO("Not yet implemented")
        }

        override fun save(context: Context) {
            super.save (context)
        }

        override fun load(context: Context) {
            super.save (context)
        }
    }

    class MapAction (override var title: String, override var enabled: Boolean) : Action (title, enabled) {
        override fun run() {
            TODO("Not yet implemented")
        }

        override fun save(context: Context) {
            super.save (context)
        }

        override fun load(context: Context) {
            super.load(context)
        }
    }

    class PhoneDialAction(
        override var title: String,
        override var enabled: Boolean,
        var phoneNumber: String
    ) : Action(title, enabled) {
        init {
            Timber.d("PhoneDialAction")
        }

        override fun run() {
            TODO("Not yet implemented")
        }

        override fun save(context: Context) {
            super.save (context)
            val key = this.javaClass.simpleName+".phoneNumber"
            LocalDataStorage.put(key, phoneNumber.toString(), context)
        }

        override fun load(context: Context) {
            super.load(context)
            val key = this.javaClass.simpleName+".phoneNumber"
            phoneNumber = LocalDataStorage.get(key, "", context).toString()
        }
    }

    enum class CAMERA_ORIENTATION(cameraOrientation: String) {
        FRONT("FRONT"), BACK("BACK");
    }

    class PhotoAction (
        override var title: String,
        override var enabled: Boolean,
        var cameraOrientation: CAMERA_ORIENTATION
    ) : Action(title, enabled) {
        init {
            Timber.d("PhotoAction: orientation: $cameraOrientation")
        }

        override fun run() {
            TODO("Not yet implemented")
        }

        override fun save(context: Context) {
            super.save (context)
            val key = this.javaClass.simpleName+".cameraOrientation"
            LocalDataStorage.put(key, cameraOrientation.toString(), context)
        }

        override fun load(context: Context) {
            super.load(context)
            val key = this.javaClass.simpleName+".cameraOrientation"
            cameraOrientation = if (LocalDataStorage.get(key, "FRONT", context).toString() == "BACK") CAMERA_ORIENTATION.BACK else CAMERA_ORIENTATION.FRONT
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

        override fun save(context: Context) {
            val key = this.javaClass.simpleName+".emailAddress"
            LocalDataStorage.put(key, emailAddress.toString(), context)
            super.save (context)
        }

        override fun load(context: Context) {
            super.load(context)

            val key = this.javaClass.simpleName+".emailAddress"
            emailAddress = LocalDataStorage.get(key, "", context).toString()
            extraText = "Sent by G-shock App:\n https://play.google.com/store/apps/details?id=org.avmedia.gshockGoogleSync"
        }
    }

    val actions = ArrayList<ActionsModel.Action>()

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