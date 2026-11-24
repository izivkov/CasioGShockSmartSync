package org.avmedia.smartsync.plugin.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class RedactTest {
    @Test fun aTokenIsMaskedToTwoCharactersAndItsLength() {
        val token = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.secret-part"
        val masked = Redact.secret(token)
        assertEquals("ey…(${token.length})", masked)
        assertFalse(masked.contains("secret"))
        assertEquals("", Redact.secret(null))
        assertEquals("…(3)", Redact.secret("abc"))
    }

    @Test fun aUrlLosesItsPathQueryAndUserInfo() {
        assertEquals("https://ha.example:8123/…", Redact.url("https://user:pw@ha.example:8123/api/webhook/0123abcd?token=x#y"))
        assertEquals("http://192.168.1.5:8123", Redact.url("http://192.168.1.5:8123"))
        assertEquals("", Redact.url(null))
    }
}
