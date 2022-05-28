import Logger.log
import com.prayansh.coup.model.Content
import com.prayansh.coup.model.GameState
import com.prayansh.coup.model.Message
import com.prayansh.coup.model.Move
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*

typealias ConnectionObserver = (ConnectionStatus) -> Unit

data class ConnectionStatus(val connected: Boolean, val error: String, val message: String)

class Session(
    val readChannel: ReceiveChannel<String>, // Channel for receiving data from socket
    val sendChannel: SendChannel<String> // Channel for sending data to socket
) {

    private var color: String = "#000000"
    var userName: String = ""
    var roomName: String = ""
    lateinit var gameState: GameState

    private val observers = mutableListOf<ConnectionObserver>()
    private fun notifyObservers(newStatus: ConnectionStatus) {
        observers.forEach {
            it(newStatus)
        }
    }

    fun registerObserver(obs: ConnectionObserver) {
        observers += obs
    }

    suspend fun initiateSession(): Boolean {
        val result = try {
            withTimeout(3 * 1000) {
                val connect = receive()
                return@withTimeout if (connect.type == Message.Type.CONNECT) {
                    log(connect.content.toString(), Logger.Level.OFF)
                    notifyObservers(ConnectionStatus(true, "", "Waiting to connect to room"))
                    true
                } else {
                    notifyObservers(ConnectionStatus(false, "", "Incorrect message type"))
                    false
                }
            }
        } catch (ex: TimeoutCancellationException) {
            notifyObservers(ConnectionStatus(false, "", "Failed to establish connection to server"))
            false
        }
        return result
    }

    suspend fun createRoom(username: String): String? {
        val createMsg = Message(
            type = Message.Type.CREATE,
            content = buildJsonObject {
                put("userName", username)
            },
            timestamp = 0.toULong()
        )
        send(createMsg)

        var errorMsg: String? = null
        var colorStr = "#000000"

        val msg = receive()
        when(msg.type) {
            Message.Type.JOIN -> {
                colorStr = msg.content["color"]?.jsonPrimitive?.content ?: "#000000"
            }
            Message.Type.ERROR -> {
                errorMsg = msg.content["msg"]?.jsonPrimitive?.content ?: "Error"
            }
            else -> {
                errorMsg = "Expected join message from server, got $msg"
            }
        }

        val roomName = msg.content["roomName"]?.jsonPrimitive?.content ?: ""
        // Start listening for future messages?
        this.color = colorStr
        this.userName = username
        this.roomName = roomName
        notifyObservers(ConnectionStatus(true, "", "Color is $colorStr, Room is $roomName"))
        return errorMsg
    }

    suspend fun joinRoom(roomName: String, username: String): String? {
        val joinMsg = Message(
            type = Message.Type.JOIN,
            content = buildJsonObject {
                put("userName", username)
                put("roomName", roomName)
            },
            timestamp = 0.toULong()
        )
        send(joinMsg)

        var errorMsg: String? = null
        var colorStr = "#000000"

        val msg = receive()
        when(msg.type) {
            Message.Type.JOIN -> {
                colorStr = msg.content["color"]?.jsonPrimitive?.content ?: "#000000"
            }
            Message.Type.ERROR -> {
                errorMsg = msg.content["msg"]?.jsonPrimitive?.content ?: "Error"
            }
            else -> {
                errorMsg = "Expected join message from server, got $msg"
            }
        }
        this.color = colorStr
        this.userName = username
        this.roomName = roomName
        notifyObservers(ConnectionStatus(true, "", "Color is $colorStr, Room is $roomName"))
        return errorMsg
    }

    suspend fun start() {
        val startMsg = Message(
            type = Message.Type.START,
            timestamp = 0.toULong(),
            content = buildJsonObject {
                put("roomName", roomName)
            }
        )
        send(startMsg)
    }

    suspend fun sendData(data: String) {
//        val content = UserData(
//            layerData = data,
//            username = userName,
//            roomName = roomName,
//            color = color
//        )
//        val msg = Message(
//            type = Message.Type.DATA,
//            content = Json.encodeToJsonElement(UserData.serializer(), content).jsonObject,
//            timestamp = Date().time.toULong()
//        )
//        send(msg)
//        notifyObservers(ConnectionStatus(true, ""))
    }

    suspend fun exit() {
        val msg = Message(
            type = Message.Type.EXIT,
            content = buildJsonObject {
                put("roomName", roomName)
            },
            timestamp = 0.toULong()
        )
        send(msg)
        notifyObservers(ConnectionStatus(false, "", "Disconnected at ${msg.timestamp}"))
    }

    suspend fun receive(): Message {
        val rawMsg = readChannel.receive()
        val parsedMsg: Message = Json.decodeFromString(rawMsg)
        return parsedMsg
    }

    suspend fun send(msg: Message) {
        val str = Json.encodeToString(msg)
        sendChannel.send(str)
    }

    suspend fun sendMove(move: Move) {
        send(
            Message(
                type = Message.Type.MOVE,
                timestamp = 0.toULong(),
                content = Json.encodeToJsonElement(
                    Content.MoveData.serializer(),
                    Content.MoveData(
                        roomName = roomName,
                        move = move
                    )
                ).jsonObject
            )
        )
    }
}
