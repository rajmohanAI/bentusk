package com.bentusk.engine

import kotlin.math.pow

/** One named colour of a palette. */
data class Swatch(val name: String, val argb: Int) {
    /** "#CBB3E8", as designers write it. */
    val hex: String get() = "#%06X".format(argb and 0xFFFFFF)
}

/** The feel of a palette, for browsing. */
enum class PaletteMood { Bold, Pastel, Earthy, Luxe, Fresh, Moody, Retro, Neon, Classic }

/**
 * A ready-made colour scheme for posts without a background photo. The first swatch is the background;
 * the rest are for text, labels and accents.
 */
data class Palette(val id: String, val name: String, val mood: PaletteMood, val swatches: List<Swatch>) {
    init {
        require(swatches.size >= 3) { "A palette needs a background and at least two other colours" }
    }

    val background: Swatch get() = swatches.first()

    /** The colour that reads best on the background, used for plain text. */
    val ink: Swatch get() = swatches.drop(1).maxBy { Contrast.ratio(background.argb, it.argb) }

    /**
     * A label pill and the text on it. Prefers an accent colour that stands out from the background and still
     * holds readable text; otherwise the pill is the ink with background-coloured text.
     */
    val pill: Pair<Swatch, Swatch>
        get() {
            val accents = swatches.drop(1).filter { it != ink }
            val accent = accents
                .filter { Contrast.ratio(it.argb, onPill(it).argb) >= Palettes.MIN_TEXT_CONTRAST }
                .maxByOrNull { Contrast.ratio(background.argb, it.argb) }
                ?: return ink to background
            return accent to onPill(accent)
        }

    private fun onPill(pill: Swatch): Swatch = swatches.filter { it != pill }.maxBy { Contrast.ratio(pill.argb, it.argb) }
}

/** WCAG 2 contrast, which decides which palette colours can carry text on which. */
object Contrast {
    fun luminance(argb: Int): Double {
        fun channel(shift: Int): Double {
            val c = (argb shr shift and 0xFF) / 255.0
            return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
    }

    /** From 1 (no contrast) to 21 (black on white). */
    fun ratio(a: Int, b: Int): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }
}

/** The palette library: over sixty schemes across nine moods, each with text that passes WCAG AA on its background. */
object Palettes {
    /** WCAG AA for normal-size text. */
    const val MIN_TEXT_CONTRAST = 4.5

    fun byId(id: String): Palette? = All.firstOrNull { it.id == id }

    fun of(mood: PaletteMood?): List<Palette> = if (mood == null) All else All.filter { it.mood == mood }

    /**
     * Recolours [design] with [palette]: the background becomes the palette's background, plain text its ink,
     * and labelled text its pill. Photos are left as they are. The palette is remembered so the text tools can
     * offer its colours.
     */
    fun apply(design: Design, palette: Palette): Design {
        val ink = palette.ink.argb
        val (pill, onPill) = palette.pill
        val layers = design.layers.map { layer ->
            when {
                layer !is TextLayer -> layer
                layer.pill != null -> layer.copy(argb = onPill.argb, pill = pill.argb)
                else -> layer.copy(argb = ink)
            }
        }
        return design.copy(fill = Fill(palette.background.argb), layers = layers, palette = palette.id)
    }

    private fun palette(name: String, mood: PaletteMood, vararg colors: Pair<String, Long>) = Palette(
        id = name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-'),
        name = name,
        mood = mood,
        swatches = colors.map { (n, c) -> Swatch(n, c.toInt()) },
    )

    val All: List<Palette> = listOf(
        // Bold
        palette("Creative Momentum", PaletteMood.Bold, "Lavender" to 0xFFCBB3E8, "Chartreuse" to 0xFFD4FF3F, "Tomato Red" to 0xFFE34234, "Ink" to 0xFF1B1C20),
        palette("Pop Art", PaletteMood.Bold, "Cobalt" to 0xFF1F4FD8, "Sunflower" to 0xFFFFD23F, "Hot Pink" to 0xFFFF4F9A, "White" to 0xFFFFFFFF),
        palette("Citrus Punch", PaletteMood.Bold, "Tangerine" to 0xFFFF8A00, "Lemon" to 0xFFFFE45C, "Grape" to 0xFF4A1F72, "Cream" to 0xFFFFF8E7),
        palette("Electric Coral", PaletteMood.Bold, "Coral" to 0xFFFF6F59, "Teal" to 0xFF0E7C7B, "Butter" to 0xFFFFE7A0, "Ink" to 0xFF172121),
        palette("Primary Play", PaletteMood.Bold, "Off White" to 0xFFFAF7F0, "Red" to 0xFFD7263D, "Blue" to 0xFF1B98E0, "Yellow" to 0xFFF4D35E, "Ink" to 0xFF1A1A1A),
        palette("Berry Bold", PaletteMood.Bold, "Magenta" to 0xFFC2185B, "Blush" to 0xFFFFE3EE, "Navy" to 0xFF14213D, "Gold" to 0xFFF9C846),
        palette("Jungle Pop", PaletteMood.Bold, "Emerald" to 0xFF00A86B, "Pink" to 0xFFFF9AC1, "Orange" to 0xFFFF7F11, "Night" to 0xFF0B1D1A),
        // Pastel
        palette("Sorbet", PaletteMood.Pastel, "Peach" to 0xFFFFD6C2, "Mint" to 0xFFC9F2E1, "Lilac" to 0xFFDCCBFF, "Plum" to 0xFF4A2C5E),
        palette("Cotton Candy", PaletteMood.Pastel, "Baby Pink" to 0xFFFFD1E3, "Sky" to 0xFFC6E2FF, "Lemon" to 0xFFFFF4B8, "Ink" to 0xFF2B2440),
        palette("Spring Morning", PaletteMood.Pastel, "Pistachio" to 0xFFD8EFC4, "Rose" to 0xFFF7C6CE, "Butter" to 0xFFFCEBB6, "Forest" to 0xFF2F4A35),
        palette("Lavender Haze", PaletteMood.Pastel, "Lavender" to 0xFFE6DDF8, "Periwinkle" to 0xFFB9C2F2, "Blush" to 0xFFF6D3E1, "Grape" to 0xFF3D2C5E),
        palette("Seafoam", PaletteMood.Pastel, "Seafoam" to 0xFFCFEFE6, "Aqua" to 0xFF9ED9D8, "Sand" to 0xFFF4E4C9, "Deep Teal" to 0xFF124E4E),
        palette("Peach Fuzz", PaletteMood.Pastel, "Peach Fuzz" to 0xFFFFBE98, "Cream" to 0xFFFFF3E6, "Rose" to 0xFFF28A8A, "Cocoa" to 0xFF4B2E26),
        palette("Macaron", PaletteMood.Pastel, "Vanilla" to 0xFFFFF5DA, "Pistachio" to 0xFFC9E4CA, "Raspberry" to 0xFFE26D8F, "Chocolate" to 0xFF3E2723),
        // Earthy
        palette("Terracotta Sun", PaletteMood.Earthy, "Terracotta" to 0xFFD47E5F, "Sand" to 0xFFE9D7BD, "Olive" to 0xFF6B7044, "Espresso" to 0xFF24170F),
        palette("Desert Dusk", PaletteMood.Earthy, "Bone" to 0xFFF2EBDD, "Clay" to 0xFFD9A07B, "Sage" to 0xFFA3B18A, "Rust" to 0xFF7A3B1E),
        palette("Olive Grove", PaletteMood.Earthy, "Olive" to 0xFF58633A, "Linen" to 0xFFF3EDE0, "Mustard" to 0xFFD9A637, "Bark" to 0xFF3B2F22),
        palette("Clay Studio", PaletteMood.Earthy, "Sandstone" to 0xFFE3C9A8, "Clay" to 0xFFB5654A, "Moss" to 0xFF5C6B47, "Charcoal" to 0xFF2A2623),
        palette("Autumn Walk", PaletteMood.Earthy, "Wheat" to 0xFFF5DEB3, "Pumpkin" to 0xFFD2691E, "Maroon" to 0xFF6D1F1F, "Oak" to 0xFF3E2C1C),
        palette("Sage Home", PaletteMood.Earthy, "Sage" to 0xFFB7C4A9, "Oat" to 0xFFEFE6D6, "Walnut" to 0xFF6F4E37, "Ink" to 0xFF22251F),
        palette("Canyon", PaletteMood.Earthy, "Canyon" to 0xFFB4533A, "Dune" to 0xFFEAC9A1, "Juniper" to 0xFF3F5E54, "Bone" to 0xFFFAF4EA),
        // Luxe
        palette("Black Gold", PaletteMood.Luxe, "Onyx" to 0xFF111111, "Gold" to 0xFFD4AF37, "Champagne" to 0xFFF1E3C6, "Ivory" to 0xFFFFFDF6),
        palette("Emerald Velvet", PaletteMood.Luxe, "Emerald" to 0xFF0B5D4B, "Gold" to 0xFFC9A646, "Ivory" to 0xFFF7F1E3, "Ink" to 0xFF0A1F1A),
        palette("Burgundy Silk", PaletteMood.Luxe, "Burgundy" to 0xFF6B0F1A, "Blush" to 0xFFE8C4C4, "Gold" to 0xFFC8A15A, "Pearl" to 0xFFFBF6F0),
        palette("Navy Pearl", PaletteMood.Luxe, "Navy" to 0xFF0F1F3D, "Pearl" to 0xFFF4F1EA, "Rose Gold" to 0xFFD9A08B, "Steel" to 0xFF8A9BB0),
        palette("Marble", PaletteMood.Luxe, "Marble" to 0xFFF2F0EC, "Taupe" to 0xFFB8AFA4, "Graphite" to 0xFF3A3A3A, "Gold" to 0xFFBF9B5A),
        palette("Royal Plum", PaletteMood.Luxe, "Plum" to 0xFF4B1D52, "Mauve" to 0xFFC49BBB, "Gold" to 0xFFD9B46A, "Cream" to 0xFFFBF3E4),
        palette("Noir Rose", PaletteMood.Luxe, "Noir" to 0xFF1A1416, "Rose" to 0xFFE7A6A1, "Dusty Pink" to 0xFFF6D7D2, "Wine" to 0xFF7C2E3B),
        // Fresh
        palette("Mint Fresh", PaletteMood.Fresh, "Mint" to 0xFFD6F5E3, "Leaf" to 0xFF2E9E5B, "Lime" to 0xFFC6F432, "Pine" to 0xFF0F3D2E),
        palette("Ocean Breeze", PaletteMood.Fresh, "Ocean" to 0xFF0077B6, "Aqua" to 0xFF90E0EF, "Foam" to 0xFFF1FBFD, "Sand" to 0xFFF4D8A8),
        palette("Lemonade", PaletteMood.Fresh, "Lemon" to 0xFFFFF07C, "Pink" to 0xFFFF8FAB, "White" to 0xFFFFFFFF, "Navy" to 0xFF1D2B53),
        palette("Matcha Latte", PaletteMood.Fresh, "Milk" to 0xFFF7F3E8, "Matcha" to 0xFFA8C686, "Leaf" to 0xFF4F772D, "Bean" to 0xFF5A3E2B),
        palette("Aqua Lime", PaletteMood.Fresh, "Aqua" to 0xFF2EC4B6, "Lime" to 0xFFD9F99D, "Coral" to 0xFFFF6B6B, "Ink" to 0xFF113537),
        palette("Sky Picnic", PaletteMood.Fresh, "Sky" to 0xFFBDE0FE, "Strawberry" to 0xFFFF5D73, "Grass" to 0xFF7BC67B, "Navy" to 0xFF14274E),
        palette("Clean White", PaletteMood.Fresh, "White" to 0xFFFFFFFF, "Cloud" to 0xFFEEF1F5, "Cobalt" to 0xFF2459E0, "Ink" to 0xFF111827),
        // Moody
        palette("Midnight", PaletteMood.Moody, "Midnight" to 0xFF0F1630, "Indigo" to 0xFF3B3F7A, "Peach" to 0xFFF39A6B, "Moon" to 0xFFF2F2F2),
        palette("Forest Night", PaletteMood.Moody, "Forest" to 0xFF1E3326, "Moss" to 0xFF7A8F5C, "Amber" to 0xFFE0A43B, "Fog" to 0xFFE8EBE4),
        palette("Deep Ocean", PaletteMood.Moody, "Abyss" to 0xFF0B2545, "Teal" to 0xFF13838F, "Seafoam" to 0xFF8DE4D0, "Foam" to 0xFFEEF4ED),
        palette("Charcoal Ember", PaletteMood.Moody, "Charcoal" to 0xFF1F1F22, "Ember" to 0xFFFF5A36, "Ash" to 0xFF8C8C8C, "Bone" to 0xFFEDE6DA),
        palette("Velvet Night", PaletteMood.Moody, "Aubergine" to 0xFF2B1A33, "Orchid" to 0xFFB565A7, "Rose" to 0xFFFFB4C6, "Mist" to 0xFFF5EEF8),
        palette("Storm", PaletteMood.Moody, "Slate" to 0xFF2F3E46, "Steel" to 0xFF8FA3AD, "Sage" to 0xFFCAD2C5, "White" to 0xFFF8F9FA),
        palette("Espresso Bar", PaletteMood.Moody, "Espresso" to 0xFF2C1A12, "Caramel" to 0xFFC68B59, "Latte" to 0xFFE8D5C0, "Cream" to 0xFFFFF8EE),
        // Retro
        palette("70s Groove", PaletteMood.Retro, "Mustard" to 0xFFE1A730, "Orange" to 0xFFD9541E, "Brown" to 0xFF5C3A21, "Cream" to 0xFFF6E7C8),
        palette("Diner", PaletteMood.Retro, "Cream" to 0xFFFFF5E1, "Cherry" to 0xFFD62839, "Mint" to 0xFFA8E6CF, "Chrome" to 0xFF2B2D42),
        palette("Vintage Postcard", PaletteMood.Retro, "Sand" to 0xFFE9C46A, "Teal" to 0xFF2A9D8F, "Peach" to 0xFFF4A261, "Rust" to 0xFFE76F51, "Ink" to 0xFF264653),
        palette("Polaroid", PaletteMood.Retro, "Off White" to 0xFFF7F3E3, "Faded Red" to 0xFFD96C5F, "Faded Blue" to 0xFF6C8EAD, "Ink" to 0xFF2E2E2E),
        palette("Arcade", PaletteMood.Retro, "Purple" to 0xFF2D1B69, "Pink" to 0xFFFF2E88, "Cyan" to 0xFF00E5FF, "Yellow" to 0xFFFFE600),
        palette("Miami 80s", PaletteMood.Retro, "Sunset" to 0xFFFF6F91, "Peach" to 0xFFFFC75F, "Teal" to 0xFF00C2A8, "Night" to 0xFF1B1B3A),
        palette("Mid-Century", PaletteMood.Retro, "Teal" to 0xFF0F7173, "Mustard" to 0xFFF2A541, "Burnt Orange" to 0xFFD1603D, "Cream" to 0xFFF4EBD9),
        // Neon
        palette("Cyber Lime", PaletteMood.Neon, "Black" to 0xFF0A0A0A, "Lime" to 0xFFC6FF00, "Magenta" to 0xFFFF00A8, "White" to 0xFFFFFFFF),
        palette("Neon Tokyo", PaletteMood.Neon, "Night" to 0xFF0D0221, "Hot Pink" to 0xFFFF2A6D, "Cyan" to 0xFF05D9E8, "Lilac" to 0xFFD1C4E9),
        palette("Acid Pop", PaletteMood.Neon, "Acid" to 0xFFE4FF1A, "Violet" to 0xFF7B2CBF, "Black" to 0xFF111111, "White" to 0xFFFFFFFF),
        palette("Laser", PaletteMood.Neon, "Ultraviolet" to 0xFF3A0CA3, "Neon Pink" to 0xFFF72585, "Electric Blue" to 0xFF4CC9F0, "White" to 0xFFFFFFFF),
        palette("Glow", PaletteMood.Neon, "Charcoal" to 0xFF141414, "Neon Orange" to 0xFFFF6B00, "Neon Green" to 0xFF39FF14, "Ash" to 0xFFD9D9D9),
        palette("Ultra Pink", PaletteMood.Neon, "Hot Pink" to 0xFFFF1F8E, "Bubblegum" to 0xFFFFC2E2, "Black" to 0xFF0D0D0D, "Lime" to 0xFFD4FF3F),
        // Classic
        palette("Monochrome", PaletteMood.Classic, "White" to 0xFFFFFFFF, "Light Grey" to 0xFFE5E5E5, "Mid Grey" to 0xFF7A7A7A, "Black" to 0xFF000000),
        palette("Ink and Paper", PaletteMood.Classic, "Paper" to 0xFFF5F1E8, "Ink" to 0xFF1C1C1C, "Red" to 0xFFC1272D, "Grey" to 0xFFA6A6A6),
        palette("Newsprint", PaletteMood.Classic, "Newsprint" to 0xFFEDEAE0, "Black" to 0xFF151515, "Blue" to 0xFF2D4D8C, "Stone" to 0xFFB3AE9F),
        palette("Blueprint", PaletteMood.Classic, "Blueprint" to 0xFF1E3A8A, "White" to 0xFFFFFFFF, "Sky" to 0xFF93C5FD, "Yellow" to 0xFFFACC15),
        palette("Scandi", PaletteMood.Classic, "Snow" to 0xFFF7F7F5, "Oak" to 0xFFD7C4A3, "Slate" to 0xFF5F6B73, "Charcoal" to 0xFF2B2B2B),
        palette("Classic Red", PaletteMood.Classic, "Red" to 0xFFB91C1C, "White" to 0xFFFFFFFF, "Cream" to 0xFFF5E6CC, "Black" to 0xFF111111),
        palette("Navy Stripe", PaletteMood.Classic, "Navy" to 0xFF1D2A44, "White" to 0xFFFFFFFF, "Red" to 0xFFD64545, "Sand" to 0xFFE8DCC4),
    )
}
