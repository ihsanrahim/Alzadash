# Autodash

A car dashboard that installs as a normal APK. No Shizuku, no KingInstaller, no Play Store.
It's built from AlzaDash and looks the same, plus a full web browser.

## What you get

- **Dashboard on the phone** (mount the phone in landscape): clock, Hijri date, prayer times,
  weather, speed and trip, radio row, now playing and lyrics, news and sports, calendar, air
  quality, rain radar, YouTube card, and the shortcut bar. Edit the layout from the phone app.
- **Browser**: address bar that also searches, back/forward, reload, bookmarks on the start page,
  ad blocking, desktop-site mode, downloads, file uploads, full-screen video, share and "open
  in app". It can also be picked as the phone's default browser. While the car is moving
  (GPS shows over 10 km/h) the page is covered and sound keeps playing; it comes back once
  you've stopped.
- **Android Auto**: *Autodash Radio* appears among the car's media apps, with your stations,
  and the steering-wheel next/previous buttons change station.
- Voice commands, Play Store / YouTube / Netflix / Maps / Waze / Gemini buttons, all-apps
  launcher, APK installer, welcome sound and a "car connected" notification that opens the
  dashboard.

## What it can't do

Android Auto only lists custom *dashboard* apps on the car screen when they were installed
from the Play Store. A normal APK can't get past that, so the Autodash dashboard and browser
run on the phone, and the car screen gets the radio. (AlzaDash with KingInstaller is still the
way to put the dashboard itself on the car screen.)

## Install

Open the **Releases** page, find **Autodash (latest build)**, tap **Autodash.apk** and install
it. Autodash installs next to AlzaDash; they don't replace each other.
For Autodash Radio on the car screen, turn on Android Auto's developer setting *Unknown sources*
if it doesn't show up.

## Build

`./gradlew assembleRelease` in this folder (JDK 17, Android SDK). CI: `.github/workflows/autodash.yml`.
