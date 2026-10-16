# GPS Stamp Camera Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A native Android camera app that burns a proof-of-visit stamp (OSM map, address, lat/long, accuracy/altitude, date/time with GMT offset, note) into each photo and saves it to the gallery with EXIF GPS.

**Architecture:** Single-Activity Jetpack Compose app. Pure-Kotlin units (formatting, tile maths, capture rules, stamp geometry, text fitting) are JVM-unit-tested; Android-bound units (location, geocoder, map tiles, CameraX, MediaStore) are thin wrappers verified by build and on-device checks. One `StampRenderer.render()` produces the stamp bitmap for both the live preview and the saved photo.

**Tech Stack:** Kotlin, Jetpack Compose (Material 3), CameraX, Play Services Location, OkHttp, DataStore Preferences, AndroidX ExifInterface, JUnit 4.

**Spec:** `docs/superpowers/specs/2026-10-16-gps-stamp-camera-design.md`

## Global Constraints

- Package / applicationId `com.gpsstamp.camera`; app name "GPS Stamp Camera".
- minSdk 26, targetSdk 35, compileSdk 35. JVM target 17.
- Gradle wrapper 8.14.3 (distribution `all`, already cached at `~/.gradle/wrapper/dists/gradle-8.14.3-all`), AGP 8.13.0.
- Library versions (in `gradle/libs.versions.toml`): Kotlin 2.1.21 (+ `org.jetbrains.kotlin.plugin.compose` same version), Compose BOM 2025.08.00, activity-compose 1.10.1, core-ktx 1.16.0, lifecycle (runtime-compose, viewmodel-compose) 2.9.2, CameraX 1.4.2 (core, camera2, lifecycle, view), play-services-location 21.3.0, kotlinx-coroutines-play-services 1.10.2, OkHttp 4.12.0, datastore-preferences 1.1.7, exifinterface 1.4.1, material-icons-extended (from BOM), junit 4.13.2. If a version fails to resolve, use the nearest older stable release and note it in the commit message.
- No API keys. OSM tiles from `https://tile.openstreetmap.org/{z}/{x}/{y}.png`, `User-Agent: GPSStampCamera/1.0 (Android; com.gpsstamp.camera)`, map attribution text exactly `© OpenStreetMap`.
- Numbers formatted with `Locale.US`.
- Capture rules: fix age < 30 000 ms, accuracy ≤ 50 m, not mocked (unless `BuildConfig.ALLOW_MOCK_LOCATION`), not already capturing.
- Movement threshold for re-geocoding / re-rendering map: 25 m.
- Photos saved to `Pictures/GPS Stamp Camera/`, JPEG quality 92, filename `GPS_yyyyMMdd_HHmmss.jpg`.
- User-facing copy (exact): `Waiting for GPS…`, `Waiting for GPS… ±N m`, `Fake GPS detected – disable mock location apps`, `Location is off`, `Couldn't save photo: <reason>`, `Precise location is required to stamp photos.`
- `local.properties` (contains `sdk.dir=C\:\\Users\\adith\\AppData\\Local\\Android\\Sdk`) is git-ignored.
- All Gradle commands run with `JAVA_HOME` = `C:\Program Files\Java\jdk-17` via `.\gradlew.bat` from the project root.

## Review Focus

1. **Landscape capture** with the UI locked to portrait: the saved photo must be upright landscape with the stamp along its bottom edge. Owner: Task 8 (OrientationEventListener → `setTargetRotation`), checked in Task 9.
2. **Long address or note** must never run off the panel: address wraps to max 2 lines, every other line ellipsizes with `…`. Owner: Task 4 (`TextFitTest`).
3. **User grants only approximate location** (Android 12+ chooser): treated as not granted, shows `Precise location is required to stamp photos.` Owner: Task 8, checked in Task 9.
4. **High-megapixel sensors (50–200 MP)** decoding into an ARGB bitmap can OOM: capture resolution is capped near 4032×3024. Owner: Task 7, checked in Task 9.
5. **Double-tap on the shutter** must produce exactly one photo. Owner: Task 3 (`canCapture` false while capturing).

---

## File Structure

```
settings.gradle.kts, build.gradle.kts, gradle.properties, gradle/libs.versions.toml
gradle/wrapper/*, gradlew, gradlew.bat, .gitignore, local.properties (ignored)
app/build.gradle.kts
app/src/main/AndroidManifest.xml
app/src/main/res/values/{strings.xml,themes.xml}, res/drawable/ic_launcher_foreground.xml, res/mipmap-anydpi-v26/ic_launcher.xml
app/src/main/java/com/gpsstamp/camera/
  MainActivity.kt
  geo/ResolvedAddress.kt, geo/Distance.kt, geo/AddressResolver.kt
  map/OsmTileMath.kt, map/OsmMapRenderer.kt
  location/LocationState.kt, location/LocationRepository.kt
  capture/CaptureRules.kt, capture/ExifFormat.kt, capture/PhotoSaver.kt
  stamp/StampFormatter.kt, stamp/StampData.kt, stamp/TextFit.kt, stamp/StampGeometry.kt, stamp/StampRenderer.kt
  camera/CameraController.kt
  settings/SettingsStore.kt
  ui/CameraViewModel.kt, ui/CameraScreen.kt, ui/PermissionGate.kt, ui/NoteDialog.kt
app/src/test/java/com/gpsstamp/camera/  (mirrors the pure units)
```

---

### Task 1: Project scaffold + StampFormatter

**Files:**
- Create: all root Gradle files, wrapper, `.gitignore`, `local.properties`, `app/build.gradle.kts`, manifest, resources, `MainActivity.kt` (temporary: Compose `Text("GPS Stamp Camera")`)
- Create: `geo/ResolvedAddress.kt`, `stamp/StampFormatter.kt`
- Test: `app/src/test/java/com/gpsstamp/camera/stamp/StampFormatterTest.kt`

**Interfaces:**
- Produces: `data class ResolvedAddress(val title: String, val fullLine: String)`; `data class StampText(val title: String, val body: List<String>)`; `object StampFormatter { fun formatDateTime(timeMs: Long, zone: ZoneId): String; fun formatLatLong(lat: Double, lon: Double): String; fun formatAccuracyAltitude(accuracyM: Float, altitudeM: Double?): String; fun buildText(lat: Double, lon: Double, accuracyM: Float, altitudeM: Double?, timeMs: Long, zone: ZoneId, address: ResolvedAddress?, note: String): StampText }`
- Produces: `BuildConfig.ALLOW_MOCK_LOCATION` — debug: `project.findProperty("allowMockLocation") == "true"`; release: `false`. `buildFeatures { compose = true; buildConfig = true }`.

- [ ] **Step 1: Generate the wrapper** — run the cached Gradle's `bin\gradle.bat wrapper --gradle-version 8.14.3 --distribution-type all` in the project root (an empty `settings.gradle.kts` with `rootProject.name = "GPSStampCamera"` must exist first). Write the remaining scaffold files. `.gitignore`: `.gradle/`, `build/`, `local.properties`, `.idea/`, `*.iml`, `captures/`.
- [ ] **Step 2: Verify scaffold builds** — `.\gradlew.bat assembleDebug` → `BUILD SUCCESSFUL`.
- [ ] **Step 3: Write failing tests** (`StampFormatterTest`):

```kotlin
val ist = ZoneId.of("Asia/Kolkata"); val ny = ZoneId.of("America/New_York"); val utc = ZoneId.of("UTC")
fun ms(iso: String) = Instant.parse(iso).toEpochMilli()

@Test fun dateTime_positiveOffset() = assertEquals("Friday, 17/07/2026 11:10 AM GMT +05:30", StampFormatter.formatDateTime(ms("2026-07-17T05:40:00Z"), ist))
@Test fun dateTime_negativeOffset() = assertEquals("Thursday, 15/01/2026 12:05 PM GMT -05:00", StampFormatter.formatDateTime(ms("2026-01-15T17:05:00Z"), ny))
@Test fun dateTime_zeroOffset() = assertEquals("Friday, 17/07/2026 12:00 AM GMT +00:00", StampFormatter.formatDateTime(ms("2026-07-17T00:00:00Z"), utc))
@Test fun latLong() = assertEquals("Lat 12.843552° Long 80.154484°", StampFormatter.formatLatLong(12.843552, 80.154484))
@Test fun latLong_negative() = assertEquals("Lat -33.856784° Long -151.215297°", StampFormatter.formatLatLong(-33.856784, -151.215297))
@Test fun accuracyAltitude_rounds() = assertEquals("Accuracy ±5 m · Alt 42 m", StampFormatter.formatAccuracyAltitude(4.6f, 41.5))
@Test fun accuracy_noAltitude() = assertEquals("Accuracy ±12 m", StampFormatter.formatAccuracyAltitude(12.2f, null))
@Test fun buildText_withAddressAndNote() {
    val t = StampFormatter.buildText(12.843552, 80.154484, 4.6f, 41.5, ms("2026-07-17T05:40:00Z"), ist,
        ResolvedAddress("Keelakottiyur, Tamil Nadu, India", "R5V3+9GJ Academic Block, VIT University, Keelakottiyur, Tamil Nadu 600127, India"), "Project review – Team 4")
    assertEquals("Keelakottiyur, Tamil Nadu, India", t.title)
    assertEquals(listOf("R5V3+9GJ Academic Block, VIT University, Keelakottiyur, Tamil Nadu 600127, India",
        "Lat 12.843552° Long 80.154484°", "Accuracy ±5 m · Alt 42 m", "Friday, 17/07/2026 11:10 AM GMT +05:30", "Project review – Team 4"), t.body)
}
@Test fun buildText_noAddress_titleIsCoordinates_noDuplicateLine_blankNoteOmitted() {
    val t = StampFormatter.buildText(12.843552, 80.154484, 4.6f, null, ms("2026-07-17T05:40:00Z"), ist, null, "   ")
    assertEquals("Lat 12.843552° Long 80.154484°", t.title)
    assertEquals(listOf("Accuracy ±5 m", "Friday, 17/07/2026 11:10 AM GMT +05:30"), t.body)
}
```

- [ ] **Step 4: Run** `.\gradlew.bat testDebugUnitTest --tests "*StampFormatterTest"` → FAIL (unresolved reference).
- [ ] **Step 5: Implement `StampFormatter`** — date pattern `EEEE, dd/MM/yyyy hh:mm a 'GMT' xxx` with `Locale.US`; coordinates `%.6f`; metres via `Math.round`; note trimmed, omitted if blank.
- [ ] **Step 6: Run** the same command → PASS.
- [ ] **Step 7: Commit** — `git add -A && git commit -m "feat: project scaffold and stamp text formatter"`

### Task 2: OSM tile maths + distance

**Files:**
- Create: `map/OsmTileMath.kt`, `geo/Distance.kt`
- Test: `map/OsmTileMathTest.kt`, `geo/DistanceTest.kt`

**Interfaces:**
- Produces: `object OsmTileMath { const val TILE_SIZE = 256; fun worldPixel(lat: Double, lon: Double, zoom: Int): WorldPixel; fun tileRange(center: WorldPixel, sizePx: Int): TileRange }`, `data class WorldPixel(val x: Double, val y: Double)`, `data class TileRange(val minX: Int, val maxX: Int, val minY: Int, val maxY: Int)`; `fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double` (earth radius 6 371 008.8 m).

- [ ] **Step 1: Write failing tests**

```kotlin
@Test fun worldPixel_vit() { val p = OsmTileMath.worldPixel(12.843552, 80.154484, 16)
    assertEquals(12124077.698, p.x, 0.01); assertEquals(7784978.436, p.y, 0.01)
    assertEquals(47359, (p.x / 256).toInt()); assertEquals(30410, (p.y / 256).toInt()) }
@Test fun worldPixel_origin() { val p = OsmTileMath.worldPixel(0.0, 0.0, 16); assertEquals(8388608.0, p.x, 1e-6); assertEquals(8388608.0, p.y, 1e-6) }
@Test fun worldPixel_southEast() { val p = OsmTileMath.worldPixel(-33.856784, 151.215297, 16)
    assertEquals(60295, (p.x / 256).toInt()); assertEquals(39325, (p.y / 256).toInt()) }
@Test fun tileRange_straddlesFourTiles() = assertEquals(TileRange(32767, 32768, 32767, 32768), OsmTileMath.tileRange(WorldPixel(8388608.0, 8388608.0), 256))
@Test fun tileRange_singleTile() = assertEquals(TileRange(47359, 47359, 30410, 30410), OsmTileMath.tileRange(WorldPixel(47359 * 256 + 128.0, 30410 * 256 + 128.0), 256))
@Test fun haversine_oneMilliDegreeAtEquator() = assertEquals(111.195, haversineMeters(0.0, 0.0, 0.0, 0.001), 0.01)
@Test fun haversine_samePoint() = assertEquals(0.0, haversineMeters(12.84, 80.15, 12.84, 80.15), 1e-9)
```

- [ ] **Step 2: Run** `.\gradlew.bat testDebugUnitTest --tests "*OsmTileMathTest" --tests "*DistanceTest"` → FAIL.
- [ ] **Step 3: Implement** — Web Mercator: `n = 2^zoom * 256`, `x = (lon + 180) / 360 * n`, `y = (1 - ln(tan φ + sec φ) / π) / 2 * n`. `tileRange`: `min = floor((c - size/2) / 256)`, `max = floor((c + size/2 - 1) / 256)` per axis.
- [ ] **Step 4: Run** → PASS.
- [ ] **Step 5: Commit** — `feat: OSM tile maths and haversine distance`

### Task 3: Location state + capture rules

**Files:**
- Create: `location/LocationState.kt`, `capture/CaptureRules.kt`
- Test: `capture/CaptureRulesTest.kt`

**Interfaces:**
- Produces:
```kotlin
sealed interface LocationState {
    data class Searching(val lastAccuracyM: Float?) : LocationState
    data class Fix(val lat: Double, val lon: Double, val accuracyM: Float, val altitudeM: Double?,
                   val elapsedRealtimeMs: Long, val isMock: Boolean) : LocationState
    data object Disabled : LocationState
}
sealed interface CaptureStatus {
    data object Ready : CaptureStatus
    data class Waiting(val accuracyM: Int?) : CaptureStatus
    data object MockDetected : CaptureStatus
    data object LocationOff : CaptureStatus
}
object CaptureRules {
    const val MAX_FIX_AGE_MS = 30_000L; const val MAX_ACCURACY_M = 50f
    fun status(state: LocationState, nowElapsedMs: Long, allowMock: Boolean): CaptureStatus
    fun canCapture(status: CaptureStatus, capturing: Boolean): Boolean
}
```
  Age uses `elapsedRealtime` (immune to wall-clock changes). Precedence: Disabled → LocationOff; mock (and !allowMock) → MockDetected; stale or inaccurate → Waiting(rounded accuracy, or null when stale); else Ready.

- [ ] **Step 1: Write failing tests** — helper `fix(acc = 5f, ageMs = 1_000L, mock = false)` with `now = 100_000L`:
  - `status(fix(), now, false) == Ready`
  - `status(fix(acc = 50f), …) == Ready`; `status(fix(acc = 50.4f), …) == Waiting(50)`
  - `status(fix(ageMs = 29_999), …) == Ready`; `status(fix(ageMs = 30_000), …) == Waiting(null)`
  - `status(fix(mock = true), now, false) == MockDetected`; `status(fix(mock = true), now, true) == Ready`
  - `status(fix(acc = 120f, mock = true), now, false) == MockDetected` (mock wins)
  - `status(Searching(118.6f), …) == Waiting(119)`; `status(Searching(null), …) == Waiting(null)`
  - `status(Disabled, …) == LocationOff`
  - `canCapture(Ready, false) == true`; `canCapture(Ready, true) == false`; `canCapture(Waiting(5), false) == false`
- [ ] **Step 2: Run** `--tests "*CaptureRulesTest"` → FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run** → PASS.
- [ ] **Step 5: Commit** — `feat: capture gating rules`

### Task 4: Stamp geometry, text fitting and renderer

**Files:**
- Create: `stamp/StampData.kt`, `stamp/TextFit.kt`, `stamp/StampGeometry.kt`, `stamp/StampRenderer.kt`
- Test: `stamp/TextFitTest.kt`, `stamp/StampGeometryTest.kt`

**Interfaces:**
- Consumes: `StampFormatter.buildText`, `ResolvedAddress` (Task 1).
- Produces:
  - `data class StampData(val lat: Double, val lon: Double, val accuracyM: Float, val altitudeM: Double?, val timeMs: Long, val zone: ZoneId, val address: ResolvedAddress?, val map: Bitmap?, val note: String)`
  - `object TextFit { fun ellipsize(text: String, maxWidth: Float, measure: (String) -> Float): String; fun wrap(text: String, maxWidth: Float, maxLines: Int, measure: (String) -> Float): List<String> }`
  - `data class Box(val left: Float, val top: Float, val right: Float, val bottom: Float)`; `data class StampGeometry(val titleSize: Float, val bodySize: Float, val lineHeightFactor: Float, val padding: Float, val map: Box?, val panel: Box, val badge: Box, val totalHeight: Float)`; `object StampGeometryCalc { fun textWidth(widthPx: Int, hasMap: Boolean): Float; fun compute(widthPx: Int, hasMap: Boolean, bodyLineCount: Int): StampGeometry }`
  - `object StampRenderer { fun render(data: StampData, widthPx: Int): Bitmap }` — transparent ARGB bitmap `widthPx × totalHeight`; callers draw it at the bottom of their image.
- Geometry constants (fractions of `w`): margin 0.02, map square 0.24, padding 0.022, title size 0.040, body size 0.028, line height = 1.25 × size, badge height 0.034. Panel height = `max(mapSize, 2·padding + 1.25·title + n·1.25·body)`. Map box bottom-aligned with panel, left = margin. Panel left = margin + (mapSize + margin if map else 0), right = w − margin. Badge sits directly above panel, right-aligned to panel. `totalHeight = margin + badgeH + panelH + margin`. `textWidth = panelWidth − 2·padding`.
- Rendering: panel black alpha 0.6, corner radius 0.015w; map drawn into its box with the same corner radius (clip path); title white bold; body white regular; full-address line (first body line when address present) wrapped to max 2 lines, all other lines ellipsized; badge = rounded dark pill with white "GPS Stamp Camera". Body line count passed to `compute` is after wrapping.

- [ ] **Step 1: Write failing tests** — `val m: (String) -> Float = { it.length * 10f }`:
  - `ellipsize("Hi", 60f, m) == "Hi"`; `ellipsize("Hello world", 60f, m) == "Hello…"`
  - `wrap("576 Chattanooga Valley Rd Flintstone GA", 200f, 2, m) == listOf("576 Chattanooga", "Valley Rd Flintston…")`
  - `wrap("Short line", 200f, 2, m) == listOf("Short line")`
  - `wrap("Supercalifragilisticexpialidocious", 100f, 2, m) == listOf("Supercali…")` (single over-long word ellipsized)
  - `compute(1000, true, 4)`: `map!!.right - map.left == 240f`; `panel.left == 280f`; `panel.right == 980f`; panel height `== 240f` (content 44+50+140=234 < 240); `totalHeight == 20 + 34 + 240 + 20`
  - `compute(1000, false, 6)`: `map == null`; `panel.left == 20f`; panel height `== 44f + 50f + 210f` (content exceeds map size)
  - `compute(2000, true, 4)` totalHeight == 2 × `compute(1000, true, 4)` totalHeight (scales linearly)
- [ ] **Step 2: Run** `--tests "*TextFitTest" --tests "*StampGeometryTest"` → FAIL.
- [ ] **Step 3: Implement `TextFit` and `StampGeometryCalc`.** `ellipsize`: longest prefix `p` with `measure(p.trimEnd() + "…") <= maxWidth`. `wrap`: greedy by words; when lines would exceed `maxLines`, the last line is `ellipsize(remainingText)`.
- [ ] **Step 4: Run** → PASS.
- [ ] **Step 5: Implement `StampData` and `StampRenderer`** using `Paint.measureText` as `measure`. Verify `.\gradlew.bat assembleDebug` → `BUILD SUCCESSFUL`.
- [ ] **Step 6: Commit** — `feat: stamp geometry, text fitting and renderer`

### Task 5: Address resolver + OSM map renderer

**Files:**
- Create: `geo/AddressResolver.kt`, `map/OsmMapRenderer.kt`

**Interfaces:**
- Consumes: `haversineMeters`, `OsmTileMath`, `ResolvedAddress`.
- Produces: `class AddressResolver(context: Context) { suspend fun resolve(lat: Double, lon: Double): ResolvedAddress? }`; `class OsmMapRenderer(context: Context) { suspend fun render(lat: Double, lon: Double): Bitmap? }`.
- Both cache their last result and its point; return the cache when the new point is ≤ 25 m away. Failures return null and are not cached.
- `AddressResolver`: `Geocoder(context, Locale.getDefault())`; API 33+ `getFromLocation(lat, lon, 1, listener)` wrapped in `suspendCancellableCoroutine`; below 33 the blocking call on `Dispatchers.IO`. Title = `listOfNotNull(locality ?: subAdminArea, adminArea, countryName).distinct().joinToString(", ")`; fullLine = `getAddressLine(0)`. `Geocoder.isPresent() == false` → null.
- `OsmMapRenderer`: zoom 16, output 384×384. Tiles via one OkHttp client (`Cache(context.cacheDir/"osm_tiles", 20 MB)`, 10 s timeouts, the Global Constraints User-Agent) on `Dispatchers.IO`; stitch tiles from `tileRange(center, 384)` and crop so the point is at (192,192). Pin: red (`#E53935`) teardrop, tip at centre, 36 px tall, white dot inside. Attribution `© OpenStreetMap` 13 px dark text on a white 70%-alpha strip bottom-right. Any tile failure → null.

- [ ] **Step 1: Implement both classes.**
- [ ] **Step 2: Verify** `.\gradlew.bat assembleDebug testDebugUnitTest` → `BUILD SUCCESSFUL`.
- [ ] **Step 3: Commit** — `feat: geocoder address resolver and OSM map renderer`

### Task 6: Location repository + settings store

**Files:**
- Create: `location/LocationRepository.kt`, `settings/SettingsStore.kt`

**Interfaces:**
- Consumes: `LocationState` (Task 3).
- Produces: `class LocationRepository(context: Context) { fun states(): Flow<LocationState> }` (cold `callbackFlow`; requires permission already granted); `class SettingsStore(context: Context) { val note: Flow<String>; suspend fun setNote(value: String); val lensFacing: Flow<Int>; suspend fun setLensFacing(value: Int) }` (default note `""`, default lens `CameraSelector.LENS_FACING_BACK`).
- `states()`: emit `Disabled` when `LocationManagerCompat.isLocationEnabled` is false, else `Searching(null)`; request `Priority.PRIORITY_HIGH_ACCURACY`, interval 2 000 ms; each location → `Fix(lat, lon, accuracy, altitude if hasAltitude else null, elapsedRealtimeNanos / 1_000_000, isMock)` where `isMock` = `location.isMock` on API 31+, else `isFromMockProvider`. Register a receiver for `LocationManager.PROVIDERS_CHANGED_ACTION` to emit `Disabled` / `Searching(null)`. Unregister both in `awaitClose`.

- [ ] **Step 1: Implement both.**
- [ ] **Step 2: Verify** `.\gradlew.bat assembleDebug` → `BUILD SUCCESSFUL`.
- [ ] **Step 3: Commit** — `feat: fused location repository and settings store`

### Task 7: Camera controller + photo saver

**Files:**
- Create: `camera/CameraController.kt`, `capture/ExifFormat.kt`, `capture/PhotoSaver.kt`
- Test: `capture/ExifFormatTest.kt`

**Interfaces:**
- Consumes: `StampData`, `StampRenderer.render` (Task 4).
- Produces:
  - `class CameraController(context: Context) { val zoomRange: StateFlow<ClosedFloatingPointRange<Float>>; val hasFrontCamera: Boolean; fun bind(owner: LifecycleOwner, previewView: PreviewView, lensFacing: Int); fun setZoom(ratio: Float); fun setFlash(mode: Int); fun setTargetRotation(rotation: Int); suspend fun takePicture(): ImageProxy }`
  - `object ExifFormat { fun dateTime(timeMs: Long, zone: ZoneId): String; fun offset(timeMs: Long, zone: ZoneId): String; fun fileName(timeMs: Long, zone: ZoneId): String }`
  - `class PhotoSaver(context: Context) { suspend fun save(image: ImageProxy, data: StampData): Uri }` (always closes `image`).
- `ImageCapture`: `CAPTURE_MODE_MINIMIZE_LATENCY`, `ResolutionSelector` with `ResolutionStrategy(Size(4032, 3024), FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER)` and 4:3 aspect (Review Focus 4). Preview also 4:3. `zoomRange` updated from `cameraInfo.zoomState`.
- `PhotoSaver` on `Dispatchers.IO`: `image.toBitmap()`, rotate by `imageInfo.rotationDegrees`, copy to mutable, draw `StampRenderer.render(data, bitmap.width)` at `y = height − stampHeight`. MediaStore insert: API 29+ `RELATIVE_PATH = "Pictures/GPS Stamp Camera"` + `IS_PENDING = 1`, cleared after write; API 26–28 `DATA` = public Pictures dir + `/GPS Stamp Camera/<fileName>` (mkdirs). Compress JPEG 92. Then `ExifInterface(fd)` from `openFileDescriptor(uri, "rw")`: `setLatLong`, `setAltitude` (if non-null), `TAG_DATETIME_ORIGINAL`, `TAG_OFFSET_TIME_ORIGINAL`, `saveAttributes()`. On any exception delete the inserted row and rethrow.

- [ ] **Step 1: Write failing tests** (`ExifFormatTest`):
  - `dateTime(ms("2026-07-17T05:40:00Z"), Asia/Kolkata) == "2026:07:17 11:10:00"`
  - `offset(ms("2026-07-17T05:40:00Z"), Asia/Kolkata) == "+05:30"`; `offset(ms("2026-01-15T17:05:00Z"), America/New_York) == "-05:00"`; `offset(…, UTC) == "+00:00"`
  - `fileName(ms("2026-07-17T05:40:00Z"), Asia/Kolkata) == "GPS_20260717_111000.jpg"`
- [ ] **Step 2: Run** `--tests "*ExifFormatTest"` → FAIL.
- [ ] **Step 3: Implement `ExifFormat`** (`xxx` offset pattern, `Locale.US`).
- [ ] **Step 4: Run** → PASS.
- [ ] **Step 5: Implement `CameraController` and `PhotoSaver`**; verify `.\gradlew.bat assembleDebug` → `BUILD SUCCESSFUL`.
- [ ] **Step 6: Commit** — `feat: CameraX controller and stamped photo saver`

### Task 8: ViewModel, camera screen and permissions

**Files:**
- Create: `ui/CameraViewModel.kt`, `ui/CameraScreen.kt`, `ui/PermissionGate.kt`, `ui/NoteDialog.kt`
- Modify: `MainActivity.kt`, `AndroidManifest.xml`, `res/values/strings.xml`

**Interfaces:**
- Consumes: everything above.
- Produces: `class CameraViewModel(app: Application) : AndroidViewModel(app)` exposing `val ui: StateFlow<CameraUiState>`, `val camera: CameraController`, `fun capture()`, `fun setNote(String)`, `fun toggleLens()`, `fun cycleFlash()`, `fun setZoom(Float)`, `fun clearError()`; `data class CameraUiState(val status: CaptureStatus, val capturing: Boolean, val preview: StampData?, val note: String, val lensFacing: Int, val flashMode: Int, val zoom: Float, val lastPhotoUri: Uri?, val error: String?)`, with `val canCapture get() = CaptureRules.canCapture(status, capturing)`.
- ViewModel: collects `LocationRepository.states()`; on each `Fix` calls `AddressResolver.resolve` and `OsmMapRenderer.render` (they self-throttle at 25 m); a 1 s ticker recomputes `status` (with `SystemClock.elapsedRealtime()` and `BuildConfig.ALLOW_MOCK_LOCATION`) and `preview` (with `System.currentTimeMillis()`, `ZoneId.systemDefault()`). `capture()`: return unless `canCapture`; snapshot `preview` with fresh `timeMs`; set `capturing`; `takePicture()` → `PhotoSaver.save` → `lastPhotoUri`; on exception `error = "Couldn't save photo: ${e.message}"`; finally `capturing = false`.
- Manifest: `CAMERA`, `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`, `INTERNET`, `WRITE_EXTERNAL_STORAGE` with `maxSdkVersion="28"`; `<uses-feature android:name="android.hardware.camera.any" />`; activity `screenOrientation="portrait"`.
- `PermissionGate`: requests the above (storage only on API ≤ 28). Coarse-only → `Precise location is required to stamp photos.` + retry button (Review Focus 3). After denial with `shouldShowRequestPermissionRationale == false` → button opens `Settings.ACTION_APPLICATION_DETAILS_SETTINGS`.
- `CameraScreen` (black background, top → bottom): top bar [flash icon cycling OFF→AUTO→ON, switch-lens icon (hidden if `!hasFrontCamera`), edit-note icon]; 3:4 `PreviewView` (`AndroidView`) with stamp overlay drawn at its bottom via `StampRenderer.render(preview, previewWidthPx).asImageBitmap()` (re-render only when `preview` changes) and a status banner at its top (`Waiting` → `Waiting for GPS…` / `Waiting for GPS… ±N m`, `MockDetected` red, `LocationOff` with an "Open settings" button → `ACTION_LOCATION_SOURCE_SETTINGS`); zoom chips `0.6x` (only if `zoomRange.start <= 0.6f`), `1x`, `2x` (only if `zoomRange.endInclusive >= 2f`); bottom row [last-photo thumbnail → `ACTION_VIEW` `image/jpeg` with read grant, white shutter (grey + disabled when `!canCapture`), spacer]. Errors via `SnackbarHost`. `NoteDialog`: single-line field, max 60 chars, Save/Cancel.
- Orientation (Review Focus 1): `OrientationEventListener` in `CameraScreen` maps degrees 45–134 → `ROTATION_270`, 135–224 → `ROTATION_180`, 225–314 → `ROTATION_90`, else `ROTATION_0`, into `camera.setTargetRotation`.

- [ ] **Step 1: Implement the four UI files, MainActivity and manifest changes.**
- [ ] **Step 2: Verify** `.\gradlew.bat assembleDebug testDebugUnitTest` → `BUILD SUCCESSFUL`, all tests pass.
- [ ] **Step 3: Commit** — `feat: camera screen, permissions and capture flow`

### Task 9: On-device verification

**Files:** none (fixes found here go into the owning task's files, committed as `fix: …`).

- [ ] **Step 1: Build for emulator** — `.\gradlew.bat assembleDebug -PallowMockLocation=true`.
- [ ] **Step 2: Start emulator** `Medium_Phone_API_36.1` (background), wait for `adb shell getprop sys.boot_completed` = `1`, `adb install -r app\build\outputs\apk\debug\app-debug.apk`.
- [ ] **Step 3: Set location** `adb emu geo fix 80.154484 12.843552` and launch; grant permissions in the UI.
- [ ] **Step 4: Check preview** — screenshot shows stamp with map, `Lat 12.843552° Long 80.154484°`, date with `GMT +05:30` (set emulator timezone if needed); shutter enabled.
- [ ] **Step 5: Capture** — tap shutter; `adb shell ls /sdcard/Pictures/GPS\ Stamp\ Camera/` lists one `GPS_*.jpg`; pull it, view it: stamp matches preview, upright.
- [ ] **Step 6: EXIF** — read pulled file's GPS tags (e.g. with a short script) → lat ≈ 12.843552, lon ≈ 80.154484.
- [ ] **Step 7: Review Focus checks** — rotate emulator to landscape, capture → landscape image, stamp along bottom (RF1); set a 60-char note → ellipsized not clipped (RF2); revoke and grant approximate-only location → precise-location message (RF3); double-tap shutter → still one new file (RF5). Front camera capture → stamp text readable (not mirrored).
- [ ] **Step 8: Release-mode mock check is manual on a real phone** — report this as not verified here.
- [ ] **Step 9: Commit any fixes**; report APK path for installing on a phone.
