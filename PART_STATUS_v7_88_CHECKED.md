# v7.88 camera-eye - status (checked)
RUN: python guards 144/144 PASS; audit_dead_ui REAL GAPS 0.
NOT RUN: Kotlin compile, JUnit (sandbox had no network). First compile = GitHub Actions "Build and Test APK".
NOT TESTED on phone: "nova peeche wale camera se dekho ye kya hai", "front camera se dekho"; "camera kholo" must NOT trigger it.
If Actions fails: send the red error lines; fix only v7.88 files (CameraEyeRules/Activity, NovaService.runCameraEye).
Not built on purpose: face -> person name / Instagram search.
