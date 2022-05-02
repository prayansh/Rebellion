package views

import ScreenState
import Session
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import net.malkowscy.model.GameState
import net.malkowscy.model.Message
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.*
import scope

@Composable
fun LobbyScreen(setScreenState: (ScreenState) -> Unit) {
    Div {
        Button(
            attrs = {
                onClick { event ->
                    setScreenState(ScreenState.CREATE)
                }
            }
        ) {
            Text("Create")
        }
        Button(
            attrs = {
                onClick { event ->
                    setScreenState(ScreenState.JOIN)
                }
            }
        ) {
            Text("Join")
        }
    }
}

@Composable
fun CreateRoom(session: Session, setScreenState: (ScreenState) -> Unit) {
    val username = remember { mutableStateOf("") }
    val waiting = remember { mutableStateOf(false) }
    if (!waiting.value) {
        Div(
            attrs = {
                style {
//                justifyContent(JustifyContent.Center)
                    display(DisplayStyle.Flex)
                    flexDirection(FlexDirection.Column)
                }
            }
        ) {
            InputText("Your name", username)
            ClickableButton("Create", onClick = { event ->
                scope.launch {
                    session.createRoom(username.value)
                    waiting.value = true
                }
            })
        }
    } else {
        Div(
            attrs = {
                style {
//                justifyContent(JustifyContent.Center)
                    display(DisplayStyle.Flex)
                    flexDirection(FlexDirection.Column)
                    width(300.px)
                }
            }
        ) {
            Text("Waiting for players")
            ClickableButton("Start", onClick = { event ->
                scope.launch {
                    session.start()
                    val msg = session.receive()
                    println("Received ${msg.content}")
                    if (msg.type == Message.Type.START) {
                        session.gameState = Json.decodeFromJsonElement(GameState.serializer(), msg.content)
                        setScreenState(ScreenState.GAME)
                    }
                }
            })
        }
    }
}

@Composable
fun JoinRoom(session: Session, setScreenState: (ScreenState) -> Unit) {
    val username = remember { mutableStateOf("") }
    val roomCode = remember { mutableStateOf("") }
    val waiting = remember { mutableStateOf(false) }
    if (!waiting.value) {
        Div(
            attrs = {
                style {
                    display(DisplayStyle.Flex)
                    flexDirection(FlexDirection.Column)
                    width(300.px)
                }
            }
        ) {
            InputText(
                label = "Your name",
                text = username
            )
            InputText(
                label = "Room code",
                text = roomCode
            )
            ClickableButton("Join", onClick = { event ->
                scope.launch {
                    session.joinRoom(roomCode.value, username.value)
                    waiting.value = true
                }
            })
        }
    } else {
        Div(
            attrs = {
                style {
//                justifyContent(JustifyContent.Center)
                    display(DisplayStyle.Flex)
                    flexDirection(FlexDirection.Column)
                    width(300.px)
                }
            }
        ) {
            Text("Waiting for start")
            scope.launch {
                val msg = session.receive()
                println("Received ${msg.content}")
                if (msg.type == Message.Type.START) {
                    session.gameState = Json.decodeFromJsonElement(GameState.serializer(), msg.content)
                    setScreenState(ScreenState.GAME)
                }
            }
        }
    }
}
