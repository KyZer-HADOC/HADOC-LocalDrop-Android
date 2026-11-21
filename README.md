# HADOC LocalDrop Android

**Mobile ↔ Mobile only.** A SHAREit-style QR pairing and local Wi-Fi file-sharing app for two Android phones.

## Connect in two taps

1. On phone A, open LocalDrop and tap **Show QR**.
2. Approve the Nearby Wi-Fi permission once. LocalDrop starts a temporary Android Local-Only Hotspot and displays a one-time QR code.
3. On phone B, open LocalDrop and tap **Scan QR**.
4. Scan phone A's QR. Android displays its system Wi-Fi join confirmation. Approve it; LocalDrop then authenticates the one-time QR token and opens the private chat.

The hotspot is created by Android's Local-Only Hotspot API. It does not provide internet access; it exists only for direct local communication. The reservation stays open for the sharing session and is released when the session ends.

## Features

- Show QR: creates a temporary host hotspot and displays one-time pairing QR credentials.
- Scan QR: scans the host code and asks Android to join the temporary Wi-Fi network.
- One-time token is consumed by the first successful connection.
- Automatic direct TCP connection after Android confirms Wi-Fi joining.
- One unified chat for multiline messages and files in both directions.
- Live file progress, transfer size, speed estimate, and received-file opening.
- No account, cloud storage, mobile data relay, or Bluetooth.

## Android system note

Android controls Wi-Fi connection approval. After scanning, Android may show a system **Connect / Join** prompt; the user must approve it. The app cannot silently join a protected Wi-Fi network or silently enable the phone's normal internet-sharing hotspot.

The app uses WifiManager.startLocalOnlyHotspot() on the host and WifiNetworkSpecifier on Android 10+ joining devices. Local-Only Hotspot networks do not include internet access.

## Build

Open in Android Studio or run:

\`\`\`bash
gradle assembleDebug
\`\`\`

APK output: app/build/outputs/apk/debug/app-debug.apk

GitHub Actions builds and uploads a debug APK on every push to main.

## Scope

This Android app is **phone-to-phone only**. It does not connect to the Windows LocalDrop app and does not support PC-to-PC sharing.
