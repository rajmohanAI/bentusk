# Carowsle

Seamless carousels, grid puzzles, a profile grid planner and highlight covers for Instagram, built natively for Android. Everything runs on the phone: no login, no watermark, no uploads.

## What it does

| Tool | Output |
| --- | --- |
| Seamless carousel | 2 to 20 slides at 1080×1350 (4:5), 1080×1440 (3:4) or 1080×1080 (1:1) that line up edge to edge when swiped |
| Grid puzzle | 3×1 up to 3×5 tiles, numbered in posting order (bottom-right first) |
| Grid preview | A 3-column planner with long-press drag to reorder, saved on the device |
| Highlight covers | 146 icons on 26 backgrounds, exported at 1080×1920 and centred for the circle crop |
| Safe zones | Overlay in the Studio and editors marking where Instagram's slide counter and tag icon sit, and where the 3:4 grid and 1:1 views crop the cover |
| Stickers | Swipe nudges that run across a seam onto the next slide, and call-to-action banners for the closing slide, all editable text |
| Video slides | Put a clip on any carousel slide, trim it (up to 60 s), mute it and frame it; it exports as a 1080-wide H.264 MP4 at 30 fps with the slide's text and stickers burned in, HDR clips tone-mapped to standard colour |
| Export check | Before export, flags empty frames, photos enlarged past their resolution, text under the slide counter or tag icon, text too small to read on a phone, and clips under 3 s; tap a finding to jump to it |
| Pinned posts | Pin up to 3 photos to the top of a grid plan and switch the plan between 3:4 and 1:1 views |
| Caption line breaks | Blank lines are copied with an invisible character so Instagram keeps paragraph spacing (can be turned off) |

## Plans

* **90-day free trial** with every feature unlocked, counted from first launch.
* After the trial the core tools stay free, with one banner ad on the home screen only.
* **Carowsle Pro** is a one-time ₹299 lifetime purchase (Play product id `bentusk_pro_lifetime`): no ads, carousels over 10 slides, PNG export, tile borders, gradient covers and multiple grid plans.

The rules live in `engine/src/main/kotlin/com/bentusk/engine/Entitlement.kt`.

## Precision

The `engine` module is plain Kotlin with no Android dependency, so the geometry is unit tested on the JVM:

* Tiles are exact integer rectangles that cover the canvas once, with no gaps or overlap.
* Each tile is rendered straight from the source photo with the shared canvas transform shifted to that tile. A rasterizer test checks that tile-by-tile output matches a whole-canvas render pixel for pixel, which is what makes carousel seams invisible.
* Photos are decoded at the smallest power-of-two subsample that still gives at least one source pixel per output pixel, within a memory budget, so 50+ MP photos export at full detail without running out of memory.
* EXIF orientation is applied as part of the transform rather than by rotating pixels.
* Wide-gamut photos are converted to sRGB on decode, the colour space Instagram shows.
* Photos drawn well below full size are first shrunk with a Lanczos-3 filter, streamed a few rows at a time, so fine detail stays clean instead of aliasing under a bilinear draw.
* The editor preview draws with the same transform, backdrop and frame code as the exporter, so what you see is what is saved.

Instagram dimensions are kept in one place: `engine/.../InstagramSpec.kt`.

## Project layout

```
engine/   Pure Kotlin geometry, decode planning and plan rules, with tests
app/      Android app (Kotlin, Jetpack Compose, Material 3, Hilt, Room, DataStore)
  core/image     decode, backdrop, tile renderer, export pipeline
  core/storage   MediaStore saver ("Carowsle" album), share sheet
  core/billing   Play Billing for the lifetime unlock
  core/ads       AdMob home banner
  feature/       home, editor (carousel + grid), export, planner, covers, pro
```

## Build

Requirements: JDK 17 and the Android SDK (platform 36).

```
./gradlew :engine:test          # geometry tests, no Android SDK needed
./gradlew :app:assembleDebug    # debug APK in app/build/outputs/apk/debug
```

GitHub Actions runs both on every push and pull request and attaches the debug APK to the run.

### Release builds

Create `keystore.properties` in the project root (never commit it):

```
storeFile=/path/to/bentusk.jks
storePassword=...
keyAlias=bentusk
keyPassword=...
```

or set `BENTUSK_KEYSTORE`, `BENTUSK_KEYSTORE_PASSWORD`, `BENTUSK_KEY_ALIAS` and `BENTUSK_KEY_PASSWORD`. Real AdMob IDs are passed with `-Pbentusk.admobAppId=...` and `-Pbentusk.admobBannerId=...`; without them the build uses Google's test ads.

## Before publishing

* Create the in-app product `bentusk_pro_lifetime` (₹299) in Play Console.
* Replace the AdMob test IDs with real ones.
* Add the privacy policy and the "Not affiliated with Instagram or Meta" line to the listing.
