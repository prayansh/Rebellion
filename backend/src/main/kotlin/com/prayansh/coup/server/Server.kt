package com.prayansh.coup.server

import com.prayansh.coup.server.plugins.configureRouting
import com.prayansh.coup.server.plugins.configureSockets
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.lettuce.core.RedisClient
import io.lettuce.core.protocol.CommandHandler
import org.slf4j.event.Level

fun envVar(key: String, defaultVal: String = ""): String {
    return System.getenv(key).let {
        if (it.isNullOrBlank()) defaultVal
        else it
    }
}

fun main() {
    val redisUrl = envVar("REDIS_URL", "redis://password@localhost:6379/0")
    val port = envVar("PORT", "80").toInt()
    val redisClient = RedisClient.create(redisUrl)
    embeddedServer(Netty, port = port) {
        install(Routing)
        install(ContentNegotiation) {
            json()
        }
        install(StatusPages) {
            exception<AuthenticationException> { call, _ ->
                call.respond(HttpStatusCode.Unauthorized)
            }
            exception<AuthorizationException> { call, _ ->
                call.respond(HttpStatusCode.Forbidden)
            }

        }
        configureRouting()
        configureSockets(redisClient)
    }.start(wait = true)
}

class AuthenticationException : RuntimeException()
class AuthorizationException : RuntimeException()
