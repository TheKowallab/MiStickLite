import importlib.util
import tempfile
import unittest
from unittest.mock import patch
from types import SimpleNamespace
import contextlib
import io
from pathlib import Path

spec = importlib.util.spec_from_file_location("service_memory", Path(__file__).with_name("service-memory.py"))
memory = importlib.util.module_from_spec(spec)
spec.loader.exec_module(memory)


class MemoryReportTest(unittest.TestCase):
    def test_android_9_table_and_summary(self):
        result = memory.parse_meminfo("** MEMINFO in pid 421 [pl.misticklite] **\n TOTAL 24680 100 200\nJava Heap: 1200\nNative Heap: 2400\nTOTAL: 24680 TOTAL SWAP PSS: 0")
        self.assertEqual((24680, 421, 1200, 2400), tuple(result[k] for k in ("total_pss_kib", "pid", "java_heap_pss_kib", "native_heap_pss_kib")))

    def test_modern_total_pss_and_comma(self):
        self.assertEqual(12345, memory.parse_meminfo("TOTAL PSS: 12,345 TOTAL RSS: 25000")["total_pss_kib"])

    def test_android_9_summary_only(self):
        self.assertEqual(3456, memory.parse_meminfo(" TOTAL: 3456 TOTAL SWAP PSS: 0")["total_pss_kib"])

    def test_absent_process_is_not_reported_as_zero_ram(self):
        result = memory.parse_meminfo("No process found for: pl.misticklite")
        self.assertEqual("not_running", result["status"])
        self.assertEqual("", result["total_pss_kib"])

    def test_invalid_dump_is_never_echoed(self):
        with self.assertRaises(ValueError) as error:
            memory.parse_meminfo("PRIVATE_UNEXPECTED_CONTENT")
        self.assertNotIn("PRIVATE", str(error.exception))

    def test_median_ignores_non_running_samples(self):
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / "sample.csv"
            path.write_text("status,pid,total_pss_kib\nrunning,42,100\nnot_running,,\nrunning,42,300\nrunning,42,200\n")
            self.assertEqual({"samples": 4, "running_samples": 3, "pids": ["42"], "median_total_pss_kib": 200,
                "launcher_states": [], "service_states": [], "redirect_modes": []}, memory.summarize(path))

    def test_closed_launcher_and_connected_redirect_state(self):
        result = memory.parse_control_status('Broadcast completed: result=0, data="ok=true mode=ON service=CONNECTED pendingPairs=0 launcher=CLOSED persistent=false"')
        self.assertEqual({"launcher_state": "CLOSED", "service_state": "CONNECTED", "redirect_mode": "ON"}, result)

    def test_visible_background_or_finishing_launcher_is_not_reported_as_closed(self):
        for state in ("FOREGROUND", "BACKGROUND", "FINISHING"):
            with self.assertRaises(ValueError):
                memory.parse_control_status(f'ok=true mode=ON service=CONNECTED pendingPairs=0 launcher={state}')

    def test_missing_receiver_result_or_held_key_aborts_sampling(self):
        for value in ('Permission Denial', 'ok=false mode=OFF service=DISCONNECTED pendingPairs=0 launcher=CLOSED',
                      'ok=true mode=ON service=CONNECTED pendingPairs=1 launcher=CLOSED'):
            with self.assertRaises(ValueError):
                memory.parse_control_status(value)

    def test_closed_ui_collection_checks_status_before_and_after_pss(self):
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / "sample.csv"
            status = SimpleNamespace(stdout='ok=true mode=ON service=CONNECTED pendingPairs=0 launcher=CLOSED')
            meminfo = SimpleNamespace(stdout='** MEMINFO in pid 421 [pl.misticklite] **\n TOTAL PSS: 12345')
            argv = ['service-memory.py', 'collect', 'TEST_SERIAL', 'redirect-ui-closed', '--samples', '1', '--require-launcher-closed', '--output', str(path)]
            with patch('sys.argv', argv), patch.object(memory.subprocess, 'run', side_effect=[status, meminfo, status]) as run, contextlib.redirect_stdout(io.StringIO()):
                memory.main()
            with path.open() as handle:
                rows = list(memory.csv.DictReader(handle))
            self.assertEqual(('CLOSED', 'CONNECTED', 'ON', '12345'), tuple(rows[0][k] for k in ('launcher_state', 'service_state', 'redirect_mode', 'total_pss_kib')))
            self.assertEqual(3, run.call_count)
            commands = [call.args[0] for call in run.call_args_list]
            self.assertEqual(['adb', '-s', 'TEST_SERIAL', 'shell', 'dumpsys', 'meminfo', 'pl.misticklite'], commands[1])
            self.assertEqual('status', commands[0][-1]); self.assertEqual('status', commands[2][-1])

    def test_mode_change_during_pss_read_discards_sample(self):
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / "sample.csv"
            before = SimpleNamespace(stdout='ok=true mode=ON service=CONNECTED pendingPairs=0 launcher=CLOSED')
            after = SimpleNamespace(stdout='ok=true mode=OFF service=CONNECTED pendingPairs=0 launcher=CLOSED')
            meminfo = SimpleNamespace(stdout='TOTAL PSS: 12345')
            argv = ['service-memory.py', 'collect', 'TEST_SERIAL', 'redirect-ui-closed', '--samples', '1', '--require-launcher-closed', '--output', str(path)]
            with patch('sys.argv', argv), patch.object(memory.subprocess, 'run', side_effect=[before, meminfo, after]), self.assertRaises(ValueError):
                memory.main()
            with path.open() as handle:
                self.assertEqual([], list(memory.csv.DictReader(handle)))


if __name__ == "__main__":
    unittest.main()
