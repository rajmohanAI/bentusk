package com.bentusk.engine

/**
 * EXIF orientation applied as a transform instead of rotating pixels, so a 50 MP photo never needs a second
 * full-size bitmap in memory just to stand it upright.
 */
enum class ExifOrientation(val exifValue: Int, val swapsAxes: Boolean) {
    Normal(1, false),
    FlipHorizontal(2, false),
    Rotate180(3, false),
    FlipVertical(4, false),
    Transpose(5, true),
    Rotate90(6, true),
    Transverse(7, true),
    Rotate270(8, true),
    ;

    /** Maps raw stored pixel coordinates (image [w] x [h]) to upright coordinates. */
    fun toUpright(w: Double, h: Double): Affine = when (this) {
        Normal -> Affine.Identity
        FlipHorizontal -> Affine(a = -1.0, tx = w)
        Rotate180 -> Affine(a = -1.0, tx = w, d = -1.0, ty = h)
        FlipVertical -> Affine(d = -1.0, ty = h)
        Transpose -> Affine(a = 0.0, b = 1.0, c = 1.0, d = 0.0)
        Rotate90 -> Affine(a = 0.0, b = -1.0, tx = h, c = 1.0, d = 0.0)
        Transverse -> Affine(a = 0.0, b = -1.0, tx = h, c = -1.0, d = 0.0, ty = w)
        Rotate270 -> Affine(a = 0.0, b = 1.0, c = -1.0, d = 0.0, ty = w)
    }

    companion object {
        fun fromExif(value: Int): ExifOrientation = entries.firstOrNull { it.exifValue == value } ?: Normal
    }
}

/** A photo as stored on disk: its raw pixel size and how it must be turned to stand upright. */
data class SourceGeometry(val raw: PxSize, val orientation: ExifOrientation = ExifOrientation.Normal) {

    /** Size of the photo as the user sees it. All framing math uses this. */
    val upright: PxSize get() = if (orientation.swapsAxes) PxSize(raw.height, raw.width) else raw

    /** Maps pixels of a raw bitmap decoded at [decoded] size into upright full-resolution coordinates. */
    fun decodedToUpright(decoded: PxSize): Affine =
        Affine(a = raw.width.toDouble() / decoded.width, d = raw.height.toDouble() / decoded.height)
            .then(orientation.toUpright(raw.width.toDouble(), raw.height.toDouble()))

    /** Full transform from a decoded raw bitmap onto [canvas] for [framing]. */
    fun decodedToCanvas(decoded: PxSize, canvas: PxSize, framing: Framing): Affine =
        decodedToUpright(decoded).then(FramingMath.transform(upright, canvas, framing))
}
