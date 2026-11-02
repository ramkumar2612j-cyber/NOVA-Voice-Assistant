#!/usr/bin/env python3
"""Static source-contract regression checks; these are not Android/device integration tests."""
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / 'app/src/main/java/com/nova/assistant'
PARSER = (SRC / 'IntentParser.kt').read_text()
ROUTER = (SRC / 'CommandRouter.kt').read_text()
SERVICE = (SRC / 'NovaService.kt').read_text()
TARGET = (SRC / 'WhatsAppTarget.kt').read_text()
PIPELINE = (SRC / 'ContactResolutionPipeline.kt').read_text()
MATCHER = (SRC / 'ContactMatcher.kt').read_text()
MANIFEST_PATH = ROOT / 'app/src/main/AndroidManifest.xml'
MANIFEST = MANIFEST_PATH.read_text()

class FinalIntegrationContract(unittest.TestCase):
    def test_manifest_is_valid_xml_and_exposes_both_packages(self):
        ET.parse(MANIFEST_PATH)
        self.assertIn('android:name="com.whatsapp"', MANIFEST)
        self.assertIn('android:name="com.whatsapp.w4b"', MANIFEST)

    def test_four_install_scenarios_are_handled_by_exact_identity(self):
        self.assertIn('MESSENGER = "com.whatsapp"', TARGET)
        self.assertIn('BUSINESS = "com.whatsapp.w4b"', TARGET)
        self.assertIn('getApplicationInfo(app.packageName, 0)', TARGET)
        self.assertIn('if (!WhatsAppTarget.isInstalled(packageManager, selectedApp))', SERVICE)
        self.assertNotIn('Neither WhatsApp Messenger nor WhatsApp Business is installed.', SERVICE)  # no silent fallback to the other app
        self.assertIn('requestedApp ?: WhatsAppTarget.App.MESSENGER', SERVICE)

    def test_explicit_launch_does_not_substitute_apps(self):
        self.assertIn('"whatsapp", "wa" -> WhatsAppTarget.App.MESSENGER', SERVICE)
        self.assertIn('"whatsapp business", "wa business" -> WhatsAppTarget.App.BUSINESS', SERVICE)
        self.assertIn('WhatsAppTarget.launchIntent(pm, exactWhatsApp)', SERVICE)
        self.assertIn('return launch(exactIntent, exactWhatsApp.packageName, exactWhatsApp.label)', SERVICE)

    def test_ambiguous_app_requests_clarify(self):
        self.assertIn('Kind.AMBIGUOUS', PARSER)
        self.assertIn('Kaun sa app kholna hai?', PARSER)
        self.assertIn('needsClarification', ROUTER)

    def test_contact_message_routes_keep_explicit_app_identity(self):
        self.assertIn('Kind.APP_MESSAGE', PARSER)
        self.assertIn('WhatsAppCommandRules.parse(sendInput)', PARSER)
        self.assertIn('whatsappTool(explicitIntent.target.orEmpty(), explicitIntent.message.orEmpty(), requestedApp)', SERVICE)
        self.assertIn('sendWhatsApp(num, message, requestedApp)', SERVICE)
        self.assertIn('.setPackage(selectedApp.packageName)', SERVICE)

    def test_real_contact_resolution_and_ambiguity_pipeline_remains(self):
        self.assertIn('ContactMatcher.match', PIPELINE)
        self.assertTrue(('ContactMatcher.match' in MATCHER) or ('score' in MATCHER.lower()))
        self.assertIn('resolveContact(to, onChosen', SERVICE)
        self.assertIn('pendingContactChoices', SERVICE)

    def test_app_specific_call_does_not_fall_back_to_cellular_call(self):
        self.assertIn('explicitIntent?.kind == IntentParser.Kind.APP_CALL', SERVICE)
        self.assertIn('I will not place a regular phone call instead.', SERVICE)
        self.assertIn('WhatsApp call', SERVICE)

    def test_existing_confirmation_and_send_feedback_are_preserved(self):
        self.assertIn('askFirst(prompt, must = true)', SERVICE)
        self.assertIn('Send button aap dabaiye', SERVICE)
        self.assertIn('I could not press send, please send it yourself', SERVICE)

    def test_duplicate_guard_is_deterministic(self):
        self.assertIn('fun claim(key: String, inFlight: MutableSet<String>): Boolean', ROUTER)
        self.assertIn('if (key.isBlank() || key in inFlight) return false', ROUTER)

    def test_unrelated_app_fuzzy_match_not_used_for_explicit_whatsapp(self):
        exact_branch = SERVICE[SERVICE.index('val exactWhatsApp = when (normalized)'):SERVICE.index('        val list = pm.queryIntentActivities', SERVICE.index('val exactWhatsApp = when (normalized)'))]
        self.assertIn('WhatsAppTarget.launchIntent(pm, exactWhatsApp)', exact_branch)
        self.assertNotIn('Logic.bestAppIndex', exact_branch)

    def test_permission_failures_are_not_reported_as_success(self):
        self.assertIn('catch (_: SecurityException)', TARGET)
        self.assertIn('?: return R(false', SERVICE)
        self.assertIn('checkSelfPermission(Manifest.permission.CALL_PHONE)', SERVICE)

if __name__ == '__main__':
    unittest.main(verbosity=2)
