package com.myappstore.smsforwarder.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OtpTest {

    @Test
    fun hebrewCodes() {
        assertEquals("482913", Otp.detect("קוד האימות שלך הוא 482913"))
        assertEquals("7731", Otp.detect("הסיסמה החד פעמית לכניסה: 7731. אין למסור אותה לאף אחד"))
        assertEquals("125690", Otp.detect("125690 הוא הקוד שלך לכניסה לאתר"))
    }

    @Test
    fun englishCodes() {
        assertEquals("123456", Otp.detect("G-123456 is your Google verification code."))
        assertEquals("987654", Otp.detect("Your OTP is 987 654"))
        assertEquals("4821", Otp.detect("Use PIN 4821 to sign in"))
    }

    @Test
    fun picksTheNumberClosestToTheKeyword() {
        assertEquals(
            "554433",
            Otp.detect("עסקה בסך 1200 ₪ בכרטיס 4455. קוד אימות: 554433"),
        )
    }

    @Test
    fun ignoresMessagesWithoutCodeWords() {
        assertNull(Otp.detect("נפגשים ב-2030 בערב? הזמנה 55512"))
        assertNull(Otp.detect("Your parcel is shipping, tracking 123456"))
    }

    @Test
    fun ignoresAmountsDatesAndPhones() {
        assertNull(Otp.detect("קוד הנחה: חיסכון של 2500 ₪"))
        assertNull(Otp.detect("code valid until 29/09/2026"))
        assertNull(Otp.detect("לקבלת קוד התקשרו 0501234567"))
    }
}
