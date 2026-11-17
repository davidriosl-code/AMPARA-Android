package com.ecoresilia.app.data

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class WeatherRepository {
    private val api = Retrofit.Builder()
        .baseUrl("https://api.open-meteo.com/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(WeatherApi::class.java)

    suspend fun fetch(latitude: Double, longitude: Double): WeatherSnapshot {
        val response = api.forecast(latitude, longitude)
        val hourly = response.hourly
        val items = if (hourly == null) emptyList() else {
            hourly.time.indices.mapNotNull { index ->
                if (index >= hourly.temperature.size || index >= hourly.chance.size || index >= hourly.precipitation.size) null
                else HourForecast(hourly.time[index], hourly.temperature[index], hourly.chance[index], hourly.precipitation[index])
            }.filter { it.time >= response.current.time.take(13) }.take(8)
        }
        return WeatherSnapshot(response.current, items)
    }
}
