# HADOC LocalDrop Android

**Phone ↔ Phone and Windows PC ↔ Phone.** A Wi-Fi-only local sharing app with QR pairing, chat, and file transfer.

## Phone ↔ Phone

1. On phone A, open LocalDrop and tap **Show QR**.
2. Approve the Nearby Wi-Fi permission once. LocalDrop starts a temporary Android Local-Only Hotspot and displays a one-time QR code.
3. On phone B, open LocalDrop and tap **Scan QR**.
4. Scan phone A's QR and approve Android's system Wi-Fi join prompt.
5. LocalDrop authenticates the one-time QR token and opens the private chat.

The host hotspot is created by Android's Local-Only Hotspot API. It does not provide internet access and is released when the sharing session ends.

## Windows PC ↔ Phone

1. Install and open the Windows **HADOC LocalDrop** app.
2. In the Windows app, enter the router Wi-Fi name (SSID) and WPA/WPA2 password once. The password is saved encrypted with Windows DPAPI for the current Windows user.
3. Click **Start LocalDrop** and select the PC's IPv4 address on that router. The PC may be connected by Ethernet.
4. On the Android phone, open LocalDrop and tap **Connect PC**.
5. Scan the Windows QR. On Android 10+, the app requests a temporary Wi-Fi connection using the router details carried in the private QR. Approve Android's system connection prompt.
6. The Android app routes its HTTP and WebSocket traffic through that temporary router network and opens the PC chat/file workspace.
7. Disconnect from the PC workspace when finished. LocalDrop releases the app-scoped Wi-Fi request so Android can return to its previous network.

The phone does not need the router password typed manually. The QR carries the Wi-Fi passphrase, so only scan it with a trusted phone and never post or forward it publicly. The router and PC must allow local device-to-device communication; guest/AP isolation or Windows Firewall blocking the app can prevent connection.

## Features

- QR pairing for phone-to-phone and PC-to-phone
- Android temporary Wi-Fi network requests; no Bluetooth
- One-time token for phone-to-phone host sessions and fresh PC pairing tokens
- Direct local chat and file transfer in both directions
- Live transfer progress, file size, and speed estimates
- PC-to-phone downloads and phone-to-PC uploads in the Android app
- No account, cloud storage, or mobile-data relay for file contents

## Android system note

Android controls Wi-Fi connection approval. The app cannot silently join a protected Wi-Fi network or silently enable the phone's normal internet-sharing hotspot. Automatic temporary router joining for PC QR codes requires Android 10 or newer and the HADOC LocalDrop Android app. Scanning a PC QR with a generic camera/browser does not initiate the app-scoped Wi-Fi request.

## Build

Open in Android Studio or run:

```bash
gradle assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`.

GitHub Actions builds the debug APK on every push to main and publishes versioned releases.

