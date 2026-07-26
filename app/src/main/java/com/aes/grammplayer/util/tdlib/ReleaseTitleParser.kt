package com.aes.grammplayer.util.tdlib

data class ReleaseInfo(
    val groupTag: String?,      // "KC"
    val title: String,          // "Blast"
    val year: Int?,             // 2026
    val resolution: String?,    // "720p"
    val service: String?,       // "NF"
    val source: String?,        // "WEB-DL"
    val audioCodec: String?,    // "AAC5.1"
    val videoCodec: String?,    // "H.265"
    val releaseGroup: String?,  // "CPTN5DW"
    val container: String?      // "mkv"
) {
    val displayTitle: String
        get() = year?.let { "$title ($it)" } ?: title
}

object ReleaseTitleParser {

    private val CONTAINER = Regex("""\b(mkv|mp4|avi|mov|ts|m4v)\b$""", RegexOption.IGNORE_CASE)
    private val BRACKET_TAG = Regex("""^\[([^]]+)]\s*""")
    private val YEAR = Regex("""\b(19|20)\d{2}\b""")
    private val PAREN_YEAR = Regex("""\((19|20)\d{2}\)""")
    private val RESOLUTION = Regex("""\b(4320p|2160p|1440p|1080p|720p|576p|480p)\b""", RegexOption.IGNORE_CASE)
    private val SERVICE = Regex("""\b(NF|AMZN|DSNP|HULU|ATVP|HMAX|PCOK|STAN)\b""", RegexOption.IGNORE_CASE)
    private val SOURCE = Regex("""\bWEB[\s-]?DL\b|\bWEBRip\b|\bBluRay\b|\bBDRip\b|\bHDTV\b|\bDVDRip\b""", RegexOption.IGNORE_CASE)
    private val AUDIO = Regex("""\b(AAC\d(?:[\s.]\d)?|DTS(?:-HD)?|E?AC3|FLAC|MP3)\b""", RegexOption.IGNORE_CASE)
    private val VIDEO_CODEC = Regex("""\bH[\s.]?26[45]\b|\bx26[45]\b|\bHEVC\b""", RegexOption.IGNORE_CASE)

    private fun String.trimSeparators(): String =
        trim().trim('.', '-', '_', ' ', '(', ')')

    fun parse(raw: String): ReleaseInfo {
        // Normalize separators first so dots/underscores don't leak into any field
        var s = raw.trim()
            .replace(Regex("""[._]+"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()

        val container = CONTAINER.find(s)?.groupValues?.get(1)
        s = CONTAINER.replace(s, "").trim()

        val groupTag = BRACKET_TAG.find(s)?.groupValues?.get(1)
        s = BRACKET_TAG.replace(s, "")

        // Prefer an explicit "(YYYY)" if present — treat it as authoritative and
        // strip the whole parenthesized chunk so the title cut lands cleanly.
        val parenYear = PAREN_YEAR.find(s)
        val yearMatch = parenYear ?: YEAR.find(s)
        val year = Regex("""(19|20)\d{2}""").find(yearMatch?.value ?: "")?.value?.toIntOrNull()

        val resolution = RESOLUTION.find(s)?.value
        val service = SERVICE.find(s)?.value
        val source = SOURCE.find(s)?.value?.replace(Regex("""\s"""), "-")
        val audio = AUDIO.find(s)?.value?.replace(Regex("""(\d)\s(\d)"""), "$1.$2")
        val video = VIDEO_CODEC.find(s)?.value?.replace(Regex("""H\s?(26[45])"""), "H.$1")

        val titleEnd = yearMatch?.range?.first ?: RESOLUTION.find(s)?.range?.first ?: s.length
        val title = s.substring(0, titleEnd).trimSeparators()

        var remainder = s
        listOf(RESOLUTION, SERVICE, SOURCE, AUDIO, VIDEO_CODEC).forEach {
            remainder = it.replace(remainder, "")
        }
        yearMatch?.let { remainder = remainder.replaceFirst(it.value, "") }
        remainder = remainder.replace(title, "").trimSeparators()
        val releaseGroup = remainder.split(Regex("""\s+"""))
            .lastOrNull { it.isNotBlank() }

        return ReleaseInfo(groupTag, title, year, resolution, service, source, audio, video, releaseGroup, container)
    }
}
