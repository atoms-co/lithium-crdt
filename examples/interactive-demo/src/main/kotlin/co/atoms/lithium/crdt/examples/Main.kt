package co.atoms.lithium.crdt.examples

import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.http.content.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import java.util.concurrent.ConcurrentHashMap

private val nodes = linkedMapOf(
    "node-a" to CrdtNode("Node A"),
    "node-b" to CrdtNode("Node B"),
    "node-c" to CrdtNode("Node C"),
)

private val stateUpdates = MutableSharedFlow<Unit>(
    replay = 1,
    onBufferOverflow = BufferOverflow.DROP_OLDEST,
)

fun main() {
    embeddedServer(Netty, port = 8080) {
        install(ContentNegotiation) { json(json) }
        install(WebSockets)
        install(CORS) {
            anyHost()
            allowMethod(HttpMethod.Post)
            allowMethod(HttpMethod.Get)
            allowHeader(HttpHeaders.ContentType)
        }
        install(StatusPages) {
            exception<Throwable> { call, cause ->
                call.respondText(
                    text = """{"error": "${cause.message?.replace("\"", "\\\"") ?: "Unknown error"}"}""",
                    contentType = ContentType.Application.Json,
                    status = HttpStatusCode.InternalServerError,
                )
            }
        }

        routing {
            staticResources("/", "static") {
                default("index.html")
            }

            route("/api") {
                get("/nodes") {
                    call.respond(nodes.values.map { it.toDto() })
                }

                post("/nodes/{id}/write") {
                    val nodeId = call.parameters["id"]
                        ?: return@post call.respond(HttpStatusCode.BadRequest, "Missing node id")
                    val node = nodes[nodeId]
                        ?: return@post call.respond(HttpStatusCode.NotFound, "Node not found")
                    val request = call.receive<WriteRequest>()

                    val currentDoc = node.document
                    val newDoc = CollaborativeDocument(
                        title = request.title ?: currentDoc?.title ?: "",
                        content = request.content ?: currentDoc?.content ?: "",
                        author = request.author ?: currentDoc?.author ?: "",
                        priority = request.priority ?: currentDoc?.priority ?: 0,
                        status = request.status ?: currentDoc?.status ?: "",
                        tags = request.tags ?: currentDoc?.tags ?: emptyMap(),
                    )

                    node.applyLocalWrite(newDoc)
                    stateUpdates.emit(Unit)
                    call.respond(nodes.values.map { it.toDto() })
                }

                post("/nodes/{id}/toggle") {
                    val nodeId = call.parameters["id"]
                        ?: return@post call.respond(HttpStatusCode.BadRequest, "Missing node id")
                    val node = nodes[nodeId]
                        ?: return@post call.respond(HttpStatusCode.NotFound, "Node not found")
                    node.online = !node.online
                    stateUpdates.emit(Unit)
                    call.respond(nodes.values.map { it.toDto() })
                }

                post("/sync/{source}/{target}") {
                    val sourceId = call.parameters["source"]
                        ?: return@post call.respond(HttpStatusCode.BadRequest, "Missing source")
                    val targetId = call.parameters["target"]
                        ?: return@post call.respond(HttpStatusCode.BadRequest, "Missing target")
                    val source = nodes[sourceId]
                        ?: return@post call.respond(HttpStatusCode.NotFound, "Source not found")
                    val target = nodes[targetId]
                        ?: return@post call.respond(HttpStatusCode.NotFound, "Target not found")

                    if (!source.online || !target.online) {
                        return@post call.respond(
                            HttpStatusCode.BadRequest,
                            """{"error": "Both nodes must be online to sync"}"""
                        )
                    }

                    val strategy = target.syncFrom(source)
                    stateUpdates.emit(Unit)
                    call.respond(
                        SyncResultDto(
                            resolution = strategy.name,
                            source = source.name,
                            target = target.name,
                        )
                    )
                }

                post("/sync-all") {
                    val onlineNodes = nodes.values.filter { it.online }
                    val results = mutableListOf<SyncResultDto>()

                    for (target in onlineNodes) {
                        for (source in onlineNodes) {
                            if (source === target) continue
                            val strategy = target.syncFrom(source)
                            results.add(
                                SyncResultDto(
                                    resolution = strategy.name,
                                    source = source.name,
                                    target = target.name,
                                )
                            )
                        }
                    }

                    stateUpdates.emit(Unit)
                    call.respond(SyncAllResultDto(results = results))
                }

                post("/reset") {
                    nodes.values.forEach { it.reset() }
                    stateUpdates.emit(Unit)
                    call.respond(nodes.values.map { it.toDto() })
                }
            }

            webSocket("/ws") {
                val currentState = json.encodeToString(
                    kotlinx.serialization.builtins.ListSerializer(NodeStateDto.serializer()),
                    nodes.values.map { it.toDto() },
                )
                send(Frame.Text(currentState))

                stateUpdates.collectLatest {
                    val state = json.encodeToString(
                        kotlinx.serialization.builtins.ListSerializer(NodeStateDto.serializer()),
                        nodes.values.map { it.toDto() },
                    )
                    send(Frame.Text(state))
                }
            }
        }
    }.start(wait = true)

    println("CRDT Demo running at http://localhost:8080")
}
