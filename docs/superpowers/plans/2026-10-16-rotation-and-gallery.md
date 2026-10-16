# Rotation-aware Preview + In-app Gallery Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the preview stamp, banner and icons follow device orientation, and add an in-app gallery (grid → swipe viewer with zoom, Share, Open in Maps, Delete).

**Architecture:** Orientation logic and link/query building are pure Kotlin objects with JVM tests. The UI rotates an "upright frame" inside the portrait-locked preview. The gallery is two Compose screens switched by a `Screen` state in `MainActivity`, backed by a `PhotoRepository` over MediaStore and a `GalleryViewModel`. Coil loads images.

**Tech Stack:** Existing stack + `io.coil-kt:coil-compose:2.7.0`.

**Spec:** `docs/superpowers/specs/2026-10-16-rotation-and-gallery-design.md`

## Global Constraints

- Work on branch `feat/gps-stamp-camera`. All Gradle commands are run in the background or with output to a file, followed by `gradlew --stop` (the PC is low on memory). **Never start the emulator.**
- `Surface.ROTATION_0/90/180/270` = 0/1/2/3. Centres: ROTATION_0 = 0°, ROTATION_270 = 90°, ROTATION_180 = 180°, ROTATION_90 = 270°. Hysteresis is ±30°.
- Compose overlay rotation (clockwise): ROTATION_0 → 0, ROTATION_90 → 90, ROTATION_180 → 180, ROTATION_270 → -90.
- Album: `Pictures/GPS Stamp Camera`. API 29+ filters on `relative_path LIKE 'Pictures/GPS Stamp Camera/%'`; API 26–28 on `_data LIKE '%/Pictures/GPS Stamp Camera/%'`.
- Maps: `geo:<lat>,<lon>?q=<lat>,<lon>(Photo%20location)` and `https://www.google.com/maps/search/?api=1&query=<lat>,<lon>`. Coordinates use 6 decimals with `Locale.US`.
- Copy (exact): `No photos yet`, `Delete this photo?`, `Delete`, `Cancel`, `Share`, `Open in Maps`, `Couldn't delete photo`. The viewer title format is `EEEE, dd/MM/yyyy hh:mm a` (Locale.US, local zone).
- Zoom is 1×–5×; double-tap toggles 1× / 2.5×. The icon rotation animation lasts 200 ms.

## Review Focus

1. **Gallery opened while the camera is bound.** The camera must be unbound when `CameraScreen` leaves composition, and rebound on return; otherwise the camera stays on in the background (battery, privacy LED). Owner: Task 2. Checked on the phone.
2. **Swipe vs pinch conflict.** At 1× a horizontal drag must page; while zoomed, drags pan the image and the pager is locked; leaving a page resets its zoom. Owner: Task 4. Checked on the phone.
3. **Deleting the last photo, or the photo being viewed.** The pager must not crash on an index past the end; the viewer closes when the list is empty. Owner: Task 4.
4. **Photo location permission missing.** The Maps button is hidden and nothing crashes (`SecurityException` caught). Owner: Task 3.
5. **Existing installs.** Users who already granted camera and location must still be asked for `ACCESS_MEDIA_LOCATION`, which is requested lazily the first time the viewer opens. Owner: Task 4. Ruling vs the spec: lazy request instead of in `PermissionGate`, so existing installs are covered too.

---

### Task 1: Orientation logic (pure)

**Files:**
- Create: `ui/OrientationTracker.kt`, `ui/OverlayRotation.kt`
- Test: `ui/OrientationTrackerTest.kt`, `ui/OverlayRotationTest.kt`

**Interfaces:**
- Produces:
  - `object OrientationTracker { fun next(degrees: Int, current: Int): Int }`
  - `object OverlayRotation { fun degreesFor(surfaceRotation: Int): Float; fun isLandscape(surfaceRotation: Int): Boolean; fun nearestEquivalent(target: Float, current: Float): Float }`

- [ ] **Step 1: Write the failing tests**
  - `next(0, R0) == R0`
  - `next(80, R0) == R270`
  - `next(50, R0) == R0` (dead zone)
  - `next(50, R270) == R270`
  - `next(350, R90) == R0`
  - `next(265, R0) == R90`
  - `next(185, R0) == R180`
  - `next(-1, R180) == R180`
  - `degreesFor`: R0 → 0, R90 → 90, R180 → 180, R270 → -90
  - `isLandscape(R90) && isLandscape(R270) && !isLandscape(R0) && !isLandscape(R180)`
  - `nearestEquivalent(-90f, 180f) == 270f`
  - `nearestEquivalent(0f, 270f) == 360f`
  - `nearestEquivalent(90f, 0f) == 90f`
- [ ] **Step 2: Run** `testDebugUnitTest --tests "*OrientationTrackerTest" --tests "*OverlayRotationTest"` → FAIL.
- [ ] **Step 3: Implement.** Use circular distance `min(|d − c|, 360 − |d − c|) <= 30` to each centre. For `nearestEquivalent`, pick `target + 360k` closest to `current`.
- [ ] **Step 4: Run** → PASS. **Commit:** `feat: orientation tracking with hysteresis`

### Task 2: Rotating preview stamp, banner and icons

**Files:**
- Modify: `ui/CameraScreen.kt`, `camera/CameraController.kt`

**Interfaces:**
- Consumes: Task 1.
- Produces: `CameraController.unbind()` (`provider.unbindAll()` if the provider is ready); `CameraScreen(vm: CameraViewModel = viewModel(), onOpenGallery: () -> Unit)`.

- [ ] **Step 1: Implement.**
  - **Rotation state:** `rotation` is held in `mutableIntStateOf(Surface.ROTATION_0)`. The `OrientationEventListener` computes `OrientationTracker.next(degrees, rotation)`; on change it updates the state and calls `camera.setTargetRotation`.
  - **Upright frame:** the preview box becomes a `BoxWithConstraints`. Frame = `requiredSize(if landscape maxHeight else maxWidth, if landscape maxWidth else maxHeight)`, aligned to the centre, with `graphicsLayer { rotationZ = degreesFor(rotation) }`. The stamp (bottom-centre) and status banner (top-centre) move inside it.
  - **Icons:** `iconAngle` = `animateFloatAsState(tween(200))` toward a target updated with `nearestEquivalent`. `Modifier.rotate(iconAngle)` is applied to the flash, switch and note icons, the zoom-chip `Text` and the `LastPhoto` box.
  - **Unbind on leave:** `DisposableEffect(Unit) { onDispose { vm.camera.unbind() } }` (Review Focus 1).
  - **Thumbnail tap:** calls `onOpenGallery()` and is always enabled; `openPhoto` is removed.
- [ ] **Step 2: Verify** `assembleDebug testDebugUnitTest` → `BUILD SUCCESSFUL`. **Commit:** `feat: preview stamp and icons follow device orientation`

### Task 3: Gallery data (pure helpers + repository)

**Files:**
- Create: `gallery/AlbumQuery.kt`, `gallery/MapsLink.kt`, `gallery/PhotoRepository.kt`
- Modify: `capture/PhotoSaver.kt` (use `AlbumQuery.ALBUM`), `gradle/libs.versions.toml`, `app/build.gradle.kts` (Coil), `AndroidManifest.xml` (`ACCESS_MEDIA_LOCATION`)
- Test: `gallery/AlbumQueryTest.kt`, `gallery/MapsLinkTest.kt`

**Interfaces:**
- Produces:
  - `object AlbumQuery { const val ALBUM = "GPS Stamp Camera"; fun selection(sdkInt: Int): String }`
  - `object MapsLink { fun geoUri(lat: Double, lon: Double): String; fun webUrl(lat: Double, lon: Double): String }`
  - `data class GalleryPhoto(val uri: Uri, val id: Long, val dateTakenMs: Long)`
  - `class PhotoRepository(context: Context) { suspend fun list(): List<GalleryPhoto>; suspend fun delete(uri: Uri): Boolean; suspend fun location(uri: Uri): Pair<Double, Double>? }`

- [ ] **Step 1: Write the failing tests**
  - `selection(29) == "relative_path LIKE 'Pictures/GPS Stamp Camera/%'"`
  - `selection(28) == "_data LIKE '%/Pictures/GPS Stamp Camera/%'"`
  - `geoUri(12.844842, 80.152630) == "geo:12.844842,80.152630?q=12.844842,80.152630(Photo%20location)"`
  - `webUrl(12.844842, 80.152630) == "https://www.google.com/maps/search/?api=1&query=12.844842,80.152630"`
  - `geoUri(-33.856784, -151.215297) == "geo:-33.856784,-151.215297?q=-33.856784,-151.215297(Photo%20location)"`
- [ ] **Step 2: Run** → FAIL. **Step 3: Implement** `AlbumQuery` and `MapsLink`. **Step 4: Run** → PASS.
- [ ] **Step 5: Implement `PhotoRepository`.**
  - All calls run on `Dispatchers.IO`.
  - `list()` queries `_ID` and `DATE_TAKEN` with the album selection, sorted `date_taken DESC, _id DESC`, and builds each URI with `ContentUris.withAppendedId(EXTERNAL_CONTENT_URI, id)`.
  - `delete()` returns `resolver.delete(...) > 0`, with exceptions mapped to false.
  - `location()`:
    - API 29+ without `ACCESS_MEDIA_LOCATION` → null.
    - Otherwise reads `ExifInterface` from `MediaStore.setRequireOriginal(uri)` (29+) or `uri`, returning `latLong` as a pair. Any exception → null (Review Focus 4).
- [ ] **Step 6:** Add Coil and `ACCESS_MEDIA_LOCATION`, and point `PhotoSaver` at `AlbumQuery.ALBUM`. **Verify** build + tests. **Commit:** `feat: gallery photo repository, maps link and album query`

### Task 4: Gallery screens and navigation

**Files:**
- Create: `ui/GalleryViewModel.kt`, `ui/GalleryScreen.kt`, `ui/PhotoViewer.kt`, `ui/ZoomableImage.kt`
- Modify: `MainActivity.kt`, `ui/CameraViewModel.kt`, `ui/CameraScreen.kt`, `res/values/strings.xml`

**Interfaces:**
- Consumes: Task 3, Task 2 (`onOpenGallery`).
- Produces:
  - `class GalleryViewModel(app: Application) : AndroidViewModel(app) { val photos: StateFlow<List<GalleryPhoto>>; fun refresh(); suspend fun delete(photo: GalleryPhoto): Boolean; suspend fun location(photo: GalleryPhoto): Pair<Double, Double>? }`
  - `CameraViewModel.refreshLastPhoto()`, which sets `lastPhotoUri` to the newest album photo, or null.

- [ ] **Step 1: Implement.**
  - **Navigation:** `MainActivity` holds `sealed interface Screen { Camera; Gallery; data class Viewer(val index: Int) }` in `remember`, with `BackHandler` for Viewer → Gallery → Camera.
  - **Camera screen:** calls `vm.refreshLastPhoto()` in a `LaunchedEffect(Unit)`.
  - **Grid:** Scaffold + `TopAppBar` (back, "GPS Stamp Camera", count). `LazyVerticalGrid(GridCells.Fixed(3))` with 2 dp spacing and square `AsyncImage` cells (`ContentScale.Crop`). Shows the empty state when the list is empty. Refreshes on entry.
  - **Viewer:** `HorizontalPager`, initial page coerced into range, `userScrollEnabled = !zoomed`. `LaunchedEffect(photos.size)` closes the viewer when the list is empty (Review Focus 3).
    - Top bar: back + formatted date.
    - Bottom row: Share, Open in Maps (only when `location` is non-null; `produceState` keyed by uri), Delete (AlertDialog → `delete` → snackbar on failure).
    - Asks once for `ACCESS_MEDIA_LOCATION` on API 29+ via `rememberLauncherForActivityResult` when the viewer opens (Review Focus 5) and re-queries location after the result.
  - **`ZoomableImage(uri, isCurrent, onZoomedChange)`:**
    - Custom `awaitEachGesture` loop: it only consumes pointer changes when two or more pointers are down or `scale > 1`.
    - Scale is clamped to 1–5, with offset clamped to ±size·(scale−1)/2.
    - Double-tap toggles 1× / 2.5× via `detectTapGestures`.
    - Zoom resets when `isCurrent` becomes false (Review Focus 2).
  - **Maps tap:** `ACTION_VIEW` on `geoUri`; on `ActivityNotFoundException`, opens `webUrl`.
  - **Share:** `Intent.createChooser(ACTION_SEND …)`.
- [ ] **Step 2: Verify** `assembleDebug testDebugUnitTest` → `BUILD SUCCESSFUL`. **Commit:** `feat: in-app gallery with viewer, share, maps and delete`

### Task 5: Checklist, review, APK

- [ ] Add the rotation and gallery checks from the spec's Testing section to `docs/phone-test-checklist.md`.
- [ ] Run the fresh whole-branch review on the range from `ec673ef` (most capable model, read-only, Review Focus above). Fix Critical/Important findings test-first where JVM-testable.
- [ ] Build the debug APK (no mock flag; confirm `ALLOW_MOCK_LOCATION = false`) and send it to the user.
