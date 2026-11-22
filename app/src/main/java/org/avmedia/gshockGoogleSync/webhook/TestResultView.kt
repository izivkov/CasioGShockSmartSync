package org.avmedia.gshockGoogleSync.webhook

import androidx.compose.runtime.Composable

/** What the Test button shows: sending, the result in words with the details folded away, or a failure to run. */
@Composable
internal fun TestResultView(state: TestUi) {
    when (state) {
        TestUi.Idle -> {}
        TestUi.Sending -> HaLoading("Sending...")
        is TestUi.Done -> {
            HaNote(state.result.headline, error = !state.result.ok)
            state.result.notes.forEach { HaNote(it) }
            HaDetails(state.result.details)
        }
        is TestUi.Failed -> {
            HaNote("The test could not run.", error = true)
            HaDetails(state.details)
        }
    }
}

/** The Test button's state. */
sealed interface TestUi {
    data object Idle : TestUi
    data object Sending : TestUi
    data class Done(val result: ReadableTest) : TestUi
    data class Failed(val details: String) : TestUi
}
