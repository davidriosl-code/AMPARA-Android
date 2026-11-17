package com.ecoresilia.app.data

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

data class GeoPlace(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val admin1: String? = null,
    val admin2: String? = null,
    val country: String? = null
) {
    fun display(): String = listOfNotNull(name, admin1, country).distinct().joinToString(", ")
}
private data class GeoResponse(val results: List<GeoPlace>? = null)
private interface GeoApi {
    @GET("v1/search")
    suspend fun search(
        @Query("name") name: String,
        @Query("count") count: Int = 8,
        @Query("language") language: String = "es",
        @Query("countryCode") country: String = "PE"
    ): GeoResponse
}
class GeocodingRepository {
    private val api = Retrofit.Builder()
        .baseUrl("https://geocoding-api.open-meteo.com/")
        .addConverterFactory(GsonConverterFactory.create()).build().create(GeoApi::class.java)
    suspend fun search(q: String): List<GeoPlace> = api.search(q).results.orEmpty()
}
