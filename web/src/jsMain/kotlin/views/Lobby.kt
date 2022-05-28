package views

import ScreenState
import Session
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.prayansh.coup.model.Content
import com.prayansh.coup.model.GameState
import com.prayansh.coup.model.Message
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text
import scope

@Composable
fun LobbyScreen(setScreenState: (ScreenState) -> Unit, setErrorMsg: (String) -> Unit) {
    Div {
        ClickableButton("Create") {
            setScreenState(ScreenState.CREATE)
        }
        ClickableButton("Join") {
            setScreenState(ScreenState.JOIN)
        }
    }
}

@Composable
fun CreateRoom(session: Session, setScreenState: (ScreenState) -> Unit, setErrorMsg: (String) -> Unit) {
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
                    val error = session.createRoom(username.value)
                    if (error != null) {
                        setScreenState(ScreenState.ERROR)
                        setErrorMsg(error)
                    } else {
                        waiting.value = true
                    }
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
            Text("Waiting for players (Code is ${session.roomName})")
            ClickableButton("Start", onClick = { event ->
                scope.launch {
                    session.start()
                    val msg = session.receive()
                    Logger.debug("Received ${msg.content}")
                    if (msg.type == Message.Type.START) {
                        val content = msg.content as Content.GameData
                        session.gameState = content.gameState
                        setScreenState(ScreenState.GAME)
                    }
                }
            })
        }
    }
}

@Composable
fun JoinRoom(session: Session, setScreenState: (ScreenState) -> Unit, setErrorMsg: (String) -> Unit) {
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
                    val error = session.joinRoom(roomCode.value, username.value)
                    if (error != null) {
                        setScreenState(ScreenState.ERROR)
                        setErrorMsg(error)
                    } else {
                        waiting.value = true
                    }
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
                Logger.debug("Received ${msg.content}")
                if (msg.type == Message.Type.START) {
                    val content = msg.content as Content.GameData
                    session.gameState = content.gameState
                    setScreenState(ScreenState.GAME)
                }
            }
        }
    }
}

@Composable
fun ErrorScreen(msg: String) {
    Div {
        Span(attrs = { style { fontSize(24.px); } }) {
            Text(msg)
        }
    }
}
