package com.bentusk.engine

import kotlin.math.ceil
import kotlin.math.max

/**
 * How to decode a photo for a render: the power-of-two subsample factor and the resulting size.
 * The decoded image always has at least as many pixels per canvas pixel as the output needs,
 * unless the memory budget forces a smaller decode.
 */
data class DecodePlan(val sampleSize: Int, val decoded: PxSize) {
    companion object {
        /**
         * @param source full size of the photo after EXIF orientation.
         * @param canvasScale source-to-canvas scale the photo will be drawn at.
         * @param maxPixels largest bitmap the device can safely hold.
         */
        fun forRender(source: PxSize, canvasScale: Double, maxPixels: Long): DecodePlan {
            require(canvasScale > 0) { "Scale must be positive" }
            require(maxPixels > 0) { "Pixel budget must be positive" }
            var sample = 1
            // Subsample while the next power of two still gives at least one source pixel per canvas pixel.
            while (canvasScale * sample * 2 <= 1.0) sample *= 2
            while (decodedSize(source, sample).pixels > maxPixels) sample *= 2
            return DecodePlan(sample, decodedSize(source, sample))
        }

        /** Plan for an on-screen preview whose longest side should be at least [maxSide] pixels. */
        fun forPreview(source: PxSize, maxSide: Int): DecodePlan {
            val longest = max(source.width, source.height)
            var sample = 1
            while (longest / (sample * 2) >= maxSide) sample *= 2
            return DecodePlan(sample, decodedSize(source, sample))
        }

        /** Matches BitmapFactory, which rounds subsampled dimensions up. */
        fun decodedSize(source: PxSize, sample: Int) = PxSize(
            max(1, ceil(source.width / sample.toDouble()).toInt()),
            max(1, ceil(source.height / sample.toDouble()).toInt()),
        )
    }
}
