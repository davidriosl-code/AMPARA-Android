package com.ecoresilia.app.data

import java.time.LocalDate
import java.time.temporal.ChronoUnit

enum class ExpiryFlag { NONE, VALID, SOON, TODAY, EXPIRED }

object BagExpiryPolicy {
    fun status(date: String, today: LocalDate = LocalDate.now()): ExpiryFlag {
        if (date.isBlank()) return ExpiryFlag.NONE
        val expiration = try { LocalDate.parse(date) } catch (_: Exception) { return ExpiryFlag.NONE }
        val days = ChronoUnit.DAYS.between(today, expiration)
        return when {
            days < 0 -> ExpiryFlag.EXPIRED
            days == 0L -> ExpiryFlag.TODAY
            days <= 7 -> ExpiryFlag.SOON
            else -> ExpiryFlag.VALID
        }
    }
}
