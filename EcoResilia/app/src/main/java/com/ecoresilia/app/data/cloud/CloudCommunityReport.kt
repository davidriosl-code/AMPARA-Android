package com.ecoresilia.app.data.cloud

/** Datos propios del usuario en Firestore; nunca constituye un aviso oficial. */
data class CloudCommunityReport(
    val id: String = "",
    val userId: String = "",
    val category: String = "",
    val description: String = "",
    val zone: String = "",
    val createdAtEpochMs: Long = 0L
)
