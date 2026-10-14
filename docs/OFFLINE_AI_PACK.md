# Offline AI pack (v6.96)
Five extra offline brains, no API key, no account. They download by themselves on Wi-Fi after install.

| # | Model | Maker | Origin | Template | Min RAM |
|---|-------|-------|--------|----------|---------|
| 1 | Qwen2.5 1.5B | Alibaba | CN | ChatML | 4 GB |
| 2 | Llama 3.2 1B | Meta | World | Llama 3 | 3 GB |
| 3 | InternLM2.5 1.8B | Shanghai AI Lab | CN | ChatML | 4 GB |
| 4 | SmolLM2 1.7B | Hugging Face | World | ChatML | 4 GB |
| 5 | DeepSeek-R1 Distill 1.5B | DeepSeek | CN | DeepSeek | 5 GB |

Order = download order = try order. Files: filesDir/models/pack/<id>.gguf, state filesDir/pack_models.json.

## What is NOT verified (check on the first CI + phone run)
1. The GitHub build never ran with these files (Gradle/kotlinc not available when written).
2. Hugging Face repo names and the API reply shape (/api/models/<repo>/tree/main -> lfs.oid, lfs.size) are from public docs; a wrong repo is skipped, never guessed.
3. The bundled llamacpp-kotlin 0.1.0 engine is old: models of architectures it does not know fail to load and are skipped (log line in SELF TEST). Llama 3 / DeepSeek chat templates are unverified on a phone; if answers look odd, compare with the GGUF's own template.
4. BOS token is left to llama.cpp (not written in the prompt).
5. Speed: 1.5B models on a mid phone are slow (several seconds per reply); the 30 s limit applies per model.

## Real-device checks
- SELF TEST shows `READY Offline AI <name>` per model, or `WAIT/WARN/FAIL` with the reason.
- Delete the built-in 0.5B model, turn internet off, ask a question: an offline pack model must answer.
