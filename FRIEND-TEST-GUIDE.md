# AirGap v2 — Friend Test Guide

AirGap **v2** is an offline group chat for Android: phones near each other connect directly (Bluetooth + local Wi-Fi radios, via Google Nearby Connections) and share one chat room. **New in v2:** a black/red tactical look, and **mesh relay** — a message can hop through a middle phone to reach someone further away. No internet, no server, no accounts, no phone numbers. The app deliberately has **no INTERNET permission** — check the manifest.

> ⚠️ **Everyone must install v2.** v2 messages use a new format and **cannot** be read or relayed by v1. If you're running v1, update before the test.

---

## 1. The message Parth can forward to friends (copy-paste)

> Yo — v2 of my no-internet chat app is ready. This one can RELAY: if you're too far from me, the message hops through a phone in the middle. Install v2, turn on **airplane mode** (then switch Bluetooth + Wi-Fi back ON), open the app, pick a nickname, tap Start. The big test: 3 phones in a line — if the far phone gets the message with a "VIA RELAY" label, we did it. Install steps below the link. 🙏

## 2. Install steps (per friend, ~2 minutes)

1. Open the GitHub **Release** link Parth sends and download `app-debug.apk`.
   - If the download came from a GitHub **Actions** run instead, it's a `.zip` (artifact name: `AirGap-debug-apk`) — open it and pull out `app-debug.apk` first.
2. Tap the `.apk` file. Android will ask to allow "Install unknown apps" for the browser/Files app — allow it, then tap **Install**.
   - **Updating from v1?** v2 has the same app identity, so it installs over v1 — just tap Install/Update. If Android ever refuses, uninstall v1 first, then install v2.
3. If Play Protect shows a warning, that's normal for a self-built test app that isn't on the Play Store yet — choose install anyway.
4. Open **AirGap** — you should see the new black/red screen.

## 3. Test 1 — basic room (everyone, same room)

1. Turn **Airplane Mode ON**. Then turn **Bluetooth ON** and **Wi-Fi ON** again (airplane mode switches them off). Data stays off — nothing can reach the internet.
2. Open the app, type a **unique nickname** (no `|` character — it's stripped anyway), tap **START**.
3. Allow every permission it asks for (see §5).
4. Watch the top: status should read **"STATUS: LIVE — ADVERTISING + DISCOVERING"**, and the peers line counts up: **"CONNECTED: 2 · NEARBY: …"**.
5. Joins/leaves show as red lines like *">> AARAV JOINED THE ROOM"*. Your own messages sit on the **right in dark red blocks labelled YOU**; other people's sit on the **left in grey blocks with their name in red**.
6. Send a message — it should land on all other phones with airplane mode on.
7. Tap **STOP** to leave the room.

## 4. Test 2 — the relay (3 phones, the v2 moment)

You need **3 phones (A, B, C)**, all running v2 with the room started:

1. Put them **in a line**: A at one end, C at the other, **B in the middle**. Space A and C as far apart as you can while B can still reach both — opposite ends of a corridor, or three rooms in a row, works. Ideally A and C are out of (or at the edge of) each other's direct range.
2. Wait until the peers lines settle (B should show both neighbours; A and C may each show mainly B).
3. **A sends a message.**
4. ✅ **Success looks like this:** the message appears on **C**, and on C the sender label reads **"A'S-NICKNAME - VIA RELAY"**. That label is the proof the message hopped through B. B also shows the message normally (B is a room member too).
5. Try replying from C back to A the same way.
6. **Relay rules (so a "failure" isn't a misread):** a message can cross **at most 3 hops**; the middle phone must have the **app open with the room started** the whole time; a message is never delivered later — if C's app was closed, it's gone.

**Honest limits (by design, not bugs):**
- Only phones with the app open and the room started, at the same time, take part. Nothing is stored.
- Text only. No photos/files yet.
- Range is still "nearby" physics — relay stretches it hop by hop, it doesn't make it city-wide.

## 5. Permissions — why it asks, per Android version

The app asks only when you tap **START**:

| Your Android | It asks for | Why |
|---|---|---|
| 13 and up | Nearby devices (Bluetooth) + Nearby Wi-Fi devices | Finding and connecting to nearby phones |
| 12 / 12L | Nearby devices (Bluetooth) + Location | Android 12 still ties Wi-Fi discovery to location |
| 11 and below | Location | Older Android ties Bluetooth/Wi-Fi discovery to location |

If nobody shows up in the room: on Android 12 and below, also make sure the phone's **Location switch itself is ON** (Settings → Location) — discovery silently finds nothing without it on those versions.

## 6. Troubleshooting

| Problem | Fix |
|---|---|
| Status stuck, no names appear | Bluetooth ON? Wi-Fi ON? Location ON (Android ≤12)? Everyone tapped START? Move closer for the first test. |
| "AirGap needs Bluetooth / nearby permissions…" toast | A permission was denied. Re-tap START and allow all — or Settings → Apps → AirGap → Permissions and grant them there. |
| Send says "No phones connected yet" | Wait for the peers line to show CONNECTED ≥ 1. Messages can't be sent to an empty room (nothing is queued). |
| Relay test: C never got A's message | Is B's app open + room started? Is B actually connected to **both** sides (check B's peers line)? Move A and C a bit closer to B and retry. |
| C got the message but **without** "VIA RELAY" | That means A and C were actually in **direct** range — no relay was needed. Spread them further apart for a true relay test. |
| Same message appears twice on one phone | **Report this to Parth with details** — v2 is supposed to dedupe by message id, so a duplicate is a real bug sighting. |
| Someone joined, then vanished | They tapped STOP, closed the app, or walked out of range. They just tap START again. |
| Install blocked | Allow "Install unknown apps" for the app you opened the file from (step 2 above). |

## 7. What to report back to Parth

One block per tester is gold:

- Phone model + Android version (Settings → About phone)
- Did the install/update work? Any warning, and what it said?
- The exact **STATUS** and **CONNECTED / NEARBY** lines at the best moment
- Test 1: did a message cross with airplane mode ON? Both directions?
- Test 2 (if you did it): did C receive A's message **with the "- VIA RELAY" label**? Roughly how far apart were A–B and B–C?
- Any duplicate message, missing message, or crash — and what you were doing at the time
