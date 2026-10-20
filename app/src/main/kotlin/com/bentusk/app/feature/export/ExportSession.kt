package com.bentusk.app.feature.export

import android.graphics.Bitmap
import com.bentusk.app.core.image.ExportFormat
import com.bentusk.app.core.image.SliceJob
import javax.inject.Inject
import javax.inject.Singleton

/** A finished cover, rendered on screen and waiting to be saved. */
data class CoverJob(val bitmap: Bitmap, val format: ExportFormat)

/** Hands the job from an editor to the export screen without serialising bitmaps through navigation. */
@Singleton
class ExportSession @Inject constructor() {
    @Volatile var slice: SliceJob? = null
        private set
    @Volatile var cover: CoverJob? = null
        private set

    fun start(job: SliceJob) {
        slice = job
        cover = null
    }

    fun start(job: CoverJob) {
        cover = job
        slice = null
    }
}
