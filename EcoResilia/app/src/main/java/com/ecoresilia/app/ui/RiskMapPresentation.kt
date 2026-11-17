package com.ecoresilia.app.ui

/** Simbología de observaciones ciudadanas; nunca equivale a nivel de alerta. */
internal object RiskMapPresentation {
    fun colorForReport(type: String): Int = when (type) {
        "Huaico" -> 0xFFE58D35.toInt()      // naranja
        "Inundación" -> 0xFF2377C5.toInt()  // azul
        "Derrumbe" -> 0xFF8752AD.toInt()    // violeta
        else -> 0xFF687782.toInt()          // sin categoría reconocida
    }

    fun symbolForReport(type: String): String = when (type) {
        "Huaico" -> "H"
        "Inundación" -> "I"
        "Derrumbe" -> "D"
        else -> "?"
    }

    fun validCoordinates(lat: Double, lon: Double): Boolean =
        lat.isFinite() && lon.isFinite() && lat in -90.0..90.0 && lon in -180.0..180.0
}
