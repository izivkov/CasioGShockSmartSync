/*
 * Created by Ivo Zivkov (izivkov@gmail.com) on 2022-05-06, 7:04 p.m.
 * Copyright (c) 2022 . All rights reserved.
 * Last modified 2022-05-06, 7:00 p.m.
 */
package org.avmedia.gShockPhoneSync.customComponents

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.util.LongSparseArray
import android.view.View
import android.view.translation.ViewTranslationResponse
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class ActionList @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : RecyclerView(context, attrs, defStyleAttr) {

    init {
        adapter = ActionAdapter(ActionsModel.actions)
        layoutManager = LinearLayoutManager(context)
    }

    private fun loadData(context: Context) {
        ActionsModel.actions.forEach {
            it.load(context)
        }
    }

    private fun saveData(context: Context) {
        ActionsModel.actions.forEach {
            it.save(context)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        saveData(context)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        loadData (context)
    }
}
