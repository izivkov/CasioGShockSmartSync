package org.avmedia.gshockapi.ble

import android.annotation.SuppressLint
import android.content.Context
import android.os.ParcelUuid
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import no.nordicsemi.android.kotlin.ble.core.ServerDevice
import no.nordicsemi.android.kotlin.ble.core.scanner.BleScanFilter
import no.nordicsemi.android.kotlin.ble.core.scanner.BleScannerSettings
import no.nordicsemi.android.kotlin.ble.core.scanner.FilteredServiceUuid
import no.nordicsemi.android.kotlin.ble.scanner.BleScanner
import no.nordicsemi.android.kotlin.ble.core.scanner.BleNumOfMatches
import no.nordicsemi.android.kotlin.ble.core.scanner.BleScanMode
import no.nordicsemi.android.kotlin.ble.core.scanner.BleScannerMatchMode

object BleScannerGShock {
    @SuppressLint("MissingPermission")
    val CASIO_SERVICE_UUID = "00001804-0000-1000-8000-00805f9b34fb"

    data class DeviceInfo (val name:String, val address:String)
    private var deferredResult: CompletableDeferred<DeviceInfo> = CompletableDeferred()

    @SuppressLint("MissingPermission")
    suspend fun scan(context: Context): CompletableDeferred<DeviceInfo> {

        val gShockFilters: List<BleScanFilter> = listOf(
            BleScanFilter(serviceUuid = FilteredServiceUuid(ParcelUuid.fromString(CASIO_SERVICE_UUID)))
        )

        val gShockSettings = BleScannerSettings(
            includeStoredBondedDevices = false,
            numOfMatches = BleNumOfMatches.MATCH_NUM_ONE_ADVERTISEMENT,
            matchMode = BleScannerMatchMode.MATCH_MODE_AGGRESSIVE,
            scanMode = BleScanMode.SCAN_MODE_LOW_LATENCY,
        )

        val scope = CoroutineScope(Dispatchers.Main)
        BleScanner(context).scan(filters = gShockFilters, settings = gShockSettings)
            .filter {
                val device: ServerDevice = it.device
                val ret = (device.name as String).startsWith("CASIO")
                ret
            }
            .onEach {
                val device = it.device
                deferredResult.complete(DeviceInfo(device.name as String, device.address))
                scope.cancel("End scanning")
            }
            .launchIn(scope)

        return deferredResult
    }
}