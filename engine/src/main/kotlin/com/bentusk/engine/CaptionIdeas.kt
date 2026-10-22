package com.bentusk.engine

/**
 * Writes caption suggestions on the phone from what the photos show, without a language model.
 * Photo labels pick a theme; the theme supplies words in each language; the tone supplies sentence shapes.
 * A cloud model can later replace [write] behind the same inputs.
 */
object CaptionIdeas {

    enum class Tone { Fun, Minimal, Poetic, Business }

    enum class Language { English, Hindi, Hinglish }

    enum class Theme { Travel, Food, People, Fashion, Nature, City, Pets, Celebration, Everyday }

    data class Request(
        val labels: List<String>,
        val tone: Tone,
        val language: Language,
        val emoji: Boolean,
        /** Slides in the post; carousels get a question that invites a reply. */
        val slides: Int = 1,
        /** Changes which sentences are picked, so "write again" gives fresh ideas. */
        val variant: Int = 0,
    )

    private val ThemeWords: Map<Theme, List<String>> = mapOf(
        Theme.Travel to listOf("travel", "beach", "sea", "ocean", "mountain", "lake", "sky", "sunset", "boat", "vacation", "hiking", "desert", "road", "temple", "monument", "waterfall"),
        Theme.Food to listOf("food", "dish", "cuisine", "coffee", "cup", "tea", "dessert", "cake", "bread", "pizza", "fruit", "meal", "drink", "baking", "breakfast"),
        Theme.People to listOf("person", "people", "smile", "selfie", "fun", "crowd", "child", "friends", "team", "hand", "skin", "hair"),
        Theme.Fashion to listOf("fashion", "clothing", "dress", "jacket", "shoe", "jeans", "sunglasses", "jewellery", "jewelry", "handbag", "saree", "textile", "hat"),
        Theme.Nature to listOf("flower", "plant", "tree", "garden", "forest", "leaf", "grass", "petal", "field", "nature", "rain"),
        Theme.City to listOf("building", "city", "skyscraper", "street", "car", "bridge", "night", "architecture", "tower", "metro"),
        Theme.Pets to listOf("dog", "cat", "pet", "puppy", "bird", "animal"),
        Theme.Celebration to listOf("party", "wedding", "cake", "balloon", "fireworks", "event", "festival", "christmas", "diwali", "candle"),
    )

    /** The theme most of the labels point to, or [Theme.Everyday] when nothing matches. */
    fun theme(labels: List<String>): Theme {
        val lower = labels.map { it.lowercase() }
        return ThemeWords.entries
            .map { (theme, words) -> theme to lower.count { label -> words.any { label.contains(it) } } }
            .filter { it.second > 0 }
            .maxByOrNull { it.second }?.first ?: Theme.Everyday
    }

    /** Hashtag topic ids (see [HashtagSets]) that suit a theme. */
    fun hashtagTopic(theme: Theme): String = when (theme) {
        Theme.Travel -> "travel"
        Theme.Food -> "food"
        Theme.Fashion -> "fashion"
        Theme.Nature, Theme.City -> "photography"
        Theme.People, Theme.Pets, Theme.Celebration, Theme.Everyday -> "life"
    }

    private class Words(val thing: String, val emoji: String)

    private fun words(theme: Theme, language: Language): Words = when (language) {
        Language.English -> when (theme) {
            Theme.Travel -> Words("this view", "🌅")
            Theme.Food -> Words("this plate", "🍽️")
            Theme.People -> Words("these faces", "😊")
            Theme.Fashion -> Words("this fit", "🖤")
            Theme.Nature -> Words("the little green things", "🌿")
            Theme.City -> Words("city lights", "🌃")
            Theme.Pets -> Words("this furry boss", "🐾")
            Theme.Celebration -> Words("tonight", "🎉")
            Theme.Everyday -> Words("ordinary days", "✨")
        }
        Language.Hindi -> when (theme) {
            Theme.Travel -> Words("यह नज़ारा", "🌅")
            Theme.Food -> Words("यह स्वाद", "🍽️")
            Theme.People -> Words("ये मुस्कानें", "😊")
            Theme.Fashion -> Words("यह अंदाज़", "🖤")
            Theme.Nature -> Words("यह हरियाली", "🌿")
            Theme.City -> Words("शहर की रौशनी", "🌃")
            Theme.Pets -> Words("यह नन्हा दोस्त", "🐾")
            Theme.Celebration -> Words("यह जश्न", "🎉")
            Theme.Everyday -> Words("ये आम दिन", "✨")
        }
        Language.Hinglish -> when (theme) {
            Theme.Travel -> Words("yeh view", "🌅")
            Theme.Food -> Words("yeh khana", "🍽️")
            Theme.People -> Words("yeh log", "😊")
            Theme.Fashion -> Words("yeh look", "🖤")
            Theme.Nature -> Words("yeh hariyali", "🌿")
            Theme.City -> Words("city ki lights", "🌃")
            Theme.Pets -> Words("yeh chhota boss", "🐾")
            Theme.Celebration -> Words("aaj ki raat", "🎉")
            Theme.Everyday -> Words("normal din", "✨")
        }
    }

    private fun shapes(tone: Tone, language: Language): List<String> = when (language) {
        Language.English -> when (tone) {
            Tone.Fun -> listOf(
                "Plot twist: {thing} stole the show {e}",
                "Not me falling for {thing} again {e}",
                "Current status: completely lost in {thing} {e}",
                "Rate {thing} out of 10. I'll wait {e}",
                "Proof that {thing} never misses {e}",
            )
            Tone.Minimal -> listOf(
                "Lately: {thing}. {e}",
                "{Thing}, nothing else. {e}",
                "More {thing}, less noise. {e}",
                "Saving {thing} for later. {e}",
            )
            Tone.Poetic -> listOf(
                "Some days are written in {thing} {e}",
                "Found a little peace in {thing} {e}",
                "Let {thing} tell the story this time {e}",
                "Soft light, slow hours and {thing} {e}",
            )
            Tone.Business -> listOf(
                "Meet {thing}, made with care for you {e} Tap the link in bio to see more.",
                "{Thing} is here {e} DM us to order yours.",
                "Swipe to see {thing} up close {e} Which one would you pick?",
                "Behind every post: real work and {thing} {e} Thank you for the support!",
            )
        }
        Language.Hindi -> when (tone) {
            Tone.Fun -> listOf(
                "सच बताऊँ? {thing} ने दिल जीत लिया {e}",
                "फिर से {thing} पर फ़िदा {e}",
                "{thing} को 10 में से कितने नंबर? {e}",
                "आज का मूड: बस {thing} {e}",
            )
            Tone.Minimal -> listOf(
                "आजकल: {thing}। {e}",
                "बस {thing}। {e}",
                "थोड़ा {thing}, थोड़ा सुकून। {e}",
            )
            Tone.Poetic -> listOf(
                "कुछ दिन {thing} में लिखे जाते हैं {e}",
                "{thing} में थोड़ा सुकून मिल गया {e}",
                "इस बार कहानी {thing} कहेगा {e}",
            )
            Tone.Business -> listOf(
                "पेश है {thing}, आपके लिए प्यार से बना {e} ज़्यादा जानने के लिए बायो का लिंक देखें।",
                "{thing} अब उपलब्ध है {e} ऑर्डर के लिए DM करें।",
                "स्वाइप करें और {thing} करीब से देखें {e} आपको कौन सा पसंद आया?",
            )
        }
        Language.Hinglish -> when (tone) {
            Tone.Fun -> listOf(
                "Sach bolun? {thing} ne dil jeet liya {e}",
                "Phir se {thing} pe fida {e}",
                "{Thing} ko 10 mein se kitne? {e}",
                "Aaj ka mood: bas {thing} {e}",
            )
            Tone.Minimal -> listOf(
                "Aajkal: {thing}. {e}",
                "Bas {thing}. {e}",
                "Thoda {thing}, thoda sukoon. {e}",
            )
            Tone.Poetic -> listOf(
                "Kuch din {thing} mein likhe jaate hain {e}",
                "{Thing} mein thoda sukoon mil gaya {e}",
                "Is baar kahani {thing} sunayega {e}",
            )
            Tone.Business -> listOf(
                "Pesh hai {thing}, aapke liye pyaar se bana {e} Link bio mein hai.",
                "{Thing} ab available hai {e} Order ke liye DM karein.",
                "Swipe karke {thing} close se dekho {e} Aapka favourite kaunsa?",
            )
        }
    }

    private fun question(language: Language): String = when (language) {
        Language.English -> "Which slide is your favourite?"
        Language.Hindi -> "आपकी पसंदीदा स्लाइड कौन सी है?"
        Language.Hinglish -> "Aapki favourite slide kaunsi hai?"
    }

    /** Three different captions for the request, ready to edit. */
    fun write(request: Request): List<String> {
        val theme = theme(request.labels)
        val w = words(theme, request.language)
        val shapes = shapes(request.tone, request.language)
        val start = Math.floorMod(request.variant * 3 + theme.ordinal, shapes.size)
        return List(minOf(3, shapes.size)) { i ->
            val shape = shapes[(start + i) % shapes.size]
            var text = shape
                .replace("{Thing}", w.thing.replaceFirstChar { it.uppercase() })
                .replace("{thing}", w.thing)
                .replace("{e}", if (request.emoji) w.emoji else "")
            text = text.replace(Regex(" +([।.!?])"), "$1").replace(Regex(" {2,}"), " ").trim()
            // Questions already invite replies; the others get one when there are slides to compare.
            if (request.slides > 1 && request.tone != Tone.Business && i == 0) text += "\n\n" + question(request.language)
            text
        }
    }
}
