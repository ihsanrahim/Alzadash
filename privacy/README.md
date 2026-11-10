# Autodash Privacy Policy

Effective date: 10 November 2026

Autodash (package `com.alzadash`) is a car dashboard for Android phones and Android Auto. This policy explains what the app uses on your phone, what it sends over the internet, and what it does not do.

## In short

- Autodash has **no accounts, no ads, no analytics and no tracking**.
- The developer **does not collect, receive or store any of your data**. There is no Autodash server.
- Everything the app saves (settings, bookmarks, radio stations, lyrics it has already found) stays **on your phone** and is deleted when you uninstall the app.
- To show some information, the app asks public online services for it, as described below.

## Permissions and what they are used for

| Permission | Why Autodash uses it | Leaves your phone? |
|---|---|---|
| Location (precise, and in the background while the car screen is open) | Shows your speed and trip, works out prayer times for where you are, gets the local weather, air quality and rain radar, and keeps video and the browser hidden while the car is moving. | Your approximate position is sent to the weather, air-quality and map services listed below. It is never sent to the developer. |
| Car speed (Android Auto) | Confirms the car is stopped before showing video or web pages. | No |
| Microphone | Voice commands and voice typing, only while you are speaking after tapping the mic button. | Speech is handled by your phone's own speech recognition service (for example Google). Autodash does not record or keep audio. |
| Calendar (read only) | Shows your upcoming events on the calendar widget. | No |
| Notification access | Reads which song your music app is playing (title, artist, album art, position) for the now-playing card and lyrics. Autodash does not read or keep your messages or other notifications. | The song title, artist and length are sent to LRCLIB to find lyrics. |
| Bluetooth | Notices when the phone connects to your car, so the dashboard can be opened. | No |
| Screen capture (phone mirroring) | Shows your phone screen on the car display, only after you start it and agree to Android's prompt. | No. The picture goes only to your car screen and is never recorded or uploaded. |
| Accessibility service (mirroring taps) | Passes the taps you make on the car screen to your phone while mirroring. It does not read screen content. | No |
| Display over other apps | Keeps the phone screen in landscape while mirroring. | No |
| Install apps | Lets you install an app file you choose yourself. | No |
| Internet, notifications, foreground service, wake lock | Streaming radio, showing a playback notification, and keeping the car screen awake. | See below |

## Online services Autodash contacts

The app connects directly from your phone to these public services. Each one gets only what is needed for the request (for example your approximate location or a song name) plus your IP address, as with any internet request. Their own privacy policies apply.

- **Open-Meteo** (weather and air quality): your approximate location.
- **RainViewer** and **OpenStreetMap** (rain radar map): the map area around you.
- **LRCLIB** (lyrics): the song title, artist and length.
- **Radio Browser** (radio station lookup) and the radio stations you play.
- **News websites and Google News** (news headlines), and **ESPN, Jolpica and MotoGP** (sports standings).
- **Websites you open** in the built-in browser, including YouTube and others. They may use cookies, which are kept on your phone and can be cleared in the app's browser settings.

## Children

Autodash is meant for adult drivers and is not directed at children under 13.

## Changes

If this policy changes, the new version will be posted at this address with a new effective date.

## Contact

Questions about this policy: open an issue at https://github.com/ihsanrahim/Alzadash/issues
