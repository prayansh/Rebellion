package com.prayansh.coup.server.session

import com.prayansh.coup.model.GameState
import com.prayansh.coup.model.Message
import com.prayansh.coup.model.PlayerColors
import com.prayansh.coup.server.newGameState
import io.ktor.websocket.*
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.pubsub.RedisPubSubAdapter
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.*

class Room(
    val roomName: String,
    val subscribeRedis: StatefulRedisPubSubConnection<String, String>,
    val publishRedis: StatefulRedisPubSubConnection<String, String>,
    val scope: CoroutineScope = CoroutineScope(Dispatchers.IO),
) {
    val clients: MutableSet<Connection> = Collections.synchronizedSet(LinkedHashSet())

    val colorSet = PlayerColors
    val size get() = clients.size
    var gameStarted: Boolean = false
    lateinit var gameState: GameState

    init {
        subscribeToChannel(roomName)
    }

    fun addClient(connection: Connection): String {
        clients.add(connection)
        val color = colorSet[size - 1]
        connection.color = color
        return color
    }

    fun addClient(session: DefaultWebSocketSession, userName: String): Connection {
        val color = colorSet[size - 1]
        val connection = Connection(session).apply {
            this.color = color
            this.name = userName
        }
        clients.add(connection)
        return connection
    }

    fun removeClient(connection: Connection) {
        clients.remove(connection)
    }

    fun startGame() {
        gameStarted = true
        gameState = newGameState(clients)
    }

    @ExperimentalLettuceCoroutinesApi
    suspend fun broadcast(msg: Message, except: Connection? = null) {
        val encoded = Json.encodeToString(msg)
        publishRedis.async().publish(roomName, encoded).await()
    }

    fun subscribeToChannel(channelName: String) {
        subscribeRedis.addListener(object : RedisPubSubAdapter<String, String>() {
            override fun message(channel: String, message: String) {
                println("Received msg on $channel")
                println("Received msg $message")
                if (channel == channelName) {
                    val incoming = Json.decodeFromString<Message>(message)
                    scope.launch {
                        clients.forEach {
                            it.send(incoming)
                        }
                    }
                }
            }
        })
        scope.launch {
            subscribeRedis.async().subscribe(roomName).await()
        }
    }

}
