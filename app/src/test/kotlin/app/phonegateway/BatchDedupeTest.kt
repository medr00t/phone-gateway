package app.phonegateway

import org.junit.Assert.*
import org.junit.Test

class BatchDedupeTest {
    private fun item(id: String?) = BatchItem("+212600000001", "Hello", null, id)

    @Test fun knownIdsAreSeparatedFromNewOnes() {
        val s = BatchDedupe.split(listOf(item("a"), item("b"), item(null)), { it == "a" })
        assertEquals(listOf("b", null), s.fresh.map { it.id })
        assertEquals(listOf("a"), s.known.map { it.id })
        assertFalse(s.isReplay)
    }

    @Test fun aBatchOfOnlyKnownIdsIsAReplay() {
        val s = BatchDedupe.split(listOf(item("a"), item("b")), { true })
        assertTrue(s.fresh.isEmpty())
        assertEquals(2, s.known.size)
        assertTrue(s.isReplay)
    }

    @Test fun anEmptyBatchIsNotAReplay() {
        assertFalse(BatchDedupe.split(emptyList(), { true }).isReplay)
    }

    @Test fun messagesWithoutIdAreAlwaysNew() {
        val s = BatchDedupe.split(listOf(item(null)), { true })
        assertEquals(1, s.fresh.size)
        assertFalse(s.isReplay)
    }
}
