package com.bentusk.app.feature.export

import android.graphics.Bitmap
import com.bentusk.app.core.image.ExportFormat
import com.bentusk.app.core.image.SliceJob
import com.bentusk.app.core.image.TemplateJob
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
    @Volatile var template: TemplateJob? = null
        private set

    /** The caption being written for the current post; it survives going back to tweak the layout. */
    @Volatile var caption: String = ""

    fun start(job: SliceJob) {
        clear()
        slice = job
    }

    fun start(job: CoverJob) {
        clear()
        cover = job
    }

    fun start(job: TemplateJob) {
        clear()
        template = job
    }

    private fun clear() {
        slice = null
        cover = null
        template = null
    }
}
