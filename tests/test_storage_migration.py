"""No production data touched: regression tests use isolated temporary directories."""
import importlib.util
from pathlib import Path
import tempfile
import unittest

SPEC = importlib.util.spec_from_file_location('storage_migration', Path(__file__).resolve().parents[1] / 'tools/storage_migration.py')
migration = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(migration)


class StorageMigrationTests(unittest.TestCase):
    def test_missing_source_is_not_an_empty_inventory(self):
        with tempfile.TemporaryDirectory(prefix='voxgest_migration_test_') as temp:
            with self.assertRaises(FileNotFoundError):
                migration.tree(Path(temp) / 'missing')

    def test_rejects_canonical_root_as_destination(self):
        with tempfile.TemporaryDirectory(prefix='voxgest_migration_test_') as temp:
            root = Path(temp)
            with self.assertRaises(RuntimeError):
                migration.guarded_destination(root, root)

    def test_rejects_destination_outside_canonical(self):
        with tempfile.TemporaryDirectory(prefix='voxgest_migration_test_') as temp:
            root = Path(temp)
            with self.assertRaises(RuntimeError):
                migration.guarded_destination(root / 'outside/file', root / 'canonical')

    def test_preserves_existing_different_file_and_source(self):
        with tempfile.TemporaryDirectory(prefix='voxgest_migration_test_') as temp:
            root = Path(temp)
            src, dest, report = root / 'source.bin', root / 'canonical/target.bin', root / 'reports'
            src.write_bytes(b'original')
            dest.parent.mkdir()
            dest.write_bytes(b'different')
            report.mkdir()
            with self.assertRaises(RuntimeError):
                migration.copy_job(dict(id='test', source=str(src), destination=str(dest), classification='TEST'), report, dest.parent)
            self.assertEqual(b'original', src.read_bytes())
            self.assertEqual(b'different', dest.read_bytes())

    def test_copy_preserves_bytes_timestamp_and_supports_artifact_name(self):
        with tempfile.TemporaryDirectory(prefix='voxgest_migration_test_') as temp:
            root = Path(temp)
            src, dest, report = root / 'source.bin', root / 'canonical/archive.bin', root / 'reports'
            src.write_bytes(b'original')
            report.mkdir()
            result = migration.copy_job(dict(id='test', source=str(src), destination=str(dest), classification='TEST'), report, dest.parent)
            self.assertEqual('PASS', result['hash_verification'])
            self.assertEqual('PASS', result['timestamp_verification'])
            self.assertEqual(0, result['bytes_reclaimed'])
            self.assertEqual(src.read_bytes(), dest.read_bytes())

    def test_rejects_source_inside_destination_recursion(self):
        with tempfile.TemporaryDirectory(prefix='voxgest_migration_test_') as temp:
            root = Path(temp)
            src, report = root / 'canonical/source', root / 'reports'
            src.mkdir(parents=True)
            report.mkdir()
            with self.assertRaises(RuntimeError):
                migration.copy_job(dict(id='test', source=str(src), destination=str(src / 'copy'), classification='TEST'), report, root / 'canonical')


if __name__ == '__main__':
    unittest.main()
