package com.ecoresilia.app.data.cloud

/** Identifica el mismo reporte antes y después del envío, incluso entre distintas cuentas. */
object CommunityEntryKeys {
    fun cloud(ownerUid: String, documentId: String): String = "cloud:$ownerUid:$documentId"
}
