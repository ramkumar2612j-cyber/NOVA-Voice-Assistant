#!/usr/bin/env python3
"""Static safety contracts for Part 1 contact resolution; not a substitute for Android device testing."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
main = ROOT / "app/src/main/java/com/nova/assistant"
logic = (main / "Logic.kt").read_text(encoding="utf-8")
service = (main / "NovaService.kt").read_text(encoding="utf-8")
lookup = (main / "ContactLookup.kt").read_text(encoding="utf-8")
matcher = (main / "ContactMatcher.kt").read_text(encoding="utf-8")
parser = (main / "ContactRequestParser.kt").read_text(encoding="utf-8")
pipeline = (main / "ContactResolutionPipeline.kt").read_text(encoding="utf-8")
manifest = (ROOT / "app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")

checks = {
    "existing contacts permission retained": "android.permission.READ_CONTACTS" in manifest,
    "existing permission flow retained": 'permRequest?.invoke("contacts")' in service,
    "real contacts provider used": "ContactsContract.CommonDataKinds.Phone.CONTENT_URI" in lookup,
    "lookup includes person IDs and phone ranking fields": all(x in lookup for x in ("CONTACT_ID", "DISPLAY_NAME", "NUMBER", "TYPE", "IS_SUPER_PRIMARY")),
    "unfiltered provider query supports phonetic variants": "resolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, projection, null, null, null)" in lookup,
    "permission/provider errors preserved": "ContactLookupResult.Failure" in lookup and "permissionDenied" in lookup,
    "candidate grouping uses contact ID": "groupBy" in matcher and "contactId" in matcher,
    "ambiguous matches are explicit": all(x in matcher for x in ("AMBIGUOUS_DUPLICATE", "AMBIGUOUS_SIMILAR", "LOW_CONFIDENCE")),
    "any second plausible contact prevents auto-selection": "if (top.exact && plausible.size == 1)" in matcher,
    "reference phrases parsed": all(x in parser for x in ("this person", "is bande ko", "isko")),
    "non-contact phone commands are not intercepted": all(x in parser for x in ("settings", "silent", "unlock", "volume")),
    "classify uses original transcript": 'ContactRequestParser.parseCommand(text)' in logic,
    "resolution pipeline does not execute actions": "ContactMatcher.match(query, result.rows)" in pipeline and "startActivity" not in pipeline,
    "retry does not automatically resume actions": "does not resume call/SMS/WhatsApp" in service,
    "call, sms, WhatsApp still resolve through shared entry point": all(f"resolveContact({x}" in service for x in ("who", "to", "to")),
    "unit tests cover contact resolution": (ROOT / "app/src/test/java/com/nova/assistant/ContactResolutionTest.kt").exists(),
}
failed = [name for name, ok in checks.items() if not ok]
for name, ok in checks.items():
    print(("PASS" if ok else "FAIL") + " " + name)
if failed:
    raise SystemExit(f"{len(failed)} contact contract check(s) failed")
