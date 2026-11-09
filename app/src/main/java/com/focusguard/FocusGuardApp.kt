package com.focusguard

import android.app.Application
import com.focusguard.data.AdultFilterProvider
import com.focusguard.di.appModule
import com.focusguard.dns.DnsFilter
import com.focusguard.service.PartnerSync
import com.focusguard.widget.WidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin

class FocusGuardApp : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@FocusGuardApp)
            modules(appModule)
        }
        // El filtro de contenido adulto lee sus listas del disco: se arma ya, fuera del hilo principal, no en el primer evento.
        CoroutineScope(Dispatchers.IO).launch { GlobalContext.get().get<AdultFilterProvider>() }
        PartnerSync.schedule(this)
        // Si el filtro DNS está encendido, la VPN vuelve a arrancar con la app (el permiso ya está dado).
        // Pedir el permiso de VPN es una llamada al sistema: fuera del hilo principal, para no alargar el arranque.
        CoroutineScope(Dispatchers.Default).launch { GlobalContext.get().get<DnsFilter>().ensureRunning() }
        PartnerSync.syncAtStart(this)
        WidgetUpdater.start(this)
    }
}
