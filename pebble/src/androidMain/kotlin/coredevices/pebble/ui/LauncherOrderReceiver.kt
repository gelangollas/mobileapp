package coredevices.pebble.ui

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.russhwolf.settings.Settings
import io.rebble.libpebblecommon.connection.LibPebble
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import kotlin.uuid.Uuid

private const val ACTION = "coredevices.pebble.SET_LAUNCHER_PREFIX"

/** Shell-only developer command. IDs are in highest-priority-first order. */
class LauncherOrderReceiver : BroadcastReceiver(), KoinComponent {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION || !isOrderedBroadcast) return
        val result = goAsync()
        try {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    if (!get<Settings>().showDebugOptions()) {
                        result.resultCode = Activity.RESULT_CANCELED
                        result.resultData = "debug-options-disabled"
                    } else if (intent.getBooleanExtra("probe", false)) {
                        result.resultCode = Activity.RESULT_OK
                        result.resultData = "ready"
                    } else {
                        val raw = intent.getStringExtra("uuids").orEmpty()
                        val parts = raw.split(',')
                        val ids = if (parts.size in 1..100) {
                            parts.mapNotNull { runCatching { Uuid.parse(it) }.getOrNull() }
                        } else emptyList()
                        val saved = if (ids.size == parts.size) get<LibPebble>().setWatchappPrefix(ids) else null
                        result.resultCode = if (saved != null) Activity.RESULT_OK else Activity.RESULT_CANCELED
                        result.resultData = saved?.joinToString(",") ?: "invalid-watchapp-prefix"
                    }
                } catch (_: Exception) {
                    result.resultCode = Activity.RESULT_CANCELED
                    result.resultData = "order-update-failed"
                } finally {
                    result.finish()
                }
            }
        } catch (_: Exception) {
            result.resultCode = Activity.RESULT_CANCELED
            result.resultData = "companion-not-ready"
            result.finish()
        }
    }
}
