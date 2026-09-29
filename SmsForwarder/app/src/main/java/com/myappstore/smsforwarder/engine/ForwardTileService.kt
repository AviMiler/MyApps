package com.myappstore.smsforwarder.engine

import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.myappstore.smsforwarder.Graph
import com.myappstore.smsforwarder.R
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Quick Settings tile: one tap turns forwarding on/off (or ends a pause). */
class ForwardTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        render()
    }

    override fun onClick() {
        super.onClick()
        // Turning back on releases waiting messages through the settings listener in Graph.
        Graph.settings.update {
            when {
                !it.masterEnabled -> it.copy(masterEnabled = true, pausedUntil = 0L)
                it.isPaused() -> it.copy(pausedUntil = 0L)
                else -> it.copy(masterEnabled = false)
            }
        }
        render()
    }

    private fun render() {
        val tile = qsTile ?: return
        val s = Graph.settings.value
        val active = s.masterEnabled && !s.isPaused()
        tile.state = if (active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.app_name)
        tile.icon = Icon.createWithResource(this, R.drawable.ic_stat_halaa)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = when {
                !s.masterEnabled -> getString(R.string.tile_off)
                s.isPaused() && s.pausedUntil == Long.MAX_VALUE -> getString(R.string.tile_paused)
                s.isPaused() -> getString(
                    R.string.tile_paused_until,
                    DateTimeFormatter.ofPattern("HH:mm").format(Instant.ofEpochMilli(s.pausedUntil).atZone(ZoneId.systemDefault())),
                )
                else -> getString(R.string.tile_on)
            }
        }
        tile.updateTile()
    }

    companion object {
        fun refresh(context: Context) {
            try {
                requestListeningState(context, ComponentName(context, ForwardTileService::class.java))
            } catch (ignored: Exception) {
                // The tile is not added or the system refused; nothing to refresh.
            }
        }
    }
}
