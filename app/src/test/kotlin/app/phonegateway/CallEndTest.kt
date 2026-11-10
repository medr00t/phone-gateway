package app.phonegateway

import org.junit.Assert.assertEquals
import org.junit.Test

class CallEndTest {
    @Test fun aCallThatEndedOnItsOwnWasReleasedByTheNetwork() = assertEquals("network", CallEnd.of(false, false))
    @Test fun aCallStillRingingAtTheDeadlineWasHungUpByTheApp() = assertEquals("app", CallEnd.of(true, false))
    @Test fun anSmsThatArrivedWhileRingingEndedTheCall() = assertEquals("sms", CallEnd.of(true, true))
    @Test fun anSmsIsReportedEvenIfTheCallEndedByItself() = assertEquals("sms", CallEnd.of(false, true))
}
