package com.prayansh.coup.server

import com.prayansh.coup.model.*
import com.prayansh.coup.model.Role.*
import io.kotest.matchers.collections.*
import org.junit.Assert.assertEquals
import org.junit.Test


class CoupGameTest {

    val player1 = Player(
        name = "P1",
        roles = Influence(BODYGUARD, true) to Influence(DIPLOMAT, true),
        coins = 7,
        ""
    )

    val player2 = Player(
        name = "P2",
        roles = Influence(GENERAL, true) to Influence(SNIPER, true),
        coins = 7,
        ""
    )

    val deck = listOf(GENERAL, SNIPER, POLITICIAN, SNIPER, BODYGUARD, DIPLOMAT)

    @Test
    fun `exchange roles on ExchangeInfluence`() {
        val currentState = GameState(
            players = listOf(player1, player2),
            deck = deck.drop(2),
            currentState = State.ExchangeInfluence(
                player = player1,
                choices = deck.take(2),
                move = Move.Exchange(player1, emptyList(), emptyList()),
            ),
            currentPlayer = 0,
            logs = emptyList()
        )
        val move = Move.Exchange(
            player = player1,
            keep = listOf(BODYGUARD, SNIPER),
            discard = listOf(GENERAL, DIPLOMAT)
        )
        val newState = updateGameState(currentState, move)
        assertEquals(
            currentState.players.map {
                if (it sameAs move.player) {
                    val roles = it.roles
                    it.copy(
                        roles = roles.first.copy(role = BODYGUARD) to roles.second.copy(role = SNIPER)
                    )
                } else {
                    it
                }
            },
            newState.players
        )
        newState.deck.shouldContainExactlyInAnyOrder(
            listOf(POLITICIAN, SNIPER, BODYGUARD, DIPLOMAT, GENERAL, DIPLOMAT)
        )
    }

    @Test
    fun `exchange roles on ExchangeInfluence when 1 role is dead`() {
        val modPlayer1 = player1.let {
            val roles = it.roles
            it.copy(
                roles = roles.first.copy(alive = false) to roles.second
            )
        }
        val currentState = GameState(
            players = listOf(modPlayer1, player2),
            deck = deck.drop(2),
            currentState = State.ExchangeInfluence(
                player = modPlayer1,
                choices = deck.take(2),
                move = Move.Exchange(modPlayer1, emptyList(), emptyList()),
            ),
            currentPlayer = 0,
            logs = emptyList()
        )
        val move = Move.Exchange(
            player = modPlayer1,
            keep = listOf(SNIPER),
            discard = listOf(GENERAL, DIPLOMAT)
        )
        val newState = updateGameState(currentState, move)
        assertEquals(
            currentState.players.map {
                if (it sameAs move.player) {
                    val roles = it.roles
                    it.copy(
                        roles = roles.first to roles.second.copy(role = SNIPER)
                    )
                } else {
                    it
                }
            },
            newState.players
        )
        newState.deck.shouldContainExactlyInAnyOrder(
            listOf(POLITICIAN, SNIPER, BODYGUARD, DIPLOMAT, GENERAL, DIPLOMAT)
        )
    }

    @Test
    fun `exchange roles on ExchangeInfluence when 1 role is dead (flip dead role)`() {
        val modPlayer1 = player1.let {
            val roles = it.roles
            it.copy(
                roles = roles.first to roles.second.copy(alive = false)
            )
        }
        val currentState = GameState(
            players = listOf(modPlayer1, player2),
            deck = deck.drop(2),
            currentState = State.ExchangeInfluence(
                player = modPlayer1,
                choices = deck.take(2),
                move = Move.Exchange(modPlayer1, emptyList(), emptyList()),
            ),
            currentPlayer = 0,
            logs = emptyList()
        )
        val move = Move.Exchange(
            player = modPlayer1,
            keep = listOf(BODYGUARD),
            discard = listOf(GENERAL, SNIPER)
        )
        val newState = updateGameState(currentState, move)
        assertEquals(
            currentState.players.map {
                if (it sameAs move.player) {
                    val roles = it.roles
                    it.copy(
                        roles = roles.first.copy(role = BODYGUARD) to roles.second
                    )
                } else {
                    it
                }
            },
            newState.players
        )
        newState.deck.shouldContainExactlyInAnyOrder(
            listOf(POLITICIAN, SNIPER, BODYGUARD, DIPLOMAT, GENERAL, SNIPER)
        )
    }

    @Test
    fun `exchange roles on ExchangeInfluence when 1 role is dead (flip dead role, a bit modified)`() {
        val modPlayer1 = player1.let {
            val roles = it.roles
            it.copy(
                roles = roles.first to roles.second.copy(alive = false)
            )
        }
        val currentState = GameState(
            players = listOf(modPlayer1, player2),
            deck = deck.drop(2),
            currentState = State.ExchangeInfluence(
                player = modPlayer1,
                choices = deck.take(2),
                move = Move.Exchange(modPlayer1, emptyList(), emptyList()),
            ),
            currentPlayer = 0,
            logs = emptyList()
        )
        val move = Move.Exchange(
            player = modPlayer1,
            keep = listOf(GENERAL),
            discard = listOf(BODYGUARD, SNIPER)
        )
        val newState = updateGameState(currentState, move)
        assertEquals(
            currentState.players.map {
                if (it sameAs move.player) {
                    val roles = it.roles
                    it.copy(
                        roles = roles.first.copy(role = GENERAL) to roles.second
                    )
                } else {
                    it
                }
            },
            newState.players
        )
        newState.deck.shouldContainExactlyInAnyOrder(
            listOf(POLITICIAN, SNIPER, BODYGUARD, DIPLOMAT, BODYGUARD, SNIPER)
        )
    }
}
