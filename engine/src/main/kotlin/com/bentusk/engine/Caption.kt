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

    /** An invisible character Instagram keeps, so a line holding only it is not dropped. */
    const val BLANK_LINE = "\u2800"

    /**
     * Makes paragraph spacing survive pasting into Instagram, which drops empty lines and trailing spaces:
     * trailing spaces are trimmed and each empty line between paragraphs gets an invisible character.
     * Leading and trailing empty lines are removed, since Instagram drops those anyway.
     */
    fun keepLineBreaks(text: String): String {
        val lines = text.replace("\r\n", "\n").split('\n').map { it.trimEnd() }
        val first = lines.indexOfFirst { it.isNotEmpty() }
        if (first < 0) return ""
        val last = lines.indexOfLast { it.isNotEmpty() }
        return lines.subList(first, last + 1).joinToString("\n") { it.ifEmpty { BLANK_LINE } }
    }
}

/** Bundled hashtag ideas by topic, so the composer works offline. Each set is small on purpose: Instagram allows five. */
object HashtagSets {
    data class Topic(val id: String, val name: String, val tags: List<String>)

    val Topics: List<Topic> = listOf(
        Topic("travel", "Travel", listOf("#travel", "#wanderlust", "#travelgram", "#incredibleindia", "#roadtrip", "#explore", "#mountains", "#beach")),
        Topic("food", "Food", listOf("#food", "#foodie", "#homecooking", "#streetfood", "#foodphotography", "#indianfood", "#dessert", "#coffee")),
        Topic("fashion", "Fashion", listOf("#ootd", "#fashion", "#style", "#streetstyle", "#ethnicwear", "#outfitinspo", "#saree", "#sneakers")),
        Topic("fitness", "Fitness", listOf("#fitness", "#workout", "#gym", "#yoga", "#running", "#healthylifestyle", "#fitindia", "#cycling")),
        Topic("photography", "Photography", listOf("#photography", "#photooftheday", "#mobilephotography", "#landscape", "#portrait", "#streetphotography", "#goldenhour", "#carousel")),
        Topic("business", "Business", listOf("#smallbusiness", "#shoplocal", "#newarrivals", "#handmade", "#entrepreneur", "#brand", "#supportsmallbusiness", "#sale")),
        Topic("art", "Art & design", listOf("#art", "#design", "#illustration", "#artist", "#graphicdesign", "#minimal", "#interiordesign", "#creative")),
        Topic("life", "Everyday", listOf("#photodump", "#weekend", "#friends", "#family", "#memories", "#lifelately", "#sundayvibes", "#celebration")),
    )
}
