/*
 * Created by Ivo Zivkov (izivkov@gmail.com) on 2022-03-30, 9:09 a.m.
 * Copyright (c) 2022 . All rights reserved.
 * Last modified 2022-03-30, 9:09 a.m.
 */

package org.avmedia.gshockGoogleSync.services

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.time.Duration

class InactivityHandler(
    private val timeout: Duration,
    private val onInactivity: () -> Unit
) {
    private data class InactivityState(
        val lastInteractionTime: Long = System.currentTimeMillis(),
        val monitoringJob: Job? = null
    )

    private val state = MutableStateFlow(InactivityState())

    fun registerInteraction() {
        state.value = state.value.copy(lastInteractionTime = System.currentTimeMillis())
    }

    fun startMonitoring() {
        val newJob = createMonitoringJob()
        state.value = state.value.copy(monitoringJob = newJob)
    }

    fun stopMonitoring() {
        state.value.monitoringJob?.cancel()
        state.value = state.value.copy(monitoringJob = null)
    }

    private fun createMonitoringJob(): Job =
        CoroutineScope(Dispatchers.Main).launch {
            state.collectLatest { currentState ->
                delay(timeout.inWholeMilliseconds)
                checkInactivity(currentState.lastInteractionTime)
            }
        }

    private fun checkInactivity(lastInteraction: Long) {
        if (isInactive(lastInteraction)) {
            onInactivity()
        }
    }

    private fun isInactive(lastInteraction: Long): Boolean =
        System.currentTimeMillis() - lastInteraction >= timeout.inWholeMilliseconds
}
