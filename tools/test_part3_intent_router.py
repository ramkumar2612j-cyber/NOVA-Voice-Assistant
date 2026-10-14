#!/usr/bin/env python3
"""Offline source-contract checks for NOVA Part 3 intent routing."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
parser = (ROOT / "app/src/main/java/com/nova/assistant/IntentParser.kt").read_text()
router = (ROOT / "app/src/main/java/com/nova/assistant/CommandRouter.kt").read_text()
service = (ROOT / "app/src/main/java/com/nova/assistant/NovaService.kt").read_text()
tests = (ROOT / "app/src/test/java/com/nova/assistant/IntentParserTest.kt").read_text()
checks = {
    "central parser class": "object IntentParser" in parser,
    "central router class": "object CommandRouter" in router,
    "call intent": "ContactRequestParser.parseCommand(raw)" in parser,
    "WhatsApp message integration": "WhatsAppCommandRules.parse(raw)" in parser,
    "generic SMS intent": '"sms"' in parser and "genericMessage" in parser,
    "app identity separated from action": "val appId: String?" in parser and "val action: Action" in parser,
    "WhatsApp Business identity reserved": '"whatsapp_business"' in parser,
    "router has no direct action execution": "startActivity" not in router and "ACTION_CALL" not in router,
    "service integration": "CommandRouter.plan(text)" in service,
    "low confidence clarification": "routePlan.intent.clarification" in service,
    "existing WhatsApp handler preserved": "whatsappTool(waReq.recipient, waReq.message" in service,
    "existing call execution handler preserved": "private fun executeChosenCall(candidate: ContactCandidate)" in service,
    "tests include call/message/open/ambiguous/negation": all(x in tests for x in ["recognizesCallAcrossEnglishAndHinglish", "recognizesWhatsAppAndGenericMessageIntent", "recognizesOpenAppAndKeepsAppSeparateFromAction", "incompleteAndMixedCommandsRequireClarification", "negatedCommandsNeverBecomeExecutableRoutes"]),
    "duplicate guard tested": "assertFalse(CommandRouter.claim" in tests,
    "safe fallback tested": "unknownCommandsUseSafeFallback" in tests,
}
for label, passed in checks.items():
    print(("PASS" if passed else "FAIL") + " " + label)
if not all(checks.values()):
    raise SystemExit(1)
print(f"PASS: {len(checks)} Part 3 source-contract checks")
