/*
 * Created by Ivo Zivkov (izivkov@gmail.com) on 2022-03-30, 12:06 a.m.
 * Copyright (c) 2022 . All rights reserved.
 * Last modified 2022-03-14, 1:48 p.m.
 */

package org.avmedia.gShockPhoneSync.ui.actions

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class ActionsViewModel : ViewModel() {

    private val _text = MutableLiveData<String>().apply {
        value = "This is Actions Fragment"
    }
    val text: LiveData<String> = _text
}