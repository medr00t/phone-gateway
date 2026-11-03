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

class BatchIdTest {
    @Test fun perMessageIdAndRefAreKept() {
        val b = BatchParser.parse("""[{"id":"notif-1","to":"+212600000001","text":"A","ref":"p-9"},{"to":"+212600000002","text":"B"}]""")
        assertEquals("notif-1", b.items[0].id)
        assertEquals("p-9", b.items[0].ref)
        assertNull(b.items[1].id)
    }
    @Test fun badOrDuplicateIdsAreRejected() {
        val b = BatchParser.parse("""[{"id":"bad id!","to":"+212600000001","text":"A"},{"id":"x","to":"+212600000001","text":"A"},{"id":"x","to":"+212600000002","text":"B"},{"id":"y","to":["+212600000003","+212600000004"],"text":"C"}]""")
        assertEquals(1, b.items.size)
        assertEquals(3, b.errors.size)
    }
}

class RetentionTest {
    private val day = 24L * 3600 * 1000
    @Test fun oldFinishedJobsAreDeletedRecentAndRunningStay() {
        val now = 100 * day
        fun job(id: String, ageDays: Int, st: Status) = Job(id, Kind.SMS, "+212600000001", "x", createdAt = now - ageDays * day).also { it.status = st }
        JobStore.add(job("old-done", 31, Status.DELIVERED))
        JobStore.add(job("old-failed", 40, Status.FAILED))
        JobStore.add(job("old-queued", 45, Status.QUEUED))
        JobStore.add(job("recent", 5, Status.DELIVERED))
        assertEquals(2, JobStore.purge(30 * day, now))
        assertNull(JobStore.get("old-done"))
        assertNull(JobStore.get("old-failed"))
        assertNotNull(JobStore.get("old-queued"))
        assertNotNull(JobStore.get("recent"))
    }
    @Test fun oldReceivedSmsAreDeleted() {
        val now = 100 * day
        Inbox.add(InboxMessage("+212600000009", "old", now - 31 * day))
        Inbox.add(InboxMessage("+212600000009", "new", now - 2 * day))
        assertEquals(1, Inbox.purge(30 * day, now))
        assertEquals(listOf("new"), Inbox.since(0, "+212600000009").map { it.text })
    }
}

class RetentionDurationTest {
    @Test fun daysAreKeptWithinBounds() {
        assertEquals(30L * 24 * 3600 * 1000, Retention.maxAgeMs(30))
        assertEquals(Retention.DAY_MS, Retention.maxAgeMs(0))
        assertEquals(3650L * Retention.DAY_MS, Retention.maxAgeMs(99999))
    }
}

class InboxDuplicateTest {
    @Test fun sameMessageTwiceWithinSecondsIsKeptOnce() {
        val now = System.currentTimeMillis()
        assertTrue(Inbox.add(InboxMessage("+212600000010", "code 1234", now)))
        assertFalse(Inbox.add(InboxMessage("+212600000010", "code 1234", now + 800)))
        assertEquals(1, Inbox.since(now - 1, "+212600000010").size)
    }

    @Test fun sameTextMuchLaterIsAnotherMessage() {
        val now = System.currentTimeMillis()
        assertTrue(Inbox.add(InboxMessage("+212600000011", "ok", now)))
        assertTrue(Inbox.add(InboxMessage("+212600000011", "ok", now + 60_000)))
    }
}
