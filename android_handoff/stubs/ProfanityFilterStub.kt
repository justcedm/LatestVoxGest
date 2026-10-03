package com.voxgest.handoff.pending

/** Handoff-only boundary to invoke immediately before text-to-speech. */
fun interface ProfanityFilter {
    fun sanitize(text: String): String
}
/**
 * Placeholder only: this implementation performs no filtering.
 * Replace it with a reviewed policy before claiming profanity protection.
 */
object PassThroughProfanityFilterStub : ProfanityFilter {
    override fun sanitize(text: String): String = text
}
