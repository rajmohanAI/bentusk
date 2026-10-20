package com.bentusk.app.feature.home

import androidx.lifecycle.ViewModel
import com.bentusk.app.core.entitlement.EntitlementRepository
import com.bentusk.engine.Entitlement
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(entitlements: EntitlementRepository) : ViewModel() {
    val entitlement: StateFlow<Entitlement> = entitlements.entitlement
}
