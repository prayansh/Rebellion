package net.malkowscy.application

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
import net.malkowscy.application.plugins.configureRouting
import net.malkowscy.application.plugins.configureSockets

fun main() {
    val redisClient = RedisClient.create("redis://password@localhost:6379/0")
    val redis1 = redisClient.connectPubSub()
    val redis2 = redisClient.connectPubSub()
	embeddedServer(Netty, port = 8080, host = "0.0.0.0") {
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
        configureSockets(redis1, redis2)
	}.start(wait = true)
}

class AuthenticationException : RuntimeException()
class AuthorizationException : RuntimeException()
