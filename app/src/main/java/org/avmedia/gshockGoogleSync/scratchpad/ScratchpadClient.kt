package org.avmedia.gshockGoogleSync.scratchpad

interface ScratchpadClient {
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
