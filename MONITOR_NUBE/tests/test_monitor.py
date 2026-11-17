import sys
import unittest
from datetime import datetime, timedelta, timezone
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'monitor'))
from ecoresilia_cloud import group_data, slug, source_date

UTC = timezone.utc


def row(oid, region, when, district='Distrito', level=''):
    return {'attributes': {'OBJECTID': oid, 'NOMBDEP': region,
            'FECHA': int(when.timestamp() * 1000), 'NOMBDIST': district,
            'NOMBPROV': 'Provincia', 'NIVEL': level}}


class CloudMonitorTests(unittest.TestCase):
    def setUp(self):
        self.now = datetime(2026, 11, 12, 3, 0, tzinfo=UTC)

    def test_group_by_region_date_type_and_dedupe(self):
        data = {'features': [row(1, 'Puno', self.now - timedelta(hours=3)),
                             row(1, 'Puno', self.now - timedelta(hours=3)),
                             row(2, 'Puno', self.now - timedelta(hours=3)),
                             row(3, 'Arequipa', self.now - timedelta(hours=3))]}
        result = group_data(data, self.now)
        self.assertEqual(len(result), 2)
        self.assertEqual(next(g for g in result if g['region']=='puno')['filas'], 2)

    def test_ignores_old_and_future(self):
        data = {'features': [row(1, 'Lima', self.now - timedelta(hours=25)),
                             row(2, 'Lima', self.now + timedelta(hours=3))]}
        self.assertEqual(group_data(data, self.now), [])

    def test_invalid_region(self):
        self.assertIsNone(slug('Desconocida'))
        self.assertEqual(slug('San Martín'), 'san_martin')

    def test_partial_response_fails_closed(self):
        with self.assertRaises(ValueError):
            group_data({'features': [], 'exceededTransferLimit': True}, self.now)

    def test_source_dates(self):
        self.assertIsNone(source_date('fecha-invalida'))
        self.assertEqual(source_date('2026-11-12T03:00:00Z'), self.now)


if __name__ == '__main__':
    unittest.main()
