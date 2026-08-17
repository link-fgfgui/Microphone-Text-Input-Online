# Speech Recognizer Extension API

MCMti 3.x accepts third-party speech recognizers through the
`me.jaffe2718.mcmti.util.SpeechRecognizer` API. The mod owns microphone capture
and supplies normalized mono `float[]` samples at 16 kHz.

## Implement A Recognizer

```java
public final class MyRecognizer extends SpeechRecognizer {
    public MyRecognizer(ResourceLocation id) {
        super(id);
    }

    @Override
    public boolean enabled() {
        return MyConfig.enabled;
    }

    @Override
    protected Component availableToast() {
        return Component.literal("My recognizer ready");
    }

    @Override
    protected Component unavailableToast() {
        return Component.literal("My recognizer unavailable");
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
        ResourceLocation.fromNamespaceAndPath("my_mod", "my_recognizer"),
        MyRecognizer::new
);
```

Initial activation happens after all client initializers have run. Registrations
made later re-select incrementally: a newly registered recognizer that is enabled
and has a higher priority (smaller value) than the current instance immediately
takes over the instance id and the previous instance is deactivated — activation
of the new one stays lazy and happens on the next `recognize()` call.
Lower-priority or disabled recognizers simply wait for the next re-selection.
Call `SpeechRecognizer.init()` after changing configuration that affects
`enabled()` or recognizer resources; it re-runs the full selection across all
recognizers.

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
