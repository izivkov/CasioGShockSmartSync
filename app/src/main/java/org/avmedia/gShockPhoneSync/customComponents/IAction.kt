/*
 * Created by Ivo Zivkov (izivkov@gmail.com) on 2022-05-06, 10:40 a.m.
 * Copyright (c) 2022 . All rights reserved.
 * Last modified 2022-05-06, 10:40 a.m.
 */

package org.avmedia.gShockPhoneSync.customComponents

interface IAction {
    fun run ()
    fun getId(): Int
    var title: String
    var enabled: Boolean
}