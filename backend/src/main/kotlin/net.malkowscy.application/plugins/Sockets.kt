package net.malkowscy.application.plugins

import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.pubsub.RedisPubSubAdapter
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection
import kotlinx.coroutines.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.time.Duration
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.future.await
import kotlinx.serialization.json.*
import net.malkowscy.model.*
import kotlin.collections.LinkedHashMap
import kotlin.random.Random

class Connection(val session: DefaultWebSocketSession) {
    companion object {
        var lastId = AtomicInteger(0)
    }

    var room: Room? = null

    var name = "user${lastId.getAndIncrement()}"
    var color = "#000000"

    suspend fun send(msg: Message) {
        session.send(msg)
    }

    suspend fun DefaultWebSocketSession.send(msg: Message) {
        send(Json.encodeToString(msg))
    }

    suspend fun sendError(msg: String) {
        send(
            Message(
                type = Message.Type.ERROR,
                timestamp = Date().time.toULong(),
                content = buildJsonObject {
                    put("msg", msg)
                }
            )
        )
    }

}

class Room(
    val roomName: String,
    val subscribeRedis: StatefulRedisPubSubConnection<String, String>,
    val publishRedis: StatefulRedisPubSubConnection<String, String>,
    val scope: CoroutineScope = CoroutineScope(Dispatchers.IO),
) {
    val clients: MutableSet<Connection> = Collections.synchronizedSet(LinkedHashSet())

    val colorSet = arrayOf(
        "#12CBC4",
        "#9980FA",
        "#FFC312",
        "#C4E538",
        "#FDA7DF",
        "#ED4C67",
        "#EE5A24",
        "#009432",
        "#0652DD",
        "#833471"
    )

    val size get() = clients.size
    lateinit var gameState: GameState

    init {
        subscribeToChannel(roomName)
    }

    fun addClient(connection: Connection): String {
        clients.add(connection)
        val color = colorSet[size - 1]
        connection.color = color
        return color
    }

    fun addClient(session: DefaultWebSocketSession, userName: String): Connection {
        val color = colorSet[size - 1]
        val connection = Connection(session).apply {
            this.color = color
            this.name = userName
        }
        clients.add(connection)
        return connection
    }

    fun removeClient(connection: Connection) {
        clients.remove(connection)
    }

    fun startGame() {
        gameState = newGameState(clients)
    }

    @ExperimentalLettuceCoroutinesApi
    suspend fun broadcast(msg: Message, except: Connection? = null) {
        val encoded = Json.encodeToString(msg)
        publishRedis.async().publish(roomName, encoded).await()
    }

    fun subscribeToChannel(channelName: String) {
        subscribeRedis.addListener(object : RedisPubSubAdapter<String, String>() {
            override fun message(channel: String, message: String) {
                println("Received msg on $channel")
                println("Received msg $message")
                if (channel == channelName) {
                    val incoming = Json.decodeFromString<Message>(message)
                    scope.launch {
                        clients.forEach {
                            it.send(incoming)
                        }
                    }
                }
            }
        })
        scope.launch {
            subscribeRedis.async().subscribe(roomName).await()
        }
    }

}

@ExperimentalLettuceCoroutinesApi
fun Application.configureSockets(
    publishRedis: StatefulRedisPubSubConnection<String, String>,
    subscribeRedis: StatefulRedisPubSubConnection<String, String>
) {
    install(WebSockets) {
        pingPeriod = Duration.ofSeconds(15)
        timeout = Duration.ofSeconds(15)
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }
    routing {
        val rooms = Collections.synchronizedMap(LinkedHashMap<String, Room>())
        webSocket("/coup") {
            println("Adding user!")
            val thisConnection = Connection(this)
            try {
                // Connect to main room
                thisConnection.send(
                    Message(
                        type = Message.Type.CONNECT,
                        timestamp = Date().time.toULong(),
                        content = buildJsonObject {
                            put("rooms", buildJsonArray {
                                rooms.keys.forEach {
                                    add(it)
                                }
                            })
                        }
                    )
                )
                // Wait for join/create message
                val initial = incoming.receive() as? Frame.Text ?: throw Exception("expected join/create message")
                val msg: Message = Json.decodeFromString(initial.readText())
                // Join ROOM
                when (msg.type) {
                    Message.Type.CREATE -> {
                        val roomName = uuid()
                        val room = Room(
                            roomName = roomName,
                            subscribeRedis = subscribeRedis,
                            publishRedis = publishRedis
                        )
                        rooms[roomName] = room
                        msg.content["userName"]?.jsonPrimitive?.content?.let {
                            thisConnection.name = it
                        }
                        val color = room.addClient(thisConnection)
                        thisConnection.send(
                            Message(
                                type = Message.Type.JOIN,
                                timestamp = Date().time.toULong(),
                                content = buildJsonObject {
                                    put("roomName", roomName)
                                    put("color", color)
                                }
                            )
                        )
                        thisConnection.room = room
                        println("Waiting for start")
                        // WAIT FOR START, but only for creator
                        val start = incoming.receive() as? Frame.Text ?: throw Exception("expected start message")
                        println("Received start")
                        val m: Message = Json.decodeFromString(start.readText())
                        if (m.type == Message.Type.START) {
                            room.startGame()
                            // broadcast start to everyone
                            room.broadcast(
                                Message(
                                    type = Message.Type.START,
                                    timestamp = Date().time.toULong(),
                                    content = Json.encodeToJsonElement(
                                        GameState.serializer(),
                                        room.gameState!!
                                    ).jsonObject
                                )
                            )
                        } else {
                            thisConnection.sendError("expected start Message")
                        }
                    }
                    Message.Type.JOIN -> {
                        val roomName = msg.content["roomName"]?.jsonPrimitive?.content
                        // TODO verify room code
                        val room = rooms[roomName]
                        if (room == null) {
                            thisConnection.sendError("Room not found")
                        } else {
                            val color = room.addClient(thisConnection)
                            msg.content["userName"]?.jsonPrimitive?.content?.let {
                                thisConnection.name = it
                            }
                            thisConnection.send(
                                Message(
                                    type = Message.Type.JOIN,
                                    timestamp = Date().time.toULong(),
                                    content = buildJsonObject {
                                        put("roomName", roomName)
                                        put("color", color)
                                    }
                                )
                            )
                            thisConnection.room = room
                        }
                    }
                    else -> {
                        thisConnection.sendError("Unexpected Message")
                    }
                }
                // Manage Game Data
                for (frame in incoming) {
                    frame as? Frame.Text ?: continue
                    val incomingMsg: Message = frame.toMessage()
                    when (incomingMsg.type) {
                        Message.Type.EXIT -> {
                            println("Received exit message from $thisConnection")
                            break
                        }
                        Message.Type.MOVE -> {
                            try {
                                val data =
                                    Json.decodeFromJsonElement(Content.MoveData.serializer(), incomingMsg.content)
                                val room = rooms[data.roomName]
                                room?.let {
                                    val newState = it.handleGameData(data.move)
                                    it.gameState = newState
                                    println("Broadcasting to ${it.roomName}, room=$it")
                                    // Broadcast to peeps
                                    it.broadcast(
                                        msg = Message(
                                            type = Message.Type.GAME,
                                            timestamp = Date().time.toULong(),
                                            content = Json.encodeToJsonElement(
                                                Content.GameData.serializer(),
                                                Content.GameData(
                                                    roomName = it.roomName,
                                                    gameState = newState
                                                )
                                            ).jsonObject
                                        )
                                    )
                                }
                            } catch (ex: Exception) {
                                ex.printStackTrace()
                            }
                        }
                        else -> {
                            println("Ignoring $incomingMsg")
                            continue
                        } // ignore
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                println(e.localizedMessage)
            } finally {
                println("Removing $thisConnection!")
                val room = thisConnection.room
                room?.removeClient(thisConnection)
            }
        }
    }
}

@Suppress("DuplicatedCode")
suspend fun Room.handleGameData(move: Move): GameState {
    val gs = gameState
    var newGameState: GameState = gs
    gs.currentState.let { state ->
        when (state) {
            is State.Turn -> { // Someone's turn
                when (move) {
                    is Move.Income -> { // They asked for income
                        val nextPlayer = (gs.currentPlayer + 1) % gs.players.size
                        newGameState = gs.copy(
                            players = gs.players.map { p ->
                                if (p sameAs move.player) {
                                    p.copy(coins = p.coins + 1)
                                } else {
                                    p
                                }
                            }, // Add 1 coin to that player
                            currentPlayer = nextPlayer, // next player in turn
                            currentState = State.Turn(gs.players[nextPlayer]),
                            logs = gs.logs.toMutableList().apply { add(move.description) }
                        )
                    }
                    is Move.Coup -> {
                        newGameState = gs.copy(
                            players = gs.players.map { p ->
                                if (p sameAs move.player) {
                                    p.copy(coins = p.coins - 7)
                                } else {
                                    p
                                }
                            }, // Consume 7 coins
                            currentState = State.WaitSurrender(move.victim, move),
                            logs = gs.logs.toMutableList().apply { add(move.description) }
                        )
                    }
                    is Move.Assassinate -> {
                        // Wait for a counter from all players
                        newGameState = gs.copy(
                            players = gs.players.map { p ->
                                if (p sameAs move.player) {
                                    p.copy(coins = p.coins - 3)
                                } else {
                                    p
                                }
                            }, // Consume 3 coins
                            currentState = State.WaitCounter(gs.players, move),
                            logs = gs.logs.toMutableList()
                                .apply { add("Attempt: " + move.description) } // maybe add an attempt option
                        )
                    }
                    is Move.ForeignAid,
                    is Move.Tax,
                    is Move.Steal,
                    is Move.Exchange -> {
                        // Wait for a counter from all players but the initiator
                        newGameState = gs.copy(
                            currentState = State.WaitCounter(gs.players.filterNot { it sameAs move.player }, move),
                            logs = gs.logs.toMutableList()
                                .apply { add("Attempt: " + move.description) } // maybe add an attempt option
                        )
                    }
                    else -> {
                        println("Bad move received: ($move, $gs)")
                    }
                }
            }
            is State.WaitCounter -> {
                when (move) {
                    is Move.Challenge -> {
                        val proofList = move.action.proofList()
                        val player = gs.players.find { it sameAs move.action.player }!!
                        val (r1, r2) = player.roles
                        newGameState = if (proofList.any { it == r1.role || it == r2.role }) {
                            // Player has the influence ask them to show
                            gs.copy(
                                currentState = State.ShowInfluence(move.action.player, move, proofList)
                            )
                        } else {
                            // Player does not have influence ask them to surrender
                            gs.copy(
                                currentState = State.WaitSurrender(player, move)
                            )
                        }
                        // based on prooflist if player doesnt have certain role,
                        // then switch gameState to WaitSurrender
                    }
                    is Move.Block -> {
                        newGameState = gs.copy(
                            currentState = State.WaitCounter(gs.players.filterNot { it sameAs move.player }, move),
                            logs = gs.logs.toMutableList().apply { add("Attempt: " + move.description) }
                        )
                    }
                    is Move.Pass -> {
                        // reduce players in wait queue
                        newGameState = gs.copy(
                            currentState = state.copy(
                                players = state.players.filterNot { it sameAs move.player }
                            ),
                            logs = gs.logs.toMutableList().apply { add(move.description) }
                        )
                    }
                    else -> {
                        println("Bad move received: ($move, $gs)")
                    }
                }
                with(newGameState) {
                    (currentState as? State.WaitCounter)?.let { state ->
                        if (state.players.isEmpty()) {
                            val nextPlayer = (currentPlayer + 1) % players.size
                            val passedMove = state.move
                            when (passedMove) {
                                is Move.ForeignAid -> {
                                    newGameState = gs.copy(
                                        players = gs.players.map { p ->
                                            if (p sameAs passedMove.player) {
                                                p.copy(coins = p.coins + 2)
                                            } else {
                                                p
                                            }
                                        }, // Add 2 coin to that player
                                        currentPlayer = nextPlayer, // next player in turn
                                        currentState = State.Turn(gs.players[nextPlayer]),
                                        logs = gs.logs.toMutableList().apply { add(passedMove.description + " PASSED") }
                                    )
                                }
                                is Move.Tax -> {
                                    newGameState = gs.copy(
                                        players = gs.players.map { p ->
                                            if (p sameAs passedMove.player) {
                                                p.copy(coins = p.coins + 3)
                                            } else {
                                                p
                                            }
                                        }, // Add 3 coin to that player
                                        currentPlayer = nextPlayer, // next player in turn
                                        currentState = State.Turn(gs.players[nextPlayer]),
                                        logs = gs.logs.toMutableList().apply { add(passedMove.description + " PASSED") }
                                    )
                                }
                                is Move.Steal -> {
                                    newGameState = gs.copy(
                                        players = gs.players.map { p ->
                                            if (p sameAs passedMove.player) {
                                                p.copy(coins = p.coins + 2)
                                            } else if (p sameAs passedMove.victim) {
                                                p.copy(coins = p.coins - 2)
                                            } else {
                                                p
                                            }
                                        }, // Exchange 2 coins from victim to player
                                        currentPlayer = nextPlayer, // next player in turn
                                        currentState = State.Turn(gs.players[nextPlayer]),
                                        logs = gs.logs.toMutableList().apply { add(passedMove.description + " PASSED") }
                                    )
                                }
                                is Move.Block -> {
                                    // Move on to next player, add to logs
                                    newGameState = gs.copy(
                                        currentPlayer = nextPlayer, // next player in turn
                                        currentState = State.Turn(gs.players[nextPlayer]),
                                        logs = gs.logs.toMutableList().apply { add(passedMove.description) }
                                    )
                                }
                                is Move.Assassinate -> {
                                    newGameState = gs.copy(
                                        currentState = State.WaitSurrender(passedMove.victim, move),
                                        logs = gs.logs.toMutableList().apply { add(move.description + " PASSED") }
                                    )
                                }
                                is Move.Exchange -> {
                                    newGameState = gs.copy(
                                        currentState = State.ExchangeInfluence(
                                            player = passedMove.player,
                                            choices = listOf(deck[0], deck[1]), // first in the new deck
                                            move = passedMove
                                        ),
                                        logs = gs.logs.toMutableList().apply { add(move.description + " PASSED") }
                                    )
                                }
                                else -> {
                                    println("invalid state: $state")
                                }
                            }
                        }
                    }
                }
            }
            is State.WaitSurrender -> {
                when (move) {
                    is Move.Surrender -> {
                        val delRole = move.role
                        val newPlayersList = gs.players.map { p ->
                            if (p sameAs move.player) {
                                val roles = p.roles.let {
                                    if (it.first.alive && it.first.role == delRole) {
                                        it.first.copy(alive = false) to it.second
                                    } else if (it.second.alive && it.second.role == delRole) {
                                        it.first to it.second.copy(alive = false)
                                    } else {
                                        it
                                    }
                                }
                                p.copy(roles = roles) // remove influence
                            } else {
                                p
                            }
                        }.filterNot { p ->
                            p.roles.toList().all { !it.alive }
                        }
                        // move to start of list if last player got booted, else stay at current
                        val nextPlayer = if (gs.currentPlayer == gs.players.size - 1) {
                            0
                        } else {
                            gs.currentPlayer
                        }
                        newGameState = gs.copy(
                            players = newPlayersList,
                            currentPlayer = nextPlayer, // next player in turn
                            currentState = State.Turn(gs.players[nextPlayer]),
                            logs = gs.logs.toMutableList().apply { add(move.description) }
                        )
                    }
                    else -> {
                        println("Bad move received: ($move, $gs)")
                    }
                }
            }
            is State.ExchangeInfluence -> {
                when (move) {
                    is Move.Exchange -> {
                        val nextPlayer = (gs.currentPlayer + 1) % gs.players.size
                        val oldRoles = move.changes.map { it.first }
                        val newRoles = move.changes.map { it.second }
                        val newDeck = gs.deck.toMutableList().apply {
                            newRoles.forEach {
                                remove(it)
                            }
                            oldRoles.forEach {
                                add(it)
                            }
                            shuffle()
                        }
                        val newPlayers = gs.players.map { p ->
                            if (p sameAs move.player) {
                                var (r1, r2) = p.roles
                                move.changes.forEach {
                                    if (r1.alive && r1.role == it.first) {
                                        r1 = r1.copy(role = it.second)
                                    } else if (r2.alive && r2.role == it.first) {
                                        r2 = r2.copy(role = it.second)
                                    }
                                }
                                p.copy(roles = r1 to r2) // exchange influence
                            } else {
                                p
                            }
                        }
                        when (val m = state.move) {
                            is Move.Show -> {
                                // Make challenger surrender their card
                                newGameState = gs.copy(
                                    deck = newDeck,
                                    players = newPlayers,
                                    currentState = State.WaitSurrender(m.challenge.player, m),
                                    logs = gs.logs.toMutableList().apply { add(move.description) }
                                )
                            }
                            is Move.Exchange -> {
                                newGameState = gs.copy(
                                    deck = newDeck,
                                    players = newPlayers,
                                    currentPlayer = nextPlayer, // next player in turn
                                    currentState = State.Turn(gs.players[nextPlayer]),
                                    logs = gs.logs.toMutableList().apply { add(move.description) }
                                )
                            }
                            else -> TODO("Nothing to do $state, $move")
                        }
                    }
                    else -> {
                        println("Bad move received: ($move, $gs)")
                    }
                }
            }
            is State.ShowInfluence -> {
                when (move) {
                    is Move.Show -> {
                        val deck = gs.deck.toMutableList().apply {
                            add(move.influence) // add back influence
                            shuffle()
                        }
                        // dont actually set this deck, its done later in the exchange influence,
                        // this is just to create the choices
                        newGameState = gs.copy(
                            currentState = State.ExchangeInfluence(
                                player = move.player,
                                choices = listOf(deck.first()), // first in the new deck
                                move = move
                            )
                        )
                    }
                    else -> {
                        println("Bad move received: ($move, $gs)")
                    }
                }
            }
        }
    }
    return newGameState
}

fun newGameState(connections: Set<Connection>): GameState {
    val deck: MutableList<Role> = LinkedList<Role>().apply { Role.values().forEach { role -> repeat(3) { add(role) } } }
    deck.shuffle()
    val influences = connections.indices.map {
        val first = deck.first()
        deck.removeAt(0)
        val second = deck.first()
        deck.removeAt(0)
        Pair(Influence(first, true), Influence(second, true))
    }
    val players = connections.mapIndexed { idx, connection ->
        Player(
            name = connection.name,
            coins = 2,
            roles = influences[idx],
            color = connection.color,
        )
    }.shuffled()
    return GameState(
        deck = deck,
        players = players,
        currentPlayer = 0,
        currentState = State.Turn(players[0]),
        logs = mutableListOf()
    )
}

const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
fun uuid(): String {
    return "ABCDEFGH"
    val random = Random(System.currentTimeMillis())
    return (1..8)
        .map { random.nextInt(0, ALPHABET.length) }
        .map(ALPHABET::get)
        .joinToString("")
}

fun Frame.Text.toMessage(): Message {
    return Json.decodeFromString(Message.serializer(), readText())
}
