# Autodash 2.0

A car home screen that installs as a normal APK on a phone or an Android head unit. Built from
the CarDashPro script, renamed to Autodash, same app id (`com.autodash`) and signing key as
earlier Autodash builds, so it installs over them.

## What's on it

Every widget can be moved and resized (grid button, bottom left, then drag; orange corner to
resize; "Reset Grid" puts them back). Layout is saved separately for full screen and split mode.

- Clock and GPS speedometer (tap to switch km/h and mph)
- Now Playing for Spotify, YT Music or whatever is playing (needs "Media Bridge" notification access)
- Prayer times (JAKIM method via aladhan.com, for where the phone is, Kuala Lumpur until it knows)
- Calendar (next 5 events this week)
- Side-by-side app dock: presets or all apps, opened next to the dashboard in split screen
- Top 20 live radio row. If a station's built-in link is dead, Autodash looks it up on
  radio-browser.info and remembers the working one.
- Shield browser: address bar that also searches, ad blocking, YouTube ad skipping, keeps
  playing sound in the background.

## Driving safety

The browser is covered while the car is moving (GPS 10 km/h or more) and comes back after
you've been stopped for 3 seconds. Sound keeps playing. Without location permission the
browser stays covered and asks for it.

## What it can't do

It doesn't appear inside Android Auto. Android Auto only shows sideloaded apps of this kind if
they came from the Play Store. Use it on the phone in landscape, or install it on an Android
head unit, where it can also be set as the home app.

## Build

`./gradlew assembleRelease` in this folder (JDK 17, Android SDK). CI:
`.github/workflows/autodash.yml` signs it with the repository secrets and publishes
`Autodash.apk` to the `autodash-latest` release.
