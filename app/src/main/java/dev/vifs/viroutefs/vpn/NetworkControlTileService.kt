// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs.vpn

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.core.content.ContextCompat
import dev.vifs.viroutefs.MainActivity
import dev.vifs.viroutefs.R

class NetworkControlTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        updateTile(VpnServiceController(applicationContext).currentState())
    }

    override fun onClick() {
        super.onClick()
        val action = Runnable { toggleNetworkControl() }
        if (isLocked && isSecure) {
            unlockAndRun(action)
        } else {
            action.run()
        }
    }

    private fun toggleNetworkControl() {
        val controller = VpnServiceController(applicationContext)
        val currentState = controller.currentState()
        if (currentState.isNetworkControlEnabled) {
            controller.stopLocalService()
            updateTile(VpnServiceUiState(VpnServiceStatus.Stopped))
            return
        }

        if (controller.prepareIntent() != null || !controller.notificationPermissionGranted()) {
            openAppForPermission()
            return
        }
        runCatching { controller.startLocalService() }
            .onSuccess { updateTile(VpnServiceUiState(VpnServiceStatus.Starting)) }
            .onFailure { updateTile(VpnServiceUiState(VpnServiceStatus.Error, it.localizedMessage)) }
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun openAppForPermission() {
        val intent = Intent(this, MainActivity::class.java)
            .setAction(ACTION_START_FROM_TILE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pendingIntent = PendingIntent.getActivity(
                this,
                TILE_START_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            startActivityAndCollapse(pendingIntent)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    private fun updateTile(state: VpnServiceUiState) {
        val tile = qsTile ?: return
        val active = state.isNetworkControlEnabled
        tile.state = if (active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.network_control_tile_label)
        tile.contentDescription = getString(
            if (active) R.string.network_control_tile_on else R.string.network_control_tile_off,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = getString(
                when {
                    state.status == VpnServiceStatus.Starting -> R.string.network_control_tile_starting
                    state.status == VpnServiceStatus.Error -> R.string.network_control_tile_error
                    active -> R.string.network_control_tile_on
                    else -> R.string.network_control_tile_off
                },
            )
        }
        tile.updateTile()
    }

    companion object {
        internal const val ACTION_START_FROM_TILE = "dev.vifs.viroutefs.action.START_FROM_TILE"
        private const val TILE_START_REQUEST_CODE = 701

        internal fun requestRefresh(context: Context) {
            requestListeningState(
                context.applicationContext,
                ComponentName(context.applicationContext, NetworkControlTileService::class.java),
            )
        }

        internal fun requestAdd(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val statusBarManager = context.getSystemService(StatusBarManager::class.java)
                statusBarManager.requestAddTileService(
                    ComponentName(context, NetworkControlTileService::class.java),
                    context.getString(R.string.network_control_tile_label),
                    Icon.createWithResource(context, R.drawable.ic_network_control_tile),
                    ContextCompat.getMainExecutor(context),
                ) { }
            } else {
                context.startActivity(
                    Intent("android.settings.QUICK_SETTINGS_SETTINGS")
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }
    }
}

internal val VpnServiceUiState.isNetworkControlEnabled: Boolean
    get() = status == VpnServiceStatus.Starting ||
        status == VpnServiceStatus.RuntimeActive ||
        status == VpnServiceStatus.ServiceActiveNoTun ||
        status == VpnServiceStatus.TunPreviewActive ||
        status == VpnServiceStatus.TunTestRouteActive
