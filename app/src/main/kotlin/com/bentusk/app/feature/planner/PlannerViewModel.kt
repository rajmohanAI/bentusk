package com.bentusk.app.feature.planner

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bentusk.app.R
import com.bentusk.app.core.data.PlanEntity
import com.bentusk.app.core.data.PlanItemEntity
import com.bentusk.app.core.entitlement.EntitlementRepository
import com.bentusk.engine.Entitlement
import com.bentusk.engine.PinnedGrid
import com.bentusk.engine.Plans
import com.bentusk.engine.ProFeature
import com.bentusk.engine.moved
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PlannerState(
    val plans: List<PlanEntity> = emptyList(),
    val selectedPlanId: Long? = null,
    val items: List<PlanItemEntity> = emptyList(),
    val adding: Boolean = false,
    val failedImports: Int = 0,
    /** Shows the grid as squares, the way some embeds and older app versions crop it, instead of 3:4. */
    val squarePreview: Boolean = false,
    /** Set when the user tried to pin more posts than Instagram allows. */
    val pinLimitHit: Boolean = false,
    /** Outcome of the last archive import, shown once. */
    val archiveResult: ArchiveImport? = null,
    val entitlement: Entitlement = Entitlement.Trial(Plans.TRIAL_DAYS),
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PlannerViewModel @Inject constructor(
    application: Application,
    private val repository: PlannerRepository,
    entitlements: EntitlementRepository,
) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(PlannerState())
    val state: StateFlow<PlannerState> = _state.asStateFlow()

    private val selected = MutableStateFlow<Long?>(null)
    private val _locked = MutableStateFlow(false)
    val locked: StateFlow<Boolean> = _locked.asStateFlow()

    /** While the user drags, the local order leads and database updates are ignored. */
    private var dragging = false
    private var saveJob: Job? = null

    init {
        viewModelScope.launch {
            val id = repository.ensureDefaultPlan(application.getString(R.string.planner_default_name))
            if (selected.value == null) selected.value = id
        }
        viewModelScope.launch { repository.plans.collect { p -> _state.update { it.copy(plans = p) } } }
        viewModelScope.launch {
            selected.filterNotNull().flatMapLatest { id ->
                _state.update { it.copy(selectedPlanId = id) }
                repository.items(id)
            }.collect { items -> if (!dragging) _state.update { it.copy(items = PinnedGrid.order(items) { i -> i.pinned }) } }
        }
        viewModelScope.launch { entitlements.entitlement.collect { e -> _state.update { it.copy(entitlement = e) } } }
    }

    fun select(planId: Long) {
        selected.value = planId
    }

    fun newPlan(name: String) {
        val s = _state.value
        if (s.plans.isNotEmpty() && !s.entitlement.allows(ProFeature.MultiplePlans)) {
            _locked.value = true
            return
        }
        viewModelScope.launch { selected.value = repository.createPlan(name) }
    }

    fun deleteSelectedPlan() {
        val id = _state.value.selectedPlanId ?: return
        val other = _state.value.plans.firstOrNull { it.id != id } ?: return
        selected.value = other.id
        viewModelScope.launch { repository.deletePlan(id) }
    }

    fun consumeLocked() {
        _locked.value = false
    }

    fun add(uris: List<Uri>) {
        val id = _state.value.selectedPlanId ?: return
        if (uris.isEmpty()) return
        _state.update { it.copy(adding = true) }
        viewModelScope.launch {
            val failed = repository.addPhotos(id, uris)
            _state.update { it.copy(adding = false, failedImports = failed) }
        }
    }

    fun consumeFailures() = _state.update { it.copy(failedImports = 0) }

    /** Lays the newest posts from the user's own Instagram archive ZIP under the plan. */
    fun importArchive(zip: Uri) {
        val id = _state.value.selectedPlanId ?: return
        _state.update { it.copy(adding = true) }
        viewModelScope.launch {
            val result = repository.importArchive(id, zip)
            _state.update { it.copy(adding = false, archiveResult = result) }
        }
    }

    fun consumeArchiveResult() = _state.update { it.copy(archiveResult = null) }

    fun setSquarePreview(on: Boolean) = _state.update { it.copy(squarePreview = on) }

    /** Pins a post to the top of the grid, or unpins it. Instagram allows only a few pinned posts. */
    fun togglePin(item: PlanItemEntity) {
        if (!item.pinned && !PinnedGrid.canPin(_state.value.items.count { it.pinned })) {
            _state.update { it.copy(pinLimitHit = true) }
            return
        }
        val pinned = !item.pinned
        // Pinning moves the post to the top; every other post keeps the place it has on screen.
        val changed = _state.value.items.map { if (it.id == item.id) it.copy(pinned = pinned) else it }
        val order = PinnedGrid.order(changed) { it.pinned }.map { it.id }
        viewModelScope.launch { repository.setPinned(item, pinned, order) }
    }

    fun consumePinLimit() = _state.update { it.copy(pinLimitHit = false) }

    fun remove(item: PlanItemEntity) {
        viewModelScope.launch { repository.removeItem(item) }
    }

    fun onDragStart() {
        dragging = true
    }

    fun move(from: Int, to: Int) {
        _state.update { it.copy(items = it.items.moved(from, to)) }
    }

    fun onDragEnd() {
        val ids = _state.value.items.map { it.id }
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            repository.saveOrder(ids)
            dragging = false
        }
    }
}
