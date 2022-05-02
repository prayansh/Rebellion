package views

import Session
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import net.malkowscy.model.*
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text

@Composable
fun GameView(session: Session) {
    val (gameState, setGameState) = remember { mutableStateOf(session.gameState) }
    val me = derivedStateOf { gameState.players.find { it.name == session.userName } }
    Div(attrs = { style { display(DisplayStyle.Flex) } }) {
        Div(attrs = { classes(AppStylesheet.peerList) }) {
            gameState.players.forEachIndexed { idx, player ->
                if (player != me.value) { // Filter this user
                    PeerCard(gameState.currentPlayer == idx, player)
                }
            }
        }
        Div(attrs = { classes(AppStylesheet.middleCol) }) {
            me.value?.let {
                UserCard(it)
                ActionsCard()
            }
        }
        Div(attrs = { classes(AppStylesheet.eventLog) }) {
            Div {
                Text("Event Log")
            }
            gameState.logs.forEach {
                Div {
                    Text(it.toString())
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
fun ActionsCard() {
    Div(
    ) {
        val availableActions = listOf(UserAction.values().toList(), CounterAction.values().toList()).flatten()
        availableActions.forEach {
            Button(attrs = {
                onClick { event ->
                    println("${it.name} clicked at ${event.movementX}, ${event.movementY}")
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

val String.css: CSSColorValue
    get() = Color(this)
