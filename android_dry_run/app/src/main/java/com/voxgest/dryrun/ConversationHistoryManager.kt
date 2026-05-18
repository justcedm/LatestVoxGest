package com.voxgest.dryrun

enum class ConversationSource {
    SIGNED,
    HEARD,
    SHORTCUT,
    EMERGENCY
}

data class ConversationMessage(
    val id: Long,
    val source: ConversationSource,
    val text: String,
    val detail: String,
    val timestampMillis: Long
)

object ConversationHistoryManager {
    private val messages = mutableListOf<ConversationMessage>()
    private val listeners = mutableSetOf<() -> Unit>()
    private var nextId = 1L

    fun addSigned(text: String) {
        add(ConversationSource.SIGNED, text, "spoken")
    }

    fun addHeard(text: String, avatarSequence: String) {
        add(ConversationSource.HEARD, text, avatarSequence)
    }

    fun addShortcut(text: String, emergency: Boolean, avatarSequence: String) {
        add(if (emergency) ConversationSource.EMERGENCY else ConversationSource.SHORTCUT, text, avatarSequence)
    }

    fun messages(): List<ConversationMessage> = messages.toList()

    fun clear() {
        messages.clear()
        notifyChanged()
    }

    fun addListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    private fun add(source: ConversationSource, text: String, detail: String) {
        val cleanText = text.trim()
        if (cleanText.isBlank()) return
        messages.add(
            0,
            ConversationMessage(
                id = nextId++,
                source = source,
                text = cleanText,
                detail = detail,
                timestampMillis = System.currentTimeMillis()
            )
        )
        notifyChanged()
    }

    private fun notifyChanged() {
        listeners.toList().forEach { it.invoke() }
    }
}
