package com.bentusk.app.navigation

import kotlinx.serialization.Serializable

@Serializable data object HomeRoute

/** [mode] is an [com.bentusk.app.feature.editor.SliceMode] name; [photo] a content URI string. */
@Serializable data class EditorRoute(val mode: String, val photo: String)

@Serializable data object ExportRoute

@Serializable data object PlannerRoute

@Serializable data object CoversRoute

@Serializable data object ProRoute

/**
 * The design editor. It opens a saved [projectId], or a new design from [templateId], or a blank [kind]
 * ([com.bentusk.engine.TemplateKind] name). [photos] are content URIs joined by newlines that fill it first.
 */
@Serializable
data class StudioRoute(
    val projectId: String? = null,
    val templateId: String? = null,
    val kind: String? = null,
    val photos: String = "",
)
