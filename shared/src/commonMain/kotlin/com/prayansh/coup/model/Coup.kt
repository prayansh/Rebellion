package com.prayansh.coup.model

import kotlinx.serialization.Serializable

@Serializable
sealed class Move {

    interface Challengeable {
        abstract val completed: String
    }

    abstract val description: String // string description of the move
    abstract val player: Player // move issuer

//    @Serializable
//    data class Turn(val player: Player) : Move() {
//        override val description: String = "${player.name}'s turn"
//    }

    @Serializable
    data class Income(override val player: Player) : Move() {
        override val description: String = "${player.name} collected income (1 coin)"
    }

    @Serializable
    data class ForeignAid(override val player: Player) : Move() {
        override val description: String = "${player.name} collected foreign aid (2 coins)"
    }

    @Serializable
    data class Tax(override val player: Player) : Move() {
        override val description: String = "${player.name} collected tax (3 coins)"
    }

    @Serializable
    data class Exchange(override val player: Player, val keep: List<Role>, val discard: List<Role>) : Move() {
        override val description: String = "${player.name} exchanged their role"
    }

    @Serializable
    data class Steal(override val player: Player, val victim: Player) : Move() {
        override val description: String = "${player.name} stole from ${victim.name} (2 coins)"
    }

    @Serializable
    data class Assassinate(override val player: Player, val victim: Player) : Move() {
        override val description: String = "${player.name} shot ${victim.name}"
    }

    @Serializable
    data class Coup(override val player: Player, val victim: Player) : Move() {
        override val description: String = "${player.name} overthrew ${victim.name}"
    }

    @Serializable
    data class Challenge(override val player: Player, val action: Move) : Move() {
        override val description: String = "${player.name} challenged \"${action.description}\""
    }

    @Serializable
    data class Pass(override val player: Player, val action: Move) : Move() {
        override val description: String = "${player.name} passed \"${action.description}\""
    }

    @Serializable
    data class Block(override val player: Player, val action: Move) : Move() {
        override val description: String = "${player.name} blocked \"${action.description}\""
    }

    @Serializable
    data class Surrender(override val player: Player, val role: Role) : Move() {
        override val description: String = "${player.name} surrendered their ${role.name} role"
    }

    @Serializable
    data class Show(override val player: Player, val influence: Role, val challenge: Challenge) : Move() {
        override val description: String = "${player.name} showed their ${influence.name} role"
    }

    // is this move challengeable
    fun isChallengeable(): Boolean {
        return when (this) {
            is Assassinate,
            is Exchange,
            is Steal,
            is Block,
            is Tax -> {
                true
            }
            else -> false
        }
    }

    // Is this move blockable
    fun isBlockable(): Boolean {
        return when (this) {
            is Assassinate,
            is ForeignAid,
            is Steal -> {
                true
            }
            else -> false
        }
    }

    // who can perform this move
    fun proofList(): List<Role> {
        return when (this) {
            is Assassinate -> listOf(Role.SNIPER)
            is Exchange -> listOf(Role.DIPLOMAT)
            is Steal -> listOf(Role.GENERAL)
            is Tax -> listOf(Role.POLITICIAN)
            is Block -> when (val a = this.action) {
                is Assassinate -> listOf(Role.BODYGUARD)
                is ForeignAid -> listOf(Role.POLITICIAN)
                is Steal -> listOf(Role.DIPLOMAT, Role.GENERAL)
                else -> emptyList()
            }
            else -> emptyList()
        }
    }

}

@Serializable
enum class Role(val color: String) {
    POLITICIAN(RoleColors[3]),
    SNIPER(RoleColors[1]),
    DIPLOMAT(RoleColors[2]),
    GENERAL(RoleColors[4]),
    BODYGUARD(RoleColors[0])
}

@Serializable
data class Influence(
    val role: Role,
    val alive: Boolean
)

@Serializable
data class Player(
    val name: String,
    val roles: Pair<Influence, Influence>,
    val coins: Int,
    val color: String
) {
    infix fun sameAs(other: Player?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        if (name != other.name) return false
        if (color != other.color) return false

        return true
    }
}

@Serializable
data class GameState(
    val deck: List<Role>,
    val currentPlayer: Int,
    val players: List<Player>,
    val currentState: State,
    val logs: List<String>
) {
//    fun update(block: GameState.() -> Unit): GameState = this.copy().apply(block) TODO work on this later
}

@Serializable
sealed class State {
    // Its X's turn
    @Serializable
    data class Turn(val player: Player) : State() {

    }

    // Waiting on `players` for countering on `move`
    // when list empty means it passed (no block / no challenge)
    @Serializable
    data class WaitCounter(val players: List<Player>, val move: Move) : State() {

    }

    // Waiting on `player` to surrender influence due to `move`
    @Serializable
    data class WaitSurrender(val player: Player, val move: Move) : State() {

    }

//    // `player` is blocking `move`
//    // can be challenged, if not `move` is nullified and next turn
//    @Serializable
//    data class Blocking(val player: Player, val move: Move) : State() {
//
//    } // could this be replaced by WaitCounter(blockee, Move.Block())

    // `player` is showing influence as a result of `challenge`, influence must be oneOf `proofList`
    @Serializable
    data class ShowInfluence(val player: Player, val challenge: Move.Challenge, val proofList: List<Role>) : State() {

    }

    // `player` is choosing influence as a result of `move`
    @Serializable
    data class ExchangeInfluence(val player: Player, val choices: List<Role>, val move: Move) : State() {

    }

    @Serializable
    data class GameOver(val winner: Player) : State()
}

fun flavorLog(move: Move, isDeadFlavor: Boolean = false): String {
    return if (isDeadFlavor) {
        when (move) {
            is Move.Assassinate -> "Stop it ${move.player.name}!! smh, ${move.victim.name} is already dead"
            is Move.Steal -> "Grave Robbing!! smh, ${move.victim} is already dead"
//            is Move.Block -> "Grave Robbing!! smh, ${move.victim} is already dead"
//            is Move.Challenge -> "Grave Robbing!! smh, ${move.victim} is already dead"
            else -> ""
        }
    } else {
        when (move) {
            is Move.Assassinate -> "TODO()"
            is Move.Block -> "TODO()"
            is Move.Challenge -> "TODO()"
            is Move.Coup -> "TODO()"
            is Move.Exchange -> "TODO()"
            is Move.ForeignAid -> "TODO()"
            is Move.Income -> "TODO()"
            is Move.Pass -> "TODO()"
            is Move.Show -> "TODO()"
            is Move.Steal -> "TODO()"
            is Move.Surrender -> "TODO()"
            is Move.Tax -> "TODO()"
        }
    }
}
