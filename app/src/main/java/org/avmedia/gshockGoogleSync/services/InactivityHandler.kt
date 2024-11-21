/*
 * Created by Ivo Zivkov (izivkov@gmail.com) on 2022-03-30, 9:09 a.m.
 * Copyright (c) 2022 . All rights reserved.
 * Last modified 2022-03-30, 9:09 a.m.
 */

package org.avmedia.gshockGoogleSync.services

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlin.time.Duration

class InactivityHandler(
    private val timeout: Duration,
    private val onInactivity: () -> Unit
) {
    private val interactionFlow = MutableStateFlow(System.currentTimeMillis())
    private var job: Job? = null

    fun registerInteraction() {
        interactionFlow.value = System.currentTimeMillis()
    }

    fun startMonitoring() {
        job = CoroutineScope(Dispatchers.Main).launch {
            interactionFlow.collectLatest { lastInteraction ->
                delay(timeout.inWholeMilliseconds)
                if (System.currentTimeMillis() - lastInteraction >= timeout.inWholeMilliseconds) {
                    onInactivity()
                }
            }
        }
    }

    fun stopMonitoring() {
        job?.cancel()
        job = null
    }
}