/*
 * Created by Ivo Zivkov (izivkov@gmail.com) on 2022-04-16, 8:56 a.m.
 * Copyright (c) 2022 . All rights reserved.
 * Last modified 2022-04-16, 8:56 a.m.
 */

package org.avmedia.gShockPhoneSync.customComponents

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import org.avmedia.gShockPhoneSync.casioB5600.CasioSupport
import org.avmedia.gShockPhoneSync.utils.ProgressEvents
import org.avmedia.gShockPhoneSync.utils.Utils
import org.avmedia.gShockPhoneSync.utils.WatchDataEvents
import org.jetbrains.anko.runOnUiThread
import timber.log.Timber

open abstract class CacheableSubscribableTextView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : com.google.android.material.textview.MaterialTextView(context, attrs, defStyleAttr) {

    init {
    }

    @SuppressLint("CheckResult")
    fun subscribe(name: String, subject: String) {
        WatchDataEvents.addSubject(subject)
        WatchDataEvents.subscribe(name, subject, onNext = {
            onDataReceived(it as String, name)
        })
    }

    protected open fun onDataReceived(value: String, name:String) {
        put(name, value)
        text = value
    }

    protected fun get(name: String): String? {
        return ValueCache.get(name)
    }

    private fun put(name: String, value:String) {
        return ValueCache.put(name, value)
    }
}