# **Microphone Text Input Mod Developer Documentation**

<div style="text-align: center;">
<p style="font-size: large;">Architectury 2.x</p>
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
- Pluggable `SpeechAsrClient` API
- MiMo: `input_audio` + `asr_options.language`
- OpenAI-compatible: multipart `audio/transcriptions` only
- Optional hotword / prompt (OpenAI-compatible `prompt` field)
- Optional HTTP proxy for online ASR (`host:port` or `http://user:pass@host:port`)

## Architecture

```
AudioRecorder (PCM 16kHz)
        │
        ▼
SpeechRecognizer
        │
        ▼
SpeechAsrClient
        ├── MimoAsrClient
        └── OpenAiCompatibleAsrClient
```

## Configuration presets

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
| `systemPrompt` | 可选热词 / 领域说明 |

### 本地 vLLM Qwen3-ASR

| Field | Value |
|-------|--------|
| `asrProvider` | `OPENAI_COMPATIBLE` |
| `apiBaseUrl` | `http://{IP}:{port}/v1` |
| `apiKey` | 可留空 |
| `model` | 本地模型路径或名称（如部署文档所示） |

## OpenAI-compatible request shape

Always:

```http
POST {apiBaseUrl}/audio/transcriptions
Authorization: Bearer {apiKey}
Content-Type: multipart/form-data
```

字段：`file` (audio.wav)、`model`、`response_format=json`、可选 `language` / `prompt`。  
文本取自 JSON 的 `text` 字段。

## Dependencies

| Dependency | Fabric | NeoForge |
|------------|--------|----------|
| Java 21 | ✓ | ✓ |
| Fabric API | see fabric.mod.json | ❌ |
| Architectury API | ❌ | see neoforge.mods.toml |
| MidnightLib | ✓ | ✓ |

## Usage

1. 安装 mod，打开配置。
2. 选择服务商并填写 base URL / key / model。
3. 默认按键 `V` 录音识别。

## Troubleshooting

- **Not Ready**：MiMo 需 API Key；OpenAI 兼容至少要有效 `apiBaseUrl`。
- **网络错误**：确认游戏客户端能访问对应主机（本地部署注意防火墙 / 地址）。
- **识别空结果**：看日志中的 HTTP 状态与响应摘要。

## License

[MIT License](LICENSE).
