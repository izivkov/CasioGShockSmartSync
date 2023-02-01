/*
 * Created by Ivo Zivkov (izivkov@gmail.com) on 2022-03-30, 12:06 a.m.
 * Copyright (c) 2022 . All rights reserved.
 * Last modified 2022-03-27, 9:37 p.m.
 */

package org.avmedia.gshockapi.utils

import android.annotation.SuppressLint
import io.reactivex.functions.Consumer
import io.reactivex.subjects.PublishSubject

object WatchDataEvents {
    private val subjects = HashMap<String, PublishSubject<String?>>()
    private val subscribers = HashMap<String, LinkedHashSet<String>>()

    fun addSubject(name: String) {
        if (subjects[name] != null) {
            return
        }
        val subject: PublishSubject<String?> = PublishSubject.create()
        subjects[name] = subject
    }

    private fun addSubscriberAndSubject(subscriber: String, subject: String) {
        if (!subscribers.containsKey(subscriber)) {
            val subjectsForThisSubscriber = LinkedHashSet<String>()
            subscribers[subscriber] = subjectsForThisSubscriber
        }

        val subjectsForThisSubscriber = subscribers[subscriber]
        if (!subjectsForThisSubscriber?.contains(subject)!!) {
            subjectsForThisSubscriber.add(subject)
        }
    }

    private fun subscriberAlreadySubscribed(subscriber: String, subject: String): Boolean {
        if (!subscribers.containsKey(subscriber)) {
            return false
        }

        val subjectsForThisSubscriber = subscribers[subscriber]
        if (subjectsForThisSubscriber == null || !subjectsForThisSubscriber.contains(subject)) {
            return false
        }
        return true
    }

    @SuppressLint("CheckResult")
    fun subscribe(subscriberName: String, subject: String, onNext: Consumer<in String?>) {
        if (!subscriberAlreadySubscribed(subscriberName, subject)) {
            getProcessor(subject)?.subscribe(onNext)
            addSubscriberAndSubject(subscriberName, subject)
        }
    }

    @SuppressLint("CheckResult")
    fun subscribeWithDeferred(
        subscriberName: String,
        subject: String,
        onNext: Consumer<in String?>
    ) {
        if (!subscriberAlreadySubscribed(subscriberName, subject)) {
            getProcessor(subject)?.subscribe(onNext)
            addSubscriberAndSubject(subscriberName, subject)
        }
    }

    private fun getProcessor(name: String): PublishSubject<String?>? {
        return subjects[name]
    }

    fun emitEvent(name: String, event: String) {
        subjects[name]?.onNext(event)
    }
}