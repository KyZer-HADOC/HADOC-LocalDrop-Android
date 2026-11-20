# HADOC LocalDrop Android

Mobile-to-mobile local sharing. This is the Android companion project for HADOC LocalDrop, with its own Android-to-Android connection service.

## Planned app behaviour

- Open LocalDrop on both Android phones.
- Grant nearby-device permissions once.
- The phones advertise and discover each other automatically.
- Connection requests are accepted automatically by the app.
- Once connected, both sides use the same chat-style screen.
- Send multiline text messages and files in either direction.
- Show file transfer progress and transfer status.
- No account, cloud upload, or internet connection is needed for the peer-to-peer transfer itself.

## Connection technology

This project uses Google Play services Nearby Connections with the P2P_CLUSTER strategy. Nearby Connections can discover and connect devices offline and uses Bluetooth, BLE and Wi-Fi transports as appropriate. Shared files are not uploaded to a cloud service.

Both phones need Google Play services with Nearby Connections support. Wi-Fi and Bluetooth should be enabled. Android may show a system permission prompt the first time the app starts.

## Build

Open this folder in Android Studio, or run gradle assembleDebug.

The APK is generated at app/build/outputs/apk/debug/app-debug.apk.

GitHub Actions builds the debug APK on every push to main. Tag a commit with v1.0.0 (or a later version) to attach the APK to a GitHub Release.

## Scope

This app is mobile-to-mobile only. It does not connect to the Windows LocalDrop app, and it does not provide PC-to-PC sharing.
