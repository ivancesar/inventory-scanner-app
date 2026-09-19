package com.inventoryscanner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SheetClientTest {
    @Test fun validUrls() {
        assertTrue(SheetClient.isValidUrl("https://script.google.com/macros/s/abc/exec"))
        for (bad in listOf(
            "http://script.google.com/macros/s/abc/exec",
            "https://evil.com/macros/s/abc/exec",
            "https://script.google.com.evil.com/macros/s/abc/exec",
            "https://script.google.com@evil.com/macros/s/abc/exec",
            "https://evil.com\\@script.google.com/",
            "",
            "   ",
        )) assertFalse(bad, SheetClient.isValidUrl(bad))
    }

    @Test fun invalidUrlIsNeverRequested() {
        assertEquals("Invalid link", SheetClient.test("http://script.google.com/x").exceptionOrNull()?.message)
    }

    @Test fun okResponses() {
        assertEquals("Stock", SheetClient.parse(200, """{"ok":true,"sheet":"Stock"}""").getOrThrow().getString("sheet"))
        assertEquals(3, SheetClient.parse(200, """{"ok":true,"added":3}""").getOrThrow().getInt("added"))
        val dup = SheetClient.parse(200, """{"ok":true,"added":0,"duplicate":true}""").getOrThrow()
        assertEquals(0, dup.getInt("added"))
    }

    @Test fun failures() {
        fun msg(status: Int, body: String) = SheetClient.parse(status, body).exceptionOrNull()?.message
        assertEquals("No rows", msg(200, """{"ok":false,"error":"No rows"}"""))
        assertEquals("Unexpected response", msg(200, "<!DOCTYPE html><html>Sign in</html>"))
        assertEquals("Unexpected response", msg(200, ""))
        assertEquals("Unexpected response", msg(200, """{"ok":false}"""))
        assertEquals("HTTP 500", msg(500, """{"ok":true}"""))
    }
}
