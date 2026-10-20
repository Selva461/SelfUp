package app.selfup

import android.app.Application
import app.selfup.data.LifeRepository
import app.selfup.data.SelfUpDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SelfUpApplication : Application() {
    /** App-wide scope for work that must outlive any single screen. */
    val appScope = CoroutineScope(SupervisorJob())

    val repository: LifeRepository by lazy { LifeRepository(SelfUpDatabase.create(this)) }

    override fun onCreate() {
        super.onCreate()
        appScope.launch { repository.seedDefaultsOnce() }
    }
}
