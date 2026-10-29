package com.orbitai.assistant

import com.orbitai.assistant.commands.CommandParser
import org.junit.Assert.*
import org.junit.Test

class CommandParserTest {

    @Test
    fun parseFlashlightOn() {
        val c = CommandParser.parseOffline("turn on flashlight")
        assertEquals(CommandParser.Type.FLASHLIGHT, c.type)
        assertEquals("on", c.payload)
    }

    @Test
    fun parseOpenYoutube() {
        val c = CommandParser.parseOffline("open youtube")
        assertEquals(CommandParser.Type.OPEN_APP, c.type)
        assertTrue(c.payload.contains("youtube"))
    }

    @Test
    fun parseRecordSteps() {
        val c = CommandParser.parseOffline("Orbit, record my steps")
        assertEquals(CommandParser.Type.ROUTINE_RECORD_START, c.type)
    }

    @Test
    fun parseStopRecording() {
        val c = CommandParser.parseOffline("stop recording")
        assertEquals(CommandParser.Type.ROUTINE_RECORD_STOP, c.type)
    }

    @Test
    fun parseTime() {
        val c = CommandParser.parseOffline("what time is it")
        assertEquals(CommandParser.Type.TIME, c.type)
    }

    @Test
    fun parseSearch() {
        val c = CommandParser.parseOffline("search quantum computing")
        assertEquals(CommandParser.Type.SEARCH, c.type)
        assertEquals("quantum computing", c.payload)
    }

    @Test
    fun parseAiActionLine() {
        val text = "Sure.\nACTION: OPEN_APP | com.android.chrome\nDone."
        val c = CommandParser.parseAiAction(text)
        assertNotNull(c)
        assertEquals(CommandParser.Type.OPEN_APP, c!!.type)
        assertEquals("com.android.chrome", c.payload)
    }
}
