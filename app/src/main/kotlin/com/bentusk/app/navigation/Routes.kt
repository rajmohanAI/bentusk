package com.bentusk.app.navigation

import kotlinx.serialization.Serializable

@Serializable data object HomeRoute

/** [mode] is an [com.bentusk.app.feature.editor.SliceMode] name; [photo] a content URI string. */
@Serializable data class EditorRoute(val mode: String, val photo: String)

@Serializable data object ExportRoute

@Serializable data object PlannerRoute

@Serializable data object CoversRoute

@Serializable data object ProRoute
