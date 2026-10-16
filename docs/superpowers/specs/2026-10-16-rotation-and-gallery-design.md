# Rotation-aware preview + in-app gallery — Design

Date: 2026-10-16
Status: Draft for review
Builds on: `2026-10-16-gps-stamp-camera-design.md` (branch `feat/gps-stamp-camera`)

## Purpose

1. When the phone is held sideways or upside down, the live preview's stamp should read upright and sit
   where it will appear on the saved photo. Today it stays stuck to the portrait bottom.
2. Let the user browse, share, locate and delete their stamped photos without leaving the app.

## 1. Rotation

### Behaviour

- The activity stays locked to portrait. Device orientation comes from the existing
  `OrientationEventListener` in `CameraScreen`.
- **Orientation with hysteresis.** A pure function `OrientationTracker.next(degrees: Int, current: Int): Int`
  returns a `Surface.ROTATION_*`. It switches to a new rotation only when `degrees` is within ±30° of that
  rotation's centre: ROTATION_0 = 0°, ROTATION_270 = 90°, ROTATION_180 = 180°, ROTATION_90 = 270°.
  Otherwise, or when `degrees` is `ORIENTATION_UNKNOWN` (-1, phone lying flat), it keeps `current`.
  The result is used for both `CameraController.setTargetRotation` (saved photo) and the UI, so the
  preview and the photo always agree.
- **Overlay mapping.** A pure function `OverlayRotation.degreesFor(surfaceRotation: Int): Float` returns the
  clockwise Compose rotation: ROTATION_0 → 0, ROTATION_90 → 90, ROTATION_180 → 180, ROTATION_270 → -90.
  `OverlayRotation.isLandscape(surfaceRotation)` is true for 90 and 270.
- **Stamp + banner.** Inside the 3:4 preview box (W×H), an "upright frame" is drawn: W×H in portrait,
  H×W in landscape. It is centred with `requiredSize`, rotated with `graphicsLayer { rotationZ }`, and holds
  the stamp (bottom-centre, rendered at frame width) and the status banner (top-centre). Rotated about its
  centre, it covers the preview exactly, so the stamp lands on the edge facing the ground at the
  landscape photo's proportions. The rotation snaps (no animation), because the frame also changes size.
- **Icons.** The flash, switch-camera and edit-note icons, the zoom-chip labels and the last-photo thumbnail
  rotate in place by the same degrees with `animateFloatAsState` (200 ms). The target is chosen as the
  equivalent angle (±360°) nearest the current value, so 270→0 turns 90° rather than 270°.

## 2. Gallery

### Navigation

- No navigation library. `MainActivity` holds a `Screen` state: `Camera`, `Gallery` or
  `Viewer(startIndex)`. `BackHandler` steps back Viewer → Gallery → Camera. Screens appear inside the
  existing `PermissionGate`.
- Tapping the last-photo thumbnail opens `Gallery`. It no longer launches the system viewer.
- On launch, the thumbnail shows the newest album photo (from `PhotoRepository`), not only one taken this
  session.

### Data — `PhotoRepository`

- `suspend fun list(): List<GalleryPhoto>` where `GalleryPhoto(uri: Uri, id: Long, dateTakenMs: Long)`,
  newest first (`DATE_TAKEN DESC, _ID DESC`).
- Album filter: API 29+ `RELATIVE_PATH LIKE 'Pictures/GPS Stamp Camera/%'`; API 26–28
  `DATA LIKE '%/Pictures/GPS Stamp Camera/%'`. The selection is built by the pure
  `AlbumQuery.selection(sdkInt: Int): String` (unit-tested).
- No new storage permission. On Android 10+ the app sees the photos it created (kept across APK updates,
  lost on uninstall). On 8–9, `WRITE_EXTERNAL_STORAGE`, already granted, allows reading.
- `suspend fun delete(uri: Uri): Boolean` uses `ContentResolver.delete`; false on failure.
- `suspend fun location(uri: Uri): Pair<Double, Double>?` reads EXIF lat/long. On API 29+ it uses
  `MediaStore.setRequireOriginal(uri)` and needs `ACCESS_MEDIA_LOCATION`. Returns null if the permission
  is missing or the photo has no GPS.

### Permission

- `ACCESS_MEDIA_LOCATION` is added to the manifest and requested by `PermissionGate` on API 29+ together
  with the existing permissions. It is **optional**: the gate does not block the camera if it is denied.

### Grid screen — `GalleryScreen`

- Top bar: back arrow + title "GPS Stamp Camera" + photo count.
- `LazyVerticalGrid`, 3 columns, square cells, 2 dp gaps, thumbnails via Coil (`AsyncImage`,
  size-limited request).
- Empty state: "No photos yet". The list is re-queried each time the screen is shown and after a delete.

### Viewer — `PhotoViewer`

- `HorizontalPager` over the same list, starting at the tapped index.
- Each page: Coil image, fit to screen. Pinch zoom 1×–5× with pan clamped to the image, double-tap toggles
  1× / 2.5×. The pager only swipes when the page is at 1×.
- Top bar: back arrow + photo date ("Friday, 16/10/2026 07:54 PM", local time, from `DATE_TAKEN`).
- Bottom bar:
  - **Share:** `ACTION_SEND`, `image/jpeg`, `EXTRA_STREAM` = uri, read grant, wrapped in a chooser.
  - **Open in Maps:** shown only when `location()` returns non-null. Opens `ACTION_VIEW` with the URI from
    the pure `MapsLink.geoUri(lat, lon)` =
    `geo:12.844842,80.152630?q=12.844842,80.152630(Photo location)` (6 decimals, `Locale.US`, label
    URL-encoded). If no app handles it, falls back to
    `https://www.google.com/maps/search/?api=1&query=12.844842,80.152630`.
  - **Delete:** an AlertDialog "Delete this photo?" with Delete / Cancel. On success the photo leaves the
    pager (the viewer closes to the grid if none remain). On failure a snackbar shows
    "Couldn't delete photo".

### Dependency

- `io.coil-kt:coil-compose:2.7.0` for content-URI image loading with sampling and memory/disk caching.

## Out of scope

- Photos created by a previous installation, or outside the album.
- Multi-select, rename, edit, cloud upload.
- Rotating the gallery screens themselves (they stay portrait).

## Testing

- JVM unit tests:
  - `OrientationTrackerTest`: 0→0; 80→ROTATION_270; 50 from ROTATION_0 keeps ROTATION_0 (inside dead zone);
    50 from ROTATION_270 keeps ROTATION_270; 350→ROTATION_0; 265→ROTATION_90; 185→ROTATION_180;
    -1 keeps current.
  - `OverlayRotationTest`: the four mappings and `isLandscape`.
  - `MapsLinkTest`: geo URI and web fallback for (12.844842, 80.152630) and a negative pair
    (-33.856784, -151.215297).
  - `AlbumQueryTest`: selection strings for sdkInt 28 and 29.
- Build: `assembleDebug` + `testDebugUnitTest` green.
- On the phone (added to `docs/phone-test-checklist.md`):
  - In all four orientations the preview stamp reads upright on the ground-facing edge, and the saved photo
    matches.
  - Icons rotate.
  - Gallery grid, swipe, pinch/double-tap zoom, Share, Open in Maps, Delete (photo disappears from grid and
    from the phone's Gallery app).
