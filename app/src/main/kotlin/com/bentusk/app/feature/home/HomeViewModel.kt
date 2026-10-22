package com.bentusk.app.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bentusk.app.core.data.Project
import com.bentusk.app.core.data.ProjectStore
import com.bentusk.app.core.data.SettingsStore
import com.bentusk.app.core.entitlement.EntitlementRepository
import com.bentusk.engine.Entitlement
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    entitlements: EntitlementRepository,
    private val settings: SettingsStore,
    private val projectStore: ProjectStore,
) : ViewModel() {
    val entitlement: StateFlow<Entitlement> = entitlements.entitlement

    val favorites: StateFlow<Set<String>> =
        settings.favorites.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    val projects: StateFlow<List<Project>> = projectStore.projects

    /** Reloads drafts; called whenever home comes back into view, since the studio saves in the background. */
    fun refreshProjects() {
        viewModelScope.launch { projectStore.refresh() }
    }

    fun toggleFavorite(templateId: String) {
        viewModelScope.launch { settings.toggleFavorite(templateId) }
    }

    fun deleteProject(id: String) {
        viewModelScope.launch { projectStore.delete(id) }
    }
}
