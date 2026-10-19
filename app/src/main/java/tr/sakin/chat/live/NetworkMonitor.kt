package tr.sakin.chat.live

import android.content.Context
import android.net.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

class NetworkMonitor(context: Context) {
    private val cm = context.getSystemService(ConnectivityManager::class.java)
    val online = callbackFlow {
        fun update() {
            val capabilities = cm.getNetworkCapabilities(cm.activeNetwork)
            trySend(capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true)
        }
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = update()
            override fun onLost(network: Network) = update()
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) = update()
        }
        cm.registerDefaultNetworkCallback(callback); update()
        awaitClose { cm.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged()
}
