package app.phonegateway

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BatchSimTest {
    @Test fun aValidSimSlotIsKeptOnTheItem() {
        val b = BatchParser.parse("""[{"to":"+212600000001","text":"Hi","sim":2}]""")
        assertTrue(b.errors.isEmpty())
        assertEquals(2, b.items[0].sim)
    }

    @Test fun noSimMeansTheItemKeepsThePhoneDefault() {
        val b = BatchParser.parse("""[{"to":"+212600000001","text":"Hi"}]""")
        assertNull(b.items[0].sim)
    }

    @Test fun anInvalidSimRefusesOnlyThatMessage() {
        val b = BatchParser.parse("""[{"to":"+212600000001","text":"Hi","sim":9},{"to":"+212600000002","text":"Ok"}]""")
        assertEquals(1, b.items.size)
        assertEquals(1, b.errors.size)
        assertTrue(b.errors[0].contains("sim"))
    }

    @Test fun theSimSpreadsOverEveryReceiverOfAMessage() {
        val b = BatchParser.parse("""[{"to":["+212600000001","+212600000002"],"text":"Hi","sim":1}]""")
        assertEquals(listOf(1, 1), b.items.map { it.sim })
    }
}
