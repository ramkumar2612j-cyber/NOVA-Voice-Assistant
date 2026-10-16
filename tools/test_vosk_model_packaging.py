import pathlib
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]

class VoskModelPackagingTest(unittest.TestCase):
    def test_ci_prepares_model_before_gradle_build(self):
        workflow = (ROOT / ".github/workflows/build.yml").read_text()
        prepare = workflow.find("Prepare and validate offline Vosk model")
        build = workflow.find("Assemble debug APK")
        self.assertGreaterEqual(prepare, 0)
        self.assertGreater(build, prepare)
        self.assertIn("./tools/prepare_vosk_model.sh", workflow)

    def test_model_script_checks_every_runtime_required_file(self):
        script = (ROOT / "tools/prepare_vosk_model.sh").read_text()
        for required in ("am/final.mdl", "graph/HCLr.fst", "graph/Gr.fst"):
            self.assertIn(required, script)
        self.assertIn("https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip", script)
        self.assertIn("curl --fail --location", script)
        self.assertIn("refusing to package it", script)

    def test_gradle_downloads_model_automatically(self):
        app = (ROOT / "app/build.gradle.kts").read_text()
        self.assertIn("gradle/vosk-model.gradle.kts", app)
        task = (ROOT / "gradle/vosk-model.gradle.kts").read_text()
        for required in ("am/final.mdl", "graph/HCLr.fst", "graph/Gr.fst",
                         "vosk-model-small-en-us-0.15.zip", "preBuild", "refusing to package it"):
            self.assertIn(required, task)

    def test_runtime_and_build_docs_match(self):
        service = (ROOT / "app/src/main/java/com/nova/assistant/NovaService.kt").read_text()
        docs = (ROOT / "docs/BUILD.md").read_text()
        for required in ("am/final.mdl", "graph/HCLr.fst", "graph/Gr.fst"):
            self.assertIn(required, service)
            self.assertIn(required, docs)

    def test_porcupine_remains_optional_and_fallback_is_documented(self):
        source = (ROOT / "app/src/main/java/com/nova/assistant/PorcupineWake.kt").read_text()
        docs = (ROOT / "docs/BUILD.md").read_text()
        self.assertIn("Vosk", source)
        self.assertIn("fallback", docs)
        self.assertIn("nova.ppn", docs)

if __name__ == "__main__":
    unittest.main()
