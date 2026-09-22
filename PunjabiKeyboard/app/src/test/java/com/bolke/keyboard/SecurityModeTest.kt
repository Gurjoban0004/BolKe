package com.bolke.keyboard

import android.text.InputType
import android.view.inputmethod.EditorInfo
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SecurityModeTest {
    @Test
    fun passwordAndNoLearningEditorsAreSecure() {
        assertTrue(
            BolKeIMEService.isSecureInput(
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD,
                0
            )
        )
        assertTrue(
            BolKeIMEService.isSecureInput(
                InputType.TYPE_CLASS_TEXT,
                EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
            )
        )
        assertFalse(BolKeIMEService.isSecureInput(InputType.TYPE_CLASS_TEXT, 0))
        assertFalse(
            BolKeIMEService.isSecureInput(
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI,
                0
            )
        )
    }

    @Test
    fun standaloneOtpIsNotSubmittedFromClipboard() {
        assertFalse(BolKeIMEService.isSafeClipboardText("123456"))
        assertTrue(BolKeIMEService.isSafeClipboardText("Your OTP-like code is 123456. Do not share it."))
    }

    @Test
    fun sharedTextMustBeBoundedPlainContent() {
        assertFalse(TranslateActivity.isSupportedPlainText(""))
        assertFalse(TranslateActivity.isSupportedPlainText("a\u0000b"))
        assertTrue(TranslateActivity.isSupportedPlainText("Delivery is expected Friday."))
    }
}
