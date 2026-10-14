# Architecture

NOVA is split into a thin Android shell and a large **pure-Kotlin rules layer** that is unit-tested without Android.

## Layers
| Layer | Files (app/src/main/java/com/nova/assistant) | Notes |
|---|---|---|
| Service / orchestration | `NovaService`, `BootReceiver`, `*Receiver`, `NovaCloudQueueWorker` | Foreground microphone service; starts only from ACTIVATE |
| UI | `MainActivity` (+ `assets/ui.html` via Bridge), `NovaHud`, `PatternSetupActivity` | WebView UI, JS bridge for settings/schedule |
| Voice | `VoiceLock`, `OnnxSpeaker`, `Fbank`, `SpeakerRules`, `VoiceFaceRules`, `VoiceFlowRules`, `ListenGuard`, `VoiceText`, `MicEffects`, `GroqStt` | Wake, owner-voice match, listening windows |
| Understanding | `Logic`, `Understand`, `Brain`, `LocalBrain(s)`, `LocalChat`, `HindiRoman`, `KnowledgeMatcher` | Command parsing, offline answers |
| Phone control | `Control`, `NovaAccessibilityService`, `PatternUnlock`, `ScreenSession`, `ScreenAlert`, `InstallTap` | Accessibility actions with safety blocks |
| Agent | `AgentRules`, `AgentTaskRules`, `AgentTaskLedger`, `AutoSetup*`, `RoutineRules` | Task plans, ledger, safe resume |
| Schedule & routines | `ScheduledTask{Rules,Store,Receiver}`, `WakeRules`, `WakeAlarmReceiver`, `BatteryRules`, `Timers` | Weekday schedule, morning greeting, wake alarm, battery SOS |
| Messaging | `WhatsAppCommandRules`, `WaBiz*`, `MessageMode`, `LeadRules`, `OfflineQueueRules` | WhatsApp, queued jobs |
| Cloud (optional) | `FreeProviders`, `FreeHttp`, `ProviderRanker`, `ApiUsage`, `ModelCatalog`, `ImageGen` | Failover across user's own keys |
| Storage & security | `Cfg`, `ExtCfg`, `SecureStore`, `*Store`, `ErrLog`, `CrashNote` | Keystore-encrypted secrets |

## Rules
- Decision logic lives in `*Rules.kt` (no Android imports) with a matching `*Test.kt`.
- Android classes call rules, never the reverse.
- Dangerous actions: local spoken yes (owner-set `skipRiskyConfirm`), sensitive screens always blocked.
- Invariants are enforced by `SafetyInvariantsTest` and `tools/test_*_contract.py`.
