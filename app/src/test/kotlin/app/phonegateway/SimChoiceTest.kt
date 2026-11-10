package app.phonegateway

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SimChoiceTest {
    @Test fun aRequestedSimWinsOverTheSetting() {
        assertEquals(1, SimChoice.resolve(1, 2))
        assertEquals(2, SimChoice.resolve(2, 0))
    }

    @Test fun theSettingIsUsedWhenNothingIsRequested() {
        assertEquals(2, SimChoice.resolve(null, 2))
    }

    @Test fun noSettingMeansThePhoneKeepsItsOwnChoice() {
        assertNull(SimChoice.resolve(null, 0))
    }

    @Test fun smsUssdAndCallsCanUseThreeDifferentSims() {
        assertEquals(1, SimChoice.resolve(null, 1))
        assertEquals(2, SimChoice.resolve(null, 2))
        assertEquals(3, SimChoice.resolve(null, 3))
    }

    @Test fun aSettingNeverSavedFallsBackToTheOlderCombinedOne() {
        assertEquals(2, SimChoice.withLegacy(false, 0, 2))
        assertEquals(true, SimChoice.withLegacy(false, false, true))
    }

    @Test fun aSavedSettingWinsOverTheOlderCombinedOne() {
        assertEquals(0, SimChoice.withLegacy(true, 0, 2))
        assertEquals(false, SimChoice.withLegacy(true, false, true))
    }
}
