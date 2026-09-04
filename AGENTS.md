# Agent notes (GrammPlayer)

## External VLC playback (TV)

Playback of local Telegram downloads uses **external VLC**, not an in-app player.

**Do not** launch VLC with `startActivityForResult` / `registerForActivityResult` to capture `extra_position`. On TV that returns our activity to the front; VLC's `VideoPlayerActivity` `finish()`es in `onStop` and the movie dies after the first frame.

**Do** launch with `PlayerHelper.play()` (`FLAG_ACTIVITY_NEW_TASK`) and restore Resume via `VlcPlaybackTracker` + `SettingsDataStore`.

Full invariant, extras, files, and verification:

→ [`docs/agents/vlc-external-playback.md`](docs/agents/vlc-external-playback.md)
