package app.phonegateway

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UssdCodeTest {
    @Test fun acceptsUssdCodes() {
        assertTrue(UssdCode.isValid("*123#"))
        assertTrue(UssdCode.isValid("*123*0612345678*50#"))
        assertTrue(UssdCode.isValid("#100#"))
    }

    @Test fun refusesAnythingElse() {
        assertFalse(UssdCode.isValid(""))
        assertFalse(UssdCode.isValid("0612345678"))
        assertFalse(UssdCode.isValid("*123"))
        assertFalse(UssdCode.isValid("*12a#"))
        assertFalse(UssdCode.isValid("*123#; rm"))
    }
}
