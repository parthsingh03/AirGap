# AirGap v2

A BitChat-style **offline group messaging app for Android**. No internet, no
server, no accounts, no phone numbers — phones talk **directly to each other**
when they're near each other, and in v2 a message can **hop through a middle
phone** to reach someone further away.

v2 is a separate project from v1 (v1 stays frozen). Changes in v2, and only
these: **(1) a GOTHAM tactical black/white/red UI** and **(2) mesh relay**.
Everything else — Nearby transport, permissions, room flow — is v1's.

> ⚠️ **All testers must run v2.** The v2 wire protocol (`MSG|…`, see below)
> is not compatible with v1's plain `nickname: message` format. A v2 phone
> will still *display* a stray plain-format message, but v1 phones cannot
> parse or relay v2 messages.

## What's new in v2

### 1. GOTHAM tactical UI

- Pure black background (`#000000`), white text, one signal-red accent
  (`#FF2E2E`). Only black, white and red, anywhere.
- Monospace everywhere, sharp square corners, red-bordered inputs,
  red START/STOP and SEND buttons.
- Messages are angular blocks, not plain lines:
  - **Mine** — right-aligned, dark red-tinted block with a red accent bar,
    sender label `YOU`.
  - **Theirs** — left-aligned, dark grey block with a grey border, sender
    nickname in red caps — plus a `- VIA RELAY` label when the message
    arrived through a relay phone.
  - **System** — centered red mono lines like `>> AARAV JOINED THE ROOM`.

### 2. Mesh relay

How a message travels in v2, in plain words:

- Phones **A** and **C** are too far apart to reach each other, but phone
  **B** is in the middle, running AirGap with the room open, and B is
  connected to both sides. A sends a message → B receives it → **B
  automatically forwards it to C**. Neither A nor C does anything special.
- Every message carries a random **id** and a **hop budget (ttl) that starts
  at 3**. Each relay phone subtracts 1 before forwarding, and a phone only
  forwards while the incoming ttl is above 1 — so one message can cross
  **at most 3 hops**.
- Each phone remembers the ids it has already seen (a set capped at 500,
  oldest evicted first). A copy of a message that loops back is **dropped**,
  never shown twice and never re-sent by its author.
- The wire format is `MSG|<id>|<ttl>|<nickname>|<text>`. Only the first four
  `|` characters are separators, so `|` and `: ` inside a message survive
  intact. Nicknames can't contain `|` (it's stripped when you start).

## Honest v2 limits

- **Relay only works while the relay phone's app is open** with the room
  started, and only if it is connected to both sides at that moment.
  Nothing is stored for later delivery.
- **Everyone in the test group must run v2** (see the warning above).
- **Android only**, same as v1.
- **Still untested on real devices.** This code was compile-checked and
  structurally reviewed, but Nearby/Bluetooth behaviour can only be proven
  on real phones sitting near each other. Expect a fix round after the
  first real-world test anyway — that's normal for nearby-radio apps.
- Internet is needed only to download/install the APK once. After that the
  chat itself needs no internet at all (the app doesn't even request the
  `INTERNET` permission).

## How it works (transport, same as v1)

- Google's **Nearby Connections API** (`play-services-nearby` **19.3.0**)
  with strategy **`P2P_CLUSTER`** — many-to-many, one shared chat room.
- Every phone **advertises and discovers at the same time** and
  auto-connects to other AirGap phones it finds.
- Nearby carries data over the phones' **Bluetooth and local Wi-Fi radios,
  directly between devices**. There is no server anywhere in the path.

## Build it in Android Studio

1. Install Android Studio (it bundles the Android SDK and a JDK).
2. Open this project folder (`File → Open…`), let Gradle sync.
3. If Android Studio offers to generate / update the **Gradle wrapper**,
   accept — this repo ships `gradle/wrapper/gradle-wrapper.properties`
   (Gradle 8.7) but not the wrapper jar/scripts; Android Studio and the
   GitHub Actions workflow both generate them automatically.
4. Connect an Android phone with USB debugging on, or use an emulator for
   the UI only (emulators can't test Nearby with real phones), and Run ▶.

Requirements baked into the project: `minSdk 26` (Android 8+),
`compileSdk / targetSdk 34`, JDK 17, Kotlin 1.9.24, AGP 8.5.2.

## Publish on GitHub (Parth's route)

1. On GitHub, **create a new public repo named `AirGap`**.
2. **Upload/push the project files** — everything in this folder goes at the
   repo root (README, `app/`, `.github/`, gradle files). Don't upload the
   `.zip` itself, upload its *contents*.
3. Open the repo's **Actions** tab → the **Build APK** workflow runs on
   push. Wait for the green ✓.
4. Open the finished run and download the **`AirGap-debug-apk`** artifact.
   Inside is `app-debug.apk`.
5. In the repo, go to **Releases → Draft a new release**, tag it (e.g.
   `v2.0`), **attach `app-debug.apk`**, and publish.
6. **Share the release link with friends** — that page is the download shop.
   They install once (steps below); the chat then runs fully offline.

## How friends install it (sideloading)

1. Open the GitHub Release link on the Android phone and download the `.apk`.
2. Tap the downloaded `.apk`.
3. Android will say installs from this source are blocked — tap **Settings**
   on that prompt, turn on **Allow from this source** for the browser/Files
   app, go back, and tap **Install**. Standard one-time step for any app
   installed outside the Play Store.
4. Open AirGap, allow the Bluetooth / nearby-devices permission prompts.

## Testing it

**Basic test (2+ phones):** same as v1 — Bluetooth + Wi-Fi radios ON,
mobile data OFF (or airplane mode with the radios switched back on), app
open on all phones, distinct nicknames, tap **Start**, send a message.

**Relay test (3 phones, the v2 moment):**

1. Line the phones up A — B — C, spaced so A and C are at (or beyond) the
   edge of each other's direct range, with B in the middle. A corridor or
   three adjacent rooms works well.
2. All three: AirGap v2 open, nicknames set, **Start** tapped. Wait until
   each phone's peers line shows its neighbours.
3. From **A**, send a message. If it appears on **C** with C's sender label
   reading **`<A's name> - VIA RELAY`** — the hop through B worked. 🎉
4. Note: B will also show the message (B is a room member, not just a pipe).

Troubleshooting: if a phone doesn't appear, toggle Bluetooth off/on, make
sure the permission prompts were allowed (Settings → Apps → AirGap →
Permissions), and for the relay test keep B's app open and in the middle.

## Project layout

- `app/src/main/java/com/parth/airgap/MainActivity.kt` — the room:
  permissions, advertising + discovery, connections, the MSG wire protocol,
  dedup seen-set and relay forwarding.
- `app/src/main/java/com/parth/airgap/ChatAdapter.kt` /
  `ChatMessage.kt` — the tactical message list (mine / theirs / system).
- `app/src/main/res/layout/activity_main.xml` — the single screen;
  `item_message_mine.xml` / `item_message_theirs.xml` /
  `item_message_system.xml` — the three message rows;
  `res/drawable/bg_*.xml` — tactical borders/blocks;
  `res/values/colors.xml` — the black/white/red palette.
- `.github/workflows/build-apk.yml` — builds the shareable debug APK.
- `FRIEND-TEST-GUIDE.md` — the guide to send to testers.

## Changelog vs v1

- UI: green palette + plain text rows → tactical black/white/red palette,
  monospace, three message block types.
- Protocol: plain `nickname: message` → `MSG|<id>|<ttl>|<nickname>|<text>`
  with dedup + up-to-3-hop relay.
- Nothing else changed: same transport, permissions, room behaviour, and
  still deliberately **no INTERNET permission**.
