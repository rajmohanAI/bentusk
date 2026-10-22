package com.bentusk.app.feature.covers

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bentusk.app.core.entitlement.EntitlementRepository
import com.bentusk.app.core.image.ExportFormat
import com.bentusk.app.feature.export.CoverJob
import com.bentusk.app.feature.export.ExportSession
import com.bentusk.engine.Entitlement
import com.bentusk.engine.Plans
import com.bentusk.engine.ProFeature
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CoversState(
    val icon: Int = 0,
    val palette: Int = 0,
    val iconFraction: Float = 0.5f,
    val entitlement: Entitlement = Entitlement.Trial(Plans.TRIAL_DAYS),
) {
    val canUsePremium: Boolean get() = entitlement.allows(ProFeature.PremiumCovers)
}

@HiltViewModel
class CoversViewModel @Inject constructor(
    private val session: ExportSession,
    entitlements: EntitlementRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(CoversState())
    val state: StateFlow<CoversState> = _state.asStateFlow()

    private val _locked = MutableStateFlow(false)
    val locked: StateFlow<Boolean> = _locked.asStateFlow()

    init {
        viewModelScope.launch { entitlements.entitlement.collect { e -> _state.update { it.copy(entitlement = e) } } }
    }

    fun selectIcon(index: Int) = _state.update { it.copy(icon = index) }

    fun selectPalette(index: Int) {
        if (CoverPalettes[index].premium && !_state.value.canUsePremium) {
            _locked.value = true
            return
        }
        _state.update { it.copy(palette = index) }
    }

    fun setIconFraction(value: Float) = _state.update { it.copy(iconFraction = value) }

    fun consumeLocked() {
        _locked.value = false
    }

    fun export(bitmap: Bitmap) {
        session.start(CoverJob(bitmap, ExportFormat.Png))
    }
}
