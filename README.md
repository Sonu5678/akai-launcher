# Akai TV launcher (Apple TV style)

Black home screen, a dock with your four apps, a 4-column grid that slides up on Down,
one glass highlight that springs between items, big thin clock, Inputs / Wi-Fi / Settings pills.
Built for the Akai AG43Q (Android 14). Nothing is rooted, nothing is deleted.

## Phone-only setup (Termux)

1. **Termux**: install from F-Droid (the Play Store one is outdated), then
   `pkg install -y unzip` and `termux-setup-storage`.
2. Unzip this project: `cp ~/storage/downloads/akai-launcher.zip ~ && unzip akai-launcher.zip && cd akai-launcher`
3. **Build it on GitHub**: `bash scripts/publish.sh`
   (logs you in, makes a public repo, waits ~3 min for the APK build)
4. **TV**: Settings → Device Preferences → About → press OK on *Build* 7 times, then
   Developer options → turn on USB debugging (and Network/ADB or Wireless debugging).
   Note the TV's IP (Settings → Network).
5. **Install**: `bash scripts/install.sh <github-user>/<repo> <tv-ip>` and press **Allow** on the TV.

If the TV only offers *Wireless debugging*: run `adb pair <ip>:<pairing-port>`, enter the code,
then use the ip:port from that screen in step 5.

When you're done, turn USB / Network debugging **off** on the TV.

## Customising (no rebuild)

Everything lives in `app/src/main/assets/config.json`. Edit it (`nano`), then push it:

```
bash scripts/config-push.sh <tv-ip> app/src/main/assets/config.json
```

The launcher reloads when you come back to it. What you can change:

| Key | What it does |
|---|---|
| `dock` | The dock apps, in order. Each entry is a list of things to match, tried left to right: an exact package name, an app name, or any part of either. `["com.netflix.ninja","netflix"]` |
| `pinned` | Apps shown first in the grid |
| `hidden` | Apps to hide from the grid (same matching as `dock`) |
| `rename` | `{"hotstar": "Hotstar"}` shows a different name |
| `showDockAppsInGrid` | Also list dock apps in the grid |
| `columns` | 3 to 6 |
| `name`, `showGreeting`, `showDate`, `clock24h` | Top-left block |
| `background`, `glassColor`, `glassOpacity`, `focusScale`, `cornerRadius` | Look |

**Wallpaper**: `bash scripts/config-push.sh <tv-ip> /path/to/wallpaper.jpg`
**Font**: push a `font.ttf` the same way (Outfit from fonts.google.com looks right).

**Finding a package name**: on the TV, long-press OK on any grid app. It shows the package name
and opens App info (where you can also uninstall or disable it).

Want to change layouts or animations? Edit `MainActivity.java`, push to GitHub, and the
build runs again. Installs update in place because the signing key is fixed.

## Switching off ads / junk apps

```
bash scripts/disable-app.sh <tv-ip> <package> [package ...]
```

Switches apps off (never uninstalls) and records them. Undo everything with
`bash scripts/restore.sh <tv-ip>`. The install script already saved your full package list
to `~/tv-backup/packages.txt`; send it to Claude and ask which ones are safe to disable.

Optional ad-blocking DNS (doesn't block YouTube ads):
```
adb shell settings put global private_dns_specifier dns.adguard-dns.com
adb shell settings put global private_dns_mode hostname
```
Undo: `adb shell settings put global private_dns_mode opportunistic`

## Troubleshooting

- **Install says INSTALL_FAILED_VERIFICATION_FAILURE** or refuses a HOME app: the TV blocks
  sideloaded launchers. Everything else (debloat, DNS) still works.
- **Another home screen keeps appearing**: list who claims HOME with
  `adb shell cmd package query-activities -a android.intent.action.MAIN -c android.intent.category.HOME`,
  then `adb shell cmd package set-home-activity com.mylauncher.akaitv/.MainActivity`.
  Only after the new home works, switch the stock one off with `disable-app.sh`.
  On Google TV, don't disable `com.google.android.tungsten.setupwraith` until you've checked
  `adb shell dumpsys wifi | grep -o 'cname=[^ ]*'` — disabling it can delete a Wi-Fi network saved
  during setup and drop the TV off Wi-Fi.
- **Wi-Fi pill just says "Wi-Fi"**: the location permission wasn't granted:
  `adb shell pm grant com.mylauncher.akaitv android.permission.ACCESS_FINE_LOCATION`
- **Home button goes somewhere odd**: `bash scripts/restore.sh <tv-ip> --remove`
- **Inputs doesn't switch**: some TVs only accept their own input picker; the pill then shows a message.
- **Build failed on GitHub**: `gh run view --log-failed` in Termux, and paste the output to Claude.
