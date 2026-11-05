# Mantel 2 — a slab Home for the Akai AG43Q

> **Mantel 2 redesign.** Home is now a row of slabs over living per-app scenes, with a glass dock (Search, Library, Deck, Settings), a Control Deck, an original icon pack and a mood palette. Written without a compiler: if the cloud build fails, send me the red lines. Sections below describe v1 and still apply to install and the way back. Not tested on the TV: scene frame rate (Settings, Deck, Scene motion: Calm or Still if it stutters), the optional Bricolage display font (fetched at build time; Inter is used if the download fails), and the 4 accent colours (the design shows 6).

Mantel is a launcher for your 43" Google TV (Android 14), driven only by the remote:
seven pinned apps and a Library tile on one shelf over a dark wallpaper, Search and Settings one
press up, every other app one press down. It follows the approved design spec ("Akai AG43Q TV
Interface: Design Specification") number for number.

> **Read this first.** The code is complete, but **it has not been compiled or run yet**. The
> workspace it was written in could not download the Android SDK, so no APK was built and nothing
> was tested on a TV. The "What is verified" section below lists exactly what was and was not
> checked. Your two older launchers stay installed as your way back.

---

## 1. What is verified, and what is not

| Checked | How |
|---|---|
| All 21 Java files are valid Java syntax | parsed with the JDK compiler (syntax only) |
| Manifest and resource XML are well-formed | parsed |
| Fonts (Inter Light, Regular, Medium) are bundled | files present |

| **Not checked (do this on the TV)** | |
|---|---|
| The code compiles against the Android 34 SDK | needs the SDK; first real test is the build |
| Anything visual: layout, colours, motion, icon quality | needs a screen |
| Remote keys, hold-OK, Back behaviour | needs the TV |
| Apps appear, pins seed correctly, launches work | needs the TV |
| Resume line | Android 14 usually refuses third-party launchers; expect it to be absent |
| Wake guard after standby | needs a real standby cycle |
| "Home check" (default Home detection) | partly readable by an app; the TV may say otherwise |
| Content search handoff | depends on Google TV's own search app |
| Backup to USB | needs a file picker on the TV; if there is none it saves to Mantel's own folder |

If the build fails, copy the red error lines from the GitHub run and send them to me; the fix is
usually one line.

---

## 2. Get the APK (no PC)

### Fastest: one command from Termux

```
pkg install git gh android-tools unzip
gh auth login -s workflow
cd ~ && unzip -o ~/storage/downloads/Mantel-project.zip && cd ~/mantel
bash tools/deploy.sh 192.168.29.42:5555
```

It pushes Mantel to its own private GitHub repo, waits for the cloud build, downloads the APK,
installs it on the TV and opens it. It does **not** make Mantel your Home app. Use a copy in
your Termux home folder (as above), not shared storage, because git refuses to work there.
Do not push Mantel into another project's repo: the builds would mix both projects' sources.

### Path A: build it free in the cloud with GitHub (recommended)

Everything runs from Termux on your phone.

1. Make a free account at github.com if you do not have one.
2. In Termux:

   ```
   pkg update
   pkg install git gh unzip
   termux-setup-storage
   ```

3. Unzip the project and open it (adjust the path if the zip is somewhere else):

   ```
   cd ~/storage/downloads
   unzip Mantel-project.zip
   cd mantel
   ```

4. Sign in to GitHub (it shows a short code; open the link it prints on your phone and type it in):

   ```
   gh auth login -s workflow
   ```

   Choose GitHub.com, HTTPS, and "Login with a web browser".

5. Upload the project. This also starts the build:

   ```
   git init -b main
   git add .
   git commit -m "Mantel"
   gh repo create mantel --private --source=. --push
   ```

6. Wait for the build (about 3 to 6 minutes), then download the APK:

   ```
   gh run watch
   gh run download -n Mantel-apk-script -D ~/storage/downloads/mantel-apk
   ```

   The file is `~/storage/downloads/mantel-apk/Mantel.apk`.

   Two builds run side by side. If the first fails with a red cross, use the other:
   `gh run download -n Mantel-apk-gradle -D ~/storage/downloads/mantel-apk`
   (that one is named `app-release.apk`).

   You can also do step 6 on the GitHub website: repo, **Actions** tab, newest run, scroll to
   **Artifacts**, download, then unzip.

### Path B: any machine that has the Android SDK

```
export ANDROID_HOME=/path/to/android-sdk
bash tools/build.sh
```

Result: `build/Mantel.apk`. It needs a JDK 17 or newer, `zip`, and the SDK's build-tools and a
platform 34 (or newer) `android.jar`. No downloads happen. The script creates a signing key at
`~/.mantel-debug.keystore` the first time; keep it, because a new build can only update the
installed one if it is signed with the same key.

### Path C: Android Studio

Open this folder, let it sync, then **Build, Build APK(s)**. The Gradle files are included for
this. They are untested.

---

## 3. Install on the TV (Termux)

1. On the TV, turn **Network debugging** back on (Settings, System, Developer options). Note the
   IP address it shows. Yours was `192.168.29.42:5555`.
2. In Termux:

   ```
   pkg install android-tools
   adb connect 192.168.29.42:5555
   ```

   Accept the prompt on the TV ("Always allow from this computer").
3. **Write down your current Home app before changing anything:**

   ```
   adb shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME
   ```

   The last line is a name like `com.mylauncher.akaitv/.MainActivity`. Save it somewhere
   (a note on your phone). This is your way back.
4. Install:

   ```
   adb install -r ~/storage/downloads/mantel-apk/Mantel.apk
   ```

   You should see `Success`. If you see `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, a copy signed with
   another key is installed; run `adb uninstall dev.mantel.tv` and install again.

Installing does **not** change your Home. Your current launcher keeps working.

---

## 4. Try it first (nothing changes)

Open Mantel by hand and look around:

```
adb shell monkey -p dev.mantel.tv -c android.intent.category.LEANBACK_LAUNCHER 1
```

You can also find **Mantel** in the TV's Apps list, or give it to the blue shortcut key.

Check these before you make it Home:

1. Seven tiles and a Library tile appear; your seven apps are in your order (Netflix, JioHotstar,
   Browsehere, YouTube, JioTV+, Spotify, Kartoons). An app that is missing leaves an empty slot.
2. OK launches an app; Back returns to Mantel's shelf; the focus lands on the app you used.
3. Down opens the Library; Up reaches Search and Settings; Hold OK on a tile opens its menu.
4. Settings, Home check shows what the TV says your Home app is.

---

## 5. Make Mantel your Home (reversible)

This only changes which app the Home key opens. Nothing is deleted or disabled.

```
adb shell cmd package set-home-activity dev.mantel.tv/.MainActivity
```

Then press the remote's Home key. If the TV asks which Home app to use, choose Mantel and
"Always". If the command is refused, use Mantel's own page: Settings, Home check, **Open
Android's home-app settings**.

### The way back (any one of these)

- **Switch back:** `adb shell cmd package set-home-activity com.mylauncher.akaitv/.MainActivity`
  (use the exact line you saved in step 3 of the install).
- **Remove Mantel:** `adb uninstall dev.mantel.tv`. The TV falls back to your other launcher.
- **From the TV alone:** Settings, Apps, Home app (or Default apps), pick your old launcher.
- **If nothing shows a Home at all:** your notes say the stock Google TV launcher is disabled.
  Re-enable it from Settings, Apps, See all apps, Show system apps. That is the last resort.

Keep **both** of your earlier launchers installed until you have used Mantel for a few days.

When you are done with adb: turn **Network debugging off** on the TV again.

---

## 6. Using it

| Key | What it does |
|---|---|
| Left / Right | move along the shelf |
| OK | open the app |
| Hold OK (half a second) | menu: Move, Remove from shelf, App info |
| Up | Resume line (if any), then Search and Settings |
| Down | the Library: every other app |
| Back | one step back; on the resting tile it does nothing, so it never leaves Mantel |
| Blue key | your TV shortcut; assign it to Mantel to open it by hand |

The first key after standby or ambient mode only wakes the screen; it never also launches an app.

### Ambient mode

After 10 idle minutes (5, 10, 30 or Off in Settings) the shelf fades out, the wallpaper dims and a
single clock appears. Any key wakes it.

### Backup

Settings, Backup, **Export settings** writes one small file with your shelf, hidden apps and look.
**Restore settings** reads it back. Do this before any factory reset.

---

## 7. Where this differs from the spec

These are on purpose, and each is small.

- **Wallpapers are drawn by the app** when it starts (six soft glows on the base colour), not
  shipped as image files.
- **The banner and icon are simple vector drawings.** The shelf is the thing to judge.
- **The Offline pill sits beside the Search and Settings glyphs** instead of replacing them, so
  those two stay reachable without a connection.
- **Sideloaded phone-style apps get a tile too.** Kartoons has no TV entry, so Mantel lists
  user-installed apps that only have a phone launcher entry; preinstalled phone-only apps stay out.
- **Content search is handed to the TV's own search app.** If it does not open, Mantel says so.
- **Resume line:** Mantel asks for Watch Next but Android 14 normally refuses third-party
  launchers. If it is refused, the line is simply absent and nothing shifts.
- **Settings, Home check:** Android only lets an app read the default Home app, not the
  "start on power-on" setting, so that row says "Not readable here".

---

## 8. Facts about the build

- Package: `dev.mantel.tv`, version 0.1.0. Android 8.0 (API 26) and up; built for API 34.
- Pure Android framework code: no libraries, no network use, no accounts, no analytics.
- Permissions: network state (for the Offline pill), delete packages (so Uninstall can ask
  Android), and Watch Next read (usually refused, harmless).
- Font: Inter (SIL Open Font License 1.1).
- Layout: everything is drawn on a 1920 x 1080 design canvas and scaled to the screen, so each
  number in the spec is used as written.
- Source map: `MainActivity` (layer stack and keys), `HomeScreen`, `LibraryScreen`,
  `SearchScreen`, `SettingsScreen`, `LookScreen`, `AmbientScreen`, `DialogOverlay`,
  `MenuOverlay`, plus `AppRepo`, `Prefs`, `IconCache`, `WallpaperView`/`WallpaperGen`.
