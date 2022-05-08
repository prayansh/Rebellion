package com.prayansh.coup.server.session

import com.prayansh.coup.model.Message
import io.ktor.websocket.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.*
import java.util.concurrent.atomic.AtomicInteger


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
