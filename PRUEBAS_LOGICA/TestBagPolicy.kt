import com.ecoresilia.app.data.*
import java.time.LocalDate

fun main() {
    val profile = HouseholdProfile(adults = 2, children = 1, babies = 1, olderAdults = 1, pets = 2)
    check(profile.people == 5)
    check(profile.valid())
    val recommendations = BagRecommendations.forFamily(profile)
    check(recommendations.first { it.name == "Agua potable" }.quantity == 5)
    check(recommendations.first { it.name == "Pañales para bebés" }.quantity == 4)
    check(recommendations.first { it.name == "Correas o transportadoras" }.quantity == 2)
    check(!HouseholdProfile(adults = 0).valid())
    check(!HouseholdProfile(adults = 21).valid())
    val today = LocalDate.of(2026, 11, 12)
    check(BagExpiryPolicy.status("2026-11-11", today) == ExpiryFlag.EXPIRED)
    check(BagExpiryPolicy.status("2026-11-12", today) == ExpiryFlag.TODAY)
    check(BagExpiryPolicy.status("2026-11-19", today) == ExpiryFlag.SOON)
    check(BagExpiryPolicy.status("2026-11-20", today) == ExpiryFlag.VALID)
    check(BagExpiryPolicy.status("", today) == ExpiryFlag.NONE)
    check(BagExpiryPolicy.status("invalid", today) == ExpiryFlag.NONE)
    println("Pruebas superadas: composicion familiar, recomendaciones y vencimientos")
}
