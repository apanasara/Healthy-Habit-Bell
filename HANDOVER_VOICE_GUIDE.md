# Voice Cue Handover Note: Studio Voice Generator & Acoustic Specification

This handover note serves as the authoritative guide for any parallel developer or autonomous AI session building voice cues (e.g. for **Surya Namaskar**, **Yoga Sequencer**, or other mindfulness features) in Habit Bell.

---

## 1. Acoustic Profile & Voice Specification

To maintain a consistent, soothing, and anti-startle audio experience across all routines, all voice cues must strictly follow this acoustic profile:

| Parameter | Value | Rationale & Architectural Semantics |
| :--- | :--- | :--- |
| **TTS Engine** | `hi-IN-SwaraNeural` | Microsoft Natural Neural voice (Hindi / Sanskrit female). |
| **Tonal Style** | Bollywood Singer **Lata Mangeshkar** | Sweet, soft, high-frequency swara; highly pleasing, serene, meditative. |
| **Pitch** | `+52Hz` | Crystalline higher frequency (warm and melodic, never harsh or shrill). |
| **Speech Rate** | `-30%` (or `-20%` for single words) | Unhurried yogic cadence (~2.2s for single words, ~2.8s–3.0s for compound phrases). |
| **Playback Gain** | `0.52f` (default) | Subdued whisper-level gain (0.15f..1.0f range) to prevent startling practitioners during meditation (*dhyana*). |
| **Lead Delay** | `120ms` | Grace period after background music ducking begins before speech playback starts. |
| **Audio Ducking** | `0.20f` volume over `350ms` | Ambient music is smoothly ducked using raised-cosine S-curve crossfading, restored over `500ms`. |

### Crucial Acoustic Directives from User Feedback:
- **NO artificial vocalizations**: Do **NOT** add synthetic filler sounds or words like *"aaha"*.
- **NO deep/bass frequencies**: Avoid low frequencies (-18Hz to -42Hz); user specifically requested high frequency sweet voice.
- **NO exaggerated pauses**: Avoid large ellipses like `...` that create 2–3 second silences mid-phrase. Use clean comma or single space separation (e.g., `"पूरक Inhale"`, `"ॐ मित्राय नमः, प्रणामासन"`).
- **Anti-Clipping Step Duration Guard**: Any step whose allocated duration is small (< 4 seconds) must avoid compound bilingual phrases and instead fall back to concise single-language cues (~2.2s) so speech is never cut off mid-word by the next step.

---

## 2. Reusable Studio Script (`scripts/generate_surya_namaskar_voice.py`)

A pre-configured, production-ready script is committed in the repository at:
[`scripts/generate_surya_namaskar_voice.py`](file:///Users/amit.manasara/Documents/Google/Health%20App/Healthy-Habit-Bell/scripts/generate_surya_namaskar_voice.py)

### How to Run:
```bash
python3 scripts/generate_surya_namaskar_voice.py
```

### What It Produces:
It synthesizes all 24 audio assets directly into `app/src/main/res/raw/`:
- **12 Option 1 (Sanskrit Solar Mantras + Asanas)**:
  1. `surya_01_pranamasana_sanskrit.mp3` (*ॐ मित्राय नमः, प्रणामासन*)
  2. `surya_02_hastauttanasana_sanskrit.mp3` (*ॐ रवये नमः, हस्तउत्तानासन*)
  3. `surya_03_padahastasana_sanskrit.mp3` (*ॐ सूर्याय नमः, पादहस्तासन*)
  4. `surya_04_ashwasanchalanasana_sanskrit.mp3` (*ॐ भानवे नमः, अश्वसञ्चालनासन*)
  5. `surya_05_dandasana_sanskrit.mp3` (*ॐ खगाय नमः, दण्डासन*)
  6. `surya_06_ashtanganamaskara_sanskrit.mp3` (*ॐ पूष्णे नमः, अष्टाङ्ग नमस्कार*)
  7. `surya_07_bhujangasana_sanskrit.mp3` (*ॐ हिरण्यगर्भाय नमः, भुजङ्गासन*)
  8. `surya_08_parvatasana_sanskrit.mp3` (*ॐ मरीचये नमः, पर्वतासन*)
  9. `surya_09_ashwasanchalanasana_sanskrit.mp3` (*ॐ आदित्याय नमः, अश्वसञ्चालनासन*)
  10. `surya_10_padahastasana_sanskrit.mp3` (*ॐ सवित्रे नमः, पादहस्तासन*)
  11. `surya_11_hastauttanasana_sanskrit.mp3` (*ॐ अर्काय नमः, हस्तउत्तानासन*)
  12. `surya_12_pranamasana_sanskrit.mp3` (*ॐ भास्कराय नमः, प्रणामासन*)
- **12 Option 2 (Bilingual Asana + Breath Guidance)**:
  - `surya_01_pranamasana_bilingual.mp3` (*प्रणामासन, Inhale and Exhale*)
  - `surya_02_hastauttanasana_bilingual.mp3` (*हस्तउत्तानासन, Inhale*)
  - `surya_03_padahastasana_bilingual.mp3` (*पादहस्तासन, Exhale*)
  - ... and so forth.

---

## 3. Python Snippet for Custom / On-The-Fly Audio Generation

If your parallel session needs to synthesize custom cues, use the following snippet:

```python
import asyncio
import edge_tts

async def synthesize_voice_cue(text: str, output_path: str, rate: str = "-30%", pitch: str = "+52Hz"):
    """
    Synthesizes speech matching Habit Bell's Lata-style SwaraNeural profile.
    
    :param text: Spoken text (e.g. Sanskrit Devanagari or English text).
    :param output_path: Destination file path (.mp3).
    :param rate: Speech rate ('-20%' for single words, '-30%' for phrases).
    :param pitch: Voice pitch ('+52Hz' standard).
    """
    comm = edge_tts.Communicate(
        text=text,
        voice="hi-IN-SwaraNeural",
        rate=rate,
        pitch=pitch
    )
    await comm.save(output_path)
    print(f"Generated voice cue: {output_path}")

# Example usage:
# asyncio.run(synthesize_voice_cue("ॐ मित्राय नमः, प्रणामासन", "app/src/main/res/raw/sample.mp3"))
```

---

## 4. Kotlin Integration Standard (`PranayamaVoiceGuide.kt` Pattern)

When integrating generated cues into your feature's engine or voice guide:

1. **Place raw files in `app/src/main/res/raw/<unique_name>.mp3`**.
2. **Resource Resolution Pattern**:
   ```kotlin
   val resId = when (style) {
       VoiceCueStyle.SANSKRIT -> R.raw.surya_01_pranamasana_sanskrit
       VoiceCueStyle.BILINGUAL -> {
           if (stepDurationSeconds < 4) R.raw.surya_01_pranamasana_sanskrit
           else R.raw.surya_01_pranamasana_bilingual
       }
       else -> null
   }
   ```
3. **Audio Playback with Raised-Cosine S-Curve Ducking**:
   - Duck background music before playback: `bgMusicManager.duckVolume(0.20f, 350L)`
   - Wait 120ms lead delay: `Handler(Looper.getMainLooper()).postDelayed({ ... }, 120L)`
   - Play via `MediaPlayer` with `volume.coerceIn(0.15f, 1.0f)`
   - On completion: restore background music: `bgMusicManager.restoreVolume(500L)`
4. **Android Native TTS Fallback**:
   - Configure local TTS with:
     ```kotlin
     engine.setSpeechRate(0.55f) // slow meditative cadence
     engine.setPitch(1.14f)      // sweet high pitch matching Lata-style tone
     ```

---

## 5. Architectural Reference

For complete cross-subsystem documentation, refer to:
- [`ARCHITECTURE.md`](file:///Users/amit.manasara/Documents/Google/Health%20App/Healthy-Habit-Bell/ARCHITECTURE.md) (Section 2.2: Audio & Soundscape Engine)
- [`PranayamaVoiceGuide.kt`](file:///Users/amit.manasara/Documents/Google/Health%20App/Healthy-Habit-Bell/app/src/main/java/com/habitbell/app/engine/PranayamaVoiceGuide.kt) (Reference implementation of playback, anti-startle delay, and ducking)
