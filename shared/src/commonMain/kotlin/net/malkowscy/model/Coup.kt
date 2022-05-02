package net.malkowscy.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = ActionSerializer::class)
sealed interface Action

class ActionSerializer : KSerializer<Action> {
    override val descriptor: SerialDescriptor
        get() = PrimitiveSerialDescriptor("Action", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): Action {
        return UserAction.INCOME // TODO()
    }

    override fun serialize(encoder: Encoder, value: Action) {
        when (value) {
            is UserAction -> {
                UserAction.serializer().serialize(encoder, value)
            }
            is CounterAction -> {
                CounterAction.serializer().serialize(encoder, value)
            }
            is GameAction -> {
                GameAction.serializer().serialize(encoder, value)
            }
        }
    }

}

@Serializable
enum class UserAction : Action {
    INCOME, TAX, FOREIGN_AID,
    STEAL,
    EXCHANGE,
    ASSASSINATE, COUP
}

@Serializable
enum class CounterAction : Action {
    BLOCK_FOREIGN_AID, BLOCK_STEAL, BLOCK_ASSASSINATION, CHALLENGE, PASS
}

@Serializable
enum class GameAction : Action {
    TURN, SURRENDER /* give up influence */
}

@Serializable
sealed class Move {

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
    data class Steal(override val player: Player, val victim: Player) : Move() {
        override val description: String = "${player.name} stole from ${victim.name} (2 coins)"
    }

    @Serializable
    data class Exchange(override val player: Player, val oldRole: Role, val newRole: Role) : Move() {
        override val description: String = "${player.name} exchanged their role"
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
    data class Surrender(override val player: Player, val influence: Role) : Move() {
        override val description: String = "${player.name} surrendered their ${influence.name} role"
    }

    @Serializable
    data class Show(override val player: Player, val influence: Role, val move: Move): Move() {
        override val description: String = "${player.name} showed their ${influence.name} role"
    }

}

@Serializable
enum class Role {
    POLITICIAN, SNIPER, DIPLOMAT, GENERAL, BODYGUARD
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
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as Player

        if (name != other.name) return false
        if (color != other.color) return false

        return true
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + color.hashCode()
        return result
    }
}

@Serializable
data class GameState(
    val deck: List<Role>,
    val currentPlayer: Int,
    val players: List<Player>,
    val currentAction: Action = GameAction.TURN,
    val currentMove: Move?, // null when it's someone's turn
    val currentState: State,
    val logs: List<String>
)

fun availableActions(coins: Int): List<Action> {
    return buildList {
        if (coins >= 10) {
            add(UserAction.COUP)
        } else {
            add(UserAction.TAX)
            add(UserAction.INCOME)
            add(UserAction.FOREIGN_AID)
            add(UserAction.STEAL)
            if (coins >= 7) {
                add(UserAction.COUP)
            }
            if (coins >= 3) {
                add(UserAction.ASSASSINATE)
            }
        }
    }
}

fun Action.isBlockable(): Boolean {
    return when (this) {
        UserAction.FOREIGN_AID,
        UserAction.STEAL,
        UserAction.ASSASSINATE
        -> true
        else -> false
    }
}


fun Action.isChallengeable(): Boolean {
    return when (this) {
        UserAction.TAX,
        UserAction.STEAL,
        UserAction.EXCHANGE,
        UserAction.ASSASSINATE,
        -> true
        else -> false
    }
}

@Serializable
sealed class State {
    /*
    Turn -> WaitCounter
    except Turn(): Income -> Turn(nextPlayer)
    except Turn(): Coup   -> WaitSurrender(coupee)

     */


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

    // `player` is showing influence as a result of `challenge`
    @Serializable
    data class ShowInfluence(val player: Player, val challenge: Move.Challenge) : State() {

    }

    // `player` is choosing influence as a result of `move`
    @Serializable
    data class ExchangeInfluence(val player: Player, val choices: List<Role>, val move: Move) : State() {

    }
}

/*
currentAction = turn(p1)
p1's screen
-> available moves = f(coins)
everyone else
-> waiting

---> p1 chooses a1

currentAction = a1(p1)
p1's screen
-> waiting
everyone else
-> block(a1)?, challenge, pass

---> if block(a1) by pX
currentAction = block(a1(pX))
pX's screen
-> waiting
p1's screen
-> challenge, pass
everyone else
-> waiting

---> if challenge by pX
currentAction = challenge(a1(pX))
pX's screen
-> waiting
p1's screen
-> choose influence to show / discard influence
everyone else
-> waiting

---> pass by all
gameStateUpdated(pass) + effect(a1)
currentAction = turn(p2)

 */
