"""Validate private CI Firebase configuration without printing its contents."""
import base64
import json
import os
from pathlib import Path

raw = os.environ.get("FIREBASE_JSON", "").strip()
encoded = os.environ.get("FIREBASE_B64", "").strip()
if not raw and not encoded:
    raise SystemExit("::error::Add GOOGLE_SERVICES_JSON (JSON file contents) or GOOGLE_SERVICES_JSON_B64 to repository Actions secrets. Firebase build stopped; no demo APK produced.")
try:
    if not raw:
        raw = base64.b64decode(encoded, validate=True).decode("utf-8")
    config = json.loads(raw)
    clients = config.get("client", [])
    matching = [c for c in clients if c.get("client_info", {}).get("android_client_info", {}).get("package_name") == "tr.sakin.chat"]
    if config.get("project_info", {}).get("project_id") != "sadietext":
        raise ValueError()
    if not any(c.get("api_key") and c.get("client_info", {}).get("mobilesdk_app_id") for c in matching):
        raise ValueError()
except (ValueError, TypeError, AttributeError, UnicodeError):
    raise SystemExit("::error::Invalid Firebase configuration: expected project sadietext and Android package tr.sakin.chat. Configuration contents omitted.") from None
Path("app/google-services.json").write_text(raw, encoding="utf-8")
print("Firebase configuration validated for sadietext / tr.sakin.chat.")
