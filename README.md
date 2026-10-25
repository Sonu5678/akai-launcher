# Akai TV launcher v2

Black home screen (v2.1: My Shows is a 2x2 grid of big cards, Other Apps is one row with a separate "More apps" pill) with a big thin clock, a funny tagline that changes with the tile you focus, a
dock (Netflix, BrowseHere, JioHotstar, Inputs), and rows that slide up when you press Down:
**My Shows**, **Other Apps** (with **More apps**) and **Play** (a small game).
Everything is drawn in code. Nothing is rooted and nothing is deleted.

Everything below runs in Termux on your phone. No PC.

## Update to this version (one command)

1. Download `akai-launcher-v2.zip` from the chat (it lands in your Downloads).
2. In Termux:
   ```
   cd ~/akai-launcher
   bash scripts/update.sh 192.168.29.42:5555
   ```
   It applies the zip, pushes to GitHub, waits for the build, and installs on the TV.
   If the build fails, nothing is installed, your TV stays as it is, and the script prints the
   error to paste to Claude.
3. Open Akai with your shortcut button. If Home already points at Akai it opens by itself.

## Make the Home button open Akai (safe, reversible)

```
bash scripts/make-default.sh 192.168.29.42:5555
```
It first checks that switching off the setup wizard won't delete a saved Wi-Fi network. It
remembers your original home, switches things off, pins Akai, and checks the result. If anything
looks wrong it rolls itself back.

## Put everything back exactly as it was

```
bash scripts/restore-original.sh 192.168.29.42:5555
```
Re-enables everything that was switched off and points Home back at the original Google TV home.
Add `--remove` to uninstall Akai completely, or `--dns-off` to set Private DNS back to automatic.

## Change things without rebuilding

Edit `app/src/main/assets/config.json` (`nano` works in Termux), then:
```
bash scripts/config-push.sh 192.168.29.42:5555 app/src/main/assets/config.json
```
It reloads when you return to Home. Check the file with a JSON checker first if you can: if it
is broken the launcher shows an error and uses its built-in defaults, it never crashes.

| Key | What it does |
|---|---|
| `dock` | The four tiles. Each is `{"match": ["name or package"], "taglines": ["...","..."]}` or `{"type": "inputs"}` |
| `myShows` | The My Shows row. `watchNext: true` shows what you are watching (see below). `items` are shows you pick by hand |
| `otherApps` | Apps in the second row. Each entry is a list of names/packages tried in order |
| `play` | `enabled: false` removes the game row |
| `hidden` | Apps hidden from All apps (exact name or package) |
| `rename` | `{"jiotv": "JioTV+"}` shows a different name |
| `columns` | Columns in All apps (3 to 7) |
| `name`, `showGreeting`, `showDate`, `clock24h` | Top-left block |
| `glassColor`, `glassOpacity`, `focusScale`, `cornerRadius` | Look |

**Wallpaper**: `bash scripts/config-push.sh <tv> /path/to/wallpaper.jpg` (1920x1080 works best). The push prints what the launcher says about it.
**Settings button**: the remote's Settings button (and the gear icon) opens a quick panel: Wi-Fi, Display, Sound, Bluetooth, Apps, All settings. It works while the Akai screen is showing.
**Font**: push a `font.ttf` the same way.
**Find a package name**: long-press OK on any app in All apps.

### Picking shows by hand
```
"myShows": { "items": [
  { "title": "Show name", "app": "netflix", "uri": "https://www.netflix.com/title/12345678",
    "poster": "show1.jpg" }
] }
```
Save the poster in your Downloads, push it with `config-push.sh`, and use its file name. If
`uri` is missing or fails, OK just opens the app.

## About My Shows ("what I'm watching")

Streaming apps can publish a "continue watching" list to Android TV. Akai reads it if your TV
allows it. Whether it works depends on this TV and on each app, and I could not test it from
here, so:
* if the TV allows it and an app publishes to it, those shows appear first (newest first);
* otherwise the row shows the shows you picked by hand;
* if neither, it shows a "Nothing here yet" card.

Only this feature can use the internet, and only to fetch poster pictures from addresses your
own apps provide. Set `"watchNext": false` and Akai never touches the network.

## Safety notes

* **Safe mode.** If the launcher stops working twice within two minutes it opens a plain screen
  with Settings, Wi-Fi and "Try normal mode" so you can never be locked out.
* **Nothing is uninstalled.** Apps are only switched off, and every switch-off is recorded.
* **Links are locked to their app.** A show link can only be opened by the app it came from.
* **Keep debugging off** on the TV when you are not changing things. That, not the code, is
  what stops other devices on your Wi-Fi from controlling the TV.
* The build is signed with a standard debug key. Keep your GitHub repo private.

## Troubleshooting

* **Build failed**: the update script prints the log. Paste it to Claude.
* **Home goes to a strange screen**: `bash scripts/restore-original.sh <tv>`.
* **Inputs doesn't switch**: tell Claude the TV model and what happens; some TVs only accept
  their own input picker.
* **An app is missing from a row**: use its package name (long-press in All apps) in
  `config.json`.
