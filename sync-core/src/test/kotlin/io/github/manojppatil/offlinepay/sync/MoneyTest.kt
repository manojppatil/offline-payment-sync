package io.github.manojppatil.offlinepay.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoneyTest {

    @Test
    fun `parses whole rupees, paise and grouped input`() {
        assertEquals(125_000L, Money.parseRupees("1250"))
        assertEquals(125_050L, Money.parseRupees("1250.5"))
        assertEquals(125_050L, Money.parseRupees("1,250.50"))
        assertEquals(5L, Money.parseRupees(" 0.05 "))
    }

    @Test
    fun `rejects anything that is not a plain amount`() {
        listOf("", "  ", "-10", "12.345", "1e3", "abc", "₹100", "10.", ".5").forEach { input ->
            assertNull("'$input' should be rejected", Money.parseRupees(input))
        }
    }

    @Test
    fun `rejects amounts too large for a Long`() {
        assertNull(Money.parseRupees("999999999999999999999"))
    }

    @Test
    fun `formats with Indian digit grouping`() {
        assertEquals("₹0.05", Money.formatRupees(5))
        assertEquals("₹999.00", Money.formatRupees(99_900))
        assertEquals("₹1,250.50", Money.formatRupees(125_050))
        assertEquals("₹1,00,000.00", Money.formatRupees(10_000_000))
        assertEquals("₹12,34,567.89", Money.formatRupees(123_456_789))
        assertEquals("-₹500.00", Money.formatRupees(-50_000))
    }
}
