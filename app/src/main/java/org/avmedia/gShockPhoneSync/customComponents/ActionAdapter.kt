/*
 * Created by Ivo Zivkov (izivkov@gmail.com) on 2022-05-07, 7:53 p.m.
 * Copyright (c) 2022 . All rights reserved.
 * Last modified 2022-05-07, 7:52 p.m.
 */

package org.avmedia.gShockPhoneSync.customComponents

import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioGroup
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.switchmaterial.SwitchMaterial
import org.avmedia.gShockPhoneSync.R
import timber.log.Timber

// This adapter handles a heterogeneous list of actions.

class ActionAdapter(private val actions: ArrayList<ActionData.Action>) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    enum class ACTION_TYPES {
        BASE_ACTION, SEPARATOR, MAP, PHOTO, PHONE_CALL, LOCATION, EMAIL, SET_TIME, ACTIVATE_VOICE_ASSISTANT
    }

    inner class ViewHolderBaseAction(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val title: TextView = itemView.findViewById<TextView>(R.id.title)

        val actionEnabled: SwitchMaterial =
            itemView.findViewById<SwitchMaterial>(R.id.actionEnabled)
    }

    inner class ViewHolderActionTakePhoto(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val title: TextView = itemView.findViewById<TextView>(R.id.title)
        val orientation: RadioGroup = itemView.findViewById<RadioGroup>(R.id.cameraOrientationGroup)
        val actionEnabled: SwitchMaterial =
            itemView.findViewById<SwitchMaterial>(R.id.actionEnabled)
    }

    inner class ViewHolderActionPhoneCall(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val title: TextView = itemView.findViewById<TextView>(R.id.title)
        val actionEnabled: SwitchMaterial =
            itemView.findViewById<SwitchMaterial>(R.id.actionEnabled)
        val phoneNumber: TextView =
            itemView.findViewById<TextView>(R.id.phone_number)
    }

    inner class ViewHolderActionSendEmail(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val title: TextView = itemView.findViewById<TextView>(R.id.title)
        val actionEnabled: SwitchMaterial =
            itemView.findViewById<SwitchMaterial>(R.id.actionEnabled)
        val emailAddress: TextView =
            itemView.findViewById<TextView>(R.id.email_address)
    }

    inner class ViewHolderActionSeparator(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val title: TextView = itemView.findViewById<TextView>(R.id.title)
    }

    // Returns the view type of the item at position for the purposes of view recycling.
    override fun getItemViewType(position: Int): Int {
        if (actions[position] is ActionData.PhotoAction) {
            return ACTION_TYPES.PHOTO.ordinal
        }
        if (actions[position] is ActionData.Separator) {
            return ACTION_TYPES.SEPARATOR.ordinal
        }
        if (actions[position] is ActionData.PhoneDialAction) {
            return ACTION_TYPES.PHONE_CALL.ordinal
        }
        if (actions[position] is ActionData.EmailLocationAction) {
            return ACTION_TYPES.EMAIL.ordinal
        }
        return ACTION_TYPES.BASE_ACTION.ordinal
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val context = parent.context
        val inflater = LayoutInflater.from(context)

        val viewHolder: RecyclerView.ViewHolder = when (viewType) {
            ACTION_TYPES.PHOTO.ordinal -> {
                val vPhoto: View = inflater.inflate(R.layout.action_take_photo_item, parent, false)
                ViewHolderActionTakePhoto(vPhoto)
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
            else -> {
                val vAction: View = inflater.inflate(R.layout.action_item, parent, false)
                ViewHolderBaseAction(vAction)
            }
        }

        return viewHolder
    }

    @RequiresApi(Build.VERSION_CODES.N)
    override fun onBindViewHolder(viewHolder: RecyclerView.ViewHolder, position: Int) {
        Timber.i("onBindViewHolder called...actions.size: ${actions.size}")
        when (viewHolder.itemViewType) {
            ACTION_TYPES.PHOTO.ordinal -> {
                val vhPhoto = viewHolder as ViewHolderActionTakePhoto
                configureActionTakePhoto(vhPhoto, position)
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
            else -> {
                val vhBaseAction: ViewHolderBaseAction =
                    viewHolder as ViewHolderBaseAction
                configureBaseActionViewHolder(vhBaseAction, position)
            }
        }
    }

    private fun configureSendEmail(
        vhEmail: ViewHolderActionSendEmail,
        position: Int
    ) {
        val action: ActionData.EmailLocationAction = actions[position] as ActionData.EmailLocationAction
        vhEmail.title.text = action.title
        vhEmail.actionEnabled.isChecked = action.enabled
        vhEmail.emailAddress.text = action.emailAddress
    }

    private fun configurePhoneCall(
        vhPhoneCall: ViewHolderActionPhoneCall,
        position: Int
    ) {
        val action: ActionData.PhoneDialAction = actions[position] as ActionData.PhoneDialAction
        vhPhoneCall.title.text = action.title
        vhPhoneCall.actionEnabled.isChecked = action.enabled
        vhPhoneCall.phoneNumber.text = action.phoneNumber
    }

    private fun configureSeparator(
        vhBaseAction: ActionAdapter.ViewHolderActionSeparator,
        position: Int
    ) {
        val action: ActionData.Action = actions[position]
        vhBaseAction.title.text = action.title
    }

    private fun configureBaseActionViewHolder(
        vhBaseAction: ActionAdapter.ViewHolderBaseAction,
        position: Int
    ) {
        val action: ActionData.Action = actions[position]
        vhBaseAction.actionEnabled.isChecked = action.enabled
        vhBaseAction.title.text = action.title
    }

    private fun configureActionTakePhoto(
        vhPhoto: ActionAdapter.ViewHolderActionTakePhoto,
        position: Int
    ) {
        val action: ActionData.PhotoAction = actions[position] as ActionData.PhotoAction
        vhPhoto.actionEnabled.isChecked = action.enabled
        vhPhoto.title.text = action.title
        // vhPhoto.orientation....
    }

    override fun getItemCount(): Int {
        return actions.size
    }
}