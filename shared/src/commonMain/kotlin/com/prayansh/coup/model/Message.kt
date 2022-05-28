package com.prayansh.coup.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
class Message(
    val type: Type,
    val content: Content,
    val timestamp: ULong
) {
    enum class Type {
        CONNECT, CREATE, JOIN, START,
        MOVE, // from client
        GAME, // from server
        EXIT, ERROR
    }
}

@Serializable
sealed class Content {
    @Serializable
    data class Connect(
        val serverName: String,
        val rooms: List<String>,
    ) : Content()

    @Serializable
    data class Create(
        val userName: String,
        val color: String
    ) : Content()

    @Serializable
    data class Join(
        val userName: String,
        val roomName: String,
        val color: String
    ) : Content()

    @Serializable
    data class MoveData(
        val roomName: String,
//        val player: Player,
        val move: Move
    ) : Content()

    @Serializable
    data class GameData(
        val roomName: String,
        val gameState: GameState
    ) : Content()

    @Serializable
    data class Initiate(
        val roomName: String
    ) : Content()

    @Serializable
    data class Exit(
        val roomName: String
    ) : Content()

    @Serializable
    data class Error(
        val errorMessage: String
    ): Content()
}
