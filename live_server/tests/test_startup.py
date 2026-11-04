"""Startup feedback without sockets, real keys or provider requests."""
import contextlib
import errno
import io
import os
import unittest
from unittest.mock import patch
from live_server.server import main


class StartupTest(unittest.TestCase):
    def run_main(self, bind_error=None):
        output = io.StringIO()
        env = {'META_API_KEY': 'synthetic_private_key_for_test', 'KAVACH_LIVE_TOKEN': 't' * 40}
        with patch.dict(os.environ, env, clear=True), patch('live_server.server.VisionServer') as factory, contextlib.redirect_stdout(output):
            if bind_error:
                factory.side_effect = bind_error
                with self.assertRaises(SystemExit) as error:
                    main()
                return output.getvalue(), str(error.exception)
            server = factory.return_value
            server.server_address = ('127.0.0.1', 8765)
            server.serve_forever.side_effect = KeyboardInterrupt
            main()
            server.server_close.assert_called_once()
        return output.getvalue(), ''

    def test_startup_and_shutdown_are_visible_without_secrets(self):
        output, _ = self.run_main()
        self.assertIn('listening on http://127.0.0.1:8765', output)
        self.assertIn('Meta API access has NOT been tested', output)
        self.assertIn('backend stopped', output)
        self.assertNotIn('synthetic_private_key_for_test', output)
        self.assertNotIn('t' * 40, output)

    def test_port_in_use_never_claims_started(self):
        output, error = self.run_main(OSError(errno.EADDRINUSE, 'private canary'))
        self.assertEqual('', output)
        self.assertIn('already in use', error)
        self.assertNotIn('canary', error)

    def test_other_bind_errors_are_sanitized(self):
        output, error = self.run_main(OSError(errno.EACCES, 'private canary'))
        self.assertEqual('', output)
        self.assertIn('Could not bind', error)
        self.assertNotIn('canary', error)
