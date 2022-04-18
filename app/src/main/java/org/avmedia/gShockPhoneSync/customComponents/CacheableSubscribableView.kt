/*
 * Created by Ivo Zivkov (izivkov@gmail.com) on 2022-04-16, 8:56 a.m.
 * Copyright (c) 2022 . All rights reserved.
 * Last modified 2022-04-16, 8:56 a.m.
 */

package org.avmedia.gShockPhoneSync.customComponents

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.View
import org.avmedia.gShockPhoneSync.utils.WatchDataEvents

open abstract class CacheableSubscribableView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    @SuppressLint("CheckResult")
    fun subscribe(name: String, subject: String) {
        WatchDataEvents.addSubject(subject)
        WatchDataEvents.subscribe(name, subject, onNext = {
            onDataReceived(it as String, name)
        })
    }

    protected open fun onDataReceived(value: String, name: String) {
        put(name, value)
    }

    protected fun get(name: String): String? {
        return ValueCache.get(name)
    }

    private fun put(name: String, value: String) {
        return ValueCache.put(name, value)
    }
}