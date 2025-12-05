package org.avmedia.gshockGoogleSync.scratchpad

/**
 * Defines the contract for a component that owns and manages a specific segment (slice)
 * of the global scratchpad memory buffer.
 *
 * Clients implementing this interface must provide:
 * - A fixed offset (starting position) within the global buffer.
 * - A fixed size (number of bytes) they require.
 * - Mechanisms to accept updated data from the manager and provide their current data back to it.
 */
interface ScratchpadClient {
    /**
     * Returns the fixed offset (starting position) in the scratchpad buffer for this client.
     * This ensures consistent buffer layout regardless of registration order.
     */
    fun getStorageOffset(): Int

    /**
     * Returns the number of bytes this client requires.
     */
    fun getStorageSize(): Int

    /**
     * The manager calls this to provide the client with its allocated slice of the main buffer.
     * @param buffer The ByteArray slice for this client.
     */
    fun setBuffer(buffer: ByteArray)

    /**
     * The manager calls this to request the client's current (and possibly modified) buffer.
     * @return The client's internal ByteArray.
     */
    fun getBuffer(): ByteArray
}
