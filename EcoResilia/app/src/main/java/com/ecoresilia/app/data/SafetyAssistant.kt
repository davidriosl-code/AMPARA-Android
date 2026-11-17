package com.ecoresilia.app.data

import java.text.Normalizer

/** Asistente educativo local, determinista y comprobable. NO es un LLM ni un sistema de alertas. */
object SafetyAssistant {
    private fun normalize(s: String): String = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
    private fun has(text: String, vararg words: String) = words.any { text.contains(it) }

    val suggestions = listOf(
        "Prepara un plan de emergencia familiar en tres pasos.",
        "¿Qué debo incluir en mi mochila según los integrantes de mi familia?",
        "¿Cómo proteger a niños y adultos mayores durante un sismo?",
        "¿Qué hago ante un huaico?",
        "¿Cómo preparar a mis mascotas para evacuar?",
        "¿Cómo verificar avisos oficiales de SENAMHI e INDECI?"
    )

    fun respond(question: String, lastMessages: List<ChatMessage>, place: String, familySize: Int): String {
        val q = normalize(question.trim())
        val lastTopic = lastMessages.firstOrNull { it.role == "user" }?.text?.let(::normalize).orEmpty()
        val urgent = has(q, "atrapad", "sepultad", "herid", "socorro", "rescate", "se esta viniendo", "ya entro el agua", "agua esta entrando", "emergencia ahora")
        if (urgent) return "⚠️ Posible peligro inmediato. Aléjate de cauces, quebradas y laderas inestables si puedes hacerlo de forma segura. No cruces corrientes ni regreses a recoger pertenencias. Solicita ayuda a bomberos (116), Policía (105) o las autoridades locales según la emergencia. EcoResilia no detecta tu situación ni despacha ayuda."
        if (has(q, "sigo", "mas detalle", "más detalle", "explica", "que mas") && has(lastTopic, "huaico", "deslizamiento", "quebrada")) {
            return "Ante señales de huaico: 1) Evita quebradas y riberas. 2) Sigue rutas indicadas por Defensa Civil; no improvises cruces. 3) Reúne a tu familia en un punto acordado. 4) Informa a autoridades si hay personas en riesgo. No uses el mapa de reportes como ruta de evacuación validada."
        }
        return when {
            has(q, "huaico", "aluvion", "quebrada", "lodo", "deslizamiento", "derrumbe") ->
                "Los huaicos pueden activarse con lluvias fuertes. Antes: evita ocupar cauces, revisa rutas de evacuación y prepara documentos. Durante: aléjate de quebradas y flujos de lodo; dirígete a zonas indicadas por las autoridades, sin cruzar corrientes. Después: no ingreses a estructuras dañadas y sigue indicaciones oficiales."
            has(q, "inund", "desbord", "agua sube") ->
                "Ante riesgo de inundación: protege documentos y equipos solo si hay tiempo y es seguro; identifica rutas hacia lugares elevados autorizados; evita entrar al agua, conducir por zonas anegadas o tocar conexiones eléctricas húmedas. Ante ingreso de agua, prioriza salir y pedir ayuda."
            has(q, "mochila", "botiquin", "kit", "provisiones", "comida", "agua potable") ->
                "Para $familySize integrante(s) del hogar: prepara agua y alimentos no perecibles según recomendaciones oficiales y necesidades individuales. Incluye botiquín, linterna, radio, cargador, documentos protegidos, medicinas prescritas e insumos para menores o mascotas. Abre ‘Mochila’ para revisar cantidades, categorías y vencimientos."
            has(q, "nin", "bebe", "adulto mayor", "discapacidad", "silla de ruedas") ->
                "Plan familiar inclusivo: identifica quién necesita apoyo, prepara medicinas e insumos personales y establece un punto de encuentro accesible. Practiquen el traslado con anticipación y acuerden dos contactos alternativos. Nunca dejes a un niño o persona dependiente aislada durante una evacuación."
            has(q, "mascota", "perro", "gato", "animal") ->
                "Incluye a tus mascotas en el plan: transportadora o correa, agua, alimento, medicamentos, identificación y contactos del veterinario. Acostúmbralas a la transportadora y conoce refugios que las acepten. Evita soltarlas durante una emergencia."
            has(q, "sismo", "terremoto", "temblor") ->
                "En un sismo: protégete de objetos que puedan caer, no uses ascensores y sigue los protocolos oficiales de tu edificio. Al terminar, evalúa daños visibles sin entrar en zonas inseguras. Prepara puntos de encuentro y una mochila para después del movimiento."
            has(q, "lluvia", "tormenta", "granizo", "viento", "pronostico", "clima") ->
                "Para $place, consulta los datos meteorológicos de Open‑Meteo en Inicio y los avisos emitidos por SENAMHI. La lluvia prevista NO demuestra que ocurrirá un huaico. Revisa drenajes, evita cauces y acuerda comunicaciones familiares si hay precipitaciones intensas."
            has(q, "alerta", "senamhi", "cenepred", "indeci", "oficial", "verific") ->
                "Distingue fuentes: SENAMHI publica avisos meteorológicos; SIGRID/CENEPRED ofrece cartografía de peligros; SINPAD/INDECI registra emergencias. La cartografía histórica y los reportes ciudadanos NO son avisos de evacuación vigentes. Revisa fecha, emisor, vigencia y área del aviso oficial."
            has(q, "ruta", "evacua", "refugio", "zona segura", "albergue") ->
                "Una ruta segura debe estar identificada por autoridades de Defensa Civil o el municipio. Evita puentes, quebradas y pendientes expuestas; acuerda dos puntos de encuentro y ensaya el traslado. EcoResilia no calcula ni certifica rutas de evacuación."
            has(q, "casa", "vivienda", "pared", "grieta", "techo", "drenaje") ->
                "Puedes completar el Diagnóstico de vivienda para recibir recomendaciones orientativas. Revisa grietas, humedad, drenajes y cercanía a cauces. Si sospechas inestabilidad, consulta a un profesional y a las autoridades; la app no certifica la seguridad estructural."
            has(q, "sin internet", "offline", "sin senal", "sin señal", "bluetooth", "cercanos") ->
                "Sin internet puedes consultar la mochila y el historial local del asistente. Los mapas solo mostrarán teselas previamente guardadas; el clima no se actualiza. Nearby permite intercambiar reportes con dispositivos al alcance, pero NO garantiza una cobertura de 5 km ni sustituye a servicios de emergencia."
            has(q, "contacto", "telefono", "numero", "bombero", "policia", "ambulancia") ->
                "En Perú, como orientación general: Bomberos 116 y Policía Nacional 105. Comprueba también los números de emergencia vigentes de tu localidad y guarda contactos familiares. Si necesitas auxilio inmediato, usa una llamada o los canales oficiales, no este chat."
            has(q, "hola", "buenas", "ayuda", "que puedes") ->
                "Puedo guiarte en preparación ante huaicos, inundaciones, sismos, evacuación familiar, mochilas, mascotas y fuentes oficiales. Trabajo sin IA generativa en esta versión, por lo que algunas preguntas especializadas necesitan consulta con una autoridad. ¿Qué situación deseas preparar?"
            else ->
                "Puedo darte pautas de prevención, pero no tengo información suficiente para responder de forma segura a esa consulta. Prueba preguntarme por huaicos, inundaciones, rutas oficiales, mochila o preparación familiar. Para peligros actuales revisa SENAMHI, INDECI y Defensa Civil."
        }
    }
}
