# Hidden Ultra-wide Mode Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** On phones that hide their ultra-wide from the camera list but allow opening it (the user's Nothing Phone (1): camera "2"), show a true "0.6x" button that previews and captures through that lens, with the existing stamp/GPS/saving pipeline.

**Architecture:** Pure Kotlin helpers (zoom math, size choice, capture rotation, candidate selection) carry the decisions and are unit-tested. `UltraWideProbe` reads Camera2 characteristics into plain `CameraSpec` data. `UltraWideSession` is a small Camera2 session (SurfaceView preview + JPEG ImageReader) used only in ultra-wide mode. CameraX keeps handling everything else and is unbound while the ultra-wide is open. `PhotoSaver` gains a bitmap entry point so both paths share stamping and saving.

**Tech Stack:** Existing stack + `android.hardware.camera2` (no new dependencies).

**Spec:** `docs/superpowers/specs/2026-10-16-hidden-ultrawide-design.md`

## Global Constraints

- Branch `feat/v1.1`. Run Gradle in the background or with output to a file, then `gradlew --stop`. Never start the emulator. The device is the user's phone over `adb` (`$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe`, serial `P2229H001783`).
- Wide threshold: ratio ≤ 0.9. JPEG cap: 4032×3024. Preview cap: 1440×1080. A size counts as 4:3 if `|w/h − 4/3| < 0.02` (landscape sizes, w ≥ h).
- Candidate ids: `"0"`–`"9"` not in `cameraIdList`; back-facing; `BACKWARD_COMPATIBLE`; not `LOGICAL_MULTI_CAMERA`; exactly one focal length, shorter than the main's.
- `CaptureRotation.degrees = (sensorOrientation − surfaceRotation×90 + 360) % 360`.
- Flash mapping: OFF → `CONTROL_AE_MODE_ON`; AUTO → `ON_AUTO_FLASH`; ON → `ON_ALWAYS_FLASH`. Run a precapture trigger before the still capture unless OFF; wait for AE `CONVERGED`/`FLASH_REQUIRED`/`LOCKED`, at most 1500 ms.
- New copy (exact): `Ultra-wide isn't available on this phone`.
- No `android.util.Size` in pure code (JVM stubs throw); use `Size2(width, height)`.

## Review Focus

1. **Surface lifecycle:** going to the gallery, pressing Home or locking the screen in ultra-wide mode must close camera "2" (`dumpsys media.camera` shows no client) and reopen it on return. Owner: Task 5. Checked in Task 6.
2. **Camera conflicts:** CameraX and camera "2" must never be open at once; switching 0.6x ↔ 1x quickly must not crash or leave a black preview. Owner: Task 5. Checked in Task 6.
3. **Capture while closing:** tapping the shutter and then immediately switching zoom or leaving must not crash (`IllegalStateException` / `CameraAccessException` → existing save-failed snackbar). Owner: Task 4/5.
4. **Landscape ultra-wide:** the photo saves upright with the stamp along the true bottom edge (CaptureRotation + `targetRotation` kept in sync). Owner: Task 1/5. Checked in Task 6 (user rotates the phone).
5. **Open failure:** a blocked hidden camera shows the snackbar, returns to 1x and hides the chip; no crash loop. Owner: Task 5.

---

### Task 1: Pure helpers

**Files:**
- Create: `ultrawide/UltraWideModel.kt` (`Size2`, `CameraSpec`, `HiddenUltraWide`), `ultrawide/UltraWideMath.kt`, `ultrawide/SizeChoice.kt`, `ultrawide/CaptureRotation.kt`, `ultrawide/UltraWideSelection.kt`
- Test: `ultrawide/UltraWideMathTest.kt`, `ultrawide/SizeChoiceTest.kt`, `ultrawide/CaptureRotationTest.kt`, `ultrawide/UltraWideSelectionTest.kt`

**Interfaces:**
- Produces:
  - `data class Size2(val width: Int, val height: Int)`
  - `data class CameraSpec(val id: String, val facingBack: Boolean, val logical: Boolean, val backwardCompatible: Boolean, val focalLengths: List<Float>, val sensorWidthMm: Float, val sensorOrientation: Int, val jpegSizes: List<Size2>, val previewSizes: List<Size2>, val hasFlash: Boolean, val hasContinuousAf: Boolean)`
  - `data class HiddenUltraWide(val cameraId: String, val zoomRatio: Float, val sensorOrientation: Int, val jpegSize: Size2, val previewSize: Size2, val hasFlash: Boolean, val hasContinuousAf: Boolean)`
  - `object UltraWideMath { fun equivalentZoom(mainFocal: Float, mainSensorWidth: Float, wideFocal: Float, wideSensorWidth: Float): Float }`
  - `object SizeChoice { fun largest4by3(sizes: List<Size2>, maxWidth: Int, maxHeight: Int): Size2? }`
  - `object CaptureRotation { fun degrees(sensorOrientation: Int, surfaceRotation: Int): Int }`
  - `object UltraWideSelection { fun pick(main: CameraSpec, hidden: List<CameraSpec>): HiddenUltraWide? }`

- [ ] **Step 1: Write failing tests**
  - `equivalentZoom(5.59f, 8.192f, 2.15f, 5.2224f)` = 0.603 ± 0.005; `equivalentZoom(4f, 6f, 4f, 6f)` = 1.0.
  - `largest4by3([4080×3072, 4000×3000, 1920×1080, 1440×1080, 640×480], 4032, 3024)` = 4000×3000.
  - The same list with cap 1440×1080 = 1440×1080.
  - `largest4by3([1920×1080], 4032, 3024)` = null.
  - `CaptureRotation.degrees(90, r)` for r = 0/1/2/3 → 90/0/270/180.
  - Selection, with `main` = Nothing camera "0" (5.59 mm, 8.192 mm) and `uw` = camera "2" (2.15 mm, 5.2224 mm, sizes incl. 4000×3000 + 1440×1080, flash, AF):
    - `pick(main, [uw])` → id "2", ratio ≈ 0.603, jpeg 4000×3000, preview 1440×1080.
    - Each of these alone returns null: a front camera, a `logical = true` camera, a 7 mm lens, a camera without 4:3 JPEG sizes, a 5.0 mm lens on an 8.192 mm sensor (ratio 0.94).
    - With two valid candidates (ratios 0.6 and 0.75), the 0.6 one is picked.
- [ ] **Step 2: Run** `testDebugUnitTest --tests "*ultrawide*"` → FAIL (unresolved).
- [ ] **Step 3: Implement.** For `largest4by3`, filter by the aspect tolerance and the cap, then take the max by area.
- [ ] **Step 4: Run** → PASS. **Commit:** `feat: ultra-wide detection maths and selection`

### Task 2: `UltraWideProbe`

**Files:** Create `ultrawide/UltraWideProbe.kt`

**Interfaces:**
- Consumes: Task 1.
- Produces: `class UltraWideProbe(context: Context) { suspend fun find(): HiddenUltraWide? }`.

- [ ] **Step 1: Implement.**
  - Runs on `Dispatchers.IO`.
  - `main` = the first listed back-facing, non-logical camera, read into `CameraSpec`.
  - For ids `"0".."9"` not in `cameraIdList`: `getCameraCharacteristics(id)` inside try/catch (any exception → skip), then convert to `CameraSpec`:
    - JPEG sizes come from `SCALER_STREAM_CONFIGURATION_MAP.getOutputSizes(ImageFormat.JPEG)`.
    - Preview sizes come from `getOutputSizes(SurfaceHolder::class.java)`.
    - `sensorWidthMm` comes from `SENSOR_INFO_PHYSICAL_SIZE.width`.
    - Continuous AF = `CONTROL_AF_MODE_CONTINUOUS_PICTURE` is in `CONTROL_AF_AVAILABLE_MODES`.
  - Returns `UltraWideSelection.pick`; null on any failure.
- [ ] **Step 2: Verify** with `assembleDebug` → `BUILD SUCCESSFUL`. **Commit:** `feat: probe for hidden ultra-wide cameras`

### Task 3: Bitmap entry point in `PhotoSaver`

**Files:** Modify `capture/PhotoSaver.kt`

**Interfaces:** Produces `suspend fun save(bitmap: Bitmap, data: StampData): Uri`, which takes ownership of `bitmap` (must be mutable and upright; recycled after). `save(image: ImageProxy, data)` becomes `save(image.use { it.toUprightBitmap() }, data)`.

- [ ] **Step 1: Refactor.** Move the stamping/MediaStore/EXIF/location body into `save(bitmap, data)`.
- [ ] **Step 2: Verify** with `assembleDebug testDebugUnitTest` → green. **Commit:** `refactor: PhotoSaver accepts an upright bitmap`

### Task 4: `UltraWideSession`

**Files:** Create `ultrawide/UltraWideSession.kt`

**Interfaces:**
- Consumes: `HiddenUltraWide`.
- Produces: `class UltraWideSession(context: Context, spec: HiddenUltraWide) { suspend fun open(previewSurface: Surface); fun setFlash(mode: Int); suspend fun capture(): Bitmap; fun close() }`.
  - `capture()` returns the decoded JPEG, mutable and **not** rotated.
  - `open` and `capture` throw on failure: `CameraAccessException`, `IllegalStateException`, or `UltraWideUnavailable` (a new exception class for `onError`/`onDisconnected` during open).

- [ ] **Step 1: Implement.**
  - **Thread:** a `HandlerThread("ultrawide")`.
  - **`open`:**
    1. `suspendCancellableCoroutine` around `openCamera(spec.cameraId, callback, handler)`.
    2. Create an `ImageReader(jpegSize, JPEG, 2)`.
    3. `createCaptureSession` with `SessionConfiguration(SESSION_REGULAR, [OutputConfiguration(preview), OutputConfiguration(reader.surface)], executor, callback)` on API 28+; the deprecated list overload below 28.
    4. Start a repeating `TEMPLATE_PREVIEW` request with the AF mode and the current AE mode.
  - **`capture`:**
    1. If flash isn't OFF, set `CONTROL_AE_PRECAPTURE_TRIGGER_START` on a one-shot preview request, then watch preview results until the AE state is `CONVERGED`/`FLASH_REQUIRED`/`LOCKED` or 1500 ms pass.
    2. Send `TEMPLATE_STILL_CAPTURE` to the reader surface with the same AF/AE modes.
    3. Await `reader.acquireNextImage()` via `setOnImageAvailableListener`, copy the bytes, and close the image.
    4. Decode the bytes with `BitmapFactory` (`inMutable = true`).
  - **`close`:** idempotent. Closes the session, device and reader, and quits the thread.
- [ ] **Step 2: Verify** with `assembleDebug` → `BUILD SUCCESSFUL`. **Commit:** `feat: Camera2 session for the hidden ultra-wide`

### Task 5: Integration (ViewModel + screen)

**Files:** Modify `ui/CameraViewModel.kt`, `ui/CameraScreen.kt`, `res/values/strings.xml`

**Interfaces:**
- Consumes: Tasks 2–4. `CameraController.targetRotation` / `unbind()` / `bind()`.
- Produces:
  - `CameraUiState` gains `hiddenWideRatio: Float?` (null = no hidden ultra-wide) and `ultraWideActive: Boolean`.
  - `CameraViewModel` gains `fun selectZoom(ratio: Float)` (replaces direct `setZoom` from the chips), `fun onUltraWideSurface(surface: Surface?)`, and `fun leaveUltraWide()`.

- [ ] **Step 1: Implement.**
  - **Probe:** in `onStart`, run `UltraWideProbe.find()` once; store it and set `hiddenWideRatio`.
  - **`selectZoom(ratio)`:**
    - If `ratio` equals `hiddenWideRatio` on the back lens: `camera.unbind()`, then `ultraWideActive = true`. The screen swaps to a SurfaceView and the session opens when the surface arrives.
    - Otherwise: if active, leave ultra-wide (close the session, `ultraWideActive = false`, the screen rebinds CameraX), then `setZoom(ratio)`.
  - **`onUltraWideSurface(surface)`:**
    - non-null → `session.open(surface)`; on exception, run `failUltraWide()`.
    - null → `session.close()`.
  - **`failUltraWide()`:** closes the session, sets `hiddenWideRatio = null` and `ultraWideActive = false`, and sets the error `Ultra-wide isn't available on this phone`. Review Focus 5.
  - **`capture()` when `ultraWideActive`:**
    1. `session.capture()`.
    2. Rotate by `CaptureRotation.degrees(spec.sensorOrientation, camera.targetRotation)`, keeping a mutable copy.
    3. `saver.save(bitmap, snapshot)`.
    4. Errors use the existing save-failed path.
  - **Leaving ultra-wide:** `toggleLens()` and `onStop()` leave it. `cycleFlash()` also calls `session.setFlash(next)`.
  - **Screen, preview:** when `ui.ultraWideActive`, the preview box shows `AndroidView { SurfaceView }` instead of the `PreviewView`.
    - The `SurfaceHolder.Callback` sets `setFixedSize(previewSize.width, previewSize.height)` in `surfaceCreated`, calls `vm.onUltraWideSurface(holder.surface)` on the first `surfaceChanged` matching that size, and calls `vm.onUltraWideSurface(null)` in `surfaceDestroyed`. Review Focus 1.
  - **Screen, CameraX binding:** `LaunchedEffect(ui.lensFacing, ui.ultraWideActive)` binds only when not active. Review Focus 2.
  - **Zoom chips:**
    - Wide ratio = `ZoomOptions.wideRatio(zoomRange.start) ?: ui.hiddenWideRatio`, shown only on the back lens.
    - Selected = `ui.ultraWideActive` for the hidden chip, `ui.zoom == option` otherwise.
    - `onZoom = vm::selectZoom`.
  - **Flash:** the button is disabled in ultra-wide mode when `!spec.hasFlash`.
- [ ] **Step 2: Verify** with `assembleDebug testDebugUnitTest` → green. **Commit:** `feat: 0.6x button opens the hidden ultra-wide`

### Task 6: On-device verification, review, release prep

- [ ] **Step 1: Install and launch.** `adb install -r` the debug APK and launch it. `adb exec-out screencap -p` → view the screenshot: the "0.6x" chip is visible.
- [ ] **Step 2: Wider preview.** Tap 0.6x (coordinates from a `uiautomator dump`). The screenshot shows a wider, upright preview. `dumpsys media.camera` shows device 2 connected and device 0 not.
- [ ] **Step 3: Portrait capture.** Once GPS is ready, tap the shutter, then `adb pull` the newest `Pictures/GPS Stamp Camera` photo and view it: upright, wider field of view, stamp correct.
- [ ] **Step 4: Switching.** Switch 0.6x → 1x → 2x → 0.6x; open the gallery and come back; press Home and return. No crash (`logcat` has no `FATAL`); `dumpsys` shows a single client each time. Review Focus 1–2.
- [ ] **Step 5: User checks.** Ask the user to shoot ultra-wide in landscape (Review Focus 4) and with flash on, then pull and check those photos.
- [ ] **Step 6: Review and release prep.**
  - Run a fresh whole-branch review (most capable model) over this plan's commits; fix Critical/Important findings test-first where JVM-testable.
  - Update the README, `dist/release-notes-v1.1.md` and the checklist, then rebuild `dist/GPS-Stamp-Camera-v1.1.apk`.
