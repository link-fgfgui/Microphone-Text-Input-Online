# Speech Recognizer Extension API

MCMti 3.x accepts third-party speech recognizers through the
`me.jaffe2718.mcmti.util.SpeechRecognizer` API. The mod owns microphone capture
and supplies normalized mono `float[]` samples at 16 kHz.

## Implement A Recognizer

```java
public final class MyRecognizer extends SpeechRecognizer {
    public MyRecognizer(Identifier id) {
        super(id);
    }

    @Override
    public boolean enabled() {
        return MyConfig.enabled;
    }

    @Override
    protected Text availableToast() {
        return Text.literal("My recognizer ready");
    }

    @Override
    protected Text unavailableToast() {
        return Text.literal("My recognizer unavailable");
    }

    @Override
    protected void activate() throws IOException {
        // Load resources first. Throw on failure so the next recognizer can run.
        super.activate();
    }

    @Override
    protected void deactivate() {
        // Release resources first.
        super.deactivate();
    }

    @Override
    public String transcribe(float[] audio) {
        return "";
    }
}
```

Register during the loader's client initialization. Smaller priorities run
first, identifiers must be globally unique, and equal priorities are shifted to
the next free value.

```java
SpeechRecognizer.register(
        10,
        Identifier.of("my_mod", "my_recognizer"),
        MyRecognizer::new
);
```

Initial activation happens after all client initializers have run. Registrations
made later trigger re-selection automatically. Call `SpeechRecognizer.init()`
after changing configuration that affects `enabled()` or recognizer resources.

## Events

Fabric listeners use `McmtiSpeechRecognizerEvents`.

NeoForge listeners must register on MCMti's mod bus:

```java
MicrophoneTextInputNeoForge.getEventBus().addListener(
        (SpeechRecognizerEvent.Activated event) -> {
            // event.getRecognizer()
        }
);
```

Available event categories are registered, activated, deactivated, transcribed,
and all-recognizers-deregistered. Audio and identifier arrays supplied by events
are defensive copies.

## Error Contract

`SpeechRecognizer.recognize(float[])` returns recognized text or an empty string.
Implementations may throw runtime exceptions from `transcribe`; MCMti catches and
logs them. `activate()` may throw `IOException` or a runtime configuration error;
MCMti cleans up the failed recognizer and attempts the next enabled one.
