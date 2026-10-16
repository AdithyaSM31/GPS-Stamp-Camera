<p align="center">
  <img src="docs/assets/icon.svg" width="128" height="128" alt="GPS Stamp Camera icon">
</p>

<h1 align="center">GPS Stamp Camera</h1>

<p align="center">
  An Android camera for proof-of-visit photos. Every photo carries a stamp showing where and when it was taken.
</p>

<p align="center">
  <a href="https://github.com/AdithyaSM31/GPS-Stamp-Camera/releases/latest"><b>Download the APK</b></a>
</p>

---

## What it does

Take a photo and the app burns a stamp into it:

- a small **OpenStreetMap** map with a pin at your position
- the **place name and street address**
- **latitude / longitude** (6 decimals), **GPS accuracy** and **altitude above sea level** (Android 14+)
- **date and time with the GMT offset**, e.g. `Friday, 16/10/2026 07:54 PM GMT +05:30`
- an optional **note**, e.g. `Project review – Team 4`

The same GPS position and time are also written into the photo's EXIF data. Photos are saved to
`Pictures/GPS Stamp Camera` in the phone's gallery.

### Built to be trusted

- **No stale or vague locations:** the shutter only works with a GPS fix under 30 seconds old and accurate to 50 m or better.
- **Fake GPS is blocked:** if Android reports a mock location, the app shows a warning and disables the shutter.
- **The phone's clock can't backdate a photo:** the stamp time comes from the GPS fix, not the phone's clock setting.
- **The address and map always match the coordinates:** they are only used within 25 m of where they were looked up. Otherwise the stamp shows coordinates only.
- **Camera only:** you can't import and stamp an old photo.

### Camera and gallery

- Front and back camera, 0.6× / 1× / 2× zoom (0.6× only on phones with an ultra-wide lens), flash off / auto / on.
- Hold the phone sideways: the preview stamp and icons turn upright, and the photo is saved in landscape with the stamp along its bottom edge.
- Built-in gallery: swipe through your stamped photos, pinch or double-tap to zoom, share them, open a photo's location in Maps, or delete it.
- Works offline. Without internet, the stamp shows the coordinates without the map and address.

## Install

1. Download `GPS-Stamp-Camera-v1.0.apk` from [Releases](https://github.com/AdithyaSM31/GPS-Stamp-Camera/releases/latest) on an Android 8.0+ phone.
2. Open it and allow your browser or file manager to **install unknown apps** when Android asks.
3. On first launch, grant **Camera** and **Precise location**. "Approximate" location isn't enough to stamp photos.

## Privacy

- No account, no tracking, no uploads. Photos stay on your phone until you share them.
- The app uses the internet only to fetch map tiles from OpenStreetMap and to turn coordinates into an address with Android's built-in geocoder.
- "Photo location" permission is requested only to open older photos in Maps.

## Build from source

Requirements: JDK 17 and the Android SDK (platform 35). Android Studio works too.

```bash
./gradlew assembleDebug        # APK in app/build/outputs/apk/debug/
./gradlew testDebugUnitTest    # JVM unit tests
```

Tech: Kotlin, Jetpack Compose (Material 3), CameraX, Google Play Services Location, OkHttp, DataStore, Coil.
The stamp layout, text fitting, capture rules, tile maths and orientation logic are plain Kotlin covered by unit tests.

```
app/src/main/java/com/gpsstamp/camera/
  camera/    CameraX preview, capture, lens, zoom, flash
  capture/   shutter rules, stamped JPEG + EXIF saving
  location/  fused GPS, mock detection, sea-level altitude
  geo/       address lookup, distance maths
  map/       OpenStreetMap tile maths and mini-map rendering
  stamp/     stamp text, layout and drawing
  gallery/   album query, saved photo locations, Maps links
  ui/        camera, gallery and viewer screens
```

Design notes and implementation plans are in [`docs/superpowers`](docs/superpowers). The manual on-phone test list is
in [`docs/phone-test-checklist.md`](docs/phone-test-checklist.md).

## Credits

Map data © [OpenStreetMap](https://www.openstreetmap.org/copyright) contributors, available under the Open Database License.
