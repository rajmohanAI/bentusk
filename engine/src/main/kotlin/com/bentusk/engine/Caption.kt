package com.bentusk.engine

/** Caption rules shared by the composer and, later, the post analyser. */
object Caption {
    private val HashtagPattern = Regex("""#[\p{L}\p{M}\p{N}_]+""")

    fun hashtags(text: String): List<String> =
        HashtagPattern.findAll(text).map { it.value.lowercase() }.distinct().toList()

    fun canAddHashtag(text: String): Boolean = hashtags(text).size < InstagramSpec.MAX_HASHTAGS

    /**
     * Adds [tag] on the hashtag line at the end of the caption, or removes it if it is already there.
     * Adding stops at Instagram's hashtag limit.
     */
    fun toggleHashtag(text: String, tag: String): String {
        val normalised = "#" + tag.trimStart('#').lowercase()
        if (normalised in hashtags(text)) {
            return text.replace(Regex("""${Regex.escape(normalised)}(?![\p{L}\p{M}\p{N}_])[ \t]*""", RegexOption.IGNORE_CASE), "")
                .replace(Regex("""[ \t]+\n"""), "\n")
                .trimEnd()
        }
        if (!canAddHashtag(text)) return text
        val body = text.trimEnd()
        val lastLine = body.substringAfterLast('\n')
        val onTagLine = lastLine.isNotBlank() && lastLine.trim().split(Regex("""\s+""")).all { it.startsWith("#") }
        return when {
            body.isEmpty() -> normalised
            onTagLine -> "$body $normalised"
            else -> "$body\n\n$normalised"
        }
    }

    fun isTooLong(text: String) = text.length > InstagramSpec.CAPTION_MAX_CHARS
}
