#!/usr/bin/env bash
# Builds the data package ZIP for Moodle: raw data, field notes, analysis script + output,
# app + server source, README, AI-usage log. Run from the repository root after the analysis.
set -euo pipefail
out="CS4473_Group11_data_package.zip"
rm -f "$out"
zip -r "$out" README.md AI_USAGE_LOG.md docs data analysis/analyse.py analysis/requirements.txt \
  analysis/README.md analysis/output server/probe_server.py server/self_test.py server/README.md \
  android-app -x "android-app/**/build/*" "android-app/.gradle/*" "android-app/local.properties" "*/__pycache__/*"
echo "wrote $out"
