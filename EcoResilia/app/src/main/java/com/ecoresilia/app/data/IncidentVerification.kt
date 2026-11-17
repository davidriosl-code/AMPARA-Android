package com.ecoresilia.app.data

import kotlin.math.*

/** Corroboración comunitaria, NO validación oficial ni predicción de desastres. */
object IncidentVerification {
    private fun distanceKm(a: IncidentReport, b: IncidentReport): Double {
        val r = 6371.0
        val dLat = Math.toRadians(b.latitude - a.latitude)
        val dLon = Math.toRadians(b.longitude - a.longitude)
        val h = sin(dLat / 2).pow(2.0) + cos(Math.toRadians(a.latitude)) *
            cos(Math.toRadians(b.latitude)) * sin(dLon / 2).pow(2.0)
        return 2 * r * asin(sqrt(h.coerceIn(0.0, 1.0)))
    }

    fun corroboratingCount(target: IncidentReport, reports: List<IncidentReport>): Int {
        val recent = reports.filter {
            it.type == target.type &&
                abs(it.timestamp - target.timestamp) <= 30 * 60 * 1000L &&
                distanceKm(target, it) <= 5.0
        }
        return recent.map { it.authorId }.distinct().size
    }
}
