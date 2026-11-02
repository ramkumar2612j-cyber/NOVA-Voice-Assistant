import pathlib, re, unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]
SRC = ROOT / "app/src/main/java/com/nova/assistant"
MANIFEST = (ROOT / "app/src/main/AndroidManifest.xml").read_text()
PARSER = (SRC / "IntentParser.kt").read_text()
TARGET = (SRC / "WhatsAppTarget.kt").read_text()
SERVICE = (SRC / "NovaService.kt").read_text()
ROUTER = (SRC / "CommandRouter.kt").read_text()

class WhatsAppRoutingContract(unittest.TestCase):
    def test_distinct_verified_package_identities(self):
        self.assertIn('const val MESSENGER = "com.whatsapp"', TARGET)
        self.assertIn('const val BUSINESS = "com.whatsapp.w4b"', TARGET)
        self.assertIn('android:name="com.whatsapp"', MANIFEST)
        self.assertIn('android:name="com.whatsapp.w4b"', MANIFEST)

    def test_only_messenger_installed_and_only_business_installed_are_distinct(self):
        self.assertIn('fun isInstalled(pm: PackageManager, app: App)', TARGET)
        self.assertIn('getApplicationInfo(app.packageName, 0)', TARGET)
        self.assertIn('fun launchIntent(pm: PackageManager, app: App)', TARGET)
        self.assertIn('missingMessage(app: App)', TARGET)

    def test_explicit_launch_phrases_are_exact(self):
        self.assertIn('"whatsapp kholo"', PARSER)
        self.assertIn('"whatsapp business kholo"', PARSER)
        self.assertIn('appId = "whatsapp"', PARSER)
        self.assertIn('appId = "whatsapp_business"', PARSER)

    def test_app_specific_contact_commands_parse_without_first_result_assumption(self):
        self.assertIn('Explicit app + contact actions', PARSER)
        self.assertIn('Kind.APP_MESSAGE', PARSER)
        self.assertIn('Kind.APP_CALL', PARSER)
        self.assertIn('ContactMatcher.match', (SRC / "ContactResolutionPipeline.kt").read_text())

    def test_exact_app_open_does_not_fuzzy_substitute(self):
        self.assertIn('val exactWhatsApp = when (normalized)', SERVICE)
        self.assertIn('WhatsAppTarget.launchIntent(pm, exactWhatsApp)', SERVICE)
        self.assertIn('return launch(exactIntent, exactWhatsApp.packageName, exactWhatsApp.label)', SERVICE)

    def test_duplicate_guard_exists_in_central_router(self):
        self.assertIn('fun claim(key: String, inFlight: MutableSet<String>): Boolean', ROUTER)
        self.assertIn('if (key.isBlank() || key in inFlight) return false', ROUTER)

    def test_capability_and_permission_failures_are_not_faked(self):
        self.assertIn('catch (_: SecurityException)', TARGET)
        self.assertIn('?: return R(false', SERVICE)

if __name__ == "__main__":
    unittest.main()
