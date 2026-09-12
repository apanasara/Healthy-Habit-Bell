#!/usr/bin/env python3
"""
# Session Preparation Countdown Voice Generator Script
-------------------------------------------------------
Synthesizes studio-mastered voice cues for the 5-second session preparation countdown:
1. "Take your position" (initial lead-in spoken at 5 seconds)
2. "Three" (spoken at 3 seconds)
3. "Two" (spoken at 2 seconds)
4. "One" (spoken at 1 second)

## Acoustic Profile Standard (Lata Mangeshkar / SwaraNeural Profile)
- Voice Engine: Microsoft Natural Neural `hi-IN-SwaraNeural`
- Pitch: `+52Hz` (crystalline, gentle, mind-pleasing)
- Speech Rate: `-10%` for lead-in phrase, `+15%` for countdown numbers
- Playback Target Gain: `0.52f` (subdued whisper-level gain)

Outputs generated files directly into `app/src/main/res/raw/`.
"""

import asyncio
import os
import subprocess
import sys

try:
    import edge_tts
except ImportError:
    print("Installing edge-tts...")
    subprocess.check_call([sys.executable, "-m", "pip", "install", "--user", "edge-tts"])
    import edge_tts

VOICE_ENGINE = "hi-IN-SwaraNeural"
PITCH = "+52Hz"

PREP_CUES = [
    {
        "filename": "prep_take_position.mp3",
        "text": "Take your position",
        "rate": "-10%"
    },
    {
        "filename": "prep_three.mp3",
        "text": "Three",
        "rate": "+15%"
    },
    {
        "filename": "prep_two.mp3",
        "text": "Two",
        "rate": "+15%"
    },
    {
        "filename": "prep_one.mp3",
        "text": "One",
        "rate": "+15%"
    }
]

async def generate_cues():
    """
    Synthesizes all preparation voice cues asynchronously into the raw resources folder.
    """
    script_dir = os.path.dirname(os.path.abspath(__file__))
    project_root = os.path.dirname(script_dir)
    raw_dir = os.path.join(project_root, "app", "src", "main", "res", "raw")
    os.makedirs(raw_dir, exist_ok=True)

    print(f"Synthesizing {len(PREP_CUES)} preparation voice cues into: {raw_dir}")

    for cue in PREP_CUES:
        output_path = os.path.join(raw_dir, cue["filename"])
        print(f"Generating '{cue['text']}' -> {cue['filename']} (Rate: {cue['rate']}, Pitch: {PITCH})...")
        communicate = edge_tts.Communicate(
            text=cue["text"],
            voice=VOICE_ENGINE,
            rate=cue["rate"],
            pitch=PITCH
        )
        await communicate.save(output_path)
        print(f"  ✓ Successfully saved: {output_path}")

    print("All preparation countdown audio assets generated successfully.")

if __name__ == "__main__":
    asyncio.run(generate_cues())
