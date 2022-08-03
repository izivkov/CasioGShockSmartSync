package org.avmedia.gShockPhoneSync.casio

object WatchFactory {
    public lateinit var watch: BluetoothWatch

    fun create(watchName: String) {
        if (!this::watch.isInitialized || watch == null) {
            // TODO: Add more watches here by extending "BluetoothWatch"
            // Use "watchName" to determine what watch to create.
            watch = Casio5600Watch()
        }
    }
}