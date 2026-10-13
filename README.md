# AirGap 📡

### Messaging with no internet. No server. No accounts. Just phones talking to phones.

[![Build APK](https://github.com/parthsingh03/AirGap/actions/workflows/build-apk.yml/badge.svg)](https://github.com/parthsingh03/AirGap/actions/workflows/build-apk.yml)
![Kotlin](https://img.shields.io/badge/Kotlin-1.9.24-7F52FF?logo=kotlin&logoColor=white)
![Android](https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white)
![INTERNET permission](https://img.shields.io/badge/INTERNET%20permission-NONE-red)

AirGap is an **offline mesh messaging app for Android**, inspired by Jack Dorsey's BitChat. Open it, pick a nickname, and chat with people physically near you — over Bluetooth and Wi-Fi Direct — even with mobile data off, Wi-Fi disconnected, or airplane mode on (radios re-enabled). The app doesn't even *request* the `INTERNET` permission: your messages physically cannot leave the phones around you.

<p align="center">
  <img src="docs/ui-preview.png" width="320" alt="AirGap tactical UI preview"/>
  <br/><em>UI preview — GOTHAM-style tactical black / white / signal-red theme.</em>
</p>

## ✨ Why AirGap exists

Networks fail exactly when communication matters most — power cuts, disasters, jammed networks at packed events, hostels and campuses with dead zones, treks and convoys far from any tower. AirGap turns every phone running it into a tiny cell tower: a private, infrastructure-free chat room that exists wherever a few people are standing.

## 🚀 Features

- **Truly offline** — no internet, SIM, router, server or account anywhere in the path
- **One shared room** — everyone nearby with the app open is in the same group chat
- **Mesh relay** — messages can hop through a middle phone to reach further (up to 3 hops), so the room stretches beyond one phone's range
- **Nickname-only identity** — no sign-up, no phone number, nothing to register
- **Tactical UI** — black / white / signal-red, monospace, with `VIA RELAY` labels showing which messages arrived through a hop
- **Zero data collection** — there is no backend to collect anything

## 🔗 How the mesh works

```text
  A ──────────────► B ──────────────► C ──────────────► D
  │                                                     
  └─ ttl = 3         └─ ttl = 2        └─ ttl = 1 ──► stops
```

- Phones discover each other automatically (Google Nearby Connections, `P2P_CLUSTER`) — Bluetooth handles discovery, Wi-Fi Direct carries the messages.
- Range per hop: roughly **30–100 m in the open, 10–30 m indoors**.
- Every message carries an id and a **hop budget (ttl) of 3**. Each relay phone forwards it with ttl − 1; at ttl 1 it is displayed but not forwarded. Duplicate copies are recognised by id and dropped — no loops, no spam.
- A relay phone needs nothing from its owner: app open = silent infrastructure.

## 📲 Download & install

1. Open the repo's **Actions** tab → latest green **Build APK** run → download the **`AirGap-debug-apk`** artifact (contains `app-debug.apk`). *(A one-tap download via Releases is coming.)*
2. On your Android phone, tap the `.apk` and allow **Install from this source** when prompted (standard for apps installed outside the Play Store).
3. Open AirGap, allow the Bluetooth / nearby-devices permissions, enter a nickname, tap **START**.

> ⚠️ All phones in a room must run the **same version** (v2's relay protocol is not compatible with v1).

## 🧪 The 2-minute field test

1. Two (or more) Android phones, AirGap v2 installed.
2. **Airplane mode ON**, then manually switch **Wi-Fi and Bluetooth back ON**. Mobile data fully off.
3. Open AirGap on both, different nicknames, tap **START**.
4. Send a message. It arrives — with no network of any kind involved. That's the whole demo.
5. **Relay test:** put a third phone between the two, out of the first phone's direct reach — watch the far phone receive it stamped **`VIA RELAY`**.

## 🛠️ Tech stack

| Layer | Tech |
| --- | --- |
| Language | Kotlin 1.9.24 |
| Transport | Google Nearby Connections (`play-services-nearby` 19.3.0), `P2P_CLUSTER` |
| Mesh protocol | `MSG\|<id>\|<ttl>\|<nickname>\|<text>` — id dedup (seen-set, cap 500), ttl-based forwarding |
| Min / target SDK | 26 (Android 8) / 34 |
| Build | Gradle 8.7 · AGP 8.5.2 · JDK 17 · GitHub Actions CI |

**Permissions:** Bluetooth, Wi-Fi state, and location (Android requires it for nearby-device discovery on older versions) — and deliberately **no `INTERNET`**. The manifest is the privacy policy.

## 🗺️ Roadmap

- [ ] Raise relay ttl 3 → 7 (campus-scale reach)
- [ ] End-to-end encryption (current version is plaintext — see below)
- [ ] Structured field reports + a bridge into the **GOTHAM** intel dashboard (`AIRGAP MESH` source)
- [ ] GitHub Release with a one-tap APK download
- [ ] Launcher icon from the final logo

## ⚠️ Honest limitations

- **Plaintext (for now).** Anyone running AirGap in your room can read the room's messages. Encryption is the top roadmap item — do not treat v2 as a secure channel yet.
- Relay only works through phones that **have AirGap installed and open** — a phone without the app is invisible to the mesh.
- Nothing is stored or delivered later: if nobody is in range, the message goes nowhere. No offline inbox.
- Android only. iOS does not expose the radios this design needs in the same way.

## 👨‍💻 Author

Built by **Parth Singh** — B.Tech ECE @ GLA University.
Portfolio: https://parthsingh03.github.io · GitHub: [@parthsingh03](https://github.com/parthsingh03)

---

*Theory is cheap. Working systems are the argument.*
