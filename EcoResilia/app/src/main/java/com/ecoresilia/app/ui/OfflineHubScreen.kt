package com.ecoresilia.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ecoresilia.app.data.EmergencyItem

/** No realiza llamadas de red: acceso a guías y a módulos que guardan datos en el teléfono. */
@Composable
internal fun OfflineHubScreen(
    bag: List<EmergencyItem>, localReports: Int,
    onNavigate: (EcoScreen) -> Unit
) {
    val ready = bag.count { it.prepared }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        SectionHeading("Sin internet", "Tu preparación siempre a mano")
        InfoNote("Esta pantalla contiene recomendaciones generales, no alertas en tiempo real ni rutas verificadas. Ante una amenaza sigue instrucciones de las autoridades.")
        FrostCard {
            Text("Datos almacenados en este celular", fontWeight = FontWeight.Bold)
            Text("Mochila: $ready/${bag.size} artículos marcados como preparados")
            Text("Reportes guardados: $localReports")
            OutlinedButton(onClick = { onNavigate(EcoScreen.BAG) }, modifier = Modifier.fillMaxWidth()) {
                Text("Abrir Mi Mochila")
            }
            OutlinedButton(onClick = { onNavigate(EcoScreen.COMMUNITY) }, modifier = Modifier.fillMaxWidth()) {
                Text("Consultar reportes guardados")
            }
            OutlinedButton(onClick = { onNavigate(EcoScreen.ALERTS) }, modifier = Modifier.fillMaxWidth()) {
                Text("Abrir buzón de avisos recibidos")
            }
        }
        FrostCard {
            Text("Ante un sismo", fontWeight = FontWeight.Bold)
            Text("Durante el movimiento: protégete de objetos que puedan caer, aléjate de ventanas y no uses ascensores. Cuando sea seguro, evacúa según el plan de tu vivienda y las indicaciones oficiales.")
        }
        FrostCard {
            Text("Ante un huaico o inundación", fontWeight = FontWeight.Bold)
            Text("No cruces quebradas, ríos, corrientes de agua ni puentes inundados. Si existe riesgo inmediato, busca un lugar seguro y sigue las indicaciones de las autoridades. No supongas que una ruta es segura por la ausencia de reportes.")
        }
        FrostCard {
            Text("Comunicación familiar", fontWeight = FontWeight.Bold)
            Text("Acuerda un punto de encuentro seguro cuando sea posible. Ten a mano teléfonos de emergencia escritos en papel, radio a pilas, agua, medicamentos esenciales y artículos para niños, mayores y mascotas según tu familia.")
        }
        FrostCard {
            Text("Qué ocurre si no hay señal", fontWeight = FontWeight.Bold)
            Text("Puedes consultar lo que ya guardaste. Los reportes pendientes se enviarán cuando vuelva internet. Para avisos nuevos necesitas conexión.")
        }
    }
}
