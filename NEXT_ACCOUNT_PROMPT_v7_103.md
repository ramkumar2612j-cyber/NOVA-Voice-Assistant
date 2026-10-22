# NOVA v7.103 continuation prompt

Start from `NOVA-v7_103-capability-learning.zip`. Preserve all existing features and architecture. Do not remove or rewrite old code without explicit approval. Keep the v6.57 lead-routing fix and all existing action confirmation/security checks.

v7.103 added bounded capability-gap learning: `CapabilityLearningRules.kt`, `CapabilityLearningStore.kt`, and NovaService hooks. The store must remain privacy-safe: never persist raw prompts, task text, screen text, audio, contact/message content, API keys, or credentials. Learning means adapting recovery guidance from coarse blocker categories, not claiming autonomous self-coding.

Next: first run full `./gradlew testDebugUnitTest` and assemble task in a real Android SDK/Gradle environment (or GitHub Actions); report exact results. Fix only reproducible failures and add regression tests. Then verify phone behavior for voice/wake word, Hindi/Hinglish, screen automation, calls, WhatsApp, startup/background recovery and provider fallback. Never mark runtime behavior PASS without real evidence. Any new code-writing/self-improvement feature must use an isolated patch, backup, tests, owner approval before applying, and rollback.
