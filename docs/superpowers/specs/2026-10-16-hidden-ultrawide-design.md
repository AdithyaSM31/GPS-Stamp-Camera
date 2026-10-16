# Hidden Ultra-wide Mode — Design

Date: 2026-10-16
Status: Draft for review
Ships in: v1.1 (with the slimmer landscape stamp and the real-min-zoom wide button already on `feat/v1.1`)

## Problem

On the user's Nothing Phone (1) (A063, Android 15), the camera app shows no wide zoom button. Evidence
gathered over USB:

- `CameraManager.cameraIdList` for this app is `[0, 1]`: back main (5.59 mm, zoom 1.0–8.0) and front.
- The ultra-wide is camera **"2"** (back, 2.15 mm lens, 5.22 mm-wide sensor, sensor orientation 90,
  continuous AF, flash, 4080×3072 JPEG, hardware level 3). Camera **"3"** is a logical camera over "0" + "2"
  with zoom range 1.0–1.0.
- Both are **missing from the list**, but this app **can read their characteristics and open camera "2"**
  (verified: `openCamera("2")` → `onOpened`).
- CameraX only uses listed cameras, so it can never reach "2". The v1.1 wide button (driven by the main
  camera's minimum zoom) therefore stays hidden on this phone and others that hide their ultra-wide the
  same way.

Field of view: main ≈ 72.5°, ultra-wide ≈ 101.9° horizontal, which is an equivalent zoom of **≈ 0.6×**.

## Goal

When a phone hides its ultra-wide from the camera list but still lets apps open it, the wide zoom button
appears with its true ratio (0.6× here) and works like the ultra-wide in the built-in camera: live preview,
stamped capture, same GPS rules, same saved-photo pipeline.

## Approach

A small **Camera2 session used only for the hidden ultra-wide**; CameraX keeps doing everything else.

Rejected alternatives:
- **Rewrite the whole camera on Camera2.** Far more code and risk to features that already work (lens
  switch, zoom, flash, orientation).
- **Reflection into CameraX** to inject camera "2". Brittle across CameraX versions.

## Detection — `UltraWideProbe`

Run once per app start, off the main thread, after camera permission is granted.

1. If CameraX's back camera already reports `minZoomRatio ≤ 0.9`, use that (existing v1.1 behaviour) and
   skip probing.
2. Otherwise check the ids `"0"`–`"9"` **not** in `cameraIdList`. Read each one's characteristics, and catch
   and skip any that throw. A candidate must:
   - face back;
   - list `BACKWARD_COMPATIBLE` and **not** `LOGICAL_MULTI_CAMERA`;
   - have a single focal length shorter than the main camera's;
   - offer JPEG output.
3. For each candidate, compute the equivalent zoom from horizontal field of view with the pure
   `UltraWideMath.equivalentZoom(mainFocal, mainSensorWidth, wideFocal, wideSensorWidth) =
   (mainSensorWidth / mainFocal) / (wideSensorWidth / wideFocal)`. Keep candidates ≤ 0.9 and pick the
   smallest ratio. Result: `HiddenUltraWide(cameraId, zoomRatio, sensorOrientation, jpegSize, previewSize,
   hasFlash, hasContinuousAf)`, or none.
   - `jpegSize`: the largest 4:3 JPEG size ≤ 4096×3072 (≈12.5 MP, the same memory as the main camera's 4032×3024 cap).
   - `previewSize`: the largest 4:3 `SurfaceTexture` size ≤ 1440×1080.
4. The probe does **not** open the camera. Opening is checked the first time the user taps the button
   (see Errors).

## Ultra-wide session — `UltraWideSession`

- **Preview:** a `TextureView` in the same 3:4 preview box, with its SurfaceTexture's default buffer size set to
  `previewSize` (landscape). TextureView applies the camera's 90° buffer transform itself, so the preview is
  upright in the portrait-locked UI. (A `SurfaceView` was tried first; on the device it sometimes scaled the
  buffer as if unrotated after a quick 1x → 0.6x switch, squeezing it into 75% of the width.) Repeating request
  on `TEMPLATE_PREVIEW` with `CONTROL_AF_MODE_CONTINUOUS_PICTURE` when available, else AF off.
- **Capture:** an `ImageReader` (JPEG, `jpegSize`, maxImages 2) and a `TEMPLATE_STILL_CAPTURE` request on
  the same session. No `JPEG_ORIENTATION` is set. The JPEG bytes are decoded, then rotated by the pure
  `CaptureRotation.degrees(sensorOrientation, surfaceRotation)` = `(sensorOrientation −
  surfaceRotationDegrees + 360) % 360`, where ROTATION_0/90/180/270 = 0/90/180/270°. This matches CameraX,
  so landscape shots come out upright and the stamp goes along the true bottom edge.
- **Flash:** follows the existing flash button. OFF → `AE_MODE_ON`; AUTO → `AE_MODE_ON_AUTO_FLASH`;
  ON → `AE_MODE_ON_ALWAYS_FLASH`, with a standard precapture trigger before the still capture when flash
  is AUTO/ON. If `hasFlash` is false the flash button is disabled in ultra-wide mode.
- **Threading:** all camera callbacks run on a dedicated `HandlerThread`. Decode, rotate and save run on
  `Dispatchers.IO`, as today.
- **Lifecycle:** opened when ultra-wide is selected and the camera screen is started; closed on stop,
  dispose, lens switch or leaving ultra-wide. CameraX is unbound before the ultra-wide opens and rebound
  after it closes, so only one camera is open at a time.

## Integration

- **Zoom chips:** wide chip label = `ZoomOptions.label(zoomRatio)` ("0.6x" here), then 1x and 2x. Tapping
  the wide chip enters ultra-wide mode. Tapping 1x/2x leaves it and restores CameraX at that zoom.
- **Lens switch:** the switch-camera button leaves ultra-wide mode (the front camera has no ultra-wide).
  The wide chip is hidden on the front camera.
- **Saving:** `PhotoSaver` gains `save(bitmap: Bitmap, data: StampData): Uri` for an already-upright
  bitmap. The existing `save(image: ImageProxy, data)` converts to an upright bitmap and calls it, so both
  paths share stamping, MediaStore, EXIF and location storage.
- **Capture rules, GPS, stamp, rotation overlay, gallery:** unchanged and shared.
- Ultra-wide mode is not remembered across launches; the app always opens on 1x.

## Errors

| Situation | Behaviour |
|---|---|
| No candidate found | Wide chip hidden (as today on phones without an ultra-wide). |
| Open fails (`CameraAccessException`, `SecurityException`, `onError`, `onDisconnected`) | Snackbar "Ultra-wide isn't available on this phone", back to 1x on CameraX, wide chip hidden for the rest of the session. |
| Capture fails | Existing "Couldn't save photo: …" snackbar; stays in ultra-wide mode. |
| Camera taken by another app while in ultra-wide | Same as open failure. |

## Out of scope

- Ultra-wide video; zoom between 0.6× and 1× (only the two fixed steps).
- Front-camera wide modes; using the hidden logical camera "3".
- Phones that block opening hidden cameras (handled by the Errors table: no button, or the button
  disappears after one failed open).

## Testing

- JVM unit tests:
  - `UltraWideMathTest`: Nothing Phone (1) values (5.59 / 8.192 vs 2.15 / 5.2224) → 0.603 ± 0.005; an equal
    lens → 1.0.
  - `UltraWideProbeSelectionTest` (pure selection over plain data): skips front, logical, longer-focal and
    > 0.9 candidates, and picks the smallest ratio.
  - `CaptureRotationTest`: sensor 90 with ROTATION_0/90/180/270 → 90/0/270/180.
  - `SizeChoiceTest`: picks the largest 4:3 size within the cap from a list including 4080×3072,
    4000×3000, 1920×1080.
- Device (the user's phone over USB with `adb`): "0.6x" appears; the preview is upright and visibly wider;
  portrait and landscape ultra-wide photos save upright with a correct stamp; flash auto/on fire; switching
  0.6x ↔ 1x ↔ 2x and front/back works; leaving for the gallery releases the camera (`dumpsys media.camera`
  shows no client).
