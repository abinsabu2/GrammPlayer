package com.aes.grammplayer.provider

/**
 * One page of lazily-loaded items.
 *
 * @param items       the rows fetched for this page
 * @param nextOffset  offset to pass to the next local (Room) page request
 * @param nextCursor  cursor (e.g. TDLib fromMessageId) for the next remote page request
 * @param endReached  true when no further pages remain
 */
data class Page<T>(
    val items: List<T>,
    val nextOffset: Int,
    val nextCursor: Long,
    val endReached: Boolean
)
