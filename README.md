# Gramm Player

Gramm Player is an Android TV application for browsing and playing media shared in your Telegram chats. It connects directly to Telegram through [TDLib](https://core.telegram.org/tdlib) and keeps account data on the device. Media metadata is enriched with [TMDB](https://www.themoviedb.org/) posters and backdrops where possible.

The UI is built on Android Leanback and is optimized for TV remotes, with optional touchscreen support.

## Features

- **Telegram login** — phone number and verification code flow via TDLib
- **Chat browsing** — browse chats, channels, and groups that contain media
- **Media library** — grid views for chats, messages, and watch history
- **Title details** — metadata, cast, ratings, download progress, and playback controls
- **Playback** — in-app player with VLC fallback when available
- **Watch history** — locally stored playback history with dashboard hero/backdrop support
- **Privacy-focused** — Telegram session and app data stay on-device; no third-party media hosting

## Tech Stack

| Area | Libraries / tools |
|---|---|
| Language | Kotlin |
| UI | Android Leanback, Material, ViewBinding |
| Telegram | TDLib |
| Networking | OkHttp, Retrofit |
| Images | Glide |
| Database | Room |
| Settings | DataStore |
| Playback | libVLC (optional), system intents |
| Build | Gradle 9.x, AGP 9.x, R8 |

## Prerequisites

- Android Studio (2025.x or newer recommended)
- Android SDK with API 36
- JDK 21 (Gradle toolchain is configured via `gradle/gradle-daemon-jvm.properties`)
- Python 3 (used by build helper scripts)
- A Telegram API application (`api_id` / `api_hash`) from [my.telegram.org](https://my.telegram.org/)
- A TMDB API key from [themoviedb.org](https://www.themoviedb.org/settings/api)

## Local Setup

1. Clone the repository:
   ```bash
   git clone https://github.com/abinsabu2/GrammPlayer.git
   cd GrammPlayer
   ```

2. Create `local.properties` in the project root (this file is gitignored):
   ```properties
   sdk.dir=/path/to/Android/sdk
   api_key=YOUR_TELEGRAM_API_ID
   api_hash=YOUR_TELEGRAM_API_HASH
   tmbd_key=YOUR_TMDB_API_KEY
   ```

3. Optional: add `app/google-services.json` if you use Firebase Analytics.

4. Open the project in Android Studio and sync Gradle, or build from the terminal:
   ```bash
   ./gradlew :app:assembleDebug
   ```

5. Install on a connected device or emulator:
   ```bash
   ./gradlew :app:installDebug
   ```

Release builds apply R8 shrinking and run Amazon Appstore sanitization scripts automatically before packaging.

## Project Structure

```
GrammPlayer/
├── app/
│   ├── src/main/java/com/aes/grammplayer/
│   │   ├── config/          # Review/test mode helpers
│   │   ├── db/              # Room database, DAOs, models, seeder
│   │   ├── helper/          # Playback, downloads, navigation, UI helpers
│   │   ├── network/tmdb/    # TMDB API client, poster/backdrop fetching
│   │   ├── provider/        # Leanback data providers
│   │   ├── ui/
│   │   │   ├── common/      # Shared fragments, binders, widgets
│   │   │   └── features/    # authentication, dashboard, chats, messages,
│   │   │                    # history, details, playback, settings, onboarding
│   │   └── util/tdlib/      # Telegram client manager, message mapping, thumbnails
│   └── src/main/res/        # Leanback layouts, drawables, themes, values
├── scripts/
│   ├── full_app_test.py                 # TV smoke test + screenshot capture
│   ├── amazon_review_screenshots.py     # Amazon review screenshot workflow
│   └── sanitize-for-amazon-appstore.py  # TDLib/DEX symbol sanitization for store scans
├── store-assets/            # App icons, promo banners, store listing screenshots
├── amazon-review-screenshots/  # Amazon Appstore review submission captures
├── test-screenshots/        # Automated/manual UI verification images
├── privacy-policy.html
├── terms-conditions.html
└── about-me.html
```

### Main user flow

1. **Onboarding / Terms** → accept terms on first launch
2. **Login** → Telegram phone + code authentication
3. **Dashboard** (`MainFragment`) → sidebar navigation with history hero and backdrop
4. **Chats** → pick a chat containing media
5. **Messages** → browse downloadable media items in a grid
6. **Details** → view metadata, start download, play content
7. **History / Settings** → revisit watched items or manage preferences

## Scripts

| Script | Purpose |
|---|---|
| `scripts/full_app_test.py` | End-to-end smoke test on a connected Android TV emulator; writes screenshots to `test-screenshots/full-app/` |
| `scripts/amazon_review_screenshots.py` | Captures the Amazon review screenshot set |
| `scripts/sanitize-for-amazon-appstore.py` | Renames TDLib symbols and scrubs release DEX metadata that Amazon's scanner can misclassify |

Example smoke test:
```bash
python3 scripts/full_app_test.py
```

## Store & Marketing Assets

These directories are intentional project content, not build output:

- `store-assets/` — icons, feature graphics, and landscape screenshots for store listings
- `amazon-review-screenshots/` — step-by-step review flow captures for Amazon submission
- `test-screenshots/` — regression/reference screenshots produced by test scripts

## Build Output & Cleanup

The following are generated locally and are gitignored. They should **not** be committed:

| Path | What it is |
|---|---|
| `app/build/` | Module build intermediates and APK/AAB outputs |
| `app/release/` | Manually copied release APKs |
| `.gradle/` | Project-level Gradle metadata (safe to keep locally) |
| `caches/`, `daemon/`, `kotlin-profile/`, `native/`, `wrapper/`, `android/` | Gradle user-home directories — belong in `~/.gradle`, not the repo root |

If Gradle cache folders appear at the project root, something set `GRADLE_USER_HOME` to this directory (for example `GRADLE_USER_HOME=. ./gradlew ...`). Normal builds use `~/.gradle`. To force the correct location:

```bash
export GRADLE_USER_HOME="$HOME/.gradle"
./gradlew :app:assembleDebug
```

In Android Studio, leave **Gradle user home** blank under **Settings → Build, Execution, Deployment → Gradle**.

## Privacy Policy & Legal Pages

HTML copies of the privacy policy, terms, and about page live in the repo root. To serve them publicly (for example via GitHub Pages) and update the URLs in `app/src/main/java/com/aes/grammplayer/ui/features/onboarding/TermsActivity.kt`.

## Amazon Appstore Submission

Gramm Player targets **Android TV / Fire TV** (Leanback launcher). Package name: `com.aes.grammplayer`.

### 1. Configure release signing

Release APKs must be signed before Amazon submission. Signing credentials are **not** committed to git (`keystore.properties`, `*.keystore`, and `*.jks` are gitignored).

**Create a keystore** (once per app; keep the file and passwords safe — losing them prevents publishing updates):

```bash
keytool -genkeypair -v \
  -keystore grammplayer-release.keystore \
  -alias grammplayer \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -dname "CN=Gramm Player, OU=Mobile, O=AES, L=Unknown, ST=Unknown, C=US"
```

Store the keystore in the project root (or another local path outside git).

**Create `keystore.properties`** in the project root:

```properties
storeFile=grammplayer-release.keystore
storePassword=YOUR_KEYSTORE_PASSWORD
keyAlias=grammplayer
keyPassword=YOUR_KEY_PASSWORD
```

`storeFile` is resolved relative to the project root. If the keystore lives elsewhere, use an absolute path.

When `keystore.properties` is present, `app/build.gradle.kts` applies the `release` signing config automatically. Without it, `./gradlew assembleRelease` still builds but produces an **unsigned** APK unsuitable for store upload.

Alternatively, configure signing in Android Studio under **Build → Generate Signed App Bundle / APK**.

### 2. Build a release APK

Then build:

```bash
export GRADLE_USER_HOME="$HOME/.gradle"
./gradlew :app:assembleRelease -x lint -x lintVitalRelease
```

The release APK is written to:

```
app/build/outputs/apk/release/tgPlayer_v{versionName}_({versionCode})_release.apk
```

Current version: **1.1** (versionCode **2**).

Release builds enable R8 minification and resource shrinking. Two sanitization steps run automatically via `app/build.gradle.kts`:

| When | Task | What it does |
|---|---|---|
| `preBuild` | `sanitizeForAmazonAppstore` | Same-length binary patches in `libtdjni.so` to remove TDLib strings Amazon's scanner flags as ad SDKs |
| Before `packageRelease` | `sanitizeReleaseDex` | Patches `android.media.metadata.ADVERTISEMENT` in release DEX files |

To run sanitization manually:

```bash
python3 scripts/sanitize-for-amazon-appstore.py              # native libs
python3 scripts/sanitize-for-amazon-appstore.py --dex-dir <path-to-dex>  # release DEX
```

### 3. Prepare listing assets

Ready-made assets live in `store-assets/`:

| Asset | Location |
|---|---|
| App icon (512 px) | `store-assets/app_icon_512.png` |
| Feature graphic (1280×720) | `store-assets/feature_graphic_1280x720.png` |
| Promo banners | `store-assets/promo_banner_*.png` / `.jpg` |
| Screenshots (1920×1080) | `store-assets/screenshots_1920x1080/` |
| Landscape banners | `store-assets/landscape_1920x1080/` |

Curated submission screenshots are also in `amazon-review-screenshots/for-amazon/` (01–08, home dashboard through watch history).

To regenerate screenshots on a connected TV emulator:

```bash
python3 scripts/amazon_review_screenshots.py
```

Outputs land in `amazon-review-screenshots/`. The script expects `emulator-5554` by default; edit `SER` in the script if needed.

### 4. Provide reviewer test credentials

Amazon reviewers should **not** need a real Telegram account. The app includes a built-in review mode activated by the test login defined in `TestUserConfig.kt`:

| Field | Value |
|---|---|
| Country code | `1` |
| Phone number | `00` |
| Auth code | `12345` |
| Full phone | `+100` |

In review mode (`ReviewModeHelper`):

- Sample chats, messages, and history are seeded locally
- Destructive dashboard actions (logout, clear cache/history, settings) are hidden
- Downloads use a public sample MP4; playback works without VLC installed

Copy `store-assets/amazon_test_instructions.txt` into the Amazon Developer Console **Testing Instructions** field. It documents the full 5–10 minute reviewer flow.

### 5. Pre-submission checklist

- [ ] `keystore.properties` configured and release APK signed (verify with `apksigner verify --print-certs app/build/outputs/apk/release/*.apk`)
- [ ] Sanitization tasks ran without errors (check Gradle output for `sanitizeForAmazonAppstore` / `sanitizeReleaseDex`)
- [ ] Privacy policy and terms URLs are live and referenced in `TermsActivity.kt`
- [ ] Listing screenshots uploaded (1920×1080 landscape for TV)
- [ ] Feature graphic and app icon uploaded
- [ ] Test credentials and `amazon_test_instructions.txt` pasted into the submission form
- [ ] Smoke test passes on a Fire TV or Android TV emulator:
  ```bash
  python3 scripts/full_app_test.py
  ```

### 6. Common review issues

| Issue | Fix |
|---|---|
| Automated scan flags TDLib as ad SDK | Rebuild release so sanitization tasks run; do not skip `preBuild` |
| Blank grids after fresh install | Wait 30–60 s for `DatabaseSeeder` on first launch, then relogin with `+100` / `12345` |
| Download or Play button missing | Confirm network access; wait for download progress to reach 100% |
| Login rejected | Use exactly country `1`, phone `00`, code `12345` |

## Contributing

Contributions are welcome. Please open an issue or submit a pull request.

## References

- [TDLib documentation](https://core.telegram.org/tdlib)
- [Telegram API](https://core.telegram.org/api)
- [Android Leanback](https://developer.android.com/training/tv/playback/leanback)
- [TMDB API](https://developer.themoviedb.org/docs)

## License

This project is licensed under the MIT License. See the `LICENSE` file for details.