package com.prayansh.coup.server

import com.prayansh.coup.model.analytics.ApplicationStarted
import com.prayansh.coup.server.plugins.configureRouting
import com.prayansh.coup.server.plugins.configureSockets
import com.segment.analytics.kotlin.core.Analytics
import com.segment.analytics.kotlin.core.BaseEvent
import com.segment.analytics.kotlin.core.utilities.putInContext
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
import com.segment.analytics.kotlin.core.platform.Plugin as AnalyticsPlugin

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
    val serverName = envVar("NAME", "unknown")
    val segmentWriteKey = envVar("SEGMENT_WRITE_KEY")
    val analytics = Analytics(segmentWriteKey) {
        application = "RebellionApp"
        flushAt = 20
        flushInterval = 10
    }.add(object: AnalyticsPlugin {
        override lateinit var analytics: Analytics
        override val type = AnalyticsPlugin.Type.Enrichment
        override fun execute(event: BaseEvent): BaseEvent? {
            event.putInContext("server_name", serverName)
            return super.execute(event)
        }
    })

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
        configureRouting(analytics)
        configureSockets(redisClient, analytics)
        analytics.track("Application Started", ApplicationStarted(id = serverName))
    }.start(wait = true)
}

class AuthenticationException : RuntimeException()
class AuthorizationException : RuntimeException()
