package com.bentusk.app.navigation

import kotlinx.serialization.Serializable

@Serializable data object HomeRoute

/** [mode] is an [com.bentusk.app.feature.editor.SliceMode] name; [photo] a content URI string. */
@Serializable data class EditorRoute(val mode: String, val photo: String)

@Serializable data object ExportRoute

@Serializable data object PlannerRoute

@Serializable data object CoversRoute

@Serializable data object ProRoute

@Serializable data object TemplatesRoute

/** [photos] are content URI strings joined by newlines, in the order they fill the template's slots. */
@Serializable data class TemplateEditorRoute(val templateId: String, val photos: String)
