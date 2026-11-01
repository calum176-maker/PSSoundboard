package com.jms.pssoundboard

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object ProConfig {
    // Always true during pre-launch / testing; flip to false when Play Billing is wired up
    private val _isPro = MutableStateFlow(true)
    val isPro: StateFlow<Boolean> = _isPro.asStateFlow()

    fun setProStatus(unlocked: Boolean) {
        _isPro.value = unlocked
    }
}
