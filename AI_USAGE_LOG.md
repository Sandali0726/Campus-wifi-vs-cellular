# AI-Usage Log and Source Attribution

Required by CS4473 assignment §5. Every AI-generated or borrowed piece of code or text is listed here.
AI tools did **not** produce any measurement data; all data in `data/raw/` comes from our own phones.

## AI-usage log

| Date | Tool | Purpose | Files / functions affected | How the output was used / what we changed |
|---|---|---|---|---|
| 2026-11-21 | Claude Code (Anthropic) | Read the brief + proposal and produce an implementation plan and issue list | `.github/issue-drafts/*` | Plan reviewed by the group; issues adopted as the work breakdown |
| 2026-11-21 | Claude Code (Anthropic) | Repository skeleton | `README.md`, `.gitignore`, `AI_USAGE_LOG.md`, folder layout | Reviewed and approved by the group |
| 2026-11-21 | Claude Code (Anthropic) | Probe server + self-test (issue 2) | `server/probe_server.py`, `server/self_test.py`, `server/README.md` | Reviewed and approved by the group |
| 2026-11-21 | Claude Code (Anthropic) | Android app: project, UI, permissions (issue 3) | `android-app/` Gradle files, `AndroidManifest.xml`, `res/`, `MainActivity.kt`, `RunConfig.kt` | Pending group review and on-phone validation |
| 2026-11-21 | Claude Code (Anthropic) | Foreground service, 10 s timer, skip logic (issue 4) | `MeasurementService.kt`, `MeasurementState.kt` | as above |
| 2026-11-21 | Claude Code (Anthropic) | Wi-Fi / cellular / location readers (issue 5) | `WifiSignalReader.kt`, `CellSignalReader.kt`, `LocationTracker.kt` | as above |
| 2026-11-21 | Claude Code (Anthropic) | HTTP probe + unit tests (issue 6) | `NetworkProbe.kt`, `ProbeMathTest.kt` | as above |
| 2026-11-21 | Claude Code (Anthropic) | Network drop/switch events (issue 7) | `NetworkMonitor.kt` | as above |
| 2026-11-21 | Claude Code (Anthropic) | CSV format, logger, data dictionary (issue 8) | `MeasurementRow.kt`, `CsvLogger.kt`, `MeasurementRowTest.kt`, `docs/DATA_DICTIONARY.md` | as above |
| 2026-11-21 | Claude Code (Anthropic) | Analysis script and synthetic test-data generator (issue 9) | `analysis/analyse.py`, `analysis/make_test_data.py`, `analysis/README.md` | Synthetic data used only to test the code, never in results |
| 2026-11-21 | Claude Code (Anthropic) | Field protocol and templates (issue 10) | `docs/PROTOCOL.md`, `data/field-notes/*_template.md` | Route descriptions to be filled in by the group |
| 2026-11-21 | Claude Code (Anthropic) | Report skeleton and packaging script (issue 12) | `report/main.tex`, `report/README.md`, `report/make_package.sh` | Text to be written by the group |

## Borrowed code / external sources

| File / function | Source (URL, docs, book) | What we changed |
|---|---|---|
| `server/README.md`, `analysis/analyse.py` (colours) | Reference categorical palette from the Claude Code dataviz guidance (blue `#2a78d6`, orange `#eb6834`) | Used as-is |
| `android-app/` | Android developer documentation (WifiInfo, TelephonyManager, TelephonyCallback, foreground services) | APIs used as documented |
