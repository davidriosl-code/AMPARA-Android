package com.ecoresilia.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ecoresilia.app.data.ChatMessage
import com.ecoresilia.app.data.SafetyAssistant

@Composable
internal fun AssistantScreen(
    messages: List<ChatMessage>, online: Boolean, busy: Boolean,
    onOnline: (Boolean) -> Unit, onAsk: (String) -> Unit, onClear: () -> Unit
) {
    var question by remember { mutableStateOf("") }
    var confirmClear by remember { mutableStateOf(false) }
    val scroll = rememberLazyListState()
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) scroll.animateScrollToItem(messages.lastIndex)
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(11.dp)) {
        AmparaPageBanner("Asistente AMPARA", "Tu ayuda para estar preparado")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (online) "Respuestas en línea" else "Consejos sin internet",
                modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium,
                color = Color(0xFF58748B))
            TextButton(onClick = { confirmClear = true }, enabled = messages.isNotEmpty()) {
                Text("Limpiar", color = Color(0xFFD1730E))
            }
        }
        Surface(color = Color.White.copy(alpha = .78f),
            shape = RoundedCornerShape(22.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = .95f))) {
            Row(Modifier.fillMaxWidth().padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Outlined.AutoAwesome, contentDescription = null,
                    tint = Color(0xFFFF9726))
                Column(Modifier.weight(1f)) {
                    Text(if (online) "Asistente con IA" else "Guía sin internet",
                        style = MaterialTheme.typography.titleMedium, color = Color(0xFF234759))
                    Text(if (online) "Responde con ayuda de internet" else "Ayuda disponible sin señal",
                        style = MaterialTheme.typography.bodySmall, color = Color(0xFF6B8693))
                }
                LiquidGlassSwitch(checked = online, onCheckedChange = onOnline, label = "Respuestas del asistente en línea")
            }
        }
        InfoNote(if (online)
            "No compartas información personal. Para emergencias, sigue a las autoridades."
        else "Consejos para prepararte; no reemplazan las indicaciones oficiales.")
        LazyColumn(modifier = Modifier.weight(1f), state = scroll,
            verticalArrangement = Arrangement.spacedBy(11.dp),
            contentPadding = PaddingValues(top = 3.dp, bottom = 8.dp)) {
            if (messages.isEmpty()) {
                item {
                    FrostCard {
                        Text("Hola, ¿en qué puedo ayudarte?", style = MaterialTheme.typography.titleMedium,
                            color = Color(0xFF234759))
                        Text("Podemos preparar un plan, revisar tu mochila o explicar cómo actuar ante un riesgo.",
                            color = Color(0xFF627E8D), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                items(SafetyAssistant.suggestions) { suggestion ->
                    Surface(onClick = { onAsk(suggestion) },
                        color = Color.White.copy(alpha = .82f),
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = .92f))) {
                        Text(suggestion, Modifier.fillMaxWidth().padding(15.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF376277))
                    }
                }
            } else {
                items(messages, key = { it.id }) { message ->
                    val isUser = message.role == "user"
                    Row(Modifier.fillMaxWidth(),
                        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start) {
                        Surface(modifier = Modifier.fillMaxWidth(.91f),
                            shape = RoundedCornerShape(20.dp),
                            color = if (isUser) Color(0xFFFFEAD7).copy(alpha = .92f)
                                    else Color.White.copy(alpha = .88f),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = .9f))) {
                            Column(Modifier.padding(15.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(if (isUser) "Tú" else "AMPARA",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isUser) Color(0xFFC46B0F) else Color(0xFF4F7387))
                                Text(message.text, style = MaterialTheme.typography.bodyMedium,
                                    color = Color(0xFF294A5C))
                            }
                        }
                    }
                }
            }
        }
        if (messages.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    "Plan familiar" to "Ayúdame a preparar un plan familiar de emergencia considerando mi hogar.",
                    "Mochila" to "Revisa qué suministros debo priorizar en mi mochila.",
                    "Avisos" to "Explícame cómo distinguir un boletín oficial de un reporte ciudadano."
                ).forEach { (label, prompt) ->
                    SuggestionChip(onClick = { onAsk(prompt) },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = Color.White.copy(alpha = .76f),
                            labelColor = Color(0xFF44687D)
                        ),
                        label = { Text(label) })
                }
            }
        }
        if (busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = Color(0xFFFF9B2F))
        Surface(
            color = Color.White.copy(alpha = .22f),
            shape = RoundedCornerShape(26.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = .92f))
        ) {
            Box(
                Modifier.background(
                    Brush.horizontalGradient(
                        listOf(Color(0x33FFFFFF), Color(0x26FFE8CC), Color(0x20FFFFFF))
                    )
                )
            ) {
                Row(Modifier.padding(start = 10.dp, end = 10.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextField(
                        value = question,
                        onValueChange = { question = it.take(800) },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Escribe tu pregunta…", color = Color(0xFF7A90A0)) },
                        maxLines = 3,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent,
                            cursorColor = Color(0xFFDD7B09)
                        )
                    )
                    FilledIconButton(onClick = { onAsk(question); question = "" },
                        enabled = question.isNotBlank() && !busy,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = Color(0xFFFF9A28)),
                        modifier = Modifier.size(46.dp)) {
                        Icon(Icons.Outlined.Send, contentDescription = "Enviar")
                    }
                }
            }
        }
    }
    if (confirmClear) AlertDialog(
        onDismissRequest = { confirmClear = false },
        title = { Text("¿Borrar conversación?") },
        text = { Text("Eliminará el historial local de este dispositivo.") },
        confirmButton = { TextButton(onClick = { onClear(); confirmClear = false }) { Text("Borrar") } },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancelar") } }
    )
}
