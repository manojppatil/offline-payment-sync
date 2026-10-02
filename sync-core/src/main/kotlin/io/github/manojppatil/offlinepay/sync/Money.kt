package io.github.manojppatil.offlinepay.sync

import java.math.BigDecimal
import kotlin.math.abs

/** Rupee amounts as typed by a person, and as shown back to them. */
object Money {
    private val amountPattern = Regex("""\d+(\.\d{1,2})?""")

    /**
     * Parses "1250", "1,250.5" or "1250.50" into paise. Returns null for anything that is not a
     * plain non-negative amount with at most two decimals.
     */
    fun parseRupees(input: String): Long? {
        val cleaned = input.trim().replace(",", "")
        if (!amountPattern.matches(cleaned)) return null
        return try {
            BigDecimal(cleaned).movePointRight(2).longValueExact()
        } catch (e: ArithmeticException) {
            null
        }
    }

    /** Formats paise with Indian digit grouping: 12345678 becomes "₹1,23,456.78". */
    fun formatRupees(amountMinor: Long): String {
        val sign = if (amountMinor < 0) "-" else ""
        val absolute = abs(amountMinor)
        val rupees = absolute / 100
        val paise = (absolute % 100).toString().padStart(2, '0')
        return "$sign₹${groupIndian(rupees.toString())}.$paise"
    }

    /** Last three digits together, then groups of two: 1,00,000 and 12,34,567. */
    private fun groupIndian(digits: String): String {
        if (digits.length <= 3) return digits
        val lastThree = digits.takeLast(3)
        val leading = digits.dropLast(3)
            .reversed()
            .chunked(2)
            .joinToString(",")
            .reversed()
        return "$leading,$lastThree"
    }
}
