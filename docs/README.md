# DogiGram website &amp; brand assets

This folder serves two purposes:

1. **The DogiGram website** — a static GitHub Pages site (landing page, privacy policy and
   account-deletion guide).
2. **Brand assets** — DogiGram's branding source images.

## Website

| File | Page |
| --- | --- |
| `index.html` | Landing page — what the app is, features, and download/releases links. |
| `privacy.html` | Privacy Policy (suitable for the Google Play listing). |
| `delete-account.html` | Account &amp; data deletion instructions (required by Google Play). |
| `assets/style.css` | Shared dark + violet stylesheet. |
| `.nojekyll` | Tells GitHub Pages to serve files as-is (no Jekyll processing). |

### Enabling GitHub Pages

1. Push this branch and merge it into the default branch (`master`).
2. In the repo, open **Settings → Pages**.
3. Under **Build and deployment**, set **Source** to *Deploy from a branch*, choose the
   `master` branch and the **`/docs`** folder, then **Save**.
4. After a minute the site is live at
   `https://kagut57.github.io/dogigram-official/`.

Use these URLs in the Google Play Console:

- Privacy policy: `https://kagut57.github.io/dogigram-official/privacy.html`
- Account deletion: `https://kagut57.github.io/dogigram-official/delete-account.html`

## Brand assets

| File | Purpose | Recommended size |
| --- | --- | --- |
| `dogigram_icon.png` | Master **app-icon** artwork (Shiba on the paper plane). Launcher icons in `TMessagesProj/src/main/res/mipmap-*/` are generated from this. | 1024×1024, square |
| `dogigram_logo.png` | **Wordmark logo** shown in the top-level `README.md`. | ~640×256 (transparent or solid bg) |
| `dogigram_banner.png` | Wide banner / social preview image. | ~1280×640 |
| `dogigram_intro.png` | App preview used on the website hero. | — |

> Some brand images committed here may be **placeholders**. Replace them with the real
> artwork (keeping the same file names) to update both the launcher icons and the website.
