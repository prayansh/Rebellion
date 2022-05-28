package com.prayansh.coup.server.plugins

import com.prayansh.coup.model.Content
import com.prayansh.coup.model.GameState
import com.prayansh.coup.model.Message
import com.prayansh.coup.server.envVar
import com.prayansh.coup.server.session.Connection
import com.prayansh.coup.server.session.Room
import com.prayansh.coup.server.session.RoomsManager
import com.prayansh.coup.server.updateGameState
import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.RedisClient
import io.lettuce.core.api.coroutines
import kotlinx.serialization.json.*
import java.time.Duration
import java.util.*
import kotlin.random.Random


@ExperimentalLettuceCoroutinesApi
fun Application.configureSockets(
    redisClient: RedisClient
) {
    val publishRedis = redisClient.connectPubSub()
    val subscribeRedis = redisClient.connectPubSub()
    val storeRedis = redisClient.connect().coroutines()
    install(WebSockets) {
        pingPeriod = Duration.ofSeconds(15)
        timeout = Duration.ofSeconds(15)
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }
    routing {
        val roomsManager = RoomsManager(storeRedis, subscribeRedis, publishRedis)
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
                            put("server", envVar("NAME", "unknown"))
                            put("rooms", buildJsonArray {
//                                rooms.keys.forEach {
//                                    add(it)
//                                }
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
                        val room = roomsManager.createRoom()
                        val name = msg.content["userName"]?.jsonPrimitive?.content ?: "unknown_name"
                        val color = room.addClient(thisConnection, name)
                        room.persistState()
                        thisConnection.send(
                            Message(
                                type = Message.Type.JOIN,
                                timestamp = Date().time.toULong(),
                                content = buildJsonObject {
                                    put("roomName", room.roomName)
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
                            room.persistState()
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
                    Message.Type.JOIN -> {
                        try {
                            val roomName = msg.content["roomName"]?.jsonPrimitive?.content ?: ""
                            val name = msg.content["userName"]?.jsonPrimitive?.content ?: "unknown_name"
                            val room = roomsManager.findRoom(roomName)
                            val color = room.addClient(thisConnection, name)
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
                        } catch (ex: RoomsManager.RoomException) {
                            thisConnection.sendError(ex.roomError)
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

                                val room = roomsManager.rooms[data.roomName]
                                room?.let {
                                    it.retrieveState()
                                    val newState = updateGameState(it.gameState, data.move)
                                    it.gameState = newState
                                    println("Broadcasting to ${it.roomName}, room=$it")
                                    it.persistState()
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
//                e.printStackTrace()
                println("ERROR: ${e.localizedMessage}")
            } finally {
                println("Removing $thisConnection!")
                val room = thisConnection.room
                room?.removeClient(thisConnection)
            }
        }
    }
}

fun Frame.Text.toMessage(): Message {
    return Json.decodeFromString(Message.serializer(), readText())
}
