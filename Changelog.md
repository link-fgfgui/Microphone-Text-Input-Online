## Changelog

### 3.0.0

1. API: add extensible `SpeechRecognizer` registration with priority selection
2. API: restore Fabric and Forge recognizer lifecycle events
3. fix: initialize after client startup so late third-party registrations work
4. fix: failed recognizers fall back to the next enabled implementation
5. fix: serialize registry changes and protect recognition state across config reloads

### 2.x provider changes

1. feature: OpenAI-compatible ASR provider (Qwen3-ASR / vLLM / DashScope)
2. support: multipart `audio/transcriptions` only
3. config: `transcriptionPrompt` (ASR `prompt` vocabulary/topic hint, not system), optional `httpProxy`
4. existing: Xiaomi MiMo-V2.5-ASR (`mimo-v2.5-asr`)

## Dependencies

| Minecraft | Fabric | Forge |
|-----------|--------|-------|
| 1.20.1    | [fabric-api 0.92.7+1.20.1](https://modrinth.com/mod/fabric-api/version/0.92.7+1.20.1) <br> [midnightlib 1.9.1+1.20.1](https://modrinth.com/mod/midnightlib/version/1.9.1+1.20.1) | [forge 47.4.10](https://files.minecraftforge.net/net/minecraftforge/forge/index_1.20.1.html) <br> [midnightlib 1.9.1+1.20.1](https://modrinth.com/mod/midnightlib/version/1.9.1+1.20.1) |
