package com.ecoresilia.app.data

import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Query

interface WeatherApi {
    @GET("v1/forecast")
    suspend fun forecast(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("current") current: String = "temperature_2m,relative_humidity_2m,precipitation,rain,wind_speed_10m,weather_code",
        @Query("hourly") hourly: String = "temperature_2m,precipitation_probability,precipitation",
        @Query("forecast_days") forecastDays: Int = 2,
        @Query("timezone") timezone: String = "auto"
    ): WeatherResponse
}

data class WeatherResponse(
    val current: CurrentWeather,
    val hourly: HourlyWeather? = null
)
data class CurrentWeather(
    @SerializedName("temperature_2m") val temperature: Double,
    @SerializedName("relative_humidity_2m") val humidity: Int,
    val precipitation: Double,
    val rain: Double,
    @SerializedName("wind_speed_10m") val windSpeed: Double,
    @SerializedName("weather_code") val code: Int = 0,
    val time: String = ""
)
data class HourlyWeather(
    val time: List<String> = emptyList(),
    @SerializedName("temperature_2m") val temperature: List<Double> = emptyList(),
    @SerializedName("precipitation_probability") val chance: List<Int> = emptyList(),
    val precipitation: List<Double> = emptyList()
)
data class HourForecast(val time: String, val temperature: Double, val chance: Int, val rain: Double)
data class WeatherSnapshot(val current: CurrentWeather, val nextHours: List<HourForecast>)
