# Pramod Piano

Standalone landscape Android piano/keyboard starter project.

## Features in this starter

- Oboe low-latency stereo output with FluidSynth 2.3.3 rendering through JNI.
- Classic black-and-white, 88-key horizontally scrollable keyboard. Every key
  is labeled with its note and octave (such as A0, C#4, or C8), and held
  multi-touch keys highlight independently.
- Touch-and-hold chord buttons above the keyboard.
- Instrument program selector (General MIDI labels).
- 33 bundled WAV/MP3 sample presets, named from the uploaded instrument files.
- SF2 file picker using Android's Storage Access Framework, with GM programs.
- Vertical pitch bender on the left that returns to center when released.
- Octave transpose and keyboard scroll lock controls.
- Google sign-in with access gated by `authorizedUsers/{uid}/allowedApps/piano`.

The bundled samples are decoded on selection and played through the Oboe audio
callback. Their preset labels use each source filename without its extension.
No additional SoundFont is bundled; choose an SF2 whose license permits your
intended use. FluidSynth is consumed from the pinned `fluidsynth-android` 2.3.3
Maven Prefab package, which provides prebuilt shared libraries for Android
ABIs. FluidSynth is LGPL-2.1; review the license and provide its notices and
relinking information with distributed builds.

## Build

Open this directory in Android Studio or build with:

```sh
./gradlew :app:assembleDebug
```

The GitHub Actions workflow generates a Gradle wrapper and builds/uploads a
debug APK. It installs Android 35, NDK 27.2.12479018 and CMake 3.22.1.

## Package

- Application ID: `com.pramod.piano`
- Min SDK: 23
- Target SDK: 35

The Piano module uses its Firebase Android registration in
`app/google-services.json`. Users without an approved Piano entitlement can
submit a pending request; the app waits for an administrator to enable the
Piano checkbox in the admin panel.

The current UI is a starter. The preset list exposes the standard 128 GM program
slots for the selected SoundFont; SoundFonts with banks outside bank 0 need
future preset enumeration support.
