import com.prayansh.coup.model.Message
import io.ktor.client.*
import io.ktor.client.features.websocket.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.cio.websocket.*
import kotlinx.browser.window
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

suspend fun HttpClient.setupSession(readChannel: SendChannel<String>, sendChannel: ReceiveChannel<String>) {
    Logger.info("Starting websocket connection")
    val loc = window.location
    val port =
        if (loc.port.isNotEmpty()) {
            loc.port.toIntOrNull() ?: 8080
        } else if(loc.protocol.contains("https")) {
            443
        } else {
            80
        }
    val name = if (loc.protocol.contains("https")) {
        "wss"
    } else {
        "ws"
    }
    val host = loc.hostname
    this.webSocket(
        {
            this.method = HttpMethod.Get
            url(name, host, port, "/coup")
        },
    ) {
        val messageOutputRoutine = launch { sendTo(readChannel) }
        val userInputRoutine = launch { receiveFrom(sendChannel) }

        userInputRoutine.join() // Wait for completion; either "exit" or error
        messageOutputRoutine.cancelAndJoin()
    }
    Logger.info("Closing websocket connection")
}

suspend fun DefaultClientWebSocketSession.sendTo(output: SendChannel<String>) {
    try {
        for (message in incoming) {
            message as? Frame.Text ?: continue
            output.send(message.readText())
        }
    } catch (e: Exception) {
        Logger.error("Error while receiving: " + e.message)
    }
}

suspend fun DefaultClientWebSocketSession.receiveFrom(input: ReceiveChannel<String>) {
    while (true) {
        val message = input.receive()
        if (message.equals("exit", true)) return
        try {
            send(message)
        } catch (e: Exception) {
            Logger.error("Error while sending: " + e.message)
            return
        }
    }
}

fun Frame.Text.toMessage(): Message {
    return Json.decodeFromString(Message.serializer(), readText())
}
