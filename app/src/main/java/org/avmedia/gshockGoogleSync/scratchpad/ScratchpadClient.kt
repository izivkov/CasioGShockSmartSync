package org.avmedia.gshockGoogleSync.scratchpad

/**
 * Defines the contract for a component that manages a segment of the scratchpad.
 * Clients are only responsible for their own data and are unaware of the global layout.
 */
interface ScratchpadClient {
    /**
     * Returns the number of bits this client requires for its data.
     */
    fun getBitSize(): Int

    /**
     * Called when the manager has new data for this specific client.
     * @param data A byte array containing ONLY this client's bits, byte-aligned starting from bit 0.
     */
    fun decode(data: ByteArray)

    /**
     * Called when the manager needs to collect data from this client.
     * @return A byte array containing the client's data, byte-aligned starting from bit 0.
     */
    fun encode(): ByteArray
}
