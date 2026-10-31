# AlzaDash

A custom Android Auto dashboard for the Perodua Alza AV 2022 (factory player with Android Auto), plus a phone companion app.

## What's on the car screen

- **Dashboard**: clock and date, speed (car sensor when shared, otherwise GPS), trip distance, trip time, average speed, and what's playing.
- **Buttons** (right side): 🎤 voice commands, 📻 Instant Radio, ➤ Go (Google Maps, Waze, drive home, Gemini tip, reset trip), ▶ video.
- **Video** (YouTube, Netflix, Google): **only while the car is stopped.** When the car moves, or there is no GPS reading, the picture is covered and only sound continues. It comes back about 3 seconds after you stop.
- **Welcome sound** when the dashboard opens.

Voice commands: "play radio", "play <station name>", "stop radio", "navigate to <place>" (add "with Waze" for Waze), "go home", "open Waze", "open Maps", "video", "dashboard", "reset trip".

## On the phone

Play Store, Netflix, YouTube, Gemini, Google Maps and Waze buttons; an all-apps launcher; an APK installer; radio station manager; settings for the welcome sound, auto start on the car's Bluetooth, resuming radio, the car's Bluetooth name and your home address.

## Install

1. On your phone, open the **Releases** page of this repository, tap **AlzaDash.apk** under "latest", and open the download. Allow installing from your browser if asked.
2. Open **AlzaDash** on the phone and tap **Allow permissions** (location, microphone, Bluetooth). Tap **Show song on dash** and switch AlzaDash on.
3. Add your radio stations under **Radio stations** (paste a stream link; radio-browser.info lists them).
4. Turn on Android Auto's unknown sources: phone **Settings → Connected devices → Android Auto** (or search "Android Auto"), scroll down, tap **Version** about 10 times until it says developer mode is on. Open the **⋮ menu → Developer settings** and tick **Unknown sources**.
5. Connect to the Alza. AlzaDash appears in the Android Auto app list on the car screen. If not, in Android Auto settings open **Customise launcher** and tick it.

Each push to `main` builds a new APK automatically (Actions tab).

## Signing (so updates install over each other)

The signing key is never stored in the repo. Add four repository secrets under **Settings → Secrets and variables → Actions**:

- `SIGNING_KEYSTORE_BASE64`: the keystore file, base64-encoded
- `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS`, `SIGNING_KEY_PASSWORD`

Without them the build uses a throwaway debug key: the APK still installs, but you'll need to uninstall the old one before installing a newer build.

## Notes

- Netflix usually refuses to play in a web view, so Netflix on the car screen may not work. YouTube works.
- Gemini on the car: say "Hey Google" or hold the steering voice button. The phone's Gemini button opens the Gemini app.

## Build yourself

`./gradlew assembleRelease` with JDK 17 and the Android SDK. Output: `app/build/outputs/apk/release/app-release.apk`.
