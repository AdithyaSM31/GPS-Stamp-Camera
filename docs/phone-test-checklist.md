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
