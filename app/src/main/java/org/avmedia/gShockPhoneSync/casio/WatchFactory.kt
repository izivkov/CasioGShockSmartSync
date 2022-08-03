package org.avmedia.gShockPhoneSync.casio

object WatchFactory {
    val watch: BluetoothWatch by lazy(::create)

    private fun create(): BluetoothWatch {
        return createFromName(getWatchName())
    }

    private fun createFromName(watchName: String): BluetoothWatch {
        return when (watchName) {
            "CASIO GW-B5600" -> Casio5600Watch()
            // TODO: Add more watches here by extending "BluetoothWatch" based on "watchName"
            else -> {
                Casio5600Watch()
            }
        }
    }

    private fun getWatchName(): String {
        // TO DO: get this from watch if possible.
        return "CASIO GW-B5600"
    }
}
