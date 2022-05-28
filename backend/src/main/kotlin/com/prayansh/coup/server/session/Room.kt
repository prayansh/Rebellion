@file:OptIn(ExperimentalLettuceCoroutinesApi::class)

package com.prayansh.coup.server.session

import com.prayansh.coup.model.GameState
import com.prayansh.coup.model.Message
import com.prayansh.coup.model.PlayerColors
import com.prayansh.coup.server.newGameState
import com.prayansh.coup.server.session.RoomsManager.Companion.fromRoomMembersValue
import com.prayansh.coup.server.session.RoomsManager.Companion.redisRoomKey
import com.prayansh.coup.server.session.RoomsManager.Companion.redisRoomMembersKey
import com.prayansh.coup.server.session.RoomsManager.Companion.roomMembersValue
import com.prayansh.coup.server.session.RoomsManager.Companion.startedKey
import com.prayansh.coup.server.session.RoomsManager.Companion.stateKey
import io.ktor.websocket.*
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.api.coroutines.RedisCoroutinesCommands
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
import kotlin.random.Random

/*
Redis Storage:
- HSET redisRoomKey()
  - memCount (HINCRBY myhash field 1)
  - gameStarted (HSET myhash started true)
  - state (HSET myhash state true)
 */

class RoomsManager(
    val storeRedis: RedisCoroutinesCommands<String, String>,
    val subscribeRedis: StatefulRedisPubSubConnection<String, String>,
    val publishRedis: StatefulRedisPubSubConnection<String, String>
) {
    val rooms = Collections.synchronizedMap(LinkedHashMap<String, Room>())

    suspend fun createRoom(): Room {
        var roomName: String
        do {
            roomName = uuid()
            val exists = storeRedis.exists(redisRoomKey(roomName)) == 1L
        } while (exists)
        val room = Room(
            roomName = roomName,
            subscribeRedis = subscribeRedis,
            publishRedis = publishRedis,
            storeRedis = storeRedis
        )
        rooms[roomName] = room
        return room
    }

    suspend fun findRoom(roomName: String): Room {
        if (!rooms.contains(roomName) && storeRedis.exists(redisRoomKey(roomName)) == 1L) {
            // Room is part of the network, create a new one for this server
            val r = Room(
                roomName = roomName,
                subscribeRedis = subscribeRedis,
                publishRedis = publishRedis,
                storeRedis = storeRedis,
            )
            r.retrieveState()
            rooms[roomName] = r
        }
        val room = rooms[roomName]
        if (room == null) {
            throw RoomException("Room not found")
        } else if (room.gameStarted) { // TODO
            throw RoomException("Game room has already started")
        } else if (room.memberCount() == 6) {
            throw RoomException("Game room is full")
        }
        return room
    }

    companion object {
        const val RoomsKey = "coup-rooms"
        const val MembersKey = "coup-members"

        fun redisRoomKey(roomName: String) = "$RoomsKey:$roomName"
        fun redisRoomMembersKey(roomName: String) = "$MembersKey:$roomName"

        fun roomMembersValue(userName: String, color: String) = "$color::$userName"

        fun fromRoomMembersValue(v: String) = v.substringBefore("::") to v.substringAfter("::")

        const val startedKey = "started"
        const val stateKey = "state"

        const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        fun uuid(): String {
            val random = Random(System.currentTimeMillis())
            return (1..8)
                .map { random.nextInt(0, ALPHABET.length) }
                .map(Companion.ALPHABET::get)
                .joinToString("")
        }
    }

    class RoomException(val roomError: String) : Exception()
}

class Room(
    val roomName: String,
    val subscribeRedis: StatefulRedisPubSubConnection<String, String>,
    val publishRedis: StatefulRedisPubSubConnection<String, String>,
    val storeRedis: RedisCoroutinesCommands<String, String>,
    val scope: CoroutineScope = CoroutineScope(Dispatchers.IO),
) {
    val clients: MutableSet<Connection> = Collections.synchronizedSet(LinkedHashSet())

    val colorSet = PlayerColors
    var gameStarted: Boolean = false
    lateinit var gameState: GameState

    init {
        subscribeToChannel(roomName)
    }

    suspend fun retrieveState() {
        gameStarted = storeRedis.hget(redisRoomKey(roomName), startedKey)?.toBoolean() ?: false
        if (gameStarted) {
            val stateStr =
                storeRedis.hget(redisRoomKey(roomName), stateKey) ?: throw RuntimeException("Redis: room not found")
            gameState = Json.decodeFromString(GameState.serializer(), stateStr)
        }
    }

    suspend fun persistState() {
        storeRedis.hset(redisRoomKey(roomName), startedKey, gameStarted.toString())
        if (gameStarted) {
            val stateStr = Json.encodeToString(GameState.serializer(), gameState)
            storeRedis.hset(redisRoomKey(roomName), stateKey, stateStr)
        }
    }

    suspend fun memberCount(): Int {
        return storeRedis.llen(redisRoomMembersKey(roomName))?.toInt() ?: 0
    }

    suspend fun addClient(connection: Connection, userName: String): String {
        clients.add(connection)
        val count = storeRedis.llen(redisRoomMembersKey(roomName))
        val size = count?.toInt() ?: 0
        val color = colorSet[size]
        storeRedis.lpush(redisRoomMembersKey(roomName), roomMembersValue(userName, color))
        connection.color = color
        connection.name = userName
        return color
    }

    suspend fun addClient(session: DefaultWebSocketSession, userName: String): Connection {
        TODO("""
        storeRedis.hincrby(redisRoomKey(roomName), memberCountKey, 1L)
        val size = memberCount()
        val color = colorSet[size - 1]
        val connection = Connection(session).apply {
            this.color = color
            this.name = userName
        }
        clients.add(connection)
        return connection
        """)
    }

    fun removeClient(connection: Connection) {
        clients.remove(connection)
    }

    suspend fun startGame() {
        gameStarted = true
        val memberList = storeRedis
            .lrange(redisRoomMembersKey(roomName), 0, -1)
            .map { fromRoomMembersValue(it) }
        gameState = newGameState(memberList)
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
