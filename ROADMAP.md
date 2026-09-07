# Roadmap

> Vote: open a Feature Request (Issues → New issue → Feature request) or 👍 an item below.

## Quick wins (1–3 days)

- [ ] **Progress bar on cards** — show `HistoryStore` bookmark (`savedPositionMs`) on Continue Watching / History grids.
- [ ] **Auto-play next** — queue next `MediaMessage` in same chat on `VlcPlaybackTracker` finish (end-clear 5s).
- [ ] **Global Search** — `Leanback SearchFragment` over `JsonSeedStore` + TMDB search + TDLib `searchMessages`.
- [ ] **Watchlist / Favorites** — per-login JSON like `HistoryStore` (heart on `MediaDetailsActivity`), new Dashboard row.
- [ ] **Filters** — by `ReleaseTitleParser` (year / `1080p`/`4K` / `WEB-DL`) + TMDB genre; chip row on `MessageGridFragment`.
- [ ] **Trailer row** — expose `PosterFetcher.trailerKey()` → YouTube intent grid on Details.
- [ ] **Subtitle picker** — pass `EXTRA_SUBTITLES` to VLC, auto-fetch via OpenSubtitles + TMDB `imdb_id`.

## Medium (1–2 weeks)

- [ ] **Up Next / Recently Added** — Dashboard rows: unwatched + latest by `visitedAt`.
- [ ] **Cast explorer** — tap cast chip → TMDB person → their movies grid.
- [ ] **Voice search** — `SpeechRecognizer` → same search backend.
- [ ] **Profiles switcher** — reuse `history_{login}.json` per-login, switch `UserSession` without logout.
- [ ] **Download manager screen** — full `ActiveDownloadManager` + `DownloadProgressTracker` UI (pause/cancel all, storage bar).
- [ ] **Smart categories** — auto rows by TMDB genre/year from `PosterFetcher` cache.
- [ ] **Parental PIN** — lock chats by `Chat.id` via `SettingsDataStore`.

## Big bets

- [ ] **Recommendations** — "Because you watched X" via TMDB `similar`/`recommendations` from top 3 `HistoryStore`.
- [ ] **Intro / Skip chapter** — skip 10s / Intro via `VLC EXTRA_START_TIME`.
- [ ] **Stats** — time watched, top studio, heatmap from `HistoryStore`.

## Suggested order

Ship `Progress bar` → `Auto-play next` → `Watchlist` first — reuse `HistoryStore` + `VlcPlaybackTracker`, no new deps.

Want one? Open a Feature Request and reference the item.
