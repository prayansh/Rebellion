import kotlinx.serialization.Serializable

@Serializable
enum class UserAction {
    INCOME, TAX, FOREIGN_AID,
    STEAL,
    EXCHANGE,
    ASSASSINATE, COUP
}

//@Serializable
//enum class CounterAction : Action {
//    BLOCK_FOREIGN_AID, BLOCK_STEAL, BLOCK_ASSASSINATION, CHALLENGE, PASS
//}

//@Serializable
//enum class GameAction : Action {
//    TURN, SURRENDER /* give up influence */
//}

interface Act {
    val name: String
    suspend fun onClick(session: Session)
}

fun UserAction.isBlockable(): Boolean {
    return when (this) {
        UserAction.FOREIGN_AID,
        UserAction.STEAL,
        UserAction.ASSASSINATE
        -> true
        else -> false
    }
}


fun UserAction.isChallengeable(): Boolean {
    return when (this) {
        UserAction.TAX,
        UserAction.STEAL,
        UserAction.EXCHANGE,
        UserAction.ASSASSINATE,
        -> true
        else -> false
    }
}


fun availableActions(coins: Int): List<UserAction> {
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
