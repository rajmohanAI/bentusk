package com.bentusk.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PaletteTest {
    @Test
    fun `the library has over fifty palettes with unique ids in every mood`() {
        assertTrue(Palettes.All.size >= 50)
        assertEquals(Palettes.All.size, Palettes.All.map { it.id }.toSet().size)
        PaletteMood.entries.forEach { assertTrue("$it is empty", Palettes.of(it).isNotEmpty()) }
        assertEquals(Palettes.All, Palettes.of(null))
    }

    @Test
    fun `every palette's text is readable`() {
        for (p in Palettes.All) {
            val text = Contrast.ratio(p.background.argb, p.ink.argb)
            assertTrue("${p.name}: ink on background is $text", text >= Palettes.MIN_TEXT_CONTRAST)
            val (pill, onPill) = p.pill
            val label = Contrast.ratio(pill.argb, onPill.argb)
            assertTrue("${p.name}: pill text is $label", label >= Palettes.MIN_TEXT_CONTRAST)
        }
    }

    @Test
    fun `contrast follows WCAG`() {
        assertEquals(21.0, Contrast.ratio(0xFF000000.toInt(), 0xFFFFFFFF.toInt()), 1e-9)
        assertEquals(1.0, Contrast.ratio(0xFFCBB3E8.toInt(), 0xFFCBB3E8.toInt()), 1e-9)
        // #777 on white is the classic just-fails-AA grey.
        assertEquals(4.48, Contrast.ratio(0xFF777777.toInt(), 0xFFFFFFFF.toInt()), 0.01)
    }

    @Test
    fun `the reference palette reads as designed`() {
        val p = Palettes.byId("creative-momentum")!!
        assertEquals(listOf("#CBB3E8", "#D4FF3F", "#E34234", "#1B1C20"), p.swatches.map { it.hex })
        assertEquals("Ink", p.ink.name)
        assertNull(Palettes.byId("missing"))
    }

    @Test
    fun `applying a palette recolours the background and text but not photos`() {
        val photo = PhotoLayer(1, NRect(0.0, 0.0, 0.5, 1.0), uri = "content://photo")
        val plain = TextLayer(2, NRect(0.5, 0.1, 0.9, 0.3), "Title", 0.08, 0xFFFFFFFF.toInt())
        val label = TextLayer(3, NRect(0.5, 0.6, 0.9, 0.7), "New", 0.04, 0xFFFFFFFF.toInt(), pill = 0xFF000000.toInt())
        val design = Design.blank(TemplateKind.Carousel, 2, PostRatio.Portrait).add(photo).add(plain).add(label)
        val p = Palettes.byId("creative-momentum")!!

        val result = Palettes.apply(design, p)

        assertEquals(Fill(p.background.argb), result.fill)
        assertEquals("creative-momentum", result.palette)
        assertSame(photo, result.layer(1))
        assertEquals(p.ink.argb, (result.layer(2) as TextLayer).argb)
        val (pill, onPill) = p.pill
        assertEquals(pill.argb, (result.layer(3) as TextLayer).pill)
        assertEquals(onPill.argb, (result.layer(3) as TextLayer).argb)
    }

    @Test
    fun `the applied palette survives saving`() {
        val design = Palettes.apply(Design.blank(TemplateKind.Grid, 1, PostRatio.Square), Palettes.All.last())
        assertEquals(design, Design.fromJson(Design.toJson(design)))
    }
}
