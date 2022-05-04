package views

import Session
import UserAction
import androidx.compose.runtime.*
import availableActions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import net.malkowscy.model.*
import net.malkowscy.model.State
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.*
import scope

@Composable
fun GameView(session: Session) {
    val scope = rememberCoroutineScope { Dispatchers.Default }
    val (gameState, setGameState) = remember { mutableStateOf(session.gameState) }
    scope.launch {
        while (true) {
            val msg = session.receive()
            if (msg.type == Message.Type.GAME) {
                println("Received ${msg.content}")
                val content = Json.decodeFromJsonElement(Content.GameData.serializer(), msg.content)
                println("Received data: $content")
                setGameState(content.gameState)
            }
        }
    }
    val me by derivedStateOf { gameState.players.find { it.name == session.userName } }
    Div(attrs = { style { display(DisplayStyle.Flex) } }) {
        Div(attrs = { classes(AppStylesheet.peerList) }) {
            gameState.players.forEachIndexed { idx, player ->
                if (player != me) { // Filter this user
                    PeerCard(gameState.currentPlayer == idx, player)
                }
            }
        }
        Div(attrs = { classes(AppStylesheet.middleCol) }) {
            me?.let { myself ->
                UserCard(myself)
                when (val state = gameState.currentState) {
                    is State.Turn -> {
                        if (state.player == myself) { // My Turn show my actions
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
                        if (state.player == myself) {
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
                        if (state.player == myself) {
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
                                    val choices = buildList {
                                        addAll(state.choices)
                                        add(myself.roles.first.role)
                                        add(myself.roles.second.role)
                                    }
                                    val chosen = choices.map { mutableStateOf(false) }
                                    Div {
                                        Text("Choose upto 2 influences to keep")
                                        choices.forEachIndexed { idx, choice ->
                                            MyCheckbox(choice.name, chosen[idx])
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
                        if (state.player == myself) {
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
                }

            }
        }
        Div(attrs = { classes(AppStylesheet.eventLog) }) {
            Div {
                Text("Event Log")
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
fun UserCard(me: Player) {
    Div(attrs = {
        style {
            border(2.px, LineStyle.Solid, Color.black);
            display(DisplayStyle.Flex); flexDirection(FlexDirection.Column)
            alignItems(AlignItems.Center); justifyContent(JustifyContent.Center)
        }
    }) {
        Span(attrs = { style { backgroundColor(Color.cyan); fontSize(38.px); } }) {
            Text("Your Influences")
        }
        Div(attrs = { style { display(DisplayStyle.Flex); width(70.percent) } }) {
            Div(attrs = {
                style {
                    width(50.percent); paddingBottom(50.percent);
                    backgroundColor(Color.red); marginRight(2.px)
                }
            }) {
                Span({ style { padding(15.px); fontSize(24.px) } }) {
                    Text(me.roles.first.role.toString())
                }
            }
            Div(attrs = {
                style {
                    width(50.percent); paddingBottom(50.percent);
                    backgroundColor(Color.purple); textAlign("center")
                }
            }) {
                Span({ style { padding(15.px); fontSize(24.px) } }) {
                    Text(me.roles.second.role.toString())
                }
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
fun PeerCard(
    isActivePlayer: Boolean,
    player: Player
) {
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
        }
        Div(attrs = { style { padding(10.px); width(20.percent) } }) {
            if (isActivePlayer) {
                Text("Active")
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
            availableActions.forEach {
                Button(attrs = {
                    onClick { event ->
                        when (it) {
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
                                setChosenAction(it)
                            }
                            UserAction.EXCHANGE -> {
                                scope.launch {
                                    // cant exactly send move exchange, might need an intermediate
//                                  session.sendMove(Move.Exchange())
                                }
                            }
                        }
                    }
                    style {
                        margin(5.px)
                        padding(5.px)
                    }
                }) {
                    Text(it.toString())
                }
            }
        }
        if (showPlayerList) {
            gameState.players.filterNot { it.name == session.userName }.forEach {
                Button(attrs = {
                    onClick { event ->
                        when (chosenAction) {
                            UserAction.ASSASSINATE -> {
                                scope.launch {
                                    session.sendMove(Move.Assassinate(me, it))
                                }
                            }
                            UserAction.COUP -> {
                                scope.launch {
                                    session.sendMove(Move.Coup(me, it))
                                }
                            }
                            UserAction.STEAL -> {
                                scope.launch {
                                    session.sendMove(Move.Steal(me, it))
                                }
                            }
                            else -> TODO()
                        }
                        setShowPlayerList(false)
                        setChosenAction(null)
                    }
                    style {
                        margin(5.px)
                        padding(5.px)
                    }
                }) {
                    Text(it.name)
                }
            }
        }
    }
}

@Composable
fun CounterActionsCard(session: Session, me: Player, move: Move) {
    Div {
        Text("${move.player.name} is attempting to do ${move::class.simpleName}...")
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

val String.css: CSSColorValue
    get() = Color(this)
