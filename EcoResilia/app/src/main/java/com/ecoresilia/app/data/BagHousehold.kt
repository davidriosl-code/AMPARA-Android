package com.ecoresilia.app.data

/** Solo guarda cantidades, nunca nombres ni datos sensibles de familiares. */
data class HouseholdProfile(
    val adults: Int = 3,
    val children: Int = 0,
    val babies: Int = 0,
    val olderAdults: Int = 0,
    val pets: Int = 0
) {
    val people: Int get() = adults + children + babies + olderAdults
    fun valid(): Boolean = adults >= 0 && children >= 0 && babies >= 0 && olderAdults >= 0 &&
        pets in 0..20 && people in 1..20
}

data class BagSuggestion(
    val name: String,
    val category: String,
    val quantity: Int,
    val unit: String,
    val reason: String,
    val aliases: List<String> = listOf(name)
)

/** Cantidades orientativas para la mochila de las primeras 24 h; no sustituye INDECI. */
object BagRecommendations {
    fun forFamily(profile: HouseholdProfile): List<BagSuggestion> {
        require(profile.valid()) { "Perfil familiar invalido" }
        val people = profile.people
        return buildList {
            add(BagSuggestion("Agua potable", "Agua", people, "botellas de 1 L", "Referencia de INDECI: 1 L por persona para la mochila; revisa necesidades y condiciones reales."))
            add(BagSuggestion("Alimentos no perecibles", "Alimentos", people, "porciones", "Referencia mínima orientativa: adapta las porciones a edades y necesidades."))
            add(BagSuggestion("Mantas", "Abrigo", people, "unidades", "Abrigo individual de acuerdo con el clima y la movilidad."))
            add(BagSuggestion("Botiquín", "Salud", maxOf(1, (people + 1) / 2), "botiquines", "Referencia para organizar mochilas; verifica medicamentos particulares."))
            add(BagSuggestion("Documentos importantes", "Documentos", 1, "carpeta", "Copias protegidas de documentos y teléfonos de emergencia."))
            if (profile.children > 0) {
                add(BagSuggestion("Alimentos para niños", "Alimentos", profile.children, "porciones", "Adaptar a restricciones y edad de cada niño."))
                add(BagSuggestion("Ropa de cambio para niños", "Abrigo", profile.children, "mudas", "Ajusta talla y clima."))
            }
            if (profile.babies > 0) {
                add(BagSuggestion("Pañales para bebés", "Higiene", 4 * profile.babies, "pañales", "Cantidad inicial ajustable según necesidades reales del bebé."))
                add(BagSuggestion("Alimento para bebés", "Alimentos", profile.babies, "reservas", "Solo el alimento indicado y adecuado para cada bebé."))
            }
            if (profile.olderAdults > 0) {
                add(BagSuggestion("Medicamentos y ayudas personales", "Salud", profile.olderAdults, "kits personales", "Incluir receta, ayudas técnicas y medicación prescrita; revisar con su profesional de salud."))
            }
            if (profile.pets > 0) {
                add(BagSuggestion("Alimento para mascotas", "Mascotas", profile.pets, "reservas", "Agregar alimento apropiado, agua y recipiente para cada mascota."))
                add(BagSuggestion("Correas o transportadoras", "Mascotas", profile.pets, "unidades", "Preparar una forma segura de trasladar cada mascota."))
            }
        }
    }
}
