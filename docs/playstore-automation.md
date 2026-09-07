# Play Store automation

## What was added

| File | Purpose |
|---|---|
| `scripts/publish-playstore.py` | rung 1: local one-command build + validate + optional upload |
| `fastlane/Fastfile` + `Appfile` + `Gemfile` | rung 2: Play upload via `supply` (no app/build.gradle change) |
| `.github/workflows/playstore.yml` | rung 3: tag/manual GitHub Actions CI |

## Local one-command (no service account needed)

```bash
# checks + builds signed AAB (needs keystore.properties + local.properties)
python3 scripts/publish-playstore.py

# bump version + build
python3 scripts/publish-playstore.py --bump patch   # 3.3 -> 3.3.1, code 303 -> 304
python3 scripts/publish-playstore.py --bump minor   # 3.3 -> 3.4
python3 scripts/publish-playstore.py --upload --track internal  # + upload if play-service-account.json present
```

Script does:
- validates `local.properties` (api_key/api_hash/tmbd_key)
- validates `keystore.properties` + storeFile exists (Play rejects unsigned)
- warns if `AndroidManifest.xml` targetSdk 34 != `app/build.gradle.kts` 36
- runs `sanitize-for-amazon-appstore.py` + `./gradlew :app:bundleRelease`
- verifies with `jarsigner -verify` + optional `bundletool validate`
- if `--upload` and `play-service-account.json` present, runs `fastlane supply`

## Enable Play API upload (once)

1. Play Console > Setup > API access > Link Google Cloud project > Enable Play Developer API
2. Create Service Account > Grant `Admin (all permissions)` or at least Release manager
3. Invite service account email in Play Console > Users and permissions
4. Download JSON -> `play-service-account.json` in project root (gitignored via `*.json`)
5. Local upload:
   ```bash
   gem install fastlane   # or bundle install
   python3 scripts/publish-playstore.py --upload --track internal
   # or directly
   fastlane android deploy track:internal
   fastlane android deploy track:closed
   fastlane android deploy track:production
   ```

## CI setup (optional)

Secrets in GitHub > Settings > Secrets and variables > Actions:

- `LOCAL_PROPERTIES` — content of local.properties (without sdk.dir)
- `KEYSTORE_BASE64` — `base64 -i grammplayer-release.keystore | pbcopy`
- `KEYSTORE_PROPERTIES` — content of keystore.properties
- `PLAY_SERVICE_ACCOUNT_JSON` — content of play-service-account.json

Triggers:
- `git tag v3.3.1 && git push origin v3.3.1` -> builds AAB + uploads to `internal`
- Actions tab > Play Store Release > Run workflow -> choose track/bump

## Play checklist (still manual, first time)

Create app `com.aes.grammplayer` if not exists, then fill:
- Store listing: icon `store-assets/app_icon_512.png`, feature `promo_banner_1024x500.png`, TV screenshots `screenshots_1920x1080/`, banner drawable
- Privacy URL: host `privacy-policy.html` via GitHub Pages
- Data safety, content rating, target audience, App access (test creds: +100 / 12345)
- Closed testing: 20 testers x 14 days required for new personal accounts before Production

## Notes

- `sanitize-for-amazon-appstore.py` stays harmless for Play; keep.
- `app/build.gradle.kts` versionCode 303 / versionName 3.3 — bump on each upload (Play rejects reused code).
- Manifest `targetSdkVersion 34` should be removed or bumped to 36 — build.gradle already drives it via AGP 9.
