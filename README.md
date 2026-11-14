# TickAlarm

Turns your TickTick reminders into labeled alarms that ring and vibrate on your Wear OS watch
(and, if you like, on your Android phone) until you dismiss them.

## How it works

1. The **phone app** reads your TickTick tasks every 15 minutes (read-only), using TickTick's Open API.
2. It works out every reminder due in the next 48 hours: on-time and early reminders, all-day
   reminders such as "9 AM that day", and the next occurrence of repeating tasks.
3. It arms an exact alarm for each one on the phone (optional) and sends the list to the **watch app**.
4. The watch arms its own alarms, so it still rings if the phone is out of range when the time comes.
5. When an alarm goes off you see the task's title full screen, with **Dismiss** and **Snooze 10 min**.
   Dismissing on one device stops it on the other.

A reminder added less than 15 minutes before it's due still rings, up to 10 minutes late,
the next time the app syncs. Completed tasks and notes never ring.

## Setup

### 1. Create a TickTick developer app (once)

1. Sign in at <https://developer.ticktick.com> and open **Manage Apps**, then **+ App Name**.
2. Set **OAuth redirect URL** to `tickalarm://oauth`.
   If TickTick won't accept that, use `http://localhost/tickalarm` instead and see step 4.
3. Keep the **Client ID** and **Client Secret** handy. They stay on your phone and never go in this repo.

### 2. Get the apps

Open the latest **Build** run under this repo's **Actions** tab (or the checks on the pull request)
and download the **tickalarm-apks** artifact. It holds `tickalarm-phone.apk` and `tickalarm-watch.apk`.
Install both APKs from the **same run**: the phone and watch apps only talk to each other when they're
signed with the same key.

### 3. Install

**Phone:** open `tickalarm-phone.apk` on the phone and allow installs from that source.

**Watch** (needs a computer once, e.g. your Mac):

```bash
brew install --cask android-platform-tools
```

1. On the watch: **Settings > System > About > Versions**, tap **Build number** 7 times.
2. **Settings > Developer options**: turn on **ADB debugging** and **Wireless debugging**.
3. In **Wireless debugging**, tap **Pair new device** and note the pairing code and address. Then:

```bash
adb pair <ip>:<pairing-port>        # enter the pairing code
adb connect <ip>:<port>             # the address shown on the Wireless debugging screen
adb -s <ip>:<port> install tickalarm-watch.apk
```

Turn **ADB debugging** off again afterwards if you like.

### 4. Connect TickTick

1. Open **TickAlarm** on the phone, paste the Client ID and Client Secret, and keep the redirect URL as
   `tickalarm://oauth` (or whatever you registered).
2. Tap **Save and connect TickTick**, sign in and approve. The browser returns to the app.
3. If you registered `http://localhost/tickalarm`, the browser ends on an error page instead: copy that page's
   address, paste it into the box at the bottom of the app, and tap **Finish sign-in with pasted address**.

### 5. Permissions and a test

1. Tap **Grant missing permissions** until it says all set (notifications, alarms & reminders,
   full-screen alarms).
2. Open TickAlarm on the watch once and allow notifications.
3. Tap **Test alarm in 1 minute** on the phone. Both devices should ring with "Test alarm from TickAlarm".

Turn off **Also ring on this phone** if you only want the watch.

## Optional: stable signing for easy updates

Without a fixed key, each build is signed with a throwaway key, so updating means uninstalling both
apps first (and connecting TickTick again). To avoid that, create a keystore once and add it as
repository secrets (**Settings > Secrets and variables > Actions**):

```bash
keytool -genkeypair -v -keystore tickalarm.jks -alias tickalarm -keyalg RSA -keysize 2048 -validity 10000
base64 -i tickalarm.jks | pbcopy   # paste as TICKALARM_KEYSTORE_B64
```

| Secret | Value |
| --- | --- |
| `TICKALARM_KEYSTORE_B64` | the base64 text of `tickalarm.jks` |
| `TICKALARM_KEYSTORE_PASSWORD` | the keystore password you chose |
| `TICKALARM_KEY_ALIAS` | `tickalarm` |
| `TICKALARM_KEY_PASSWORD` | the key password you chose |

Keep `tickalarm.jks` out of the repo (it's in `.gitignore`).

## Limits

- TickTick sign-ins last about 180 days. When one expires the phone shows a "Reconnect TickTick"
  notification and alarms stop updating until you connect again.
- Only TickTick reminders are read. Reminders kept elsewhere need a TickTick copy to ring.
- Alarms go off using your phone's and watch's own clocks and time zone.

## Project layout

| Module | What's in it |
| --- | --- |
| `shared` | Plain Kotlin: TickTick models, reminder parsing and alarm planning, with unit tests |
| `ring` | Android library used by both apps: scheduling, the alarm notification and ring screen, dismiss sync |
| `phone` | Phone app: TickTick sign-in, 15-minute sync, settings screen, sends alarms to the watch |
| `wear` | Watch app: receives alarms from the phone and rings them |

Run the core tests without the Android SDK:

```bash
./gradlew -PsharedOnly=true :shared:test
```
