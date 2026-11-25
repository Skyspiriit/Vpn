#!/usr/bin/env python3
"""Apply Amin's opt-out to the exact pinned connectcore source before compiling it."""

from pathlib import Path
import sys

root = Path(sys.argv[1])


def replace_once(name, old, new):
    path = root / name
    text = path.read_text()
    if text.count(old) != 1:
        raise SystemExit(f"Privacy patch no longer matches {name}")
    path.write_text(text.replace(old, new, 1))


replace_once(
    "engine.go",
    "\tTelemetryOutboxDirectory string",
    "\tTelemetryOutboxDirectory string\n\n"
    "\t// DisableTelemetry prevents session analytics and geo lookups. Set before Start.\n"
    "\tDisableTelemetry bool",
)
replace_once(
    "helpers.go",
    "func (s *Engine) newManager(brokerURL string) *clienttelemetry.Manager {",
    "func (s *Engine) newManager(brokerURL string) *clienttelemetry.Manager {\n"
    "\tif s.DisableTelemetry {\n\t\treturn nil\n\t}",
)

(root / "amin_privacy_test.go").write_text('''package connectcore

import "testing"

func TestAminTelemetryDisabledCreatesNoReporter(t *testing.T) {
    engine := New()
    engine.DisableTelemetry = true
    if engine.newManager("https://unused.invalid/") != nil {
        t.Fatal("telemetry opt-out constructed a reporter")
    }
}
''')
