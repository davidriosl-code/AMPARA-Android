package com.ecoresilia.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ecoresilia.app.data.HomeAssessment
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Puntuación de preparación educativa. No constituye diagnóstico técnico ni puntaje de riesgo territorial. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RiskScreen(assessments: List<HomeAssessment>, onSave: (Int, String, String) -> Unit) {
    var building by remember { mutableStateOf("Adobe") }
    var roof by remember { mutableStateOf("Calamina") }
    var drainage by remember { mutableStateOf(false) }
    var nearStream by remember { mutableStateOf(false) }
    var slope by remember { mutableStateOf(false) }
    var evacuation by remember { mutableStateOf(false) }
    var emergencyBag by remember { mutableStateOf(false) }
    var familyPlan by remember { mutableStateOf(false) }
    var evaluated by remember { mutableStateOf(false) }

    val points = (if (building == "Adobe") 2 else if (building == "Madera") 1 else 0) +
        (if (roof == "Calamina") 1 else 0) + (if (!drainage) 2 else 0) +
        (if (nearStream) 3 else 0) + (if (slope) 2 else 0) +
        (if (!evacuation) 1 else 0) + (if (!emergencyBag) 1 else 0) +
        (if (!familyPlan) 1 else 0)
    val priority = when {
        points >= 8 -> "Acciones prioritarias pendientes"
        points >= 4 -> "Aspectos importantes por mejorar"
        else -> "Mantén y revisa tu preparación"
    }
    val recommendation = buildString {
        if (nearStream) append("Consulta los mapas oficiales de quebradas y rutas autorizadas; no ocupes cauces. ")
        if (slope) append("Solicita evaluación profesional de taludes y laderas. ")
        if (!drainage) append("Revisa el drenaje y evacuación de aguas pluviales. ")
        if (building == "Adobe") append("Consulta a un profesional si hay grietas, humedad o daños estructurales. ")
        if (!evacuation) append("Identifica rutas oficiales y un punto de encuentro. ")
        if (!familyPlan) append("Practica un plan familiar de comunicación. ")
        if (!emergencyBag) append("Prepara la mochila de emergencia. ")
        append("Estos consejos NO determinan si tu vivienda es segura ante un desastre.")
    }
    val summary = "$building / $roof / Drenaje:$drainage / Quebrada:$nearStream / Ladera:$slope / Ruta:$evacuation / Mochila:$emergencyBag / Plan:$familyPlan"
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        SectionHeading("Diagnóstico de preparación", "Cuestionario orientativo de vivienda y hogar")
        InfoNote("La puntuación ordena tareas de prevención. NO calcula probabilidades de desastre ni reemplaza una evaluación profesional.")
        FrostCard {
            Text("1 · Vivienda y ubicación", fontWeight = FontWeight.Bold)
            Text("Material predominante")
            SingleChoice("Adobe", listOf("Adobe", "Concreto", "Madera"), building) { building = it; evaluated = false }
            Text("Tipo de techo")
            SingleChoice("Calamina", listOf("Calamina", "Teja", "Losa"), roof) { roof = it; evaluated = false }
            RiskCheck("¿Tiene drenaje adecuado?", drainage) { drainage = it; evaluated = false }
            RiskCheck("¿Está cerca de una quebrada o cauce?", nearStream) { nearStream = it; evaluated = false }
            RiskCheck("¿Está en ladera o pendiente?", slope) { slope = it; evaluated = false }
        }
        FrostCard {
            Text("2 · Preparación familiar", fontWeight = FontWeight.Bold)
            RiskCheck("¿Conoces una ruta oficial de evacuación?", evacuation) { evacuation = it; evaluated = false }
            RiskCheck("¿Tu mochila está preparada?", emergencyBag) { emergencyBag = it; evaluated = false }
            RiskCheck("¿Tu familia tiene punto de encuentro?", familyPlan) { familyPlan = it; evaluated = false }
            PrimaryAction("Evaluar preparación", onClick = { evaluated = true }, modifier = Modifier.fillMaxWidth())
        }
        if (evaluated) FrostCard {
            Text(priority, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text("Indicador interno: $points puntos pendientes (0 es mejor)")
            Text(recommendation)
            PrimaryAction("Guardar evaluación", onClick = { onSave(points, summary, recommendation); evaluated = false }, modifier = Modifier.fillMaxWidth())
        }
        SectionHeading("Historial guardado", "Disponible sin conexión")
        if (assessments.isEmpty()) InfoNote("Todavía no has guardado evaluaciones.")
        assessments.take(5).forEach { assessment ->
            FrostCard {
                Text(SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(assessment.createdAt)), fontWeight = FontWeight.SemiBold)
                Text("${assessment.score} puntos pendientes")
                Text(assessment.recommendation, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun SingleChoice(defaultValue: String, choices: List<String>, selected: String, onSelected: (String) -> Unit) {
    Column {
        choices.forEach { option ->
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                RadioButton(selected = selected == option, onClick = { onSelected(option) })
                Text(option)
            }
        }
    }
}

@Composable
private fun RiskCheck(label: String, selected: Boolean, onSelected: (Boolean) -> Unit) {
    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Checkbox(checked = selected, onCheckedChange = onSelected)
        Text(label, modifier = Modifier.weight(1f))
    }
}
