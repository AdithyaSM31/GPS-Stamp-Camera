# GPS Stamp Camera — Design

Date: 2026-10-16
Status: Draft for review

## Purpose

An Android camera app for **proof-of-visit photos**. A person on site takes a
photo and the app burns a stamp into it showing where and when it was taken,
so a reviewer can trust the photo. Visual reference: "GPS Map Camera: Geo
Tagging" (Play Store) — map thumbnail bottom-left, dark rounded text panel
bottom-right.

## Scope

### In scope (v1)

- Camera-only capture (no importing existing photos).
- Stamp contents:
  - Small OpenStreetMap thumbnail with a red pin at the capture location and
    "© OpenStreetMap" attribution.
  - Title line: locality, state, country (e.g. "Keelakottiyur, Tamil Nadu, India").
  - Full address line(s).
  - `Lat 12.843552° Long 80.154484°`
  - Accuracy and altitude: `Accuracy ±5 m · Alt 42 m`
  - Date/time with timezone: `Friday, 17/07/2026 11:10 AM GMT +05:30`
  - Optional user note line (e.g. "Project review – Team 4"), omitted when empty.
  - App badge "GPS Stamp Camera".
- Front/back camera switch, zoom chips 0.6x / 1x / 2x (0.6x only when the
  device's minimum zoom ratio is ≤ 0.6), flash off/on/auto.
- Live preview shows the same stamp that will be saved.
- Save JPEG to the gallery at `Pictures/GPS Stamp Camera/` with EXIF GPS
  latitude/longitude/altitude and `DateTimeOriginal` + offset.
- Last-photo thumbnail that opens the saved photo in the system viewer for
  sharing.
- Note text and last-used lens persisted between launches.

### Out of scope (v1)

- Server upload, login, admin review.
- Video recording.
- Weather, compass, magnetic field, Plus Code, templates.
- Satellite map view.
- Stamping gallery photos after the fact.

## Platform

- Kotlin, Jetpack Compose, single Activity.
- minSdk 26 (Android 8.0), targetSdk / compileSdk 35.
- Package: `com.gpsstamp.camera`. App name: "GPS Stamp Camera".
- Libraries: CameraX (core, camera2, lifecycle, view), Play Services Location,
  Compose BOM + Material 3, OkHttp, DataStore Preferences, AndroidX
  ExifInterface, Kotlin coroutines. No API keys.

## Architecture

```
CameraScreen (Compose UI)
  ├─ CameraViewModel ── combines state from:
  │    ├─ LocationRepository   → Flow<LocationState>
  │    ├─ AddressResolver      → suspend resolve(lat, lon): Address?
  │    ├─ OsmMapRenderer       → suspend render(lat, lon, sizePx): Bitmap?
  │    └─ SettingsStore        → note, lens
  ├─ CameraController (CameraX: preview, capture, lens, zoom, flash)
  └─ StampOverlay (draws StampRenderer output over the preview)

Capture: CameraViewModel.capture()
  → freeze StampData
  → CameraController.takePicture() (in-memory ImageProxy)
  → PhotoSaver.save(image, stampData) on Dispatchers.IO
     → decode + rotate (+ un-mirror front camera)
     → StampRenderer.draw(canvas, stampData, widthPx)
     → MediaStore insert + JPEG write + ExifInterface tags
  → Uri of saved photo → thumbnail
```

### Units

| Unit | Responsibility | Depends on |
|---|---|---|
| `LocationRepository` | Request high-accuracy updates (interval 2 s) from `FusedLocationProviderClient`; emit `LocationState`: `Searching(lastAccuracyM?)`, `Fix(lat, lon, accuracyM, altitudeM?, timeMs, isMock)`, `Disabled`. Mock detection via `Location.isMock` (API 31+) / `isFromMockProvider` (older). | Play Services Location |
| `AddressResolver` | `android.location.Geocoder` (async listener on API 33+, blocking call on IO dispatcher below). Returns `Address(title, fullLine)` or null. Caches last result; re-resolves only when moved > 25 m from the cached point. | Geocoder |
| `OsmTileMath` | Pure functions: lat/lon + zoom → tile x/y and pixel offset within tile (Web Mercator, 256 px tiles). | none |
| `OsmMapRenderer` | Zoom 16. Fetch the tiles covering a square around the point from `https://tile.openstreetmap.org/{z}/{x}/{y}.png` with a descriptive `User-Agent`, using OkHttp with a 20 MB disk cache. Stitch, crop centered on the point, draw red pin, draw "© OpenStreetMap" in the bottom-right corner. Returns null on any failure. Re-renders only when moved > 25 m. | OkHttp, `OsmTileMath` |
| `StampData` | Immutable snapshot: lat, lon, accuracyM, altitudeM?, timeMs, zoneId, address?, mapBitmap?, note. | none |
| `StampFormatter` | Pure functions producing each text line from `StampData` values (date/time pattern `EEEE, dd/MM/yyyy hh:mm a 'GMT' xxx` → `GMT +05:30`; coordinates to 6 decimals; accuracy/altitude rounded to whole metres). Locale fixed to `Locale.US` for numbers. | none |
| `StampRenderer` | Draws the stamp onto an `android.graphics.Canvas` of a given width, anchored to the bottom. All sizes are proportional to width so preview and full-res output match. Layout: margin 2% of width; map square = panel height on the left (omitted when `mapBitmap` is null, panel spans full width); panel is black at 60% alpha with rounded corners; lines in white sans-serif; title larger. Returns the stamp height so callers can position it. | `StampFormatter` |
| `CaptureRules` | Pure function `canCapture(state, nowMs, allowMock, capturing): Boolean` and `statusMessage(...)` implementing the rules in "Capture rules" below. | none |
| `CameraController` | Binds CameraX `Preview` + `ImageCapture` (`CAPTURE_MODE_MINIMIZE_LATENCY`) to the lifecycle; exposes lens switch, zoom ratio, flash mode, min/max zoom, and `suspend takePicture(): ImageProxy`. | CameraX |
| `PhotoSaver` | Convert `ImageProxy` → `Bitmap`, apply `rotationDegrees` (no horizontal mirroring for the front camera — the saved image is true orientation so text reads correctly), draw stamp, write JPEG quality 92 to MediaStore (`RELATIVE_PATH = Pictures/GPS Stamp Camera`, `IS_PENDING` on API 29+), then write EXIF GPS + `DateTimeOriginal` + `OffsetTimeOriginal`. Returns `Uri`. | MediaStore, ExifInterface, `StampRenderer` |
| `SettingsStore` | DataStore Preferences: `note: String`, `lensFacing: Int`. | DataStore |
| `CameraViewModel` | Holds UI state; combines location, address, map, settings into `previewStamp: StampData?`; computes `canCapture`; runs capture and exposes `lastPhotoUri`, `captureError`. | all of the above |
| `CameraScreen` | Permission gate, `PreviewView`, stamp overlay, status banner, top bar (flash, switch lens, edit note), zoom chips, shutter, last-photo thumbnail, note dialog. | `CameraViewModel`, `CameraController` |

## Capture rules

`canCapture` is true only when **all** hold:

- Camera and fine-location permissions granted.
- `LocationState` is `Fix`.
- Fix age < 30 s (`now - timeMs`).
- `accuracyM <= 50`.
- `isMock == false`, unless the debug-only build flag `ALLOW_MOCK_LOCATION`
  is true (always false in release builds).
- No capture currently in progress.

On shutter press the ViewModel snapshots `StampData` **before** calling
`takePicture()`; the stamp reflects the moment of the press.

## Error handling

| Situation | Behaviour |
|---|---|
| Permissions denied | Full-screen explanation with a "Grant permissions" button; if permanently denied, button opens app settings. |
| Location services off | Banner "Location is off" with button opening `Settings.ACTION_LOCATION_SOURCE_SETTINGS`. |
| Searching / weak fix | Shutter disabled; banner "Waiting for GPS… ±N m". |
| Mock location | Shutter disabled; red banner "Fake GPS detected – disable mock location apps". |
| Geocoder fails / offline | `address = null`; title line becomes the coordinates; full-address line omitted. |
| Tile download fails | `mapBitmap = null`; panel spans full width. |
| Save fails | Snackbar "Couldn't save photo: <reason>"; shutter re-enabled; pending MediaStore row deleted. |

## Testing

- JVM unit tests:
  - `StampFormatterTest`: date/time with positive, negative and zero offsets;
    6-decimal coordinates including negative values; accuracy/altitude
    rounding; null altitude omitted.
  - `OsmTileMathTest`: known lat/lon → tile x/y at zoom 16 (checked against
    published OSM tile numbers) and pixel offsets in range 0–255.
  - `CaptureRulesTest`: `canCapture` truth table (age, accuracy, mock, debug flag).
- Build: `gradlew assembleDebug` and `gradlew testDebugUnitTest` pass.
- Manual on emulator/phone: preview stamp appears, shutter gating works,
  saved photo in gallery shows stamp matching preview, EXIF has GPS.
  Mock-location blocking verified on a real phone with a mock-location app;
  the emulator is run with the debug flag on.
