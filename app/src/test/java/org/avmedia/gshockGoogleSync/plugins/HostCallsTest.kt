package org.avmedia.gshockGoogleSync.plugins

import kotlinx.coroutines.runBlocking
import org.avmedia.smartsync.plugin.api.FailReason
import org.avmedia.smartsync.plugin.api.PluginResult
import org.junit.Assert.assertEquals
import org.junit.Test

/** No exception crosses the plugin interface: a host call that throws gives a Failed result. */
class HostCallsTest {
    @Test fun aFailingHostCallReturnsFailedNotAnException() = runBlocking {
        val r = HostCalls.safely<List<Int>> { error("the database is closed") }
        assertEquals(PluginResult.Failed(FailReason.INTERNAL, "IllegalStateException"), r)
    }

    @Test fun aCallThatWorksGivesItsValue() = runBlocking {
        assertEquals(PluginResult.Ok(3), HostCalls.safely { PluginResult.Ok(3) })
    }

    @Test(expected = kotlinx.coroutines.CancellationException::class)
    fun cancellationIsNotTurnedIntoAFailure(): Unit = runBlocking {
        HostCalls.safely<Unit> { throw kotlinx.coroutines.CancellationException("stopped") }
    }
}
