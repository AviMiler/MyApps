package com.myappstore.smsforwarder

import android.app.Application
import android.content.Context
import com.myappstore.smsforwarder.contacts.ContactsRepository
import com.myappstore.smsforwarder.data.AppDatabase
import com.myappstore.smsforwarder.data.AppSettings
import com.myappstore.smsforwarder.data.SettingsStore
import com.myappstore.smsforwarder.engine.ForwardEngine
import com.myappstore.smsforwarder.engine.ForwardTileService
import com.myappstore.smsforwarder.engine.Notifier
import com.myappstore.smsforwarder.engine.Scheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Graph.init(this)
    }
}

/** Process-wide singletons. Receivers, the tile and the UI all go through here. */
object Graph {

    private lateinit var appContext: Context

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val db: AppDatabase by lazy { AppDatabase.create(appContext) }
    val settings: SettingsStore by lazy { SettingsStore(appContext) }
    val contacts: ContactsRepository by lazy { ContactsRepository(appContext) }
    val notifier: Notifier by lazy { Notifier(appContext) }
    val scheduler: Scheduler by lazy { Scheduler(appContext) }
    val engine: ForwardEngine by lazy {
        ForwardEngine(appContext, db, settings, contacts, notifier, scheduler)
    }

    fun init(context: Context) {
        appContext = context.applicationContext
        notifier.createChannels()
        settings.addListener(::onSettingsChanged)
    }

    private fun onSettingsChanged(old: AppSettings, new: AppSettings) {
        val holdsChanged = old.masterEnabled != new.masterEnabled ||
            old.pausedUntil != new.pausedUntil ||
            old.holdWhilePaused != new.holdWhilePaused ||
            old.quietWindow() != new.quietWindow() ||
            old.holdDuringQuiet != new.holdDuringQuiet ||
            old.restEnabled != new.restEnabled ||
            old.restCityId != new.restCityId ||
            old.candleMinutes != new.candleMinutes ||
            old.havdalahMinutes != new.havdalahMinutes ||
            old.restIncludesHolidays != new.restIncludesHolidays
        if (holdsChanged) scope.launch { engine.reevaluateHeld() }
        if (old.masterEnabled != new.masterEnabled || old.pausedUntil != new.pausedUntil) {
            ForwardTileService.refresh(appContext)
        }
    }
}
