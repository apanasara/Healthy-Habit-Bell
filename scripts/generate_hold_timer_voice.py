#!/usr/bin/env python3
"""
Hold Timer Voice Generator Script
---------------------------------
Generates studio-mastered audio assets for the Yoga & Physiotherapy Hold Timer
using the project's standard melodious Lata-style voice profile:
- Voice Engine: Microsoft hi-IN-SwaraNeural (Natural Indian Female)
- Pitch: +52Hz (crystalline, sweet, serene)
- Cadence: -15% (unhurried yogic pacing)
- Standard Gain: 0.52f
"""

import asyncio
import os
import sys

try:
    import edge_tts
except ImportError:
    print("Installing edge-tts...")
    os.system(f"{sys.executable} -m pip install --user edge-tts")
    import edge_tts

VOICE_ENGINE = "hi-IN-SwaraNeural"
PITCH = "+52Hz"

HOLD_CUES = [
    {
        "filename": "hold_cue_english.mp3",
        "text": "Hold",
        "rate": "-15%"
    },
    {
        "filename": "rest_cue_english.mp3",
        "text": "Rest",
        "rate": "-15%"
    },
    {
        "filename": "hold_session_complete.mp3",
        "text": "Session complete",
        "rate": "-15%"
    }
]

async def generate_cues():
    script_dir = os.path.dirname(os.path.abspath(__file__))
    project_root = os.path.dirname(script_dir)
    raw_dir = os.path.join(project_root, "app", "src", "main", "res", "raw")
    os.makedirs(raw_dir, exist_ok=True)

    print(f"Synthesizing {len(HOLD_CUES)} hold timer voice cues into: {raw_dir}")

    for cue in HOLD_CUES:
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

    print("All hold timer studio audio assets generated successfully.")

if __name__ == "__main__":
    asyncio.run(generate_cues())
