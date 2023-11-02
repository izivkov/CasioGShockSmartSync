/*
 * Created by Ivo Zivkov (izivkov@gmail.com) on 2022-03-30, 12:06 a.m.
 * Copyright (c) 2022 . All rights reserved.
 * Last modified 2022-03-14, 1:48 p.m.
 */

package org.avmedia.gShockPhoneSync

import android.bluetooth.BluetoothAdapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }
    @Test
    fun test_checkBluetoothAddress() {

        assertTrue(checkBluetoothAddress("C0:A7:5A:57:98:07"))
        assertTrue(checkBluetoothAddress("00:43:A8:23:10:F0"))

        // Can't be null.
        assertFalse(checkBluetoothAddress(null))
        // Must be 17 characters long.
        assertFalse(checkBluetoothAddress(""))
        assertFalse(checkBluetoothAddress("0"))
        assertFalse(checkBluetoothAddress("00"))
        assertFalse(checkBluetoothAddress("00:"))
        assertFalse(checkBluetoothAddress("00:0"))
        assertFalse(checkBluetoothAddress("00:00"))
        assertFalse(checkBluetoothAddress("00:00:"))
        assertFalse(checkBluetoothAddress("00:00:0"))
        assertFalse(checkBluetoothAddress("00:00:00"))
        assertFalse(checkBluetoothAddress("00:00:00:"))
        assertFalse(checkBluetoothAddress("00:00:00:0"))
        assertFalse(checkBluetoothAddress("00:00:00:00"))
        assertFalse(checkBluetoothAddress("00:00:00:00:"))
        assertFalse(checkBluetoothAddress("00:00:00:00:0"))
        assertFalse(checkBluetoothAddress("00:00:00:00:00"))
        assertFalse(checkBluetoothAddress("00:00:00:00:00:"))
        assertFalse(
            checkBluetoothAddress(
                "00:00:00:00:00:0"
            )
        )
        // Must have colons between octets.
        assertFalse(
            checkBluetoothAddress(
                "00x00:00:00:00:00"
            )
        )
        assertFalse(
            checkBluetoothAddress(
                "00:00.00:00:00:00"
            )
        )
        assertFalse(
            checkBluetoothAddress(
                "00:00:00-00:00:00"
            )
        )
        assertFalse(
            checkBluetoothAddress(
                "00:00:00:00900:00"
            )
        )
        assertFalse(
            checkBluetoothAddress(
                "00:00:00:00:00?00"
            )
        )
        // Hex letters must be uppercase.
        assertFalse(
            checkBluetoothAddress(
                "a0:00:00:00:00:00"
            )
        )
        assertFalse(
            checkBluetoothAddress(
                "0b:00:00:00:00:00"
            )
        )
        assertFalse(
            checkBluetoothAddress(
                "00:c0:00:00:00:00"
            )
        )
        assertFalse(
            checkBluetoothAddress(
                "00:0d:00:00:00:00"
            )
        )
        assertFalse(
            checkBluetoothAddress(
                "00:00:e0:00:00:00"
            )
        )
        assertFalse(
            checkBluetoothAddress(
                "00:00:0f:00:00:00"
            )
        )
        assertTrue(
            checkBluetoothAddress(
                "00:00:00:00:00:00"
            )
        )
        assertTrue(
            checkBluetoothAddress(
                "12:34:56:78:9A:BC"
            )
        )
        assertTrue(
            checkBluetoothAddress(
                "DE:F0:FE:DC:B8:76"
            )
        )
    }

    private val ADDRESS_LENGTH = 17
    fun checkBluetoothAddress(address: String?): Boolean {
        if (address == null || address.length != ADDRESS_LENGTH) {
            return false
        }
        for (i in 0 until ADDRESS_LENGTH) {
            val c = address[i]
            when (i % 3) {
                0, 1 -> {
                    if (c >= '0' && c <= '9' || c >= 'A' && c <= 'F') {
                        // hex character, OK
                        break
                    }
                    return false
                }

                2 -> {
                    if (c == ':') {
                        break // OK
                    }
                    return false
                }
            }
        }
        return true
    }
}
