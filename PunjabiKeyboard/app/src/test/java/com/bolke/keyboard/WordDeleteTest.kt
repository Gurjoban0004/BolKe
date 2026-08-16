package com.bolke.keyboard

import org.junit.Assert.assertEquals
import org.junit.Test

/** Boundary checks for the hold-to-delete-words stage of the backspace key. */
class WordDeleteTest {

    @Test
    fun deletesLastWordPlusTrailingSpace() {
        assertEquals(5, BolKeIMEService.wordDeleteLength("main ghar aaya "))
        assertEquals(4, BolKeIMEService.wordDeleteLength("main ghar aaya"))
        assertEquals(5, BolKeIMEService.wordDeleteLength("hanji"))
    }

    @Test
    fun handlesRunsOfSpacesAndEmptyInput() {
        assertEquals(7, BolKeIMEService.wordDeleteLength("ok fine   "))
        assertEquals(3, BolKeIMEService.wordDeleteLength("   "))
        assertEquals(1, BolKeIMEService.wordDeleteLength(""))
    }
}
