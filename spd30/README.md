# Pramod Octapad SPD-30

Standalone Android project based on the Octapad Pro runtime. The app ID is
`pramod.octapad.spd30`; Java classes retain the existing `com.pramod.loopmidi`
namespace so the copied runtime and resource references remain unchanged.

The `pro` flavor uses the same `allowedApps/pro` Firebase access as Octapad Pro
and bundles drum sounds only. Loop audio assets are not included.

## Firebase setup

Register `pramod.octapad.spd30` as an Android app in the existing
`pramod-octapad-loop` Firebase project, add the debug signing SHA-1, and place
that app's downloaded `google-services.json` in `app/`. The source repository
does not contain a Firebase registration for this package, so the Google
Services build task requires that file before building.

## Build

From this directory, run:

```sh
gradle :app:assembleProDebug
```
