package com.prayansh.coup.model.analytics

import com.prayansh.coup.model.GameState
import com.prayansh.coup.model.Move
import kotlinx.serialization.Serializable

@Serializable
data class WebpageRequested(
    val url: String
)

@Serializable
data class ApplicationStarted(
    val id: String
)

@Serializable
data class ApplicationError(
    val cause: String
)

@Serializable
data class GameCreated(
    val roomName: String,
    val creator: String
)

@Serializable
data class MoveProperties(
    val roomName: String,
    val gameState: GameState,
    val move: Move,
)

@Serializable
data class GameUpdated(
    val roomName: String,
    val gameState: GameState,

)
