#!/usr/bin/env python3
"""EcoResilia: SOLO difundir reportes ciudadanos revisados con aprobación explícita.

No publica reportes privados, offline, sin revisar o simulacros. No es un sistema oficial
ni de alertas críticas. El envío automático se encuentra desactivado por defecto.
"""
import os
import re
import sys
from datetime import datetime, timezone, timedelta
from ecoresilia_cloud import REGIONS, admin_services

COLLECTION = "ecoresilia_public_reports_v1"
ALLOWED_KINDS = {"lluvia", "inundacion", "huaico", "deslizamiento", "otro"}


def topic_for(region):
    if region not in REGIONS:
        raise ValueError("Región inválida")
    return f"ecoresilia_v2_{region}_comunidad"


def eligible(document, now=None, moderators=None):
    """Comprobaciones previas al envío, SIN confiar en que existe moderación en el cliente."""
    now = now or datetime.now(timezone.utc)
    moderators = moderators if moderators is not None else set(filter(None, os.getenv("ECORESILIA_MODERATOR_UIDS", "").split(",")))
    if not moderators or not isinstance(document, dict):
        return False
    if document.get("moderation_status") != "approved" or document.get("delivery_status") != "pending":
        return False
    if document.get("is_simulation") is not False:
        return False
    if document.get("approved_by") not in moderators:
        return False
    if document.get("region") not in REGIONS or document.get("kind") not in ALLOWED_KINDS:
        return False
    at = document.get("approved_at")
    if not isinstance(at, datetime) or at.tzinfo is None or at > now or now - at > timedelta(hours=48):
        return False
    description = document.get("summary")
    if not isinstance(description, str) or not 15 <= len(description.strip()) <= 220:
        return False
    if not isinstance(document.get("source_note"), str) or len(document["source_note"].strip()) < 5:
        return False
    return True


def publish(db, messaging):
    if os.getenv("ECORESILIA_COMMUNITY_AUTO_PUBLISH", "false").lower().strip() != "true":
        print("COMUNIDAD: publicación automática desactivada; 0 envíos")
        return 0
    moderators = set(filter(None, os.getenv("ECORESILIA_MODERATOR_UIDS", "").split(",")))
    if not moderators:
        raise RuntimeError("No hay moderadores autorizados: envío bloqueado")
    docs = db.collection(COLLECTION).where("moderation_status", "==", "approved").limit(100).stream()
    sent = 0
    for snap in docs:
        data = snap.to_dict()
        if not eligible(data, moderators=moderators):
            continue
        # Cambiar estado mediante transacción evita envíos simultáneos.
        from google.cloud import firestore
        @firestore.transactional
        def reserve(transaction):
            current = snap.reference.get(transaction=transaction)
            if not current.exists or not eligible(current.to_dict(), moderators=moderators):
                return False
            transaction.update(snap.reference, {"delivery_status": "reserved",
                                                "reserved_at": datetime.now(timezone.utc)})
            return True
        if not reserve(db.transaction()):
            continue
        region = data["region"]
        title = "EcoResilia · Reporte ciudadano revisado · " + region.replace("_", " ").title()
        body = ("Se publicó una observación ciudadana revisada. "
                "No es una alerta oficial; confirma vigencia y fuente en la aplicación.")
        message = messaging.Message(
            topic=topic_for(region),
            data={"title": title, "body": body, "category": "reporte_revisado",
                  "notice_id": "comunidad-" + snap.id,
                  "region": region, "report_id": snap.id},
            android=messaging.AndroidConfig(priority="normal", ttl=timedelta(minutes=30)),
        )
        try:
            message_id = messaging.send(message)
        except Exception as exc:
            # No reintentar automáticamente un resultado ambiguo.
            snap.reference.update({"delivery_status": "requires_review",
                                   "error_type": type(exc).__name__})
            print("ENVÍO AMBIGUO:", snap.id, type(exc).__name__, file=sys.stderr)
            continue
        snap.reference.update({"delivery_status": "sent", "sent_at": datetime.now(timezone.utc),
                               "firebase_message_id": message_id})
        sent += 1
    print("COMUNIDAD: FCM aceptó", sent, "mensajes informativos; entrega no garantizada")
    return sent


if __name__ == "__main__":
    if os.getenv("ECORESILIA_COMMUNITY_AUTO_PUBLISH", "false").lower().strip() != "true":
        print("COMUNIDAD: desactivado por defecto, sin leer credenciales ni enviar")
        sys.exit(0)
    database, client = admin_services()
    publish(database, client)
