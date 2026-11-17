package com.ecoresilia.app.ui

fun main() {
    check(RiskMapPresentation.colorForReport("Huaico") != RiskMapPresentation.colorForReport("Inundación"))
    check(RiskMapPresentation.colorForReport("Derrumbe") != RiskMapPresentation.colorForReport("Huaico"))
    check(RiskMapPresentation.symbolForReport("Huaico") == "H")
    check(RiskMapPresentation.symbolForReport("Inundación") == "I")
    check(RiskMapPresentation.validCoordinates(-16.409, -71.537))
    check(!RiskMapPresentation.validCoordinates(Double.NaN, -71.537))
    check(!RiskMapPresentation.validCoordinates(-95.0, -71.537))
    check(!RiskMapPresentation.validCoordinates(-16.409, 181.0))
    println("OK: simbología y coordenadas del mapa")
}
