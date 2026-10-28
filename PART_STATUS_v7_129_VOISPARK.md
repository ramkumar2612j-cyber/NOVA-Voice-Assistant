# v7.129 VoiSpark Ultron voice (add-only)
Voice ID given by owner: ad2612df-ff6e-4291-bcc6-902767a6a5fd (NOT yet proven to be "Ultron": needs owner's API key + internet; app has VOISPARK CHECK).
Official (from docs screenshots): base https://api.voispark.com, Authorization: Bearer <token>, POST /api/v2/tts/generate (voice_id, model_id fish-audio/drama-3, text, configs, sync), GET /api/v2/tts/task/{task_id}, GET /api/v2/voices, GET /api/v2/voices/{id}/models?operation=tts, GET /api/benefit; envelope {code,message,data}, code 0 = ok even on HTTP 200; Drama 3 = 83 languages auto detect, 4 credits/char, 10,000 char limit.
NOT in the docs I had: finished-task / sync answer fields (where audio is), status words, audio format. Code only DISCOVERS an https audio link or base64; VOISPARK CHECK prints the answer shape (keys only) so it can be pinned.
Chain: VoiSpark (<=240 chars) -> existing Ultron-effect voice -> normal Android voice. 10 min pause after any VoiSpark failure.
Key: SecureStore slot "voispark" only; never in code/log/ErrLog/report.
Files: VoiSparkApi.kt, VoiSparkClient.kt, VoiSparkApiTest.kt, tools/test_voispark_contract.py; edits: Cfg.kt, NovaService.kt, MainActivity.kt, ui.html (add-only).
Kotlin NOT compiled, phone NOT tested.
