package org.avmedia.gshockapi.io

import android.os.Build
import androidx.annotation.RequiresApi
import org.avmedia.gshockapi.utils.Utils

@RequiresApi(Build.VERSION_CODES.O)
object HomeTimeIO {
    private data class State(
        val homeCity: String = ""
    )

    private var state = State()

    suspend fun request(): String {
        state = state.copy(
            homeCity = WorldCitiesIO.request(0)
                .let { Utils.toAsciiString(it, 2) }
        )
        return state.homeCity
    }

    fun onReceived(data: String) {
        state = state.copy(homeCity = data)
    }
}
