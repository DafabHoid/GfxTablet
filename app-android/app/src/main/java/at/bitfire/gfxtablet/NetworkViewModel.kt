package at.bitfire.gfxtablet

import androidx.lifecycle.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class NetworkViewModel : ViewModel()
{
    /** The name of the host we want to send NetEvents */
    val hostName = MutableLiveData<String>()

    /** The NetworkClient instance which performs the network I/O and relays NetEvents coming from the canvas */
    val netClient = NetworkClient()

    /** whenever {@link hostName} changes, the netClient gets reconfigured and the result (success) is reflected in this LiveData */
    val connectedState: LiveData<Boolean>

    private suspend fun reconfigureClient(hostName: String): Boolean
    {
        return withContext(Dispatchers.IO) {
            // run this potentially blocking operation in the I/O context, so the outer coroutine can suspend in the meantime
            netClient.reconfigureNetworking(hostName)
        }
    }

    init
    {
        // reconfigure the client every time the hostName changes
        connectedState = hostName.switchMap {
            // map the hostName to a LiveData object by calling a coroutine
            // run the coroutine in the viewModelScope so it is cancelled when the ViewModel gets destroyed
            hostName: String -> liveData(viewModelScope.coroutineContext) {
                emit(reconfigureClient(hostName))
            }
        }
        // start the NetworkClient on creation in a separate thread
        Thread(netClient).start()
    }

    override fun onCleared()
    {
        // stop the NetworkClient when this ViewModel is no longer needed
        netClient.queue.add(NetEvent(NetEvent.Type.TYPE_DISCONNECT))
    }
}