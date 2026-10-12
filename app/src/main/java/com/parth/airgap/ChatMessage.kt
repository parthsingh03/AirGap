package com.parth.airgap

/** Display kind of a chat row — drives the adapter's view type. */
enum class ChatKind { MINE, THEIRS, SYSTEM }

/**
 * One row in the chat room.
 *
 * @param sender nickname label for chat messages ("" for system lines).
 * @param text   the message body, or the full system line text.
 * @param kind   MINE = sent from this phone, THEIRS = received, SYSTEM = room event.
 * @param viaRelay true when this received message arrived after at least one
 *                 mesh-relay hop (its wire ttl was below the initial value).
 */
data class ChatMessage(
    val sender: String,
    val text: String,
    val kind: ChatKind,
    val viaRelay: Boolean = false
) {
    /** Convenience flag kept for readability at display call sites. */
    val mine: Boolean get() = kind == ChatKind.MINE
    val system: Boolean get() = kind == ChatKind.SYSTEM
}
