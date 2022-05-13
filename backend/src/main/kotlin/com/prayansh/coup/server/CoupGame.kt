package com.prayansh.coup.server

import com.prayansh.coup.model.*
import com.prayansh.coup.server.session.Connection
import java.util.*

// TODO write unit tests for this

// Updating game state due to incoming move
fun updateGameState(gs: GameState, move: Move): GameState {
    println("Processing $move \n currentGameState: $gs")
    var newGameState: GameState = gs
    when (val state = gs.currentState) {
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
                        currentState = State.WaitCounter(gs.players.filterNot { it sameAs move.player }, move),
                        logs = gs.logs.toMutableList().apply { add("Attempt: " + move.description) }
                    )
                }
                is Move.ForeignAid,
                is Move.Tax,
                is Move.Steal,
                is Move.Exchange -> {
                    // Wait for a counter from all players but the initiator
                    newGameState = gs.copy(
                        currentState = State.WaitCounter(gs.players.filterNot { it sameAs move.player }, move),
                        logs = gs.logs.toMutableList().apply { add("Attempt: " + move.description) }
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
                    val pendingPlayers = state.players.filterNot { it sameAs move.player }
                    newGameState = gs.copy(
                        currentState = state.copy(
                            players = pendingPlayers
                        ),
                        logs = gs.logs.toMutableList().apply { add(move.description) }
                    )
                    if (pendingPlayers.isEmpty()) { // Everyone passed the move
                        val passedMove = state.move
                        println("$passedMove was passed")
                        println("Old GameState: $newGameState")
                        newGameState = applyMove(newGameState, passedMove)
                        println("New GameState: $newGameState")
                    }
                }
                else -> {
                    println("Bad move received: ($move, $gs)")
                }
            }
        }
        is State.WaitSurrender -> {
            when (move) {
                is Move.Surrender -> {
                    val delRole = move.role
                    var playerRemoved = -1
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
                    }.filterIndexed { idx, p -> // filter out players who have both roles dead
                        val allNotAlive = p.roles.toList().all { !it.alive }
                        if (allNotAlive) playerRemoved = idx
                        !allNotAlive
                    }

                    if (newPlayersList.size == 1) {
                        val winner = gs.players[0]
                        newGameState = gs.copy(
                            players = newPlayersList,
                            currentState = State.GameOver(winner),
                            logs = gs.logs.toMutableList().apply {
                                add(move.description)
                                add("${winner.name} has won the game")
                            }
                        )
                    } else {
                        // This is tough logic IMO, change carefully
                        val nextPlayer = if (playerRemoved == -1) {
                            (gs.currentPlayer + 1) % gs.players.size
                        } else {
                            if (gs.currentPlayer == gs.players.size - 1) {
                                0
                            } else if (playerRemoved > gs.currentPlayer) {
                                (gs.currentPlayer + 1) % newPlayersList.size
                            } else {
                                gs.currentPlayer
                            }
                        }
                        newGameState = gs.copy(
                            players = newPlayersList,
                            currentPlayer = nextPlayer, // next player in turn
                            currentState = State.Turn(gs.players[nextPlayer]),
                            logs = gs.logs.toMutableList().apply { add(move.description) }
                        )
                    }
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
                    val discard = move.discard
                    val keep = move.keep.toMutableList()
                    val newDeck = gs.deck.toMutableList().apply {
                        discard.forEach {
                            add(it)
                        }
                        shuffle()
                    }
                    val newPlayers = gs.players.map { p ->
                        if (p sameAs move.player) {
                            val roles = p.roles.toList().map {
                                if (it.alive) {
                                    val removed = keep.removeAt(0)
                                    it.copy(role = removed)
                                } else {
                                    it
                                }
                            }
                            p.copy(roles = roles[0] to roles[1]) // exchange influence
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
                    // TODO verify the showcase

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
        is State.GameOver -> {
            // NO-OP
        }
    }
    return newGameState
}

fun verifyMove(gameState: GameState, move: Move): Boolean {
    TODO()
}

// Updating game state based on the move being passed
fun applyMove(gs: GameState, passedMove: Move): GameState {
    var newGameState = gs
    val nextPlayer = (gs.currentPlayer + 1) % gs.players.size
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
                currentState = State.WaitSurrender(passedMove.victim, passedMove),
                logs = gs.logs.toMutableList().apply { add(passedMove.description + " PASSED") }
            )
        }
        is Move.Exchange -> {
            newGameState = gs.copy(
                deck = gs.deck.drop(2),
                currentState = State.ExchangeInfluence(
                    player = passedMove.player,
                    choices = gs.deck.take(2),
                    move = passedMove
                ),
                logs = gs.logs.toMutableList().apply { add(passedMove.description + " PASSED") }
            )
        }
        else -> {
            println("invalid state: $gs.state")
        }
    }
    return newGameState
}

fun newGameState(connections: Set<Connection>): GameState {
    val deck: MutableList<Role> = LinkedList<Role>().apply {
        Role.values().forEach { role -> repeat(3) { add(role) } }
    }
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
