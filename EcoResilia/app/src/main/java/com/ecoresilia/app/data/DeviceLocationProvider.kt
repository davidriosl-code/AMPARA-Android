package com.ecoresilia.app.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource

/** Solicitud puntual y voluntaria; no rastrea la ubicación en segundo plano. */
object DeviceLocationProvider {
    data class Result(
        val latitude: Double, val longitude: Double, val isLastKnown: Boolean,
        val accuracyMeters: Float? = null, val capturedAtMillis: Long = 0L
    )

    fun request(context: Context, onSuccess: (Result) -> Unit, onError: (String) -> Unit,
                requireFresh: Boolean = false) {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (requireFresh && !fine) {
            onError("Activa el permiso de ubicación precisa para colocar tu marcador.")
            return
        }
        if (!fine && !coarse) {
            onError("Concede permiso de ubicación para centrar el mapa.")
            return
        }
        val client = LocationServices.getFusedLocationProviderClient(context)
        val token = CancellationTokenSource()
        val priority = if (fine) Priority.PRIORITY_HIGH_ACCURACY else Priority.PRIORITY_BALANCED_POWER_ACCURACY
        try {
            client.getCurrentLocation(priority, token.token)
                .addOnSuccessListener { location ->
                    if (location != null && valid(location) && (!requireFresh || freshEnough(location))) {
                        onSuccess(Result(location.latitude, location.longitude, false,
                            if (location.hasAccuracy()) location.accuracy else null,
                            location.time))
                    } else if (requireFresh) onError("No se obtuvo tu posición actual. Enciende el GPS y vuelve a intentar.")
                    else fallback(context, onSuccess, onError)
                }
                .addOnFailureListener {
                    if (requireFresh) onError("No se pudo obtener tu posición actual. Inténtalo de nuevo.")
                    else fallback(context, onSuccess, onError)
                }
        } catch (_: SecurityException) {
            onError("El sistema denegó el permiso de ubicación.")
        } catch (_: Exception) {
            if (requireFresh) onError("No se pudo consultar la ubicación actual.")
            else fallback(context, onSuccess, onError)
        }
    }

    private fun freshEnough(location: Location): Boolean =
        location.time > 0L && kotlin.math.abs(System.currentTimeMillis() - location.time) <= 120_000L


    private fun fallback(context: Context, onSuccess: (Result) -> Unit, onError: (String) -> Unit) {
        try {
            val client = LocationServices.getFusedLocationProviderClient(context)
            client.lastLocation.addOnSuccessListener { last ->
                if (last != null && valid(last)) onSuccess(Result(last.latitude, last.longitude, true, if (last.hasAccuracy()) last.accuracy else null, last.time))
                else fallbackSystem(context, onSuccess, onError)
            }.addOnFailureListener { fallbackSystem(context, onSuccess, onError) }
        } catch (_: Exception) { fallbackSystem(context, onSuccess, onError) }
    }

    private fun fallbackSystem(context: Context, onSuccess: (Result) -> Unit, onError: (String) -> Unit) {
        try {
            val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val last = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
                .filter { manager.isProviderEnabled(it) }
                .mapNotNull { manager.getLastKnownLocation(it) }
                .filter(::valid)
                .maxByOrNull { it.time }
            if (last != null) onSuccess(Result(last.latitude, last.longitude, true, if (last.hasAccuracy()) last.accuracy else null, last.time))
            else onError("No se obtuvo una ubicación. Activa la ubicación del teléfono o busca tu distrito.")
        } catch (_: SecurityException) { onError("Permiso de ubicación insuficiente.") }
          catch (_: Exception) { onError("No se pudo consultar la ubicación del dispositivo.") }
    }

    private fun valid(location: Location): Boolean = location.latitude.isFinite() && location.longitude.isFinite() &&
        location.latitude in -90.0..90.0 && location.longitude in -180.0..180.0
}
