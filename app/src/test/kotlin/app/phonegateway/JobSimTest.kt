package app.phonegateway

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class JobSimTest {
    @Test fun simSlotSurvivesTheStoredJson() {
        val job = Job("j1", Kind.USSD, "*139*0680055230*5#", "*139*0680055230*5#", "9", null, 2000, sim = 2)
        assertEquals(2, Job.fromJson(job.toJson()).sim)
    }

    @Test fun jobsWithoutSimStayWithoutSim() {
        val job = Job("j2", Kind.SMS, "+212600000000", "hi")
        assertNull(Job.fromJson(job.toJson()).sim)
    }
}
