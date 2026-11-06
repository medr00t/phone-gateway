package app.phonegateway

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    @Test fun aJobThatUsedNoChosenSimNeverGetsASimFieldSoTheHospitalPayloadIsUnchanged() {
        val job = Job("j3", Kind.SMS, "+212600000000", "hi", "7")
        job.status = Status.DELIVERED
        assertFalse(job.toJson().has("sim"))
    }

    @Test fun theSimActuallyUsedIsReportedInTheJobJson() {
        val job = Job("j4", Kind.SMS, "+212600000000", "hi", "7")
        job.usedSim = 2
        assertEquals(2, job.toJson().getInt("sim"))
        assertEquals(2, Job.fromJson(job.toJson()).usedSim)
    }

    @Test fun theUsedSimWinsOverTheRequestedOneInTheReport() {
        val job = Job("j5", Kind.USSD, "*139*1*5#", "*139*1*5#", null, null, 2000, sim = 1)
        job.usedSim = 2
        assertEquals(2, job.toJson().getInt("sim"))
    }

    @Test fun ussdResponseIsKeptInTheJson() {
        val job = Job("j6", Kind.USSD, "*139*1*5#", "*139*1*5#")
        job.response = "ok"
        assertTrue(job.toJson().has("response"))
        assertEquals("ok", Job.fromJson(job.toJson()).response)
    }
}
