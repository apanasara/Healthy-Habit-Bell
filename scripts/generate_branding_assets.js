/**
 * @file generate_branding_assets.js
 * @description Build script for generating multi-resolution raster branding assets across Android, Google Cast, and Smart TV platforms.
 * 
 * Architectural Role:
 * - Ingests canonical source branding assets from `Healthy-Habit-Bell/branding/` (SVG, high-res PNG, and WebP).
 * - Synthesizes Android adaptive launcher foregrounds, legacy mipmaps, round icons, splash screen icons,
 *   Android TV / Google TV 16:9 Leanback banners, and Smart TV (LG webOS, Samsung Tizen) splash/icon assets.
 * - Employs headless Google Chrome and macOS `sips` for hardware-accelerated, subpixel-precise rendering.
 * 
 * Concurrency & Thread Safety:
 * - Operates as a single-threaded Node.js command-line automation utility, executing synchronous child processes.
 */

const fs = require("fs");
const path = require("path");
const { execFileSync } = require("child_process");

/** Path to headless Chrome executable on macOS. */
const CHROME_PATH = "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome";

/** Project root and directory constants. */
const ROOT_DIR = path.resolve(__dirname, "..");
const APP_RES_DIR = path.resolve(ROOT_DIR, "Healthy-Habit-Bell/app/src/main/res");
const BRANDING_DIR = path.resolve(ROOT_DIR, "Healthy-Habit-Bell/branding");
const TV_PLATFORMS_DIR = path.resolve(ROOT_DIR, "Healthy-Habit-Bell/tv-platforms");

/** Source asset file paths. */
const SRC_TRANSPARENT_PNG = path.join(BRANDING_DIR, "HabitBell_Transparent.png");
const SRC_SOLID_CHROMECAST_PNG = path.join(BRANDING_DIR, "HabitBell_ChromeCast.png");

/**
 * Executes headless Chrome to render an HTML document to an exact-resolution PNG screenshot.
 *
 * @param {string} htmlContent Full HTML markup string to render.
 * @param {number} width Target viewport and output image width in pixels.
 * @param {number} height Target viewport and output image height in pixels.
 * @param {string} outputPath Target absolute filesystem destination path for the PNG file.
 * @param {boolean} transparent Whether the background should render with 100% alpha transparency.
 * @throws {Error} If Chrome execution fails or exits with a non-zero exit code.
 */
function renderHtmlToPng(htmlContent, width, height, outputPath, transparent = false) {
  const tempHtmlPath = path.join("/tmp", `render_${Date.now()}_${Math.random().toString(36).substring(2)}.html`);
  fs.writeFileSync(tempHtmlPath, htmlContent, "utf8");

  const args = [
    "--headless",
    "--disable-gpu",
    `--window-size=${width},${height}`,
    `--screenshot=${outputPath}`,
    `file://${tempHtmlPath}`
  ];

  if (transparent) {
    args.push("--default-background-color=00000000");
  }

  try {
    execFileSync(CHROME_PATH, args, { stdio: "ignore" });
  } finally {
    if (fs.existsSync(tempHtmlPath)) {
      fs.unlinkSync(tempHtmlPath);
    }
  }
}

/**
 * Resizes an existing image file using the macOS `sips` system tool.
 *
 * @param {string} inputPath Source image path.
 * @param {number} width Target width in pixels.
 * @param {number} height Target height in pixels.
 * @param {string} outputPath Destination image path.
 * @throws {Error} If `sips` command fails.
 */
function resizeWithSips(inputPath, width, height, outputPath) {
  execFileSync("/usr/bin/sips", ["-z", String(height), String(width), inputPath, "--out", outputPath], { stdio: "ignore" });
}

/**
 * Generates Android Adaptive Icon Foregrounds for all standard screen densities.
 *
 * Safe-zone ratio: In Android adaptive icons, canvas is 108dp x 108dp with a 72dp center safe zone.
 * The transparent logo is centered and scaled to 50% of the canvas width to guarantee 0% clipping
 * across circle, squircle, teardrop, and rounded square OEM launcher masks.
 */
function generateAdaptiveForegrounds() {
  const densities = [
    { name: "mipmap-mdpi", size: 108, logoWidth: 54 },
    { name: "mipmap-hdpi", size: 162, logoWidth: 81 },
    { name: "mipmap-xhdpi", size: 216, logoWidth: 108 },
    { name: "mipmap-xxhdpi", size: 324, logoWidth: 162 },
    { name: "mipmap-xxxhdpi", size: 432, logoWidth: 216 }
  ];

  console.log("Generating Adaptive Icon Foregrounds...");
  for (const density of densities) {
    const targetDir = path.join(APP_RES_DIR, density.name);
    fs.mkdirSync(targetDir, { recursive: true });
    const targetFile = path.join(targetDir, "ic_launcher_foreground.png");

    const html = `<!DOCTYPE html>
<html>
<head>
<meta charset="utf-8">
<style>
  * { margin: 0; padding: 0; box-sizing: border-box; }
  body {
    width: ${density.size}px;
    height: ${density.size}px;
    background: transparent;
    display: flex;
    justify-content: center;
    align-items: center;
    overflow: hidden;
  }
  img {
    width: ${density.logoWidth}px;
    height: auto;
    display: block;
  }
</style>
</head>
<body>
  <img src="file://${SRC_TRANSPARENT_PNG}" />
</body>
</html>`;

    renderHtmlToPng(html, density.size, density.size, targetFile, true);
    console.log(`  ✓ ${density.name}/ic_launcher_foreground.png (${density.size}x${density.size})`);
  }
}

/**
 * Generates legacy square and round Android launcher icons across all standard screen densities.
 */
function generateLegacyLauncherIcons() {
  const densities = [
    { name: "mipmap-mdpi", size: 48 },
    { name: "mipmap-hdpi", size: 72 },
    { name: "mipmap-xhdpi", size: 96 },
    { name: "mipmap-xxhdpi", size: 144 },
    { name: "mipmap-xxxhdpi", size: 192 }
  ];

  console.log("Generating Legacy Square & Round Launcher Icons...");
  for (const density of densities) {
    const targetDir = path.join(APP_RES_DIR, density.name);
    fs.mkdirSync(targetDir, { recursive: true });

    // Square Icon
    const squareTarget = path.join(targetDir, "ic_launcher.png");
    resizeWithSips(SRC_SOLID_CHROMECAST_PNG, density.size, density.size, squareTarget);
    console.log(`  ✓ ${density.name}/ic_launcher.png (${density.size}x${density.size})`);

    // Round Icon (rendered with circular CSS border-radius)
    const roundTarget = path.join(targetDir, "ic_launcher_round.png");
    const roundHtml = `<!DOCTYPE html>
<html>
<head>
<meta charset="utf-8">
<style>
  * { margin: 0; padding: 0; box-sizing: border-box; }
  body {
    width: ${density.size}px;
    height: ${density.size}px;
    background: transparent;
    display: flex;
    justify-content: center;
    align-items: center;
    overflow: hidden;
  }
  .circle {
    width: ${density.size}px;
    height: ${density.size}px;
    border-radius: 50%;
    overflow: hidden;
    background-color: #060709;
    display: flex;
    justify-content: center;
    align-items: center;
  }
  img {
    width: ${Math.round(density.size * 0.72)}px;
    height: auto;
    display: block;
  }
</style>
</head>
<body>
  <div class="circle">
    <img src="file://${SRC_TRANSPARENT_PNG}" />
  </div>
</body>
</html>`;

    renderHtmlToPng(roundHtml, density.size, density.size, roundTarget, true);
    console.log(`  ✓ ${density.name}/ic_launcher_round.png (${density.size}x${density.size})`);
  }
}

/**
 * Generates Android Splash Screen logo assets and Android TV Leanback 16:9 launcher banner.
 */
function generateSplashAndBannerAssets() {
  console.log("Generating Splash Screen & Android TV Banner Assets...");
  const drawableDir = path.join(APP_RES_DIR, "drawable");
  fs.mkdirSync(drawableDir, { recursive: true });

  // 1. High-resolution Splash Screen Logo (512x512 transparent PNG)
  const splashTarget = path.join(drawableDir, "ic_splash_logo.png");
  const splashHtml = `<!DOCTYPE html>
<html>
<head>
<meta charset="utf-8">
<style>
  * { margin: 0; padding: 0; box-sizing: border-box; }
  body {
    width: 512px;
    height: 512px;
    background: transparent;
    display: flex;
    justify-content: center;
    align-items: center;
    overflow: hidden;
  }
  img {
    width: 380px;
    height: auto;
    display: block;
    filter: drop-shadow(0 12px 28px rgba(0, 0, 0, 0.6));
  }
</style>
</head>
<body>
  <img src="file://${SRC_TRANSPARENT_PNG}" />
</body>
</html>`;
  renderHtmlToPng(splashHtml, 512, 512, splashTarget, true);
  console.log(`  ✓ drawable/ic_splash_logo.png (512x512)`);

  // 2. Android TV / Google TV Leanback 16:9 Launcher Banner (320x180)
  const bannerTarget = path.join(drawableDir, "tv_banner.png");
  const bannerHtml = `<!DOCTYPE html>
<html>
<head>
<meta charset="utf-8">
<style>
  * { margin: 0; padding: 0; box-sizing: border-box; }
  body {
    width: 320px;
    height: 180px;
    background: #060709;
    display: flex;
    flex-direction: column;
    justify-content: center;
    align-items: center;
    overflow: hidden;
    position: relative;
  }
  .radial-glow {
    position: absolute;
    width: 260px;
    height: 260px;
    border-radius: 50%;
    background: radial-gradient(circle, rgba(229, 169, 60, 0.2) 0%, rgba(6, 7, 9, 0) 70%);
  }
  img {
    width: 90px;
    height: auto;
    display: block;
    position: relative;
    z-index: 1;
  }
  .title {
    margin-top: 10px;
    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
    font-size: 13px;
    font-weight: 500;
    letter-spacing: 2px;
    color: #E5A93C;
    text-transform: uppercase;
    position: relative;
    z-index: 1;
  }
</style>
</head>
<body>
  <div class="radial-glow"></div>
  <img src="file://${SRC_TRANSPARENT_PNG}" />
  <div class="title">Habit Bell</div>
</body>
</html>`;
  renderHtmlToPng(bannerHtml, 320, 180, bannerTarget, false);
  console.log(`  ✓ drawable/tv_banner.png (320x180)`);
}

/**
 * Generates TV platform assets: LG webOS splash/icons, Samsung Tizen, and Google Cast Receiver.
 */
function generateTvPlatformAssets() {
  console.log("Generating Smart TV & Google Cast Platform Assets...");

  // 1. LG webOS (1920x1080 Splash and Icons)
  const webosDir = path.join(TV_PLATFORMS_DIR, "lg-webos");
  if (fs.existsSync(webosDir)) {
    const webosSplashTarget = path.join(webosDir, "splash.png");
    const webosSplashHtml = `<!DOCTYPE html>
<html>
<head>
<meta charset="utf-8">
<style>
  * { margin: 0; padding: 0; box-sizing: border-box; }
  body {
    width: 1920px;
    height: 1080px;
    background: #060709;
    display: flex;
    flex-direction: column;
    justify-content: center;
    align-items: center;
    overflow: hidden;
    position: relative;
  }
  .glow {
    position: absolute;
    width: 700px;
    height: 700px;
    border-radius: 50%;
    background: radial-gradient(circle, rgba(229, 169, 60, 0.22) 0%, rgba(6, 7, 9, 0) 70%);
  }
  img {
    width: 280px;
    height: auto;
    position: relative;
    z-index: 2;
    filter: drop-shadow(0 20px 40px rgba(0,0,0,0.8));
  }
  .title {
    margin-top: 28px;
    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
    font-size: 32px;
    font-weight: 300;
    letter-spacing: 6px;
    color: #FFFFFF;
    text-transform: uppercase;
    position: relative;
    z-index: 2;
  }
  .subtitle {
    margin-top: 8px;
    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
    font-size: 16px;
    font-weight: 500;
    letter-spacing: 3px;
    color: #E5A93C;
    text-transform: uppercase;
    position: relative;
    z-index: 2;
  }
</style>
</head>
<body>
  <div class="glow"></div>
  <img src="file://${SRC_TRANSPARENT_PNG}" />
  <div class="title">Habit Bell</div>
  <div class="subtitle">Mindful Wellness & Living Room Timer</div>
</body>
</html>`;
    renderHtmlToPng(webosSplashHtml, 1920, 1080, webosSplashTarget, false);
    console.log(`  ✓ lg-webos/splash.png (1920x1080)`);

    resizeWithSips(SRC_SOLID_CHROMECAST_PNG, 80, 80, path.join(webosDir, "icon.png"));
    resizeWithSips(SRC_SOLID_CHROMECAST_PNG, 130, 130, path.join(webosDir, "largeIcon.png"));
    console.log(`  ✓ lg-webos/icon.png (80x80) & largeIcon.png (130x130)`);
  }

  // 2. Samsung Tizen
  const tizenDir = path.join(TV_PLATFORMS_DIR, "samsung-tizen");
  if (fs.existsSync(tizenDir)) {
    resizeWithSips(SRC_SOLID_CHROMECAST_PNG, 117, 117, path.join(tizenDir, "icon.png"));
    console.log(`  ✓ samsung-tizen/icon.png (117x117)`);
  }

  // 3. Google Cast Receiver
  const castReceiverAssetsDir = path.join(TV_PLATFORMS_DIR, "google-cast-receiver/assets");
  fs.mkdirSync(castReceiverAssetsDir, { recursive: true });
  fs.copyFileSync(SRC_SOLID_CHROMECAST_PNG, path.join(castReceiverAssetsDir, "icon_512.png"));
  fs.copyFileSync(SRC_TRANSPARENT_PNG, path.join(castReceiverAssetsDir, "logo.png"));
  console.log(`  ✓ google-cast-receiver/assets/icon_512.png & logo.png`);
}

/**
 * Main execution entry point.
 */
function main() {
  console.log("=== Habit Bell Official Branding Asset Generator ===");
  if (!fs.existsSync(SRC_TRANSPARENT_PNG) || !fs.existsSync(SRC_SOLID_CHROMECAST_PNG)) {
    console.error("Error: Canonical source assets not found in branding directory.");
    process.exit(1);
  }

  generateAdaptiveForegrounds();
  generateLegacyLauncherIcons();
  generateSplashAndBannerAssets();
  generateTvPlatformAssets();
  console.log("=== All Branding Assets Successfully Generated! ===");
}

main();
