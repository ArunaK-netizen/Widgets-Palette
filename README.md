# Widget Palette

A native Kotlin Android Moon-phase widget for the Nothing OS Lucent aesthetic.
The app is named **Widget Palette**. Its native customization screen previews
the Lunar Lucent, Mini Moon, and Constellation of the Day widgets and lets
people personalize the background on a shared or per-widget-instance basis.
The launcher icon uses the supplied Widget Palette artwork in adaptive and
legacy Android icon resources.

All app and widget UI text uses the bundled Geist Sans variable font from the
official Vercel Geist repository. The font is licensed under SIL OFL 1.1; its
license is bundled in `app/src/main/assets/licenses/OFL-Geist.txt`.

## Widget appearance editor

The app previews the bundled Moon and constellation artwork and supports adding
a widget through Android's pinned-widget flow or the launcher widget picker.
Appearance controls include Lucent, Liquid Glass, and Solid styles, a preset
palette and adjustable hue, opacity, corner roundness, and image inset. Saved
shared defaults update widgets that inherit them; a placed widget can be
selected to save a separate appearance. Android home-screen widgets do not
receive wallpaper pixels, so Liquid Glass uses a subtle rendered highlight
rather than claiming true wallpaper blur.

## Astronomical calculation

The app uses Astronomy Engine Kotlin **2.1.19**
([official project](https://github.com/cosinekitty/astronomy), MIT license).
The implementation converts the device's current `Instant` to a UTC
`io.github.cosinekitty.astronomy.Time`, then uses the documented:

- `moonPhase(time)` for geocentric apparent ecliptic longitude difference.
  Astronomy Engine defines 0° as New Moon, 90° as First Quarter, 180° as Full
  Moon, and 270° as Third/Last Quarter. The increasing angle through 0°–180°
  identifies waxing; 180°–360° identifies waning.
- `illumination(Body.Moon, time).phaseFraction` for the apparent illuminated
  fraction. This uses the Moon-centered Sun/Earth phase angle, not solar
  elongation at Earth.
- `searchMoonPhase(0.0, time + 1 ms, -35.0)` as a backward probe for the
  preceding New Moon, falling back to a search from `time` if the probe's event
  lies in the future. A positive-window `searchMoonPhase` then finds the
  following New Moon. Events are checked against the observation so age is
  elapsed UTC seconds from the most recent New Moon and is less than that
  actual lunation.
- `searchMoonPhase(180.0, time, 35.0)` for the next Full Moon event. If the
  observation is exactly at Full Moon, the search retries 1 ms later to return
  a strictly future event. The information page formats that instant in the
  phone's local time zone.

Phase labels use the Astronomy Engine angle direction to distinguish waxing
(0°–180°) from waning (180°–360°). The previous broad Full Moon interval was
removed: it incorrectly called near-full 198° waning phases Full Moon. Full
Moon is now limited to within 0.5° of the 180° event; 97–99% on the waning
side remains Waning Gibbous. Rounded illumination is capped at 99% unless the
phase is classified as Full Moon, so 100% is reserved for that label. Other
phase boundaries remain at 22.5°, 67.5°, 112.5°, 247.5°, 292.5°, and 337.5°.

Astronomy Engine documents positional accuracy within ±1 arcminute. The
displayed integer illumination is rounded from its phase fraction. Unit tests
use actual event instants returned by Astronomy Engine, allow 2 percentage
points around quarter-event illumination, and test a calculated 198° waning
gibbous phase (97–99%). The previously mentioned “98% Waning Gibbous”
observation has no timestamp, so it is not treated as a reproducible
timestamp-specific test case.

## Verified Moon images

All 361 original images are preserved in `app/moon-originals/`. The APK bundles
the 360 unique cyclic samples, `000.png` through `359.png`; `360.png` repeats
the 0° endpoint and is not included twice. The original, unmodified PNGs are
selected by Astronomy Engine's continuous geocentric phase angle, rounded to
the nearest one-degree filename. Angles wrap at 360°, giving at most 0.5° of
image-angle quantization.

Visual checks confirm 000 is New Moon, 090 is right-lit waxing First Quarter,
180 is Full, and 270 is left-lit waning Last Quarter. A contact sheet confirmed
that the images proceed from right-lit waxing to left-lit waning.

## Widget size, interaction, and updates

The widget starts at 2 x 2 (`minWidth` and `minHeight` 110 dp; Android 12+
target-cell hints 2 x 2) and can resize down to the compact 1 x 1 Moon-only
view (minimum resize dimensions 40 dp). Maximum resize dimensions are 110 dp.
The compact view uses a circular muted background and no text. In a rectangular
1 x 2 or 2 x 1 size, the larger Moon moves left and the current phase,
illumination percentage, and local date are all centered in the remaining
width. At 2 x 2, tapping shows
the full information page; it explicitly labels today's phase and local date,
not the next Full Moon date.

Android's AppWidget resize metadata supports minimum/maximum dimensions and
horizontal/vertical resizing, but not a whitelist of discrete grid shapes.
Therefore some launchers can still offer intermediate 2 x 1 or 1 x 2 sizes
within those limits. Lunar Lucent uses a compact side-by-side presentation for
rectangular shapes and keeps other intermediate shapes Moon-only rather than
clipping the full information page. Exact
resize handles and cell dp mapping depend on the launcher; test on Nothing OS.
A 4 x 2 variant is not included.

Both widget providers define a launcher preview layout and a static preview
image fallback. The full-Moon preview art is copied from the verified,
user-supplied `180.png` image.

## Constellation of the Day widget

The fixed 2 x 2 widget advances through all 88 constellations in alphabetical
order, changing to one new constellation at each local date change starting
from the day the widget is added, then restarting the cycle after 88 days.
Each constellation has three tap-through
pages: its COSMO360 night-sky photograph, a schematic drawn from its star-line
data, and a profile with a short description and approximate sky coordinates.
The selected page is saved per widget instance across updates and restarts.
Standard home-screen widgets do not provide a reliable custom horizontal swipe
gesture, so tapping advances one page.

All 88 COSMO360 960 px WebP photographs are bundled for offline use. The
Widget Palette app's **Photo credits** page provides each photographer credit,
source page, original-photo source when listed, and a clickable CC BY 4.0
license. The full attributions are also included with the constellation data at
`app/src/main/assets/constellations/constellations.json` and in
`app/constellation-library/attributions.json`. The constellation linework is
from d3-celestial; its BSD-3-Clause license is bundled at
`app/src/main/assets/licenses/d3-celestial-BSD-3-Clause.txt`. Coordinate
centers displayed in the profiles are approximate chart centers derived from
the linework, not official IAU boundary centroids.

## Firebase development distribution

This is a native Kotlin app, so compiled code still has to be delivered as a
signed Android package; Android does not permit silent OTA replacement of its
Kotlin/DEX code. Firebase App Distribution sends each development build to the
Firebase App Tester app, avoiding manual APK copying. The tester accepts and
installs the update through Android's package installer.

One-time Firebase setup:

1. The registered Firebase Android package is
   `com.company.widgetspalette`; the app's Gradle `applicationId` matches it.
2. Keep the Firebase config at `app/google-services.json`. It is ignored by
   Git and the publish script reads the matching Firebase App ID from it.
3. Enable App Distribution, add your Google account as a tester, and create a
   tester group if desired.
4. Install Firebase CLI (`npm install -g firebase-tools`) and authenticate once
   with `firebase login`. The Firebase Gradle plugin uses this local CLI login;
   no service-account key is stored in this project.
5. Install **Firebase App Tester** on the phone and accept its tester invite.

After making code changes, publish directly from this Windows project folder:

```powershell
.\scripts\Publish-FirebaseDevelopment.ps1 -TesterGroups "your-phone"
```

Use either `-TesterGroups "your-phone"` or
`-TesterEmails "you@example.com"` to notify a target in Firebase App
Distribution.
The Firebase App ID is read directly from `app/google-services.json`.
Without a group or tester list, Firebase uploads the release but sends no
tester invitations or update notifications. The script increments
`development-version.txt` only after a successful upload and invokes
the `development` Gradle build variant. This development APK uses the same
application ID and local debug signing key, allowing Android to update the
existing installation. If the app was installed from a differently signed
build, remove that installation before accepting the first Firebase build.
The package ID was changed from `com.lunarlucent` to match the supplied
Firebase registration, so the first `com.company.widgetspalette` install will
appear alongside the old package until you choose to remove the old app.

Firebase App Tester notifies you when a build is available. You still approve
installation in Android; this distributes native updates, not silent code
patches. No Firebase credentials or private keys are committed.

Tap anywhere to switch between the clean Moon face and the information page.
Android's standard `RemoteViews` widget API does not provide a reliable
cross-launcher horizontal swipe page gesture.

The widget renders on add/update and schedules a battery-conscious refresh at
the next local midnight. Date, clock, time-zone, and boot broadcasts also
refresh it and reschedule the alarm. Android may defer non-exact alarms while
the device is idle; phase-event times themselves are calculated for the actual
instant whenever the widget renders.

## Build and install

Requires JDK 17, Android SDK Platform 34, Android Build Tools 34.0.0 or newer,
and Gradle 8.7. From the project root:

```powershell
.\work\gradle-8.7\gradle-8.7\bin\gradle.bat :app:testDebugUnitTest :app:assembleDebug
```

The APK is at `app/build/outputs/apk/debug/app-debug.apk`. Install it, then
long-press an empty home-screen area, open **Widgets**, and add Lunar Lucent
under Widget Palette.
Remove and re-add any previously placed widget so the launcher reads the
updated resize metadata.
