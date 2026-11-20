# HADOC LocalDrop Android

**Mobile ↔ Mobile only.** A Wi-Fi LAN file-sharing and chat app for two Android phones.

## How to connect

1. Connect both phones to the same Wi-Fi router, or connect the second phone to the first phone's hotspot.
2. Turn Wi-Fi on. Bluetooth is not needed and the app does not ask for Bluetooth permissions.
3. Open HADOC LocalDrop on both phones.
4. The apps broadcast a small discovery message over the local Wi-Fi network and automatically establish a direct TCP connection when the other phone is found.

The app does not silently turn on a hotspot or join a protected Wi-Fi network. Android requires the user to enable a hotspot or approve joining a Wi-Fi network through system controls. Once both phones are on the same local network, discovery and connection are automatic.

## Features

- Automatic Wi-Fi LAN discovery and one-to-one connection.
- Multiline chat messages in either direction.
- Send files in either direction.
- Transfer progress, transferred size, and live speed estimate.
- Received files are saved inside the app and can be opened from the conversation.
- No account, cloud storage, or external relay.

## Network design

- UDP broadcast is used only to find another LocalDrop phone on the same Wi-Fi / hotspot network.
- File and chat data travel directly over a TCP socket between the two phones.
- Mobile/cellular interfaces are excluded from Wi-Fi discovery; the direct connection is bound to Android's Wi-Fi network when available.
- No Bluetooth APIs or Google Nearby Connections dependency are used.

Both phones must be on the same local Wi-Fi network. A phone-to-phone transfer over the local network does not upload the file to the internet.

## Build

Open the project in Android Studio or run:

```bash
gradle assembleDebug
```

The APK is generated at:

`app/build/outputs/apk/debug/app-debug.apk`

GitHub Actions builds a debug APK on every push to `main` and uploads it as an artifact.

## Scope

This app is mobile-to-mobile only. It does not connect to the Windows LocalDrop app, and it does not provide PC-to-PC sharing.
