/*
 * Created by Ivo Zivkov (izivkov@gmail.com) on 2022-05-07, 7:53 p.m.
 * Copyright (c) 2022 . All rights reserved.
 * Last modified 2022-05-07, 7:52 p.m.
 */

package org.avmedia.gShockPhoneSync.ui.actions

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.switchmaterial.SwitchMaterial
import org.avmedia.gShockPhoneSync.R
import timber.log.Timber

// This adapter handles a heterogeneous list of actions.

class ActionAdapter(private val actions: ArrayList<ActionsModel.Action>) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    enum class ACTION_TYPES {
        BASE_ACTION, SEPARATOR, MAP, PHOTO, PHONE_CALL, LOCATION, EMAIL, SET_TIME, ACTIVATE_VOICE_ASSISTANT, SET_EVENTS, TOGGLE_FLASHLIGHT
    }

    open inner class ViewHolderBaseAction(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val title: TextView = itemView.findViewById<TextView>(R.id.title)
        var icon: ImageView = itemView.findViewById<ImageView>(R.id.icon)

        val actionEnabled: SwitchMaterial =
            itemView.findViewById<SwitchMaterial>(R.id.actionEnabled)
    }

    inner class ViewHolderSetTime(itemView: View) : ViewHolderBaseAction(itemView)
    inner class ViewHolderSetEvents(itemView: View) : ViewHolderBaseAction(itemView)
    inner class ViewHolderSaveLocation(itemView: View) : ViewHolderBaseAction(itemView)
    inner class ViewHolderStartVoiceAssis(itemView: View) : ViewHolderBaseAction(itemView)
    inner class ViewHolderMap(itemView: View) : RecyclerView.ViewHolder(itemView)

    inner class ViewHolderActionTakePhoto(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val title: TextView = itemView.findViewById<TextView>(R.id.title)
        var icon: ImageView = itemView.findViewById<ImageView>(R.id.icon)
        val radioGroup: RadioGroup = itemView.findViewById<RadioGroup>(R.id.cameraOrientationGroup)
        val actionEnabled: SwitchMaterial =
            itemView.findViewById<SwitchMaterial>(R.id.actionEnabled)
    }

    inner class ViewHolderActionToggleFlashlight(itemView: View) :
        RecyclerView.ViewHolder(itemView) {
        val title: TextView = itemView.findViewById<TextView>(R.id.title)
        var icon: ImageView = itemView.findViewById<ImageView>(R.id.icon)
        val actionEnabled: SwitchMaterial =
            itemView.findViewById<SwitchMaterial>(R.id.actionEnabled)
    }

    inner class ViewHolderActionPhoneCall(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val title: TextView = itemView.findViewById<TextView>(R.id.title)
        val actionEnabled: SwitchMaterial =
            itemView.findViewById<SwitchMaterial>(R.id.actionEnabled)
        var phoneNumber: TextView =
            itemView.findViewById<TextView>(R.id.phone_number)
    }

    inner class ViewHolderActionSendEmail(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val title: TextView = itemView.findViewById<TextView>(R.id.title)
        var icon: ImageView = itemView.findViewById<ImageView>(R.id.icon)
        val actionEnabled: SwitchMaterial =
            itemView.findViewById<SwitchMaterial>(R.id.actionEnabled)
        val emailAddress: TextView = itemView.findViewById<TextView>(R.id.email_address)
    }

    inner class ViewHolderActionSeparator(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val title: TextView = itemView.findViewById<TextView>(R.id.title)
    }

    // Returns the view type of the item at position for the purposes of view recycling.
    override fun getItemViewType(position: Int): Int {

        if (actions[position] is ActionsModel.PhotoAction) {
            return ACTION_TYPES.PHOTO.ordinal
        }
        if (actions[position] is ActionsModel.ToggleFlashlightAction) {
            return ACTION_TYPES.TOGGLE_FLASHLIGHT.ordinal
        }
        if (actions[position] is ActionsModel.Separator) {
            return ACTION_TYPES.SEPARATOR.ordinal
        }
        if (actions[position] is ActionsModel.PhoneDialAction) {
            return ACTION_TYPES.PHONE_CALL.ordinal
        }
        if (actions[position] is ActionsModel.EmailLocationAction) {
            return ACTION_TYPES.EMAIL.ordinal
        }
        if (actions[position] is ActionsModel.MapAction) {
            return ACTION_TYPES.MAP.ordinal
        }
        if (actions[position] is ActionsModel.SetTimeAction) {
            return ACTION_TYPES.SET_TIME.ordinal
        }
        if (actions[position] is ActionsModel.SetLocationAction) {
            return ACTION_TYPES.LOCATION.ordinal
        }
        if (actions[position] is ActionsModel.StartVoiceAssistAction) {
            return ACTION_TYPES.ACTIVATE_VOICE_ASSISTANT.ordinal
        }
        if (actions[position] is ActionsModel.SetEventsAction) {
            return ACTION_TYPES.SET_EVENTS.ordinal
        }

        return ACTION_TYPES.BASE_ACTION.ordinal
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val context = parent.context
        val inflater = LayoutInflater.from(context)

        val viewHolder: RecyclerView.ViewHolder = when (viewType) {
            ACTION_TYPES.MAP.ordinal -> {
                val vMap: View = inflater.inflate(R.layout.action_map_item, parent, false)
                ViewHolderMap(vMap)
            }
            ACTION_TYPES.PHOTO.ordinal -> {
                val vPhoto: View =
                    inflater.inflate(R.layout.action_take_photo_item, parent, false)
                ViewHolderActionTakePhoto(vPhoto)
            }
            ACTION_TYPES.TOGGLE_FLASHLIGHT.ordinal -> {
                val vFlashlight: View =
                    inflater.inflate(R.layout.action_item, parent, false)
                ViewHolderActionToggleFlashlight(vFlashlight)
            }
            ACTION_TYPES.SEPARATOR.ordinal -> {
                val vSeparator: View = inflater.inflate(R.layout.separator_item, parent, false)
                ViewHolderActionSeparator(vSeparator)
            }
            ACTION_TYPES.PHONE_CALL.ordinal -> {
                val vPhoneCall: View = inflater.inflate(R.layout.phone_call_item, parent, false)
                ViewHolderActionPhoneCall(vPhoneCall)
            }
            ACTION_TYPES.EMAIL.ordinal -> {
                val vEmail: View = inflater.inflate(R.layout.email_item, parent, false)
                ViewHolderActionSendEmail(vEmail)
            }
            ACTION_TYPES.SET_TIME.ordinal -> {
                val vSetTime: View = inflater.inflate(R.layout.action_item, parent, false)
                ViewHolderSetTime(vSetTime)
            }
            ACTION_TYPES.SET_EVENTS.ordinal -> {
                val vSetEvents: View = inflater.inflate(R.layout.action_item, parent, false)
                ViewHolderSetEvents(vSetEvents)
            }
            ACTION_TYPES.LOCATION.ordinal -> {
                val vLocation: View = inflater.inflate(R.layout.action_item, parent, false)
                ViewHolderSaveLocation(vLocation)
            }
            ACTION_TYPES.ACTIVATE_VOICE_ASSISTANT.ordinal -> {
                val vVoiceAssistant: View = inflater.inflate(R.layout.action_item, parent, false)
                ViewHolderStartVoiceAssis(vVoiceAssistant)
            }
            else -> {
                val vAction: View = inflater.inflate(R.layout.action_item, parent, false)
                ViewHolderBaseAction(vAction)
            }
        }

        return viewHolder
    }

    override fun onBindViewHolder(viewHolder: RecyclerView.ViewHolder, position: Int) {
        Timber.i("onBindViewHolder called...actions.size: ${actions.size}")

        when (viewHolder.itemViewType) {
            ACTION_TYPES.PHOTO.ordinal -> {
                val vhPhoto = viewHolder as ViewHolderActionTakePhoto
                configureActionTakePhoto(vhPhoto, position)
            }
            ACTION_TYPES.TOGGLE_FLASHLIGHT.ordinal -> {
                val vhFlashlight = viewHolder as ViewHolderActionToggleFlashlight
                configureActionToggleFlashlight(vhFlashlight, position)
            }
            ACTION_TYPES.SEPARATOR.ordinal -> {
                val vhSeparator = viewHolder as ViewHolderActionSeparator
                configureSeparator(vhSeparator, position)
            }
            ACTION_TYPES.PHONE_CALL.ordinal -> {
                val vhPhoneCall = viewHolder as ViewHolderActionPhoneCall
                configurePhoneCall(vhPhoneCall, position)
            }
            ACTION_TYPES.EMAIL.ordinal -> {
                val vhEmail = viewHolder as ViewHolderActionSendEmail
                configureSendEmail(vhEmail, position)
            }
            ACTION_TYPES.MAP.ordinal -> {
                val vhMap = viewHolder as ViewHolderMap
                configureMap(vhMap, position)
            }
            ACTION_TYPES.SET_TIME.ordinal -> {
                val vhTime = viewHolder as ViewHolderSetTime
                configureTime(vhTime, position)
            }
            ACTION_TYPES.SET_EVENTS.ordinal -> {
                val vhEvents = viewHolder as ViewHolderSetEvents
                configureEvents(vhEvents, position)
            }
            ACTION_TYPES.LOCATION.ordinal -> {
                val vhLocation = viewHolder as ViewHolderSaveLocation
                configureLocation(vhLocation, position)
            }
            ACTION_TYPES.ACTIVATE_VOICE_ASSISTANT.ordinal -> {
                val vhVoiceAssistant = viewHolder as ViewHolderStartVoiceAssis
                configureVoiceAssistant(vhVoiceAssistant, position)
            }
            else -> {
                val vhBaseAction: ViewHolderBaseAction =
                    viewHolder as ViewHolderBaseAction
                configureBaseActionViewHolder(vhBaseAction, position)
            }
        }
    }

    private fun configureVoiceAssistant(
        vhVoiceAssistant: ViewHolderStartVoiceAssis,
        position: Int
    ) {
        val action: ActionsModel.StartVoiceAssistAction =
            actions[position] as ActionsModel.StartVoiceAssistAction
        vhVoiceAssistant.title.text = action.title
        vhVoiceAssistant.actionEnabled.isChecked = action.enabled
        vhVoiceAssistant.icon.setImageResource(R.drawable.voice_assist)

        vhVoiceAssistant.actionEnabled.setOnCheckedChangeListener(CompoundButton.OnCheckedChangeListener { _, isChecked ->
            action.enabled = isChecked
        })
    }

    private fun configureLocation(vhLocation: ViewHolderSaveLocation, position: Int) {
        val action: ActionsModel.SetLocationAction =
            actions[position] as ActionsModel.SetLocationAction
        vhLocation.title.text = action.title
        vhLocation.actionEnabled.isChecked = action.enabled
        vhLocation.icon.setImageResource(R.drawable.location)

        vhLocation.actionEnabled.setOnCheckedChangeListener(CompoundButton.OnCheckedChangeListener { _, isChecked ->
            action.enabled = isChecked
        })
    }

    private fun configureTime(vhTime: ViewHolderSetTime, position: Int) {
        val action: ActionsModel.SetTimeAction = actions[position] as ActionsModel.SetTimeAction
        vhTime.title.text = action.title
        vhTime.actionEnabled.isChecked = action.enabled
        vhTime.icon.setImageResource(R.drawable.time)

        vhTime.actionEnabled.setOnCheckedChangeListener(CompoundButton.OnCheckedChangeListener { _, isChecked ->
            action.enabled = isChecked
        })
    }

    private fun configureEvents(vhEvents: ViewHolderSetEvents, position: Int) {
        val action: ActionsModel.SetEventsAction = actions[position] as ActionsModel.SetEventsAction
        vhEvents.title.text = action.title
        vhEvents.actionEnabled.isChecked = action.enabled
        vhEvents.icon.setImageResource(R.drawable.events)

        vhEvents.actionEnabled.setOnCheckedChangeListener(CompoundButton.OnCheckedChangeListener { _, isChecked ->
            action.enabled = isChecked
        })
    }

    private fun configureSendEmail(
        vhEmail: ViewHolderActionSendEmail,
        position: Int
    ) {
        val action: ActionsModel.EmailLocationAction =
            actions[position] as ActionsModel.EmailLocationAction
        vhEmail.title.text = action.title
        vhEmail.actionEnabled.isChecked = action.enabled
        vhEmail.emailAddress.text = action.emailAddress

        vhEmail.actionEnabled.setOnCheckedChangeListener(CompoundButton.OnCheckedChangeListener { _, isChecked ->
            action.enabled = isChecked
        })

        vhEmail.emailAddress.onFocusChangeListener = View.OnFocusChangeListener { v, hasFocus ->
            if (!hasFocus) {
                (actions[position] as ActionsModel.EmailLocationAction).emailAddress =
                    (v as EditText).text.toString()

                // save value here in case we close screen while still in this field
                action.save(vhEmail.itemView.context)
            }
        }
    }

    private fun configurePhoneCall(
        vhPhoneCall: ViewHolderActionPhoneCall,
        position: Int
    ) {
        val action: ActionsModel.PhoneDialAction = actions[position] as ActionsModel.PhoneDialAction
        vhPhoneCall.title.text = action.title
        vhPhoneCall.actionEnabled.isChecked = action.enabled
        vhPhoneCall.phoneNumber.text = action.phoneNumber

        vhPhoneCall.actionEnabled.setOnCheckedChangeListener(CompoundButton.OnCheckedChangeListener { _, isChecked ->
            action.enabled = isChecked
        })

        vhPhoneCall.phoneNumber.onFocusChangeListener = View.OnFocusChangeListener { v, hasFocus ->
            if (!hasFocus) {
                (actions[position] as ActionsModel.PhoneDialAction).phoneNumber =
                    (v as EditText).text.toString()

                // save value here in case we close screen while still in this field
                action.save(vhPhoneCall.itemView.context)
            }
        }
    }

    private fun configureSeparator(
        vhBaseAction: ViewHolderActionSeparator,
        position: Int
    ) {
        val action: ActionsModel.Action = actions[position]
        vhBaseAction.title.text = action.title
    }

    private fun configureMap(
        vhBaseAction: ViewHolderMap,
        position: Int
    ) {
        val action: ActionsModel.Action = actions[position]
    }

    private fun configureBaseActionViewHolder(
        vhBaseAction: ViewHolderBaseAction,
        position: Int
    ) {
        val action: ActionsModel.Action = actions[position]
        vhBaseAction.actionEnabled.isChecked = action.enabled
        vhBaseAction.title.text = action.title

        vhBaseAction.actionEnabled.setOnCheckedChangeListener(CompoundButton.OnCheckedChangeListener { _, isChecked ->
            actions[position].enabled = isChecked
        })
    }

    private fun configureActionTakePhoto(
        vhPhoto: ViewHolderActionTakePhoto,
        position: Int
    ) {
        val action: ActionsModel.PhotoAction = actions[position] as ActionsModel.PhotoAction
        vhPhoto.actionEnabled.isChecked = action.enabled
        vhPhoto.title.text = action.title
        vhPhoto.icon.setImageResource(R.drawable.camera)

        if (action.cameraOrientation.toString() == "FRONT")
            vhPhoto.radioGroup.check(R.id.front)
        else
            vhPhoto.radioGroup.check(R.id.back)

        vhPhoto.actionEnabled.setOnCheckedChangeListener(CompoundButton.OnCheckedChangeListener { _, isChecked ->
            action.enabled = isChecked
        })

        vhPhoto.radioGroup.setOnCheckedChangeListener(
            RadioGroup.OnCheckedChangeListener { group, checkedId ->
                if (checkedId == R.id.front) {
                    action.cameraOrientation = ActionsModel.CAMERA_ORIENTATION.FRONT
                } else {
                    action.cameraOrientation = ActionsModel.CAMERA_ORIENTATION.BACK
                }
            })
    }

    private fun configureActionToggleFlashlight(
        vhFlashlight: ViewHolderActionToggleFlashlight,
        position: Int
    ) {
        val action: ActionsModel.ToggleFlashlightAction =
            actions[position] as ActionsModel.ToggleFlashlightAction
        vhFlashlight.actionEnabled.isChecked = action.enabled
        vhFlashlight.title.text = action.title
        vhFlashlight.icon.setImageResource(R.drawable.flashlight)

        vhFlashlight.actionEnabled.setOnCheckedChangeListener(CompoundButton.OnCheckedChangeListener { _, isChecked ->
            action.enabled = isChecked
        })
    }

    override fun getItemCount(): Int {
        return actions.size
    }
}