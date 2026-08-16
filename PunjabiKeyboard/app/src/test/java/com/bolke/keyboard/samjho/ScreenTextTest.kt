package com.bolke.keyboard.samjho

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The two pieces of Samjho that are pure logic. Everything else needs a device. */
class ScreenTextTest {

    @Test
    fun hitTestPicksTheSmallestContainingRect() {
        // The conversation list contains the row, which contains the message.
        val list = TextTarget(0, 0, 1000, 2000, "whole list")
        val row = TextTarget(0, 400, 1000, 600, "row")
        val message = TextTarget(40, 420, 700, 580, "the message")

        val hit = ScreenText.hitTest(listOf(list, row, message), 300, 500)

        assertEquals("the message", hit?.text)
    }

    @Test
    fun hitTestMissesReturnNull() {
        val message = TextTarget(40, 420, 700, 580, "the message")
        assertNull(ScreenText.hitTest(listOf(message), 900, 100))
        assertNull(ScreenText.hitTest(emptyList(), 300, 500))
        // Right and bottom edges are exclusive, so a tap on the seam belongs to the neighbour.
        assertNull(ScreenText.hitTest(listOf(message), 700, 500))
    }

    @Test
    fun alreadyPunjabiTextIsNotSentToTheTranslator() {
        assertFalse(ScreenText.isTranslatable("ਕੀ ਹਾਲ ਹੈ"))
        assertFalse(ScreenText.isTranslatable(""))
        assertFalse(ScreenText.isTranslatable("12:45"))
        // Mixed, but mostly Gurmukhi: a stray Latin word does not make it English.
        assertFalse(ScreenText.isTranslatable("ਮੈਂ ਘਰ ਆ ਗਿਆ ok"))
    }

    @Test
    fun englishTextIsTranslatable() {
        assertTrue(ScreenText.isTranslatable("Can you send the bill before Friday?"))
        assertTrue(ScreenText.isTranslatable("OTP is 448210"))
    }
}
