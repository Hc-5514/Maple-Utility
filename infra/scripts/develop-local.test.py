"""Unit regression; no production connection, Docker invocation, or real secrets."""
import base64
import contextlib
import hashlib
import importlib.util
import io
import json
import os
import pathlib
import tempfile
import unittest
from unittest import mock

spec = importlib.util.spec_from_file_location('develop_local', pathlib.Path(__file__).with_name('develop_local.py'))
local = importlib.util.module_from_spec(spec)
spec.loader.exec_module(local)


class LocalDevelopTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = pathlib.Path(self.temp.name)
        self.patch = mock.patch.multiple(local, ROOT=self.root, CONFIG=self.root / '.env.develop.local', BACKUPS=self.root / 'backups')
        self.patch.start()
        self.addCleanup(self.patch.stop)

    def init(self):
        (self.root / '.env').write_text('KAKAO_CLIENT_ID=fixture-public-id\nKAKAO_CLIENT_SECRET=fixture-only-secret\nDB_PASSWORD=do-not-copy\n')
        with contextlib.redirect_stdout(io.StringIO()):
            local.init()

    def backup(self):
        root = self.root / 'dump'
        root.mkdir()
        data = b'fixture-only-archive'
        (root / 'maple.dump').write_bytes(data)
        (root / 'SHA256SUMS').write_text(hashlib.sha256(data).hexdigest() + '  maple.dump\n')
        (root / 'pg_dump.log').write_text('')
        return root

    def test_init_generates_independent_secrets_without_copying_database_password(self):
        self.init()
        settings = local.load()
        self.assertNotEqual(settings['DB_PASSWORD'], 'do-not-copy')
        self.assertEqual(len(base64.b64decode(settings['NEXON_API_KEY_SECRET'])), 32)
        self.assertEqual(local.CONFIG.stat().st_mode & 0o777, 0o600)

    def test_init_preserves_existing_configuration(self):
        self.init()
        original = local.CONFIG.read_bytes()
        with contextlib.redirect_stdout(io.StringIO()):
            local.init()
        self.assertEqual(local.CONFIG.read_bytes(), original)

    def test_init_requires_kakao_settings(self):
        with self.assertRaisesRegex(ValueError, 'Kakao'):
            local.init()
        self.assertFalse(local.CONFIG.exists())

    def test_environment_parser_keeps_shell_text_as_literal_data(self):
        path = self.root / 'env'
        path.write_text("VALUE='$(touch never-created) $HOME'\n")
        self.assertEqual(local.read_env(path)['VALUE'], '$(touch never-created) $HOME')
        self.assertFalse((self.root / 'never-created').exists())

    def test_duplicate_environment_names_rejected(self):
        path = self.root / 'env'
        path.write_text('A=one\nA=two\n')
        with self.assertRaisesRegex(ValueError, 'Duplicate'):
            local.read_env(path)

    def test_configuration_symlink_rejected(self):
        self.init()
        alias = self.root / 'alias'
        alias.symlink_to(local.CONFIG)
        with self.assertRaisesRegex(ValueError, 'symlink'):
            local.read_env(alias)

    def test_publicly_readable_configuration_rejected(self):
        self.init()
        local.CONFIG.chmod(0o644)
        with self.assertRaisesRegex(ValueError, '600'):
            local.load()

    def test_valid_backup_checksum_accepted(self):
        root = self.backup()
        self.assertEqual(local.verify(root), hashlib.sha256((root / 'maple.dump').read_bytes()).hexdigest())

    def test_corrupt_backup_rejected_before_starting_containers(self):
        root = self.backup()
        (root / 'maple.dump').write_bytes(b'damaged')
        with mock.patch.object(local, 'compose') as compose:
            with self.assertRaisesRegex(ValueError, 'checksum mismatch'):
                local.restore(root)
            compose.assert_not_called()

    def test_manifest_with_different_filename_rejected(self):
        root = self.backup()
        (root / 'SHA256SUMS').write_text('a' * 64 + '  ../other.dump\n')
        with self.assertRaisesRegex(ValueError, 'manifest'):
            local.verify(root)

    def test_dump_warning_requires_review(self):
        root = self.backup()
        (root / 'pg_dump.log').write_text('warning fixture')
        with self.assertRaisesRegex(ValueError, 'warnings'):
            local.verify(root)

    def test_wrong_project_label_rejected_before_database_commands(self):
        metadata = [{'Config': {'Labels': {'com.docker.compose.project': 'production', 'com.docker.compose.service': 'postgres'}}}]
        with mock.patch.object(local, 'compose', return_value='fixture-cid'), mock.patch.object(local, 'output', return_value=json.dumps(metadata)):
            with self.assertRaisesRegex(ValueError, 'Wrong project'):
                local.service('postgres')

    def test_remote_docker_host_rejected_without_running_docker(self):
        with mock.patch.dict(os.environ, {'DOCKER_HOST': 'tcp://production:2376'}, clear=True), mock.patch.object(local, 'output') as output, mock.patch.object(local, 'run') as run:
            with self.assertRaisesRegex(ValueError, 'Remote Docker endpoint'):
                local.compose('up', '-d')
            output.assert_not_called()
            run.assert_not_called()

    def test_ssh_docker_context_rejected_before_compose(self):
        with mock.patch.dict(os.environ, {'DOCKER_CONTEXT': 'remote'}, clear=True), mock.patch.object(local, 'output', return_value='ssh://production') as output, mock.patch.object(local, 'run') as run:
            with self.assertRaisesRegex(ValueError, 'Remote Docker context'):
                local.compose('up', '-d')
            output.assert_called_once_with(['docker', 'context', 'inspect', 'remote', '--format', '{{.Endpoints.docker.Host}}'])
            run.assert_not_called()

    def test_effective_remote_default_context_rejected(self):
        with mock.patch.dict(os.environ, {}, clear=True), mock.patch.object(local, 'output', side_effect=['default', 'tcp://production:2376']):
            with self.assertRaisesRegex(ValueError, 'Remote Docker context'):
                local.assert_local_docker()

    def test_unix_docker_desktop_context_accepted(self):
        with mock.patch.dict(os.environ, {}, clear=True), mock.patch.object(local, 'output', side_effect=['desktop-linux', 'unix:///fixture/docker.sock']):
            local.assert_local_docker()

    def history(self):
        migration = self.root / 'backend/src/main/resources/db/migration/V1__fixture.sql'
        migration.parent.mkdir(parents=True)
        migration.write_bytes(b'\xef\xbb\xbfSELECT 1;\r\n-- fixture\r\n')
        return [{'version': '1', 'type': 'SQL', 'script': migration.name,
                 'checksum': -1621092548, 'success': True}]

    def test_valid_migration_with_bom_and_crlf_history_accepted(self):
        rows = self.history()
        with mock.patch.object(local, 'sql', return_value=json.dumps(rows)):
            local.validate_history('fixture')

    def test_changed_migration_checksum_rejected(self):
        rows = self.history()
        rows[0]['checksum'] = 123
        with mock.patch.object(local, 'sql', return_value=json.dumps(rows)):
            with self.assertRaisesRegex(ValueError, 'checksum mismatch'):
                local.validate_history('fixture')

    def test_failed_migration_history_rejected(self):
        rows = self.history()
        rows[0]['success'] = False
        with mock.patch.object(local, 'sql', return_value=json.dumps(rows)):
            with self.assertRaisesRegex(ValueError, 'Invalid Flyway history'):
                local.validate_history('fixture')

    def test_unknown_migration_version_rejected(self):
        rows = self.history()
        rows[0].update(version='99', script='V99__future.sql')
        with mock.patch.object(local, 'sql', return_value=json.dumps(rows)):
            with self.assertRaisesRegex(ValueError, 'Unknown migration'):
                local.validate_history('fixture')

    def test_duplicate_migration_version_rejected(self):
        rows = self.history()
        rows.append(dict(rows[0]))
        with mock.patch.object(local, 'sql', return_value=json.dumps(rows)):
            with self.assertRaisesRegex(ValueError, 'Invalid Flyway history'):
                local.validate_history('fixture')

    def test_empty_migration_history_rejected(self):
        with mock.patch.object(local, 'sql', return_value='[]'):
            with self.assertRaisesRegex(ValueError, 'Missing Flyway history'):
                local.validate_history('fixture')

    def test_migration_filename_traversal_rejected(self):
        rows = self.history()
        rows[0]['script'] = '../V1__fixture.sql'
        with mock.patch.object(local, 'sql', return_value=json.dumps(rows)):
            with self.assertRaisesRegex(ValueError, 'Invalid migration filename'):
                local.validate_history('fixture')

    def test_invalid_history_rejects_restore_before_credential_cleanup_or_swap(self):
        root = self.backup()
        queries = []
        def sql(query, db='postgres'):
            queries.append(query)
            if query == 'SELECT current_database()':
                return local.DB
            if 'information_schema.tables' in query:
                return 'f'
            return ''
        with mock.patch.object(local, 'compose'), mock.patch.object(local, 'sql', side_effect=sql), mock.patch.object(local, 'service', return_value='fixture-cid'), mock.patch.object(local, 'run'), mock.patch.object(local, 'validate_history', side_effect=ValueError('Flyway checksum mismatch')):
            with self.assertRaisesRegex(ValueError, 'checksum mismatch'):
                local.restore(root)
        self.assertFalse(any('ALTER DATABASE' in query or 'DELETE FROM' in query for query in queries))
        self.assertTrue(any(query.startswith('DROP DATABASE IF EXISTS restore_') for query in queries))

    def test_failed_archive_restore_never_renames_existing_database(self):
        root = self.backup()
        queries = []
        def sql(query, db='postgres'):
            queries.append(query)
            if query == 'SELECT current_database()':
                return local.DB
            if 'information_schema.tables' in query:
                return 'f'
            return ''
        with mock.patch.object(local, 'compose'), mock.patch.object(local, 'sql', side_effect=sql), mock.patch.object(local, 'service', return_value='fixture-cid'), mock.patch.object(local, 'run', side_effect=RuntimeError('fixture restore failed')):
            with self.assertRaisesRegex(RuntimeError, 'restore failed'):
                local.restore(root)
        self.assertFalse(any('ALTER DATABASE' in query for query in queries))
        self.assertTrue(any(query.startswith('DROP DATABASE IF EXISTS restore_') for query in queries))

    def test_changed_business_records_abort_swap(self):
        root = self.backup()
        queries = []
        def sql(query, db='postgres'):
            queries.append(query)
            if query == 'SELECT current_database()':
                return local.DB
            if 'information_schema.tables' in query:
                return 'f'
            if query == 'SELECT count(*) FROM public.user_api_keys':
                return '0'
            return ''
        before = {'characters': '1:before', 'flyway_schema_history': '24:hash'}
        after = {'characters': '0:after', 'flyway_schema_history': '24:hash'}
        with mock.patch.object(local, 'compose'), mock.patch.object(local, 'sql', side_effect=sql), mock.patch.object(local, 'service', return_value='fixture-cid'), mock.patch.object(local, 'run'), mock.patch.object(local, 'validate_history'), mock.patch.object(local, 'snapshot', side_effect=[before, after]):
            with self.assertRaisesRegex(ValueError, 'Business data'):
                local.restore(root)
        self.assertIn('DELETE FROM public.user_api_keys', queries)
        self.assertFalse(any('ALTER DATABASE' in query for query in queries))


if __name__ == '__main__':
    unittest.main()
