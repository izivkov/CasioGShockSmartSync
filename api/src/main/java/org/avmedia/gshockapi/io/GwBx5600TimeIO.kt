package org.avmedia.gshockapi.io

import CachedIO
import android.os.Build
import androidx.annotation.RequiresApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import org.avmedia.gshockapi.ble.Connection
import org.avmedia.gshockapi.ble.GetSetMode
import org.avmedia.gshockapi.io.IOFunctional
import timber.log.Timber
import java.time.LocalDateTime

@RequiresApi(Build.VERSION_CODES.O)
object GwBx5600TimeIO {

    private data class State(
        val deferredResult: CompletableDeferred<String>? = null
    )

    private var state = State()

    fun onReceived(data: String) {
        state.deferredResult?.complete(data)
    }

    private suspend fun request(step: Int, reqPayload: String): String {
        state = state.copy(deferredResult = CompletableDeferred())
        val reqBytes = IOFunctional.toCasioCmd(reqPayload)
        
        // Write to SP_REQUEST (using GET mode for now, though it might need a specific GetSetMode)
        Connection.write(GetSetMode.GET, reqBytes)
        
        return state.deferredResult?.await() ?: ""
    }

    suspend fun setTime(now: LocalDateTime) {
        Timber.i("GwBx5600TimeIO.setTime: setting time to \$now")
        
        // Step 1: time slot data (hardcoded)
        Connection.write(GetSetMode.GET, IOFunctional.toCasioCmd("051d001d00240024012402"))
        delay(150)
        Connection.write(GetSetMode.SET, IOFunctional.toCasioCmd("020f001d00010606e9760000ffffffffffff0f001d020302001901ffffffffffffffff"))
        delay(150)

        // Step 2: world city data (hardcoded)
        Connection.write(GetSetMode.GET, IOFunctional.toCasioCmd("031e001e001e00"))
        delay(150)
        Connection.write(GetSetMode.SET, IOFunctional.toCasioCmd("0607001e00e97604040207001e01000000000007001e02190124040014002400014044ba36ef8055fc4002007372be637d04140024010100000000000000000000000000000000140024020100000000000000000000000000000002"))
        delay(150)

        // Step 3: city names (hardcoded)
        Connection.write(GetSetMode.GET, IOFunctional.toCasioCmd("061f001f061f011f071f021f08"))
        delay(150)
        Connection.write(GetSetMode.SET, IOFunctional.toCasioCmd("0614001f004d414452494400000000000000000000000014001f064d4144000000000000000000000000000014001f0128555443290000000000000000000000000014001f0755544300000000000000000000000000000000000014001f02544f4b594f0000000000000000000000000014001f0854594f000000000000000000000000000000000000"))
        delay(150)

        // Step 4: final time command
        writeTimeCommand(now)
    }

    private fun writeTimeCommand(now: LocalDateTime) {
        val casioDow = (now.dayOfWeek.value % 7)
        val timeCmd = ByteArray(11)
        timeCmd[0] = 0x09
        timeCmd[1] = (now.year and 0xFF).toByte()
        timeCmd[2] = ((now.year shr 8) and 0xFF).toByte()
        timeCmd[3] = now.monthValue.toByte()
        timeCmd[4] = now.dayOfMonth.toByte()
        timeCmd[5] = now.hour.toByte()
        timeCmd[6] = now.minute.toByte()
        timeCmd[7] = now.second.toByte()
        timeCmd[8] = casioDow.toByte()
        timeCmd[9] = 0x50.toByte()
        timeCmd[10] = 0x01.toByte()

        Connection.write(GetSetMode.SET, timeCmd)
    }
}
