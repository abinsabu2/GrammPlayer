package com.aes.grammplayer.helper

/**
 * Shared LibVLC startup options for preview and in-app full-screen playback.
 */
internal object VlcPlaybackOptions {

    fun build(hwDecode: Boolean): ArrayList<String> =
        arrayListOf(
            "--intf", "dummy",
            "--no-video-title-show",
            "--no-stats"
        ).apply {
            if (!hwDecode) {
                add("--hw-decoder=none")
            }
        }
}