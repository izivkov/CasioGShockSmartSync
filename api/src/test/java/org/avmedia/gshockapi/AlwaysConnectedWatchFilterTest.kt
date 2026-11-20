package org.avmedia.gshockapi

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AlwaysConnectedWatchFilterTest {

    @Before
    fun setUp() {
        AlwaysConnectedWatchFilter.reset()
    }

    @Test
    fun nonAlwaysConnectedWatchIsAlwaysAllowed() {
        // GW-B5600 is not always connected
        assertTrue(AlwaysConnectedWatchFilter.connectionFilter("CASIO GW-B5600"))
        assertTrue(AlwaysConnectedWatchFilter.connectionFilter("CASIO GW-B5600"))
    }

    @Test
    fun alwaysConnectedWatchAllowedFirstTime() {
        // DW-H5600 is always connected
        assertTrue(AlwaysConnectedWatchFilter.connectionFilter("CASIO DW-H5600"))
    }

    @Test
    fun alwaysConnectedWatchDeniedSubsequentTimesWithin6Hours() {
        // DW-H5600 allowed first time
        assertTrue(AlwaysConnectedWatchFilter.connectionFilter("CASIO DW-H5600"))

        // Immediately subsequent connection attempt denied
        assertFalse(AlwaysConnectedWatchFilter.connectionFilter("CASIO DW-H5600"))
    }

    @Test
    fun blankOrEmptyWatchNameIsAllowed() {
        assertTrue(AlwaysConnectedWatchFilter.connectionFilter(""))
    }

    @Test
    fun setAllowOnceBypassesFilterAndResetsOnUpdate() {
        // Initial connection
        assertTrue(AlwaysConnectedWatchFilter.connectionFilter("CASIO DW-H5600"))

        // Reconnection within 6 hours normally denied
        assertFalse(AlwaysConnectedWatchFilter.connectionFilter("CASIO DW-H5600"))

        // Enabling allowOnce allows connection
        AlwaysConnectedWatchFilter.setAllowOnce("CASIO DW-H5600", true)
        assertTrue(AlwaysConnectedWatchFilter.isAllowOnce("CASIO DW-H5600"))
        assertTrue(AlwaysConnectedWatchFilter.connectionFilter("CASIO DW-H5600"))

        // After updateConnectionTime runs, allowOnce resets to false
        AlwaysConnectedWatchFilter.updateConnectionTime("CASIO DW-H5600")
        assertFalse(AlwaysConnectedWatchFilter.isAllowOnce("CASIO DW-H5600"))

        // Next connection within 6 hours is denied again
        assertFalse(AlwaysConnectedWatchFilter.connectionFilter("CASIO DW-H5600"))
    }
}
