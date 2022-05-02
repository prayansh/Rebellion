import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import io.ktor.client.*
import io.ktor.client.engine.js.*
import io.ktor.client.features.websocket.*
import kotlinx.browser.window
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.await
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import net.malkowscy.model.*
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.renderComposable
import views.*

object AppStylesheet : StyleSheet() {

    init {
        "*" style {
        }
        "body" style {
            backgroundColor(Color("#222222"))
            color(Color.white)
        }
    }

    val peerList by style {
        display(DisplayStyle.Flex)
        flexDirection(FlexDirection.Column)
        width(30.percent)
        minHeight(100.percent)

        self + type("div") style {
            flex(1)
            display(DisplayStyle.Flex)
            flexDirection(FlexDirection.Column)
            justifyContent(JustifyContent.Center)
        }
    }

    val middleCol by style {
        display(DisplayStyle.Flex)
        flexDirection(FlexDirection.Column)
        width(40.percent)
        minHeight(100.percent)
    }

    val eventLog by style {
        display(DisplayStyle.Flex)
        flexDirection(FlexDirection.Column)
        width(30.percent)
        minHeight(100.percent)
    }

}

val client = HttpClient(Js) {
    install(WebSockets)
}

val p1 = Player("Pray", Pair(Influence(Role.BODYGUARD, true), Influence(Role.BODYGUARD, true)), 2, "#12CBC4")
val p2 = Player("Ming", Pair(Influence(Role.POLITICIAN, true), Influence(Role.SNIPER, true)), 4, "#9980FA")
val p3 = Player("Prateek", Pair(Influence(Role.DIPLOMAT, true), Influence(Role.GENERAL, true)), 3, "#FFC312")

enum class ScreenState {
    LOBBY, JOIN, CREATE, GAME
}

val scope = MainScope()
fun main() {

    val readChannel = Channel<String>()
    val sendChannel = Channel<String>()
    scope.launch {
        client.setupSession(readChannel, sendChannel)
    }
    val session = Session(readChannel, sendChannel)
    session.registerObserver {
        println(it)
    }
    scope.launch {
        session.initiateSession()
    }
    var roomCode = mutableStateOf("")
    renderComposable(rootElementId = "root") {
        val (screenState, setScreenState) = remember { mutableStateOf(ScreenState.LOBBY) }
        Style(AppStylesheet)
        when (screenState) {
            ScreenState.LOBBY -> {
                LobbyScreen(setScreenState)
            }
            ScreenState.JOIN -> {
                JoinRoom(session, setScreenState)
            }
            ScreenState.CREATE -> {
                CreateRoom(session, setScreenState)
            }
            ScreenState.GAME -> {
                GameView(session)
            }
        }
    }
}
