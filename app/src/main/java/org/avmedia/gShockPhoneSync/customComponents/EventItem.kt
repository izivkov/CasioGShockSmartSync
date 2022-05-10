/*
 * Created by Ivo Zivkov (izivkov@gmail.com) on 2022-03-30, 12:06 a.m.
 * Copyright (c) 2022 . All rights reserved.
 * Last modified 2022-03-29, 5:57 p.m.
 */

package org.avmedia.gShockPhoneSync.customComponents

import android.content.Context
import android.util.AttributeSet
import android.widget.TextView
import kotlin.reflect.KFunction

class EventItem @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : com.google.android.material.card.MaterialCardView(context, attrs, defStyleAttr) {

    private lateinit var onDataChanged: KFunction<Unit>
    private lateinit var event: EventsModel.Event
    private lateinit var eventTitle: TextView

    init {}

    fun setEventData(event: EventsModel.Event) {
        this.event = event
    }

    fun setOnDataChange(onDataChanged: KFunction<Unit>) {
        this.onDataChanged = onDataChanged
    }
}
