# Gramm Player

Gramm Player is an Android TV application for browsing and playing media shared in your Telegram chats. It connects directly to Telegram through [TDLib](https://core.telegram.org/tdlib) and keeps account data on the device. Media metadata is enriched with [TMDB](https://www.themoviedb.org/) posters and backdrops where possible.

The UI is built on Android Leanback and is optimized for TV remotes, with optional touchscreen support.

## Features

- **Telegram login** — phone number and verification code flow via TDLib; review login `+100` / `12345` seeds local data
- **Chat browsing** — paged Leanback grids via `JsonSeedStore` + `ChatsDataProvider` (`messagesPageSize` configurable)
- **Media library** — paged message grids per chat via `MediaMessageDataProvider` + `MediaDownloadDataProvider`
- **Title details** — `ReleaseTitleParser` + TMDB enrichment (poster/backdrop, cast, ratings, trailer), file metadata chips, download progress, and fixed Auto-Play thresholds (progress % / buffer MB)
- **Playback** — **external VLC only** (`PlayerHelper.play()` with `FLAG_ACTIVITY_NEW_TASK` + `FileProvider`); never `startActivityForResult` on TV. Resume restored via `VlcPlaybackTracker` (MediaBrowser session) + `SettingsDataStore` bookmark (`MIN_RESUME 5s`, end-clear `5s`)
- **Watch history** — per-login file store (`filesDir/history/history_{login}.json`, cap 100, newest-first) via `HistoryStore`; recorded on detail-page visit, cleared on logout/clear-history; powers Continue Watching row + dashboard backdrop
- **Storage auto-manager** — threshold-based oldest-first delete (`documents/temp/videos/test_videos` under `filesDir/tdlib/files`) + optional move-to-SD (`StorageAutoManager`), triggered from Dashboard `onResume` and Settings → Run now; SD detection is Fire TV-safe
- **Dashboard** — Leanback `BrowseSupportFragment` with Continue Watching, Active Download, and internal/external free-space cards; hero backdrop from latest history entry
- **TMDB enrichment** — `PosterFetcher` with OkHttp `MODERN_TLS`, 4-permit semaphore, in-flight dedup, movie + TV search caches, `Glide` wired to `TmdbClient.okHttpClient`
- **Privacy-focused** — Telegram session (`tdlib` dir) and app data stay on-device; no third-party media hosting

## Tech Stack

| Area | Libraries / tools |
|---|---|
| Language | Kotlin |
| UI | Android Leanback, Material, ViewBinding |
| Telegram | TDLib (`org.drinkless:tdlib`) |
| Networking | OkHttp 4.12, Retrofit 3.0 + Gson, `PosterFetcher`/`TmdbClient` |
| Images | Glide 4.11 (OkHttp integration, `GlideUrl` → `TmdbClient.okHttpClient`) |
| Persistence | No Room — `JsonSeedStore` (`assets/seed/test_seed.json`) + `HistoryStore` JSON per login + `SettingsDataStore` (DataStore Preferences) |
| Playback | External VLC (`org.videolan.vlc`) via `PlayerHelper` + `VlcPlaybackTracker` (MediaBrowser) + `SettingsDataStore` bookmark; system `ACTION_VIEW` intents |
| Storage | `ApplicationHelper` + `StorageAutoManager` + `MediaFileHelper` |
| Build | Gradle 9.x, AGP 9.0.1, R8, `sanitize-for-amazon-appstore.py` (native + DEX) |

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

3. Optional: `app/google-services.json` is not required — Firebase/Analytics was removed (Sept 2026). Skip unless you re-add it.

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
│   │   ├── config/          # Review/test mode helpers (ReviewModeHelper, TestUserConfig)
│   │   ├── db/model/        # Plain models only (Chat, MediaMessage) — Room removed, see JsonSeedStore
│   │   ├── helper/          # PlayerHelper, VlcPlaybackTracker, StorageAutoManager,
│   │   │                    #   ApplicationHelper, MediaFileHelper, ActiveDownloadManager,
│   │   │                    #   DashboardBackdropHelper, GlideHelper, FormatHelper, etc.
│   │   ├── history/         # HistoryStore (per-login JSON) + HistoryEntry
│   │   ├── network/tmdb/    # TmdbClient/Api, PosterFetcher, TlsHelper
│   │   ├── provider/        # JsonSeedStore (assets/seed/test_seed.json), ChatsDataProvider,
│   │   │                    #   MediaMessageDataProvider, HistoryDataProvider, Page
│   │   ├── session/         # UserSession
│   │   ├── ui/
│   │   │   ├── common/      # BaseGridFragment/BaseHostActivity, binders, widgets
│   │   │   └── features/    # authentication, dashboard, chats, messages,
│   │   │                    # history, details (ReleaseTitleParser UI), settings, onboarding
│   │   └── util/tdlib/      # TelegramClientManager, MediaMessageMapper, ReleaseTitleParser, thumbnails
│   ├── src/main/assets/seed/test_seed.json  # Bundled seed for TEST/REVIEW mode (TMDB-shared)
│   └── src/main/res/        # Leanback layouts, drawables, themes, values
├── scripts/
│   ├── full_app_test.py                 # TV smoke test + screenshot capture
│   ├── amazon_review_screenshots.py     # Amazon review screenshot workflow (writes amazon-review-screenshots/)
│   ├── sanitize-for-amazon-appstore.py  # TDLib/DEX symbol sanitization for store scans
│   ├── publish-playstore.py             # One-command checks + bundleRelease + optional Play upload (rung 1)
│   └── record_login_demo.py             # Login demo recording helper
├── fastlane/                # Fastfile + Appfile — Play upload via supply (rung 2)
├── .github/workflows/playstore.yml  # Tag/manual CI: build AAB + upload to Play (rung 3)
├── docs/
│   └── playstore-automation.md  # Rungs 1-3 guide + Play checklist
├── privacy-policy.html
├── terms-conditions.html
└── about-me.html
```

> `store-assets/`, `amazon-review-screenshots/`, `test-screenshots/` are **not** in the repo at `HEAD` — they are generated or kept locally for store listings. Past commits shipped them; current tree relies on `app/src/main/res/drawable` banners/icons + `scripts/amazon_review_screenshots.py` to regenerate captures.

### Main user flow

1. **Onboarding / Terms** (`OnboardingActivity` / `TermsActivity`) → accept terms on first launch (`SettingsDataStore.isTocAccepted`)
2. **Login** (`LoginActivity`) → TDLib phone + code; review shortcut `+100` / `12345` → `ReviewModeHelper.isReviewMode()` true
3. **Dashboard** (`MainFragment : BrowseSupportFragment`) → sidebar (Chats / History / Settings) + Continue Watching row (`HistoryStore`) + Active Download row + Storage cards (internal/external free) + hero backdrop (`DashboardBackdropHelper`)
4. **Chats** (`ChatsGridActivity/Fragment`) → paged via `ChatsDataProvider` → `JsonSeedStore.getChatsPaged()` in review mode, TDLib otherwise
5. **Messages** (`MessageGridActivity/Fragment`) → paged via `MediaMessageDataProvider` (`messagesPageSize` from `SettingsDataStore`, choices 25/50/100/200)
6. **Details** (`MediaDetailsActivity`) → `ReleaseTitleParser` display title, TMDB poster/backdrop/cast/trailer (`PosterFetcher`), file metadata chips, `SettingsDataStore` Auto-Play thresholds (progress % / buffer MB), download → `MediaDownloadDataProvider` + `ActiveDownloadManager` + `DownloadProgressTracker`, then **external VLC** (`PlayerHelper.play()` + `VlcPlaybackTracker` bookmark)
7. **History** (`HistoryGridActivity/Fragment`) → `HistoryDataProvider` → `HistoryStore.loadPage()`
8. **Settings** (`SettingsActivity/Fragment : GuidedStepSupportFragment`) → Auto-Play, thresholds, Messages per page, Storage auto-delete / move-to-SD / threshold (300/500/1000 MB), Run auto-clean now

## Scripts

| Script | Purpose |
|---|---|
| `scripts/full_app_test.py` | End-to-end smoke test on a connected Android TV emulator; writes screenshots to `test-screenshots/full-app/` |
| `scripts/amazon_review_screenshots.py` | Captures the Amazon review screenshot set → `amazon-review-screenshots/` (`SER=emulator-5554` by default) |
| `scripts/sanitize-for-amazon-appstore.py` | Renames TDLib symbols and scrubs release DEX metadata that Amazon's scanner can misclassify |
| `scripts/publish-playstore.py` | One-command Play bundle flow: validates `local.properties`/`keystore.properties`, warns on `targetSdk` drift, runs sanitization, `bundleRelease`, `jarsigner`/`bundletool` verify, optional `fastlane supply` (`--bump patch|minor|major`, `--upload --track internal|closed|production`) |
| `scripts/record_login_demo.py` | Login demo recording helper |

Example smoke test:
```bash
python3 scripts/full_app_test.py
```

Play bundle (no service account needed):
```bash
python3 scripts/publish-playstore.py                 # checks + signed AAB
python3 scripts/publish-playstore.py --bump patch    # bump 3.4 (304) → 3.4.1 (305) + build
python3 scripts/publish-playstore.py --upload --track internal  # + supply if play-service-account.json present
```

CI (`.github/workflows/playstore.yml`): push tag `v*` or manual dispatch; needs `LOCAL_PROPERTIES`, `KEYSTORE_BASE64`, `KEYSTORE_PROPERTIES`, `PLAY_SERVICE_ACCOUNT_JSON` secrets. See `docs/playstore-automation.md`.

## Store & Marketing Assets

No `store-assets/` / `amazon-review-screenshots/` / `test-screenshots/` at `HEAD` — they are **generated locally**:

- Run `scripts/amazon_review_screenshots.py` on a TV emulator to regenerate `amazon-review-screenshots/` (and `for-amazon/` curated set 01–08).
- Run `scripts/full_app_test.py` for `test-screenshots/`.
- Store listing icons/banners live as drawables (`app/src/main/res/drawable/banner.png`, `tv_banner.png`, `ic_launcher*`) and are referenced by `docs/playstore-automation.md` when filling Play Console listing; keep any exported 512 px / 1280×720 / 1920×1080 assets out of git or add them under a local `store-assets/` ignored by `.gitignore` `*.aab`/`*.apk` rules.

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

### 2. Build a release APK or AAB

Then build (Amazon = APK, Play = AAB):

```bash
export GRADLE_USER_HOME="$HOME/.gradle"
./gradlew :app:assembleRelease -x lint -x lintVitalRelease   # Amazon APK
./gradlew :app:bundleRelease                                  # Play AAB
# or one-command (validates + sanitizes + builds + verifies):
python3 scripts/publish-playstore.py                 # signed AAB
python3 scripts/publish-playstore.py --bump patch    # version bump + AAB
python3 scripts/publish-playstore.py --upload --track internal  # + fastlane supply
```

Outputs (renamed via `applicationVariants.all`):

```
app/build/outputs/apk/release/tgPlayer_v{versionName}_({versionCode})_release.apk
app/build/outputs/bundle/release/*.aab
```

Current version: **3.4** (versionCode **304**) — `app/build.gradle.kts`. The old `1.1 (2)` in earlier README was stale. Bump via `scripts/publish-playstore.py --bump patch|minor|major` (versionCode always +1; Play rejects reused codes).

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

`sanitize-for-amazon-appstore.py` is harmless for Play — keep it.

> `AndroidManifest.xml` currently declares `targetSdkVersion="34"` while `app/build.gradle.kts` targets `36`. AGP 9 drives the merged manifest, but align the manifest to `36` (or remove the `<uses-sdk>` tag) before Play upload — `scripts/publish-playstore.py` warns on this drift.

### 3. Prepare listing assets

No `store-assets/` at `HEAD` — export from drawables or regenerate:

| Asset | Source / how to produce |
|---|---|
| App icon (512 px) | `app/src/main/res/drawable/ic_launcher_512.png` + `mipmap-*/ic_launcher.png`; export 512 px for console |
| Feature graphic (1280×720) | `app/src/main/res/drawable/banner.png` / `tv_banner.png` / `gp_logo_bk_bg.png` |
| Screenshots (1920×1080) | Run `python3 scripts/amazon_review_screenshots.py` → `amazon-review-screenshots/` (also `for-amazon/` curated 01–08) |
| Play listing | `docs/playstore-automation.md` — Store listing, Data safety, Content rating, App access (`+100` / `12345`) |

To regenerate screenshots on a connected TV emulator:

```bash
python3 scripts/amazon_review_screenshots.py
```

Outputs land in `amazon-review-screenshots/`. The script expects `emulator-5554` by default; edit `SER` in the script if needed.

### 3b. Google Play upload (optional)

One-command local (needs `keystore.properties` + `local.properties` + signing):

```bash
python3 scripts/publish-playstore.py --upload --track internal   # needs play-service-account.json
# or directly:
gem install fastlane   # or bundle install (Gemfile)
fastlane android deploy track:internal
fastlane android deploy track:closed
fastlane android deploy track:production
```

Enable once: Play Console → Setup → API access → Link Cloud project → Enable Play Developer API → Create Service Account (Release manager) → Invite email in Users and permissions → Download JSON → `play-service-account.json` (gitignored via `*.json`). CI via `.github/workflows/playstore.yml` (`LOCAL_PROPERTIES`, `KEYSTORE_BASE64`, `KEYSTORE_PROPERTIES`, `PLAY_SERVICE_ACCOUNT_JSON` secrets; trigger `git tag v3.4.1 && git push origin v3.4.1` or manual dispatch). Full guide: `docs/playstore-automation.md`.

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

No `store-assets/amazon_test_instructions.txt` at `HEAD` (deleted Sept 2026). For Amazon **Testing Instructions**, paste the review login (`+100` / `12345`) + 5–10 min flow: Onboarding → Login → Dashboard → Chats → Messages → Details → Download → Play (VLC) → History. The same creds apply for Play Console → App access.

### 5. Pre-submission checklist

- [ ] `keystore.properties` configured and release signed (verify: `apksigner verify --print-certs app/build/outputs/apk/release/*.apk` or `jarsigner -verify` for AAB; `scripts/publish-playstore.py` also verifies)
- [ ] Sanitization tasks ran without errors (check Gradle output for `sanitizeForAmazonAppstore` / `sanitizeReleaseDex`)
- [ ] Privacy policy and terms URLs are live and referenced in `TermsActivity.kt` (host `privacy-policy.html` via GitHub Pages)
- [ ] Listing screenshots uploaded (1920×1080 landscape for TV) — regenerate via `scripts/amazon_review_screenshots.py` if needed
- [ ] Feature graphic and app icon uploaded (from `drawable/banner.png` / `ic_launcher_512.png`)
- [ ] Test credentials (`+100` / `12345`) pasted into Amazon Testing Instructions and Play → App access
- [ ] Smoke test passes on a Fire TV or Android TV emulator:
  ```bash
  python3 scripts/full_app_test.py
  ```

### 6. Common review issues

| Issue | Fix |
|---|---|
| Automated scan flags TDLib as ad SDK | Rebuild release so sanitization tasks run; do not skip `preBuild` |
| Blank grids after fresh install | Seed is now `assets/seed/test_seed.json` (no `DatabaseSeeder`); just login with `+100` / `12345` — old 30–60 s wait no longer applies |
| Download or Play button missing | Details uses a 3-state button model: `FRESH` (Download) → `DOWNLOADING` (Cancel) → `READY` (Play + Resume). Confirm network; wait for 100% or Auto-Play thresholds (progress % / buffer MB in Settings) |
| Login rejected | Use exactly country `1`, phone `00`, code `12345` (`+100`) |
| VLC not launching | Install VLC (`org.videolan.vlc`) — `PlayerHelper.isVlcInstalled()` checks `<queries>` visibility; `VlcPlaybackTracker` resumes via `SettingsDataStore` bookmark |
| Play upload rejected (versionCode) | `versionCode` must increase each upload — use `scripts/publish-playstore.py --bump patch` |
| Play warning targetSdk drift | Align `AndroidManifest.xml` `targetSdkVersion` to `36` or remove `<uses-sdk>`; `publish-playstore.py` flags `34 != 36` |

## Roadmap

Planned features are tracked in [`ROADMAP.md`](ROADMAP.md) — grouped Quick wins / Medium / Big bets. Open a **Feature Request** (`Issues → New issue`) or 👍 an item to vote. Suggested first: Progress bar on cards → Auto-play next → Watchlist.

## Contributing

Contributions are welcome. Please open an issue or submit a pull request. For bugs and ideas, use the templates: `Issues → New issue → Bug report / Feature request`.

## References

- [TDLib documentation](https://core.telegram.org/tdlib)
- [Telegram API](https://core.telegram.org/api)
- [Android Leanback](https://developer.android.com/training/tv/playback/leanback)
- [TMDB API](https://developer.themoviedb.org/docs)

## License

This project is licensed under the MIT License. See the `LICENSE` file for details.