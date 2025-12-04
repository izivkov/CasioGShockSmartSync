package org.avmedia.gshockGoogleSync.scratchpad

import org.avmedia.gshockapi.IGShockAPI
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScratchpadManager @Inject constructor(
    private val api: IGShockAPI
) {
    private val clients = mutableListOf<ScratchpadClient>()
    private val clientOffsets = mutableMapOf<ScratchpadClient, Int>()
    private var totalSize = 0
    private var masterBuffer: ByteArray = ByteArray(0)

    /**
     * Clients call this method to register themselves with the manager.
     */
    fun register(client: ScratchpadClient) {
        if (!clients.contains(client)) {
            clients.add(client)
            recalculateLayout()
        }
    }

    private fun recalculateLayout() {
        var currentOffset = 0
        clients.forEach { client ->
            clientOffsets[client] = currentOffset
            currentOffset += client.getStorageSize()
        }
        totalSize = currentOffset
        masterBuffer = ByteArray(totalSize)
    }

    /**
     * Loads data from the watch and distributes it to all registered clients.
     */
    suspend fun load() {
        if (totalSize == 0) return
        val data = api.getScratchpadData(0, totalSize)
        if (data.size != totalSize) {
            return
        }
        masterBuffer = data

        clients.forEach { client ->
            val offset = clientOffsets[client] ?: 0
            val size = client.getStorageSize()
            if (offset + size <= masterBuffer.size) {
                val slice = masterBuffer.copyOfRange(offset, offset + size)
                client.setBuffer(slice)
            }
        }
    }

    /**
     * Gathers data from all clients and saves the combined buffer to the watch.
     */
    suspend fun save() {
        if (totalSize == 0) return
        clients.forEach { client ->
            val offset = clientOffsets[client] ?: 0
            val clientBuffer = client.getBuffer()
            if (offset + clientBuffer.size <= masterBuffer.size) {
                clientBuffer.copyInto(destination = masterBuffer, destinationOffset = offset)
            }
        }
        api.setScratchpadData(masterBuffer, 0)
    }
}
