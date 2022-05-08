package com.prayansh.coup.server.plugins

import com.prayansh.coup.model.Content
import com.prayansh.coup.model.GameState
import com.prayansh.coup.model.Message
import com.prayansh.coup.server.session.Connection
import com.prayansh.coup.server.session.Room
import com.prayansh.coup.server.updateGameState
import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection
import kotlinx.serialization.json.*
import java.time.Duration
import java.util.*
import kotlin.random.Random

@ExperimentalLettuceCoroutinesApi
fun Application.configureSockets(
    redisConn: StatefulRedisPubSubConnection<String, String>,
    subscribeRedis: StatefulRedisPubSubConnection<String, String>
) {
    install(WebSockets) {
        pingPeriod = Duration.ofSeconds(15)
        timeout = Duration.ofSeconds(15)
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }
    routing {
        val rooms = Collections.synchronizedMap(LinkedHashMap<String, Room>())
        webSocket("/coup") {
            println("Adding user!")
            val thisConnection = Connection(this)
            try {
                // Connect to main room
                thisConnection.send(
                    Message(
                        type = Message.Type.CONNECT,
                        timestamp = Date().time.toULong(),
                        content = buildJsonObject {
                            put("rooms", buildJsonArray {
                                rooms.keys.forEach {
                                    add(it)
                                }
                            })
                        }
                    )
                )
                // Wait for join/create message
                val initial = incoming.receive() as? Frame.Text ?: throw Exception("expected join/create message")
                val msg: Message = initial.toMessage()
                // Join ROOM
                when (msg.type) {
                    Message.Type.CREATE -> {
                        val roomName = uuid()
                        if (rooms.contains(roomName)) {
                            thisConnection.sendError("Room already created")
                        } else {
                            val room = Room(
                                roomName = roomName,
                                subscribeRedis = subscribeRedis,
                                publishRedis = redisConn
                            )
                            rooms[roomName] = room
                            msg.content["userName"]?.jsonPrimitive?.content?.let {
                                thisConnection.name = it
                            }
                            val color = room.addClient(thisConnection)
                            thisConnection.send(
                                Message(
                                    type = Message.Type.JOIN,
                                    timestamp = Date().time.toULong(),
                                    content = buildJsonObject {
                                        put("roomName", roomName)
                                        put("color", color)
                                    }
                                )
                            )
                            thisConnection.room = room
                            println("Waiting for start")
                            // WAIT FOR START, but only for creator
                            val start = incoming.receive() as? Frame.Text ?: throw Exception("expected start message")
                            println("Received start")
                            val m: Message = start.toMessage()
                            if (m.type == Message.Type.START) {
                                room.startGame()
                                // broadcast start to everyone
                                room.broadcast(
                                    Message(
                                        type = Message.Type.START,
                                        timestamp = Date().time.toULong(),
                                        content = Json.encodeToJsonElement(
                                            GameState.serializer(),
                                            room.gameState!!
                                        ).jsonObject
                                    )
                                )
                            } else {
                                thisConnection.sendError("expected start Message")
                            }
                        }
                    }
                    Message.Type.JOIN -> {
                        val roomName = msg.content["roomName"]?.jsonPrimitive?.content
                        // TODO verify room code
                        val room = rooms[roomName]
                        if (room == null) {
                            thisConnection.sendError("Room not found")
                        } else if (room.gameStarted) {
                            thisConnection.sendError("Game room has already started")
                        } else if (room.clients.size == 6) {
                            thisConnection.sendError("Game room is full")
                        } else {
                            val color = room.addClient(thisConnection)
                            msg.content["userName"]?.jsonPrimitive?.content?.let {
                                thisConnection.name = it
                            }
                            thisConnection.send(
                                Message(
                                    type = Message.Type.JOIN,
                                    timestamp = Date().time.toULong(),
                                    content = buildJsonObject {
                                        put("roomName", roomName)
                                        put("color", color)
                                    }
                                )
                            )
                            thisConnection.room = room
                        }
                    }
                    else -> {
                        thisConnection.sendError("Unexpected Message")
                    }
                }
                // Manage Game Data
                for (frame in incoming) {
                    frame as? Frame.Text ?: continue
                    val incomingMsg: Message = frame.toMessage()
                    when (incomingMsg.type) {
                        Message.Type.EXIT -> {
                            println("Received exit message from $thisConnection")
                            break
                        }
                        Message.Type.MOVE -> {
                            try {
                                val data =
                                    Json.decodeFromJsonElement(Content.MoveData.serializer(), incomingMsg.content)
                                val room = rooms[data.roomName]
                                room?.let {
                                    val newState = updateGameState(it.gameState, data.move)
                                    it.gameState = newState
                                    println("Broadcasting to ${it.roomName}, room=$it")
                                    // Broadcast to peeps
                                    it.broadcast(
                                        msg = Message(
                                            type = Message.Type.GAME,
                                            timestamp = Date().time.toULong(),
                                            content = Json.encodeToJsonElement(
                                                Content.GameData.serializer(),
                                                Content.GameData(
                                                    roomName = it.roomName,
                                                    gameState = newState
                                                )
                                            ).jsonObject
                                        )
                                    )
                                }
                            } catch (ex: Exception) {
                                ex.printStackTrace()
                            }
                        }
                        else -> {
                            println("Ignoring $incomingMsg")
                        } // ignore
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                println(e.localizedMessage)
            } finally {
                println("Removing $thisConnection!")
                val room = thisConnection.room
                room?.removeClient(thisConnection)
            }
        }
    }
}

const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
fun uuid(): String {
    val random = Random(System.currentTimeMillis())
    return (1..8)
        .map { random.nextInt(0, ALPHABET.length) }
        .map(ALPHABET::get)
        .joinToString("")
}

fun Frame.Text.toMessage(): Message {
    return Json.decodeFromString(Message.serializer(), readText())
}
