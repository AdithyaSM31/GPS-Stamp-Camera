# On-phone test checklist

The app has not been run on a device yet (the emulator exhausted this PC's memory).
Install `app/build/outputs/apk/debug/app-debug.apk` on an Android 8+ phone
(allow "Install unknown apps" for the app you open it with), then check:

## Basics
- [ ] First launch asks for camera + location. Choosing **Approximate** shows
      "Precise location is required to stamp photos."
- [ ] Outdoors, the banner reads "Waiting for GPS… ±N m", then disappears and the
      shutter turns white within ~30 s.
- [ ] Preview stamp shows map with red pin, place title, address, Lat/Long,
      "Accuracy ±N m · Alt N m" (altitude only on Android 14+), date with GMT offset.
- [ ] Take a photo. The thumbnail appears; tapping it opens the photo. The saved
      stamp matches the preview and is in Gallery under "GPS Stamp Camera".
- [ ] Photo details (Gallery → info) show the GPS location.

## Camera controls
- [ ] Flash cycles off → auto → on. 0.6x appears only on phones with ultra-wide.
- [ ] Front camera photo: stamp text reads normally (not mirrored).
- [ ] Hold the phone sideways and shoot: saved photo is landscape, stamp along the bottom.
- [ ] Edit note → "Project review – Team 4" appears as the last stamp line.
      A very long address wraps to 2 lines and ends with "…" instead of overflowing.
- [ ] Double-tap the shutter quickly: exactly one new photo.

## Proof-of-visit safety
- [ ] Walk ~100 m and shoot immediately: the address/map either match the new spot
      or are absent (title shows coordinates) — never the previous spot.
- [ ] Leave the app, travel to another site, reopen: shutter waits for a fresh fix;
      first photo never shows the previous site's address.
- [ ] Turn off automatic time and set yesterday's date: the stamp still shows today.
- [ ] Install a mock-location app, set it as mock location app in Developer options:
      red "Fake GPS detected" banner, shutter disabled.
- [ ] Turn Location off: "Location is off" with an Open settings button.
- [ ] Airplane mode: photo still saves; title becomes coordinates, no map box.

## Rotation (v2)
- [ ] Hold the phone sideways (both ways) and upside down: the preview stamp and
      "Waiting for GPS…" banner turn to read upright, on the edge facing the ground.
- [ ] Shoot in each orientation: the saved photo matches the preview (landscape photo,
      stamp along its long bottom edge).
- [ ] Flash, switch-camera, note icons, zoom labels and the gallery thumbnail turn in place.
- [ ] Tilting near 45° doesn't make the stamp flicker; lying flat keeps the last orientation.

## Gallery (v2)
- [ ] The thumbnail shows your newest photo right after opening the app.
- [ ] Tap it: grid of this app's photos, newest first, with a count. Back returns to the camera.
- [ ] While in the gallery, the camera is off (no camera-in-use indicator).
- [ ] Tap a photo: full screen. Swipe left/right; pinch to zoom and pan; double-tap toggles zoom.
      While zoomed, swiping pans instead of changing photo.
- [ ] First time in the viewer, Android asks about photo location. Allow it: "Open in Maps"
      appears and opens Google Maps at the spot. Deny it: the button is hidden, nothing crashes.
- [ ] Share opens the share sheet; sending to WhatsApp/Drive works.
- [ ] Delete asks "Delete this photo?"; confirming removes it from the grid and from the phone's
      Gallery app. Deleting the last photo returns to an empty grid ("No photos yet").
- [ ] New launcher icon: white pin with a camera lens on a blue-teal background
      (with Android 13+ themed icons on, a single-colour pin).

## v1.1
- [ ] Landscape photo: the stamp is a slim strip along the bottom (about the same share of
      the height as in portrait), still spanning the width; the preview matches.
- [ ] Portrait photos look exactly as before.
- [ ] On a phone with a 0.5x ultra-wide, the wide zoom button reads "0.5x" and gives the full
      ultra-wide view; on a 0.6x phone it reads "0.6x"; with no ultra-wide (or front camera) it's hidden.
- [ ] Phone with a hidden ultra-wide (e.g. Nothing Phone (1)): a "0.6x" button appears; tapping it
      shows a wider, upright preview. Portrait and landscape photos save upright at full size with the
      stamp; flash auto/on fire. Switching 0.6x/1x/2x quickly, opening the gallery, pressing Home and
      coming back all work without a frozen or black preview.
