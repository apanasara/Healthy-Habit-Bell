#!/usr/bin/env python3
"""
Surya Namaskar Voice Generator Script
-------------------------------------
Generates the 12 classical Surya Namaskar Asana cues and Solar Mantras
using the project's standard melodious Lata-style voice profile:
- Voice Engine: Microsoft hi-IN-SwaraNeural (Natural Hindi / Sanskrit Female)
- Pitch: +52Hz (crystalline, gentle, mind-pleasing)
- Rate: -30% (unhurried yogic cadence)
- Gain Standard: 0.52f (whisper-soft)
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
RATE = "-30%"

# 12 Classical Surya Namaskar Steps (Asana + Solar Mantra + Breath Direction)
SURYA_NAMASKAR_STEPS = [
    {
        "step": 1,
        "raw_name": "surya_01_pranamasana",
        "asana": "प्रणामासन",
        "mantra": "ॐ मित्राय नमः",
        "breath": "Inhale and Exhale"
    },
    {
        "step": 2,
        "raw_name": "surya_02_hastauttanasana",
        "asana": "हस्तउत्तानासन",
        "mantra": "ॐ रवये नमः",
        "breath": "Inhale"
    },
    {
        "step": 3,
        "raw_name": "surya_03_padahastasana",
        "asana": "पादहस्तासन",
        "mantra": "ॐ सूर्याय नमः",
        "breath": "Exhale"
    },
    {
        "step": 4,
        "raw_name": "surya_04_ashwasanchalanasana",
        "asana": "अश्वसञ्चालनासन",
        "mantra": "ॐ भानवे नमः",
        "breath": "Inhale"
    },
    {
        "step": 5,
        "raw_name": "surya_05_dandasana",
        "asana": "दण्डासन",
        "mantra": "ॐ खगाय नमः",
        "breath": "Retain"
    },
    {
        "step": 6,
        "raw_name": "surya_06_ashtanganamaskara",
        "asana": "अष्टाङ्ग नमस्कार",
        "mantra": "ॐ पूष्णे नमः",
        "breath": "Exhale"
    },
    {
        "step": 7,
        "raw_name": "surya_07_bhujangasana",
        "asana": "भुजङ्गासन",
        "mantra": "ॐ हिरण्यगर्भाय नमः",
        "breath": "Inhale"
    },
    {
        "step": 8,
        "raw_name": "surya_08_parvatasana",
        "asana": "पर्वतासन",
        "mantra": "ॐ मरीचये नमः",
        "breath": "Exhale"
    },
    {
        "step": 9,
        "raw_name": "surya_09_ashwasanchalanasana",
        "asana": "अश्वसञ्चालनासन",
        "mantra": "ॐ आदित्याय नमः",
        "breath": "Inhale"
    },
    {
        "step": 10,
        "raw_name": "surya_10_padahastasana",
        "asana": "पादहस्तासन",
        "mantra": "ॐ सवित्रे नमः",
        "breath": "Exhale"
    },
    {
        "step": 11,
        "raw_name": "surya_11_hastauttanasana",
        "asana": "हस्तउत्तानासन",
        "mantra": "ॐ अर्काय नमः",
        "breath": "Inhale"
    },
    {
        "step": 12,
        "raw_name": "surya_12_pranamasana",
        "asana": "प्रणामासन",
        "mantra": "ॐ भास्कराय नमः",
        "breath": "Exhale"
    }
]

async def generate_clips(output_dir: str = "app/src/main/res/raw"):
    os.makedirs(output_dir, exist_ok=True)
    print(f"=== Synthesizing Surya Namaskar Audio Assets ({len(SURYA_NAMASKAR_STEPS)} steps) ===")
    print(f"Voice Profile: {VOICE_ENGINE} | Pitch: {PITCH} | Rate: {RATE}\n")

    for item in SURYA_NAMASKAR_STEPS:
        # 1. Asana + Mantra Cue (Option 1: Sanskrit / Mantra)
        sanskrit_text = f"{item['mantra']}, {item['asana']}"
        sanskrit_out = os.path.join(output_dir, f"{item['raw_name']}_sanskrit.mp3")
        comm_sanskrit = edge_tts.Communicate(sanskrit_text, VOICE_ENGINE, rate=RATE, pitch=PITCH)
        await comm_sanskrit.save(sanskrit_out)
        print(f"✓ Generated [{item['step']:02d}/12] Sanskrit: {sanskrit_out} ({sanskrit_text})")

        # 2. Asana + English Direction Cue (Option 2: Bilingual)
        bilingual_text = f"{item['asana']}, {item['breath']}"
        bilingual_out = os.path.join(output_dir, f"{item['raw_name']}_bilingual.mp3")
        comm_bilingual = edge_tts.Communicate(bilingual_text, VOICE_ENGINE, rate=RATE, pitch=PITCH)
        await comm_bilingual.save(bilingual_out)
        print(f"✓ Generated [{item['step']:02d}/12] Bilingual: {bilingual_out} ({bilingual_text})\n")

    print("=== All 24 Surya Namaskar audio assets generated successfully! ===")

if __name__ == "__main__":
    out_path = sys.argv[1] if len(sys.argv) > 1 else "app/src/main/res/raw"
    asyncio.run(generate_clips(out_path))
