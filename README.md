# **Microphone Text Input Mod Developer Documentation**

<div style="text-align: center;">
<p style="font-size: large;">MultiLoader (Fabric + Forge) for Minecraft 1.20.1</p>
</div>

<div style="text-align: center;">

<img alt="image" src="common/src/main/resources/assets/mcmti/icon.png"/>
</div>

## Introduction

Client-side Minecraft mod: record microphone audio and convert speech to chat text via **online ASR**.

Supported backends:

| Provider | Implementation | Typical use |
|----------|----------------|-------------|
| **Xiaomi MiMo** | `MimoAsrClient` | Cloud `mimo-v2.5-asr` |
| **OpenAI Compatible** | `OpenAiCompatibleAsrClient` | Qwen3-ASR (vLLM / 阿里云百炼), other OpenAI-style ASR |

## Features

- Modes: `AUTO_SEND`, `RELEASE_KEY_TO_SEND`, `RELEASE_KEY_TO_INPUT`
- Capture: mono 16-bit PCM @ 16 kHz → WAV upload
- Pluggable `SpeechRecognizer` API for local or online ASR extensions
- MiMo: Chat Completions with `input_audio` + `asr_options.language`
- OpenAI-compatible: **only** multipart `POST /audio/transcriptions`
- Optional transcription `prompt` (vocabulary / topic / prior-segment hint — **not** a chat system prompt)
- Config-screen Load / Unload microphone controls
- Default message prefix: `[🎙]`

## Architecture

```
AudioRecorder (float PCM, 16kHz mono)
         │
         ▼
SpeechRecognizer
         │
         ▼
Provider client
        ├── MimoAsrClient              → POST {base}/chat/completions
        └── OpenAiCompatibleAsrClient  → POST {base}/audio/transcriptions
                │
                └── AsrHttpClients (timeout)
```

## Configuration reference

| Field | Applies to | Notes |
|-------|------------|--------|
| `asrProvider` | all | `MIMO` \| `OPENAI_COMPATIBLE` |
| `apiBaseUrl` | all | Base ending with `/v1` (trailing `/` OK) |
| `apiKey` | all | Required for MiMo / most cloud; optional for local servers |
| `model` | all | Provider model id |
| `language` | all | MiMo: `auto` / `zh` / `en`. OpenAI-compatible: `auto` omits field |
| `requestTimeoutMs` | all | HTTP connect/request timeout (ms) |
| `transcriptionPrompt` | OpenAI only | Multipart `prompt` field. See [Transcription prompt](#transcription-prompt) |
| `mode` | all | `AUTO_SEND` / `RELEASE_KEY_TO_SEND` / `RELEASE_KEY_TO_INPUT` |
| `recordCycleMs` | `AUTO_SEND` | Record window length (ms) |
| `recordBufferSize` | key-release modes | Capture buffer size (bytes) |
| `prefix` | all | Prepended to chat text (default `[🎙]`) |

Saving config re-inits the ASR client on a virtual thread.

### MiMo (default)

| Field | Value |
|-------|--------|
| `asrProvider` | `MIMO` |
| `apiBaseUrl` | `https://api.xiaomimimo.com/v1` |
| `apiKey` | your MiMo key (**required**) |
| `model` | `mimo-v2.5-asr` |
| `language` | `auto` / `zh` / `en` |

### 阿里云百炼 Qwen ASR

| Field | Value |
|-------|--------|
| `asrProvider` | `OPENAI_COMPATIBLE` |
| `apiBaseUrl` | `https://dashscope.aliyuncs.com/compatible-mode/v1` (北京) 或 `https://dashscope-intl.aliyuncs.com/compatible-mode/v1` (新加坡) |
| `apiKey` | DashScope key |
| `model` | `qwen3-asr-flash` |
| `transcriptionPrompt` | optional vocabulary / domain hint |

### 本地 vLLM / OpenAI-compatible ASR

| Field | Value |
|-------|--------|
| `asrProvider` | `OPENAI_COMPATIBLE` |
| `apiBaseUrl` | `http://{IP}:{port}/v1` |
| `apiKey` | 可留空 |
| `model` | 本地模型路径或名称 |
| `transcriptionPrompt` | optional |

## Transcription prompt

Only used when `asrProvider = OPENAI_COMPATIBLE`. Sent as the multipart form field `prompt` on `/audio/transcriptions` when non-blank; omitted when empty.

This is **not** a Chat Completions system message. Providers (e.g. OpenAI `gpt-4o-transcribe`, Qwen ASR) typically use it to:

- supply domain vocabulary (proper nouns, brands, game terms)
- hint the audio topic
- continue context from a previous audio segment

It is **not** intended for role/instructions such as:

- ❌ “You are a secretary”
- ❌ “Summarize instead of transcribing”
- ❌ “Remove filler words”
- ❌ “Output Markdown”

Default value is a short Minecraft vocabulary / topic hint, for example:

```text
Minecraft in-game chat. Expected vocabulary: creeper, zombie, skeleton, ...
```

**MiMo ignores this field** entirely.

## Request shapes

### MiMo

```http
POST {apiBaseUrl}/chat/completions
Authorization: Bearer {apiKey}
api-key: {apiKey}
Content-Type: application/json
```

Body uses multimodal `input_audio` (WAV data URL) and top-level `asr_options.language`.  
Text from `choices[0].message.content`.

### OpenAI-compatible (always transcriptions)

```http
POST {apiBaseUrl}/audio/transcriptions
Authorization: Bearer {apiKey}   # if configured
Content-Type: multipart/form-data
```

| Part | Required | Notes |
|------|----------|--------|
| `file` | yes | `audio.wav` |
| `model` | yes | |
| `response_format` | yes | fixed `json` |
| `language` | no | omitted when config is `auto` / blank |
| `prompt` | no | from `transcriptionPrompt` when non-blank |

Text from JSON `text` (or plain-text body if the server does not return JSON).

There is **no** `chat/completions` / `audio_url` path for this provider anymore.

## Extension API

Third-party recognizers register before the first client tick:

```java
SpeechRecognizer.register(
        10,
        new ResourceLocation("my_mod", "my_recognizer"),
        MySpeechRecognizer::new
);
```

Lower priority numbers win. Registration after client startup triggers a
re-selection automatically. `SpeechRecognizer.recognize(float[])` remains the
simple compatibility API and returns text; the mod UI uses
`recognizeOutcome(float[])` to display request errors.

Implementations should load resources in `activate()`, release them in
`deactivate()`, and call the superclass method after successful activation or
deactivation. Fabric integrations can subscribe through
`McmtiSpeechRecognizerEvents`; Forge integrations subscribe to
`MicrophoneTextInputForge.getEventBus()` and `SpeechRecognizerEvent`.

## Dependencies

| Dependency | Fabric | Forge |
|------------|--------|-------|
| Java 17 | ✓ | ✓ |
| Fabric API | see fabric.mod.json | ❌ |
| MidnightLib | ✓ | ✓ |

## Usage

1. Install the mod and open config (Mod Menu / MidnightLib).
2. Choose provider; set `apiBaseUrl` / `apiKey` / `model` (and prompt if needed).
3. Optionally Load microphone from the config screen (also auto-opens on first record).
4. Default key `V`: record → recognize → send or insert by mode.

## Troubleshooting

- **Not Ready**: MiMo needs API key; OpenAI-compatible needs a non-empty `apiBaseUrl`.
- **Network errors**: ensure the game client can reach the host (firewall, LAN address, proxy port is HTTP not SOCKS-only).
- **Empty recognition**: check logs for HTTP status and response snippet; confirm the server implements `/audio/transcriptions` if using OpenAI-compatible.
- **Prompt seems ignored**: expected for some backends; keep it short vocabulary/topic text, not system-style instructions. MiMo never sends it.

## License

[MIT License](LICENSE).
