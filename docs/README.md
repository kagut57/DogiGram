# DogiGram website

The static site served at https://dogi-gram.vercel.app/ from this `docs/` folder.

| File | What it is |
| --- | --- |
| `index.html` | Home page: features, live demo, what's new, download |
| `privacy.html` | Privacy policy |
| `delete-account.html` | How to delete your account or data |
| `updates.json` | Release history shown under "What's new" |
| `assets/` | Stylesheet, script, icons, Google Play badge, QR code |

## What's new updates itself

`.github/workflows/play-updates.yml` runs every hour. It reads the Google Play
listing with `.github/scripts/play_updates.py`, and when a new version is live it adds
the version, date and release notes to `updates.json` and commits the change. Vercel
redeploys on that commit, and the home page shows the new release at the top of
"What's new" with every earlier release listed below it.

To check right away after publishing an update, open the repository's Actions tab,
choose "Sync Play Store updates" and run it.
