import os
import sys
import unittest
from datetime import datetime, timedelta, timezone
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'monitor'))
from community_notifications import eligible, topic_for

NOW = datetime(2026,11,13,2,0,tzinfo=timezone.utc)
DOC = dict(moderation_status='approved', delivery_status='pending', is_simulation=False,
           approved_by='uid_moderador_demo', region='arequipa', kind='huaico',
           approved_at=NOW-timedelta(hours=1),
           summary='Un reporte ciudadano revisado sin emergencia confirmada',
           source_note='Fuentes adjuntas y revisadas')

class NotificationSafetyTests(unittest.TestCase):
    def test_approved_report_is_eligible_only_for_authorized_moderator(self):
        self.assertTrue(eligible(DOC, NOW, {'uid_moderador_demo'}))
        self.assertFalse(eligible(DOC, NOW, {'otro'}))
    def test_never_send_simulations(self):
        self.assertFalse(eligible(dict(DOC, is_simulation=True), NOW, {'uid_moderador_demo'}))
    def test_never_send_unreviewed(self):
        self.assertFalse(eligible(dict(DOC, moderation_status='unreviewed'), NOW, {'uid_moderador_demo'}))
    def test_never_send_old_reports(self):
        self.assertFalse(eligible(dict(DOC, approved_at=NOW-timedelta(days=3)), NOW, {'uid_moderador_demo'}))
    def test_regional_topic(self):
        self.assertEqual(topic_for('arequipa'),'ecoresilia_v2_arequipa_comunidad')
        with self.assertRaises(ValueError):topic_for('region_falsa')

if __name__=='__main__':unittest.main()
