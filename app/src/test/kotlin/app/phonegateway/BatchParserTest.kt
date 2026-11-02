package app.phonegateway

import org.junit.Assert.*
import org.junit.Test

class BatchParserTest {
    @Test fun differentTextsAndReceivers() {
        val b = BatchParser.parse("""[{"to":"+212600000001","text":"Hello A"},{"to":"06 00-00 00 02","text":"Hello B","ref":42}]""")
        assertTrue(b.errors.isEmpty())
        assertEquals(2, b.items.size)
        assertEquals("Hello A", b.items[0].text)
        assertEquals("0600000002", b.items[1].to)
        assertEquals("42", b.items[1].ref)
    }

    @Test fun objectFormWithDelayAndAliases() {
        val b = BatchParser.parse("""{"delayMs":3000,"messages":[{"receiver":"+212600000001","message":"Hi"}]}""")
        assertEquals(3000L, b.delayMs)
        assertEquals(1, b.items.size)
    }

    @Test fun toArrayExpandsToOneJobPerReceiver() {
        val b = BatchParser.parse("""[{"to":["+212600000001","+212600000002"],"text":"Same text"}]""")
        assertEquals(2, b.items.size)
    }

    @Test fun badRowsAreReportedAndGoodRowsKept() {
        val b = BatchParser.parse("""[{"to":"abc","text":"x"},{"to":"+212600000001"},{"text":"no receiver"},"oops",{"to":"+212600000003","text":"ok"}]""")
        assertEquals(1, b.items.size)
        assertEquals(4, b.errors.size)
    }

    @Test fun invalidJsonAndWrongShape() {
        assertEquals(1, BatchParser.parse("{not json").errors.size)
        assertEquals(1, BatchParser.parse("42").errors.size)
        assertEquals(1, BatchParser.parse("""{"delayMs":1}""").errors.size)
    }

    @Test fun limits() {
        val long = "x".repeat(BatchParser.MAX_TEXT + 1)
        assertEquals(1, BatchParser.parse("""[{"to":"+212600000001","text":"$long"}]""").errors.size)
        val many = (1..BatchParser.MAX_ITEMS + 1).joinToString(",", "[", "]") { """{"to":"+212600000001","text":"a"}""" }
        assertTrue(BatchParser.parse(many).items.isEmpty())
    }
}

class CallVerdictTest {
    @Test fun pickedUpIsVoicemail() = assertEquals("VOICEMAIL", CallVerdict.decide(true, false))
    @Test fun rejectedWithSmsIsAlive() = assertEquals("ALIVE", CallVerdict.decide(false, true))
    @Test fun rejectedWithoutSmsIsOffline() = assertEquals("OFFLINE", CallVerdict.decide(false, false))
}

class SmsCompatTest {
    @Test fun statesMatchSmsGate() {
        assertEquals("Pending", SmsCompat.state(Status.QUEUED))
        assertEquals("Sent", SmsCompat.state(Status.SENT))
        assertEquals("Delivered", SmsCompat.state(Status.DELIVERED))
        assertEquals("Failed", SmsCompat.state(Status.FAILED))
    }
    @Test fun severalReceivers() {
        assertEquals("Failed", SmsCompat.aggregate(listOf(Status.DELIVERED, Status.FAILED)))
        assertEquals("Delivered", SmsCompat.aggregate(listOf(Status.DELIVERED, Status.DELIVERED)))
        assertEquals("Sent", SmsCompat.aggregate(listOf(Status.SENT, Status.DELIVERED)))
        assertEquals("Processed", SmsCompat.aggregate(listOf(Status.SENT, Status.QUEUED)))
        assertEquals("Pending", SmsCompat.aggregate(listOf(Status.QUEUED, Status.QUEUED)))
    }
}
