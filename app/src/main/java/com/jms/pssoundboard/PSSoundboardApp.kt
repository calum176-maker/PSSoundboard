package com.jms.pssoundboard

import android.app.Application
import com.jms.pssoundboard.audio.SoundboardEngine
import com.jms.pssoundboard.data.SoundRepository
import com.jms.pssoundboard.data.SpewVaultRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Process-wide owner of the [SoundboardEngine], [SoundRepository], and [SpewVaultRepository]
 * so that the main UI and the floating overlay service share the same RAM-resident SoundPool samples,
 * deck state, and persistent Master Spew Vault.
 */
class PSSoundboardApp : Application() {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    lateinit var engine: SoundboardEngine
        private set
    lateinit var repository: SoundRepository
        private set
    lateinit var vaultRepository: SpewVaultRepository
        private set

    override fun onCreate() {
        super.onCreate()
        engine = SoundboardEngine(this)
        repository = SoundRepository(this, appScope)
        vaultRepository = SpewVaultRepository(this, appScope)

        appScope.launch {
            repository.initialize()
            vaultRepository.initialize()
            engine.setGain(repository.state.value.gain)
            // Keep every pad's sample resident in SoundPool RAM for zero-latency triggering.
            repository.state
                .map { it.allFilePaths() }
                .distinctUntilChanged()
                .collect { paths -> engine.syncResident(paths, setOf(repository.testTonePath)) }
        }
    }
}
