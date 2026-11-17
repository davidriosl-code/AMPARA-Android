package com.ecoresilia.app.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.outlined.Backpack
import androidx.compose.material.icons.outlined.PeopleOutline
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ecoresilia.app.data.BagExpiryPolicy
import com.ecoresilia.app.data.BagRecommendations
import com.ecoresilia.app.data.ExpiryFlag
import com.ecoresilia.app.data.EmergencyItem
import com.ecoresilia.app.data.HouseholdProfile
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit

private val bagCategories = listOf("Agua", "Alimentos", "Salud", "Documentos", "Herramientas", "Abrigo", "Higiene", "Mascotas", "General")

@Composable
internal fun BagScreen(
    bag: List<EmergencyItem>,
    household: HouseholdProfile,
    reminderEnabled: Boolean,
    onReminderEnabled: (Boolean) -> Unit,
    expiryRemindersEnabled: Boolean,
    onExpiryRemindersEnabled: (Boolean) -> Unit,
    onHouseholdChange: (HouseholdProfile) -> Unit,
    onAdd: (String, String, Int, String) -> Unit,
    onUpdate: (EmergencyItem) -> Unit,
    onDelete: (EmergencyItem) -> Unit,
    onToggle: (EmergencyItem) -> Unit
) {
    var editing by remember { mutableStateOf<EmergencyItem?>(null) }
    var showingDialog by remember { mutableStateOf(false) }
    var selectedCategory by rememberSaveable { mutableStateOf("Todos") }
    var profileOpen by rememberSaveable { mutableStateOf(false) }
    var recommendationsOpen by rememberSaveable { mutableStateOf(false) }
    val reviewPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) onReminderEnabled(true)
    }
    val expiryPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) onExpiryRemindersEnabled(true)
    }
    val categories = bagCategories.filter { category -> bag.any { it.category == category } }
    val tabs = listOf("Todos") + categories
    val visible = if (selectedCategory == "Todos") bag else bag.filter { it.category == selectedCategory }
    val prepared = bag.count { it.prepared }
    val expired = bag.count { BagExpiryPolicy.status(it.expiresOn) == ExpiryFlag.EXPIRED }
    val upcoming = bag.count { BagExpiryPolicy.status(it.expiresOn) in setOf(ExpiryFlag.SOON, ExpiryFlag.TODAY) }
    val recommendations = remember(household) { BagRecommendations.forFamily(household) }
    val recommendationsPending = recommendations.count { recommendation ->
        val item = bag.firstOrNull { it.name.equals(recommendation.name, ignoreCase = true) ||
            recommendation.aliases.any { alias -> itemNameMatches(it.name, alias) } }
        item == null || item.quantity < recommendation.quantity
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(top = 14.dp, bottom = 22.dp)
    ) {
        item { AmparaPageBanner("Mochila inteligente", "Preparación familiar, con o sin internet") }
        item {
            Surface(shape = RoundedCornerShape(26.dp),
                color = Color.White.copy(alpha = .28f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = .88f))) {
                Column(Modifier.fillMaxWidth().background(Brush.verticalGradient(
                    listOf(Color(0xFFE9F5FF), Color(0xFFD8ECFB), Color(0xFFFFF2E1))))
                    .padding(20.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Outlined.Backpack, contentDescription = null,
                            tint = Color(0xFFFF9523), modifier = Modifier.size(42.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Preparación familiar", style = MaterialTheme.typography.titleLarge,
                                color = Color(0xFF184357))
                            Text("$prepared de ${bag.size} elementos preparados",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF5D7B8B))
                        }
                        Text("${if (bag.isEmpty()) 0 else 100 * prepared / bag.size}%",
                            style = MaterialTheme.typography.headlineMedium,
                            color = Color(0xFF184357))
                    }
                    LinearProgressIndicator(
                        progress = { if (bag.isEmpty()) 0f else prepared.toFloat() / bag.size },
                        modifier = Modifier.fillMaxWidth().height(7.dp),
                        color = Color(0xFFFF9928), trackColor = Color(0xFFFFE8CF))
                    Text("${household.people} personas · ${household.pets} mascotas · ${maxOf(1, (household.people + 1) / 2)} mochila(s) orientativas",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF5E7C8D))
                    Text("El progreso cuenta elementos marcados; no certifica una mochila completa.",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF6E8794))
                    if (expired > 0 || upcoming > 0) {
                        Surface(color = Color.White.copy(alpha = .86f),
                            shape = RoundedCornerShape(14.dp)) {
                            Text("Revisar vencimientos: $expired vencido(s), $upcoming próximos",
                                modifier = Modifier.padding(10.dp),
                                color = Color(0xFF9A3F43), style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
        item {
            FrostCard {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Mi hogar", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                    TextButton(onClick = { profileOpen = !profileOpen }) {
                        Text(if (profileOpen) "Ocultar" else "Personalizar")
                    }
                }
                Text("Distingue adultos, niños, bebés, adultos mayores y mascotas para adaptar las sugerencias.",
                    style = MaterialTheme.typography.bodySmall)
                if (profileOpen) {
                    FamilyCounter("Adultos", household.adults,
                        onMinus = { onHouseholdChange(household.copy(adults = household.adults - 1)) },
                        onPlus = { onHouseholdChange(household.copy(adults = household.adults + 1)) },
                        canMinus = household.adults > 0 && household.people > 1,
                        canPlus = household.people < 20)
                    FamilyCounter("Niños", household.children,
                        onMinus = { onHouseholdChange(household.copy(children = household.children - 1)) },
                        onPlus = { onHouseholdChange(household.copy(children = household.children + 1)) },
                        canMinus = household.children > 0 && household.people > 1,
                        canPlus = household.people < 20)
                    FamilyCounter("Bebés", household.babies,
                        onMinus = { onHouseholdChange(household.copy(babies = household.babies - 1)) },
                        onPlus = { onHouseholdChange(household.copy(babies = household.babies + 1)) },
                        canMinus = household.babies > 0 && household.people > 1,
                        canPlus = household.people < 20)
                    FamilyCounter("Adultos mayores", household.olderAdults,
                        onMinus = { onHouseholdChange(household.copy(olderAdults = household.olderAdults - 1)) },
                        onPlus = { onHouseholdChange(household.copy(olderAdults = household.olderAdults + 1)) },
                        canMinus = household.olderAdults > 0 && household.people > 1,
                        canPlus = household.people < 20)
                    FamilyCounter("Mascotas", household.pets,
                        onMinus = { onHouseholdChange(household.copy(pets = household.pets - 1)) },
                        onPlus = { onHouseholdChange(household.copy(pets = household.pets + 1)) },
                        canMinus = household.pets > 0,
                        canPlus = household.pets < 20)
                    InfoNote("Tu perfil se guarda solo en este dispositivo. Las mascotas no se cuentan como integrantes humanos.")
                }
            }
        }
        item {
            FrostCard {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Sugerencias para tu familia", fontWeight = FontWeight.Bold)
                        Text("$recommendationsPending de ${recommendations.size} sugerencias por revisar",
                            style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = { recommendationsOpen = !recommendationsOpen }) {
                        Text(if (recommendationsOpen) "Ocultar" else "Ver")
                    }
                }
                Text("Referencias orientativas para las primeras 24 h; no cambian tu inventario sin tu permiso.",
                    style = MaterialTheme.typography.bodySmall)
                if (recommendationsOpen) {
                    recommendations.forEach { suggestion ->
                        val current = bag.firstOrNull { itemNameMatches(it.name, suggestion.name) ||
                            suggestion.aliases.any { alias -> itemNameMatches(it.name, alias) } }
                        HorizontalDivider()
                        Text(suggestion.name, fontWeight = FontWeight.SemiBold)
                        Text("Referencia: ${suggestion.quantity} ${suggestion.unit} · ${suggestion.reason}",
                            style = MaterialTheme.typography.bodySmall)
                        Text(if (current == null) "No está en el inventario" else "Inventario: ${current.quantity} · ${current.category}",
                            style = MaterialTheme.typography.labelSmall)
                        if (current == null) {
                            TextButton(onClick = { onAdd(suggestion.name, suggestion.category, suggestion.quantity, "") }) {
                                Text("Añadir sugerencia")
                            }
                        } else if (current.quantity < suggestion.quantity) {
                            TextButton(onClick = { onUpdate(current.copy(quantity = suggestion.quantity, prepared = false)) }) {
                                Text("Ajustar a ${suggestion.quantity} y marcar pendiente")
                            }
                        }
                    }
                    Text("Basado en materiales de preparación del INDECI. Ajusta necesidades reales, peso, edades y tratamientos personales.",
                        style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        item {
            FrostCard {
                Text("Recordatorios de mi mochila", fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Revisar mochila cada día", modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall)
                    LiquidGlassSwitch(checked = reminderEnabled, label = "Revisar mochila cada día", onCheckedChange = { enabled ->
                        if (enabled && Build.VERSION.SDK_INT >= 33) reviewPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        else onReminderEnabled(enabled)
                    })
                }
                HorizontalDivider()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Avisarme de vencimientos (7 días antes)", modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall)
                    LiquidGlassSwitch(checked = expiryRemindersEnabled, label = "Avisarme de vencimientos", onCheckedChange = { enabled ->
                        if (enabled && Build.VERSION.SDK_INT >= 33) expiryPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        else onExpiryRemindersEnabled(enabled)
                    })
                }
                Text("Te recordaremos revisar los elementos de tu mochila. Los avisos pueden retrasarse para ahorrar batería.",
                    style = MaterialTheme.typography.labelSmall)
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Inventario", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                PrimaryAction("Añadir", onClick = { editing = null; showingDialog = true })
            }
        }
        item {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                tabs.forEach { category ->
                    FilterChip(selected = selectedCategory == category,
                        onClick = { selectedCategory = category },
                        label = { Text(category) },
                        shape = RoundedCornerShape(16.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = Color.White.copy(alpha = .85f),
                            selectedContainerColor = Color(0xFFFFE8CF),
                            selectedLabelColor = Color(0xFFB86408)))
                }
            }
        }
        if (visible.isEmpty()) item { InfoNote("No hay artículos en esta categoría. Añade uno desde el botón superior.") }
        items(visible, key = { it.id }) { item ->
            FrostCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = item.prepared, onCheckedChange = { onToggle(item) })
                    Column(Modifier.weight(1f)) {
                        Text(item.name, fontWeight = FontWeight.SemiBold)
                        Text("${item.category} · Cantidad: ${item.quantity}", style = MaterialTheme.typography.bodySmall)
                        if (item.expiresOn.isNotBlank()) {
                            val status = BagExpiryPolicy.status(item.expiresOn)
                            val label = when (status) {
                                ExpiryFlag.EXPIRED -> "VENCIDO"
                                ExpiryFlag.TODAY -> "VENCE HOY"
                                ExpiryFlag.SOON -> "PRÓXIMO A VENCER"
                                else -> "vigente"
                            }
                            Text("Vence: ${item.expiresOn} · $label",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (status in setOf(ExpiryFlag.EXPIRED, ExpiryFlag.TODAY, ExpiryFlag.SOON))
                                    MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    IconButton(onClick = { editing = item; showingDialog = true }) {
                        Icon(Icons.Default.Edit, "Editar")
                    }
                    IconButton(onClick = { onDelete(item) }) {
                        Icon(Icons.Default.Delete, "Eliminar")
                    }
                }
            }
        }
    }
    if (showingDialog) BagItemDialog(
        initial = editing,
        onDismiss = { showingDialog = false },
        onSave = { name, category, quantity, expiry ->
            val previous = editing
            if (previous == null) onAdd(name, category, quantity, expiry)
            else onUpdate(previous.copy(name = name, category = category, quantity = quantity, expiresOn = expiry))
            showingDialog = false
        }
    )
}

private fun itemNameMatches(value: String, expected: String): Boolean = value.trim().equals(expected.trim(), ignoreCase = true)

@Composable
private fun FamilyCounter(
    title: String, value: Int, onMinus: () -> Unit, onPlus: () -> Unit,
    canMinus: Boolean, canPlus: Boolean
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
        IconButton(onClick = onMinus, enabled = canMinus) { Text("−", fontWeight = FontWeight.Bold) }
        Text("$value", fontWeight = FontWeight.Bold)
        IconButton(onClick = onPlus, enabled = canPlus) { Text("+", fontWeight = FontWeight.Bold) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BagItemDialog(initial: EmergencyItem?, onDismiss: () -> Unit,
                          onSave: (String, String, Int, String) -> Unit) {
    var name by remember(initial) { mutableStateOf(initial?.name.orEmpty()) }
    var category by remember(initial) { mutableStateOf(initial?.category ?: "General") }
    var quantity by remember(initial) { mutableStateOf((initial?.quantity ?: 1).toString()) }
    var expiry by remember(initial) { mutableStateOf(initial?.expiresOn.orEmpty()) }
    var expanded by remember { mutableStateOf(false) }
    var calendarVisible by remember { mutableStateOf(false) }
    val validDate = expiry.isBlank() || try { LocalDate.parse(expiry); true } catch (_: Exception) { false }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Añadir elemento" else "Editar elemento") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it.take(80) }, label = { Text("Nombre") }, singleLine = true)
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                    OutlinedTextField(category, {}, readOnly = true, label = { Text("Categoría") }, modifier = Modifier.menuAnchor())
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        bagCategories.forEach { option ->
                            DropdownMenuItem(text = { Text(option) }, onClick = { category = option; expanded = false })
                        }
                    }
                }
                OutlinedTextField(quantity, { quantity = it.filter(Char::isDigit).take(3) },
                    label = { Text("Cantidad") }, singleLine = true)
                OutlinedTextField(expiry, { expiry = it.take(10) },
                    label = { Text("Vencimiento (AAAA-MM-DD), opcional") }, singleLine = true, isError = !validDate)
                TextButton(onClick = { calendarVisible = true }) { Text("Elegir fecha en calendario") }
                if (!validDate) Text("Escribe una fecha válida (por ejemplo 2027-10-19)",
                    color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name.trim(), category, quantity.toIntOrNull() ?: 1, expiry) },
                enabled = name.isNotBlank() && (quantity.toIntOrNull() ?: 0) in 1..999 && validDate) {
                Text("Guardar")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
    if (calendarVisible) {
        val selected = rememberDatePickerState(
            initialSelectedDateMillis = try {
                LocalDate.parse(expiry).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            } catch (_: DateTimeParseException) { null }
        )
        DatePickerDialog(
            onDismissRequest = { calendarVisible = false },
            confirmButton = {
                TextButton(onClick = {
                    selected.selectedDateMillis?.let { millis ->
                        expiry = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toString()
                    }
                    calendarVisible = false
                }) { Text("Elegir") }
            },
            dismissButton = { TextButton(onClick = { calendarVisible = false }) { Text("Cancelar") } }
        ) { DatePicker(state = selected) }
    }
}
