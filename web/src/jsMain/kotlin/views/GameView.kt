package views

import AppStylesheet
import Session
import UserAction
import androidx.compose.runtime.*
import availableActions
import com.prayansh.coup.model.*
import com.prayansh.coup.model.State
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text
import scope

@Composable
fun GameView(session: Session) {
    val scope = rememberCoroutineScope { Dispatchers.Default }
    val (gameState, setGameState) = remember { mutableStateOf(session.gameState) }
    val (showCheatSheet, setShowCheatSheet) = remember { mutableStateOf(false) }
    scope.launch {
        while (true) {
            val msg = session.receive()
            if (msg.type == Message.Type.GAME) {
                Logger.debug("Received ${msg.content}")
                val content = Json.decodeFromJsonElement(Content.GameData.serializer(), msg.content)
                Logger.debug("Received data: $content")
                setGameState(content.gameState)
            }
        }
    }
    val me by mutableStateOf(gameState.players.find { it.name == session.userName }, referentialEqualityPolicy())
    Div(attrs = { style { display(DisplayStyle.Flex) } }) {
        if (showCheatSheet) {
            CheatSheet(setShowCheatSheet)
        }
        Div(attrs = { classes(AppStylesheet.peerList) }) {
            Span(attrs = { style { fontSize(38.px) } }) {
                Text("Players")
            }
            UserList(gameState.players, gameState.currentPlayer, me)
        }

        Div(attrs = { classes(AppStylesheet.middleCol) }) {
            ClickableButton("Show cheatsheet") {
                setShowCheatSheet(!showCheatSheet)
            }
            Span(attrs = { style { fontSize(24.px); justifyContent(JustifyContent.Center) } }) {
                Text("Cards in deck: ${gameState.deck.size}")
            }
            me?.let { myself ->
                UserCard(myself)
                GameState(session, gameState, myself)
            }
        }
        Div(attrs = { classes(AppStylesheet.eventLog) }) {
            Div {
                Span(attrs = { style { fontSize(38.px); } }) {
                    Text("Event Log")
                }
            }
            gameState.logs.forEach {
                Div {
                    Text(it)
                }
            }
        }
    }
}

@Composable
fun GameState(session: Session, gameState: GameState, myself: Player) {
    when (val state = gameState.currentState) {
        is State.Turn -> {
            if (state.player sameAs myself) { // My Turn show my actions
                ActionsCard(session, gameState)
            } else {
                Text("Waiting on ${state.player.name}'s turn...")
            }
        }
        is State.WaitCounter -> {
            if (state.players.contains(myself)) { // Waiting on me
                CounterActionsCard(session, myself, state.move)
            } else {
                Text("Waiting on ${state.players.joinToString(", ") { it.name }}...")
            }
        }
        is State.ShowInfluence -> {
            if (state.player sameAs myself) {
                Div {
                    val (r1, r2) = myself.roles
                    Text("Choose role to prove your action")
                    if (r1.alive) {
                        ClickableButton(r1.role.name) {
                            scope.launch {
                                session.sendMove(Move.Show(myself, r1.role, state.challenge))
                            }
                        }
                    }
                    if (r2.alive) {
                        ClickableButton(r2.role.name) {
                            scope.launch {
                                session.sendMove(Move.Show(myself, r2.role, state.challenge))
                            }
                        }
                    }
                }
            } else {
                Text("Waiting on ${state.player.name}'s to show influence...")
            }
        }
        is State.ExchangeInfluence -> {
            if (state.player sameAs myself) {
                when (val m = state.move) {
                    is Move.Show -> {
                        val choice = state.choices[0]
                        Div {
                            Text("Confirm influence to replace ${m.influence} with")
                            ClickableButton(choice.name) {
                                scope.launch {
                                    session.sendMove(Move.Exchange(myself, listOf(m.influence to choice)))
                                }
                            }
                        }
                    }
                    is Move.Exchange -> {
                        val myRoles = myself.roles.toList()
                        val aliveCount = myRoles.count { it.alive }
                        val roles = buildList {
                            addAll(state.choices)
                            myRoles.forEach {
                                if (it.alive) {
                                    add(it.role)
                                }
                            }
                        }
                        val choices = roles.map { it to mutableStateOf(false) }
                        val error = mutableStateOf("")
                        Div {
                            Text("Choose upto $aliveCount influences to keep ${error.value}")
                            Div(attrs = {
                                style {
                                    display(DisplayStyle.Flex); flexDirection(FlexDirection.Column)
                                }
                            }) {
                                val disable = remember { mutableStateOf(false) }
                                val chosenCount = choices.count { it.second.value }
                                disable.value = chosenCount >= aliveCount
                                choices.forEach { choice ->
                                    MyCheckbox(choice.first.name, choice.second, !choice.second.value && disable.value)
                                }
                            }
                            ClickableButton("Submit") {
                                scope.launch {
                                    val changes =
                                        choices.filter { it.second.value }
                                            .zip(choices.filter { !it.second.value }) { a, b ->
                                                b.first to a.first
                                            }
                                    if (changes.size == aliveCount) {
                                        session.sendMove(Move.Exchange(myself, changes))
                                    } else {
                                        error.value = "(Select only $aliveCount influence(s))"
                                    }
                                }
                            }
                        }
                    }
                    else -> {

                    }
                }
            } else {
                Text("Waiting on ${state.player.name}'s to exchange influence...")
            }
        }
        is State.WaitSurrender -> {
            if (state.player sameAs myself) {
                Div {
                    val (r1, r2) = myself.roles
                    Text("Choose role to surrender")
                    if (r1.alive) {
                        ClickableButton(r1.role.name) {
                            scope.launch {
                                session.sendMove(Move.Surrender(myself, r1.role))
                            }
                        }
                    }
                    if (r2.alive) {
                        ClickableButton(r2.role.name) {
                            scope.launch {
                                session.sendMove(Move.Surrender(myself, r2.role))
                            }
                        }
                    }
                }
            } else {
                Text("Waiting on ${state.player.name}'s to surrender an influence...")
            }
        }
        is State.GameOver -> {
            Div {
                Text("${state.winner.name} has won the game!!")
            }
        }
    }
}

@Composable
fun UserCard(me: Player) {
    Div(attrs = {
        style {
            border(2.px, LineStyle.Solid, Color.black);
            display(DisplayStyle.Flex); flexDirection(FlexDirection.Column)
            alignItems(AlignItems.Center); justifyContent(JustifyContent.Center)
            backgroundColor(me.color.css)
        }
    }) {
        Span(attrs = { style { fontSize(38.px); } }) {
            Text("Your Influences")
        }
        Div(attrs = { style { display(DisplayStyle.Flex); width(90.percent) } }) {
            me.roles.toList().forEach {
                RoleView(it)
            }
        }
        Div {
            Span({ style { padding(15.px); fontSize(24.px) } }) {
                Text("Coins: ${me.coins}")
            }
        }
    }
}

@Composable
fun UserList(
    players: List<Player>,
    currentPlayer: Int,
    thisPlayer: Player?,
) {
    players.forEachIndexed { idx, player ->
        val isActivePlayer = currentPlayer == idx
        val activeInfluences = listOf(player.roles.first, player.roles.second).count { it.alive }
        Div(attrs = { style { backgroundColor(player.color.css); display(DisplayStyle.Flex); flexDirection(FlexDirection.Row) } }) {
            Div(attrs = { style { padding(10.px); width(80.percent) } }) {
                Div {
                    Span(attrs = { style { fontSize(24.px) } }) {
                        Text(player.name)
                    }
                }
                Div {
                    Span(attrs = { style { fontSize(18.px) } }) {
                        Text("Influences: $activeInfluences")
                    }
                }
                Div {
                    Span(attrs = { style { fontSize(18.px) } }) {
                        Text("Coins: ${player.coins}")
                    }
                }
                val dead = player.roles.toList().fold("") { acc, it ->
                    if (!it.alive) {
                        acc + it.role + ","
                    } else {
                        acc
                    }
                }
                if (dead.isNotEmpty()) {
                    Div {
                        Span(attrs = { style { fontSize(18.px) } }) {
                            Text("Dead: $dead")
                        }
                    }
                }
            }
            Div(attrs = { style { padding(10.px); width(20.percent) } }) {
                if (player sameAs thisPlayer) {
                    Text("(You)")
                }
                if (isActivePlayer) {
                    Text("(Active)")
                }
            }
        }
    }
}

@Composable
fun ActionsCard(session: Session, gameState: GameState) {
    Div {
        val (showPlayerList, setShowPlayerList) = remember { mutableStateOf(false) }
        val (chosenAction, setChosenAction) = remember { mutableStateOf<UserAction?>(null) }
        val me = gameState.players.find { it.name == session.userName }!!
        Div {
            // TODO cleanup this code a bit
            val availableActions = availableActions(me.coins)
            availableActions.forEach { userAction ->
                ClickableButton(userAction.toString()) {
                    when (userAction) {
                        UserAction.INCOME -> {
                            scope.launch {
                                session.sendMove(Move.Income(me))
                            }
                        }
                        UserAction.FOREIGN_AID -> {
                            scope.launch {
                                session.sendMove(Move.ForeignAid(me))
                            }
                        }
                        UserAction.TAX -> {
                            scope.launch {
                                session.sendMove(Move.Tax(me))
                            }
                        }
                        UserAction.STEAL, UserAction.ASSASSINATE, UserAction.COUP -> {
                            // choose user first
                            setShowPlayerList(true)
                            setChosenAction(userAction)
                        }
                        UserAction.EXCHANGE -> {
                            scope.launch {
                                // cant exactly send move exchange, might need an intermediate
                                session.sendMove(Move.Exchange(me, emptyList()))
                            }
                        }
                    }
                }
            }
        }
        if (showPlayerList) {
            ColumnDiv {
                Span(attrs = { style { fontSize(18.px) } }) {
                    Text("Choose a player...")
                }
                Div {
                    gameState.players.filterNot { it.name == session.userName }.forEach { victim ->
                        ClickableButton(victim.name) {
                            when (chosenAction) {
                                UserAction.ASSASSINATE -> {
                                    scope.launch {
                                        session.sendMove(Move.Assassinate(me, victim))
                                    }
                                }
                                UserAction.COUP -> {
                                    scope.launch {
                                        session.sendMove(Move.Coup(me, victim))
                                    }
                                }
                                UserAction.STEAL -> {
                                    scope.launch {
                                        session.sendMove(Move.Steal(me, victim))
                                    }
                                }
                                else -> {
                                    // NO-OP
                                }
                            }
                            setShowPlayerList(false)
                            setChosenAction(null)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CounterActionsCard(session: Session, me: Player, move: Move) {
    ColumnDiv {
        Text("${move.player.name} is attempting to do ${move::class.simpleName}...")
        RowDiv {
            if (move.isChallengeable()) {
                ClickableButton("Challenge") {
                    scope.launch {
                        session.sendMove(Move.Challenge(me, move))
                    }
                }
            }
            if (move.isBlockable()) {
                ClickableButton("Block") {
                    scope.launch {
                        session.sendMove(Move.Block(me, move))
                    }
                }
            }
            ClickableButton("Pass") {
                scope.launch {
                    session.sendMove(Move.Pass(me, move))
                }
            }
        }
    }
}

val String.css: CSSColorValue
    get() = Color(this)
