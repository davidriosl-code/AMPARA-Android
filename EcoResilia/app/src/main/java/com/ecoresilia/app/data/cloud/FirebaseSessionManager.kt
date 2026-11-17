package com.ecoresilia.app.data.cloud

import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/** La identidad Firebase es independiente de la identidad Nearby local. */
class FirebaseSessionManager(private val auth: FirebaseAuth) {
    val userChanges: Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    val currentUser: FirebaseUser? get() = auth.currentUser

    suspend fun ensureAnonymousSession(): FirebaseUser {
        auth.currentUser?.let { return it }
        return auth.signInAnonymously().await().user
            ?: error("No se recibió una sesión de Firebase")
    }

    suspend fun linkOrRegister(email: String, password: String): FirebaseUser {
        val credential = EmailAuthProvider.getCredential(email.trim(), password)
        val user = auth.currentUser
        require(user == null || user.isAnonymous) { "Cierra la sesión registrada antes de crear otra cuenta" }
        val result = if (user?.isAnonymous == true) {
            // Conserva el UID y los reportes del usuario anónimo si Firebase permite la vinculación.
            user.linkWithCredential(credential).await()
        } else {
            auth.createUserWithEmailAndPassword(email.trim(), password).await()
        }
        return result.user ?: error("Firebase no confirmó el registro")
    }

    suspend fun signIn(email: String, password: String): FirebaseUser =
        auth.signInWithEmailAndPassword(email.trim(), password).await().user
            ?: error("Firebase no confirmó el inicio de sesión")

    fun signOutRegisteredUser() {
        require(auth.currentUser?.isAnonymous != true) {
            "Para no perder reportes temporales, primero vincula tu cuenta con un correo"
        }
        auth.signOut()
    }
}
