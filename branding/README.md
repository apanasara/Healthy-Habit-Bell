# Habit Bell • Official Branding & Asset Specification

This directory maintains the authoritative master vector artwork and high-resolution raster graphic assets for **Habit Bell** across all operating systems and consumer electronics hardware targets (Android Mobile/Tablet, Google Cast, Android TV / Google TV, Apple TV, LG webOS, and Samsung Tizen).

---

## 1. Master Asset Inventory

| File | Format | Dimensions | Background | Description & Target Surface |
| :--- | :--- | :--- | :--- | :--- |
| **`HabitBell_Inkscape.svg`** | Scalable Vector (SVG) | 200mm × 200mm (Vector) | Black (`#000000`) | Master Inkscape vector design project with layers, drop shadow filters, and path nodes. |
| **`HabitBell.svg`** | Scalable Vector (SVG) | 200mm × 200mm (Vector) | Black (`#000000`) | Clean SVG vector export containing the official blooming lotus flower and Tibetan bell symbol. |
| **`HabitBell_ChromeCast.png`** | Raster PNG (32-bit RGBA) | 512 × 512 px | Solid Obsidian (`#060709`) | **Google Cast SDK Developer Console** production app icon. |
| **`HabitBell_Transparent.png`** | Raster PNG (32-bit RGBA) | 1027 × 893 px | 100% Alpha Transparent | High-resolution master transparent logo for splash screens, adaptive icons, and UI headers. |
| **`HabitBell_Black.webp`** | WebP (Lossless) | 756 × 756 px | Solid Obsidian (`#060709`) | High-density WebP graphic for responsive web delivery and store listings. |

---

## 2. Color Palette & Typography

* **Obsidian Slate (Background)**: `#060709` / `#0A0B0E`
* **Vibrant Warm Gold (Brand Primary)**: `#E5A93C`
* **Amber Resonance**: `#F5B041` / `#FDCB6E`
* **Lotus Petal Highlight**: `#FFEAA7`
* **Pure Text / Accents**: `#FFFFFF`

---

## 3. Automated Asset Pipeline

Run the automated Node.js generation script from the project root to generate all multi-density and platform-specific assets:

```bash
node scripts/generate_branding_assets.js
```

### Generated Artifacts:
1. **Android Adaptive Icons** (`app/src/main/res/mipmap-*`):
   - `ic_launcher_foreground.png`: Scaled to 50% canvas width with 25% safety margin, guaranteeing 0% clipping across circular, square, squircle, or pebble launcher masks.
   - `ic_launcher.png` and `ic_launcher_round.png`: Multi-density legacy icons (mdpi 48px to xxxhdpi 192px).
2. **Splash Screen**:
   - `app/src/main/res/drawable/ic_splash_logo.png`: 512×512 transparent logo.
   - `app/src/main/res/drawable/splash_background.xml`: Instant cold-boot window background eliminating white flashes.
   - `app/src/main/res/values-v31/styles.xml`: Android 12+ native `SplashScreen` attributes.
   - `app/src/main/java/com/habitbell/app/ui/screens/SplashScreen.kt`: Serene animated Compose splash overlay.
3. **Android TV & Smart TV**:
   - `app/src/main/res/drawable/tv_banner.png`: 320×180 16:9 Leanback banner.
   - `tv-platforms/lg-webos/splash.png`: 1920×1080 Full HD splash screen.
   - `tv-platforms/lg-webos/icon.png`: 80×80 and 130×130 app tray icons.
   - `tv-platforms/samsung-tizen/icon.png`: 117×117 app tile icon.
   - `tv-platforms/google-cast-receiver/assets/`: 512×512 app icon and transparent logo.
