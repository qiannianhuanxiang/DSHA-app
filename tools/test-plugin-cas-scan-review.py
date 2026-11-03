#!/usr/bin/env python3
"""Independent negative controls for descriptor-anchored startup CAS scans."""
import contextlib
import importlib.util
import os
from pathlib import Path
import unittest
from unittest.mock import patch


_spec = importlib.util.spec_from_file_location(
    'cas_scan_review_fixture', Path(__file__).with_name('test-plugin-cas-materialize.py'))
_fixture = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(_fixture)


@unittest.skipIf(os.name == 'nt', 'Actual Linux directory descriptors and NOFOLLOW')
class CasScanReviewTest(unittest.TestCase):
    setUp = _fixture.CasMaterializeTest.setUp
    tearDown = _fixture.CasMaterializeTest.tearDown
    make_link = _fixture.CasMaterializeTest.make_link

    def inject_during_deep_read(self, action):
        deep = self.package / 'a/b/c'
        deep.mkdir(parents=True)
        (deep / 'payload').write_bytes(b'ordinary deep file')
        reader = self.transaction.cas_links.__globals__['DeleteObjects']
        original = reader.stat
        listdir = os.listdir
        fired = []

        def inspect(instance, parent, name):
            if name == 'payload' and not fired:
                # The operation really addresses the held deep directory FD.
                self.assertEqual(Path(os.readlink('/proc/self/fd/' + str(parent))), deep)
                fired.append(True)
                action()
            return original(instance, parent, name)

        def list_private(directory):
            actual = (Path(os.readlink('/proc/self/fd/' + str(directory)))
                      if isinstance(directory, int) else Path(directory))
            outside = self.root / 'outside'
            self.assertFalse(actual == outside or outside in actual.parents,
                             'The scanner followed a substituted external directory')
            return listdir(directory)

        @contextlib.contextmanager
        def injected():
            with patch.object(reader, 'stat', inspect), patch.object(os, 'listdir', list_private):
                yield

        return injected(), fired

    def test_replaced_parent_link_cannot_redirect_held_descendant_scan(self):
        outside = self.root / 'outside'
        outside.mkdir()
        (outside / 'untouched').write_bytes(b'external original')
        retained = self.root / 'retained-package'

        def replace():
            self.package.rename(retained)
            self.package.symlink_to(outside, target_is_directory=True)

        injected, fired = self.inject_during_deep_read(replace)
        with injected, self.assertRaises((ValueError, OSError)):
            self.transaction.cas_links()
        self.assertTrue(fired)
        self.assertEqual((outside / 'untouched').read_bytes(), b'external original')
        self.assertEqual(list(outside.iterdir()), [outside / 'untouched'])
        self.assertTrue((retained / 'index.js').is_symlink())
        self.assertEqual(self.target.read_bytes(), b'export const answer=42;\n')

    def test_parent_rename_away_and_back_rejected_by_directory_stability(self):
        retained = self.root / 'retained-package'

        def replace_and_restore():
            self.package.rename(retained)
            self.package.mkdir()
            self.package.rmdir()
            retained.rename(self.package)

        injected, fired = self.inject_during_deep_read(replace_and_restore)
        with injected, self.assertRaises((ValueError, OSError)):
            self.transaction.cas_links()
        self.assertTrue(fired)
        self.assertTrue(self.link.is_symlink())
        self.assertEqual(self.target.read_bytes(), b'export const answer=42;\n')

    def test_replaced_ancestor_above_module_root_rejected_on_unwind(self):
        outside = self.root / 'outside'
        outside.mkdir()
        (outside / 'untouched').write_bytes(b'external original')
        retained = self.root / 'retained-profile'

        def replace():
            self.profile.rename(retained)
            self.profile.symlink_to(outside, target_is_directory=True)

        injected, fired = self.inject_during_deep_read(replace)
        with injected, self.assertRaises((ValueError, OSError)):
            self.transaction.cas_links()
        self.assertTrue(fired)
        self.assertEqual((outside / 'untouched').read_bytes(), b'external original')
        self.assertEqual(list(outside.iterdir()), [outside / 'untouched'])
        self.assertTrue((retained / 'node_modules/test-plugin/index.js').is_symlink())

    def test_scan_candidates_do_not_authorize_replaced_publish_ancestor(self):
        original = self.transaction.boundary
        retained = self.root / 'retained-package'
        outside = self.root / 'outside'
        outside.mkdir()
        (outside / 'index.js').write_bytes(b'external later original')

        def boundary(name):
            if name == 'cas-prepared':
                self.package.rename(retained)
                self.package.symlink_to(outside, target_is_directory=True)
            return original(name)

        self.transaction.boundary = boundary
        with self.assertRaises((ValueError, OSError)):
            self.transaction.materialize_cas_links()
        self.assertTrue(self.transaction.pending())
        self.assertEqual((outside / 'index.js').read_bytes(), b'external later original')
        self.assertTrue((retained / 'index.js').is_symlink())
        self.assertEqual(self.target.read_bytes(), b'export const answer=42;\n')


if __name__ == '__main__':
    unittest.main(verbosity=2)
