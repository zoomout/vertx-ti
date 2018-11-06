package com.bogdan.starter.verticles

import com.bogdan.starter.Constants.httpServerPort
import com.bogdan.starter.Constants.dbQueue
import com.github.rjeschke.txtmark.Processor
import io.vertx.core.AbstractVerticle
import io.vertx.core.Future
import io.vertx.core.eventbus.DeliveryOptions
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.handler.BodyHandler
import io.vertx.ext.web.templ.FreeMarkerTemplateEngine
import org.slf4j.LoggerFactory
import java.util.*

class HttpServerVerticle : AbstractVerticle() {

    private val log = LoggerFactory.getLogger(HttpServerVerticle::class.java)
    private val templateEngine = FreeMarkerTemplateEngine.create()
    private val emptyPageMarkDown = "# A new page\n" + "\n" + "Feel-free to write in Markdown!\n"

    @Throws(Exception::class)
    override fun start(startFuture: Future<Void>) {
        log.info("Starting HttpServerVerticle...")
        val server = vertx.createHttpServer()
        val router = Router.router(vertx)

        router.get("/").handler { rc -> indexPageHandler(rc) }
        router.get("/wiki/:page").handler { rc -> pageRenderingHandler(rc) }
        router.post().handler(BodyHandler.create())
        router.post("/save").handler { rc -> pageUpdateHandler(rc) }
        router.post("/create").handler { rc -> pageCreateHandler(rc) }
        router.post("/delete").handler { rc -> pageDeletionHandler(rc) }

        val portNumber = config().getInteger(httpServerPort)
        server
                .requestHandler { router.accept(it) }
                .listen(portNumber, { ar ->
                    if (ar.succeeded()) {
                        log.info("HTTP server running on port $portNumber")
                        startFuture.complete()
                    } else {
                        log.error("Could not start a HTTP server", ar.cause())
                        startFuture.fail(ar.cause())
                    }
                })
    }

    private fun indexPageHandler(context: RoutingContext) {

        val options = DeliveryOptions().addHeader("action", "all-pages")

        vertx.eventBus().send<Any>(dbQueue, JsonObject(), options) { reply ->
            if (reply.succeeded()) {
                val body = reply.result().body() as JsonObject
                context.put("title", "Wiki home")
                context.put("pages", body.getJsonArray("pages").list)
                templateEngine.render(context, "templates", "/index.ftl") { ar ->
                    if (ar.succeeded()) {
                        context.response().putHeader("Content-Type", "text/html")
                        context.response().end(ar.result())
                    } else {
                        context.fail(ar.cause())
                    }
                }
            } else {
                context.fail(reply.cause())
            }
        }
    }


    private fun pageRenderingHandler(context: RoutingContext) {
        val requestedPage = context.request().getParam("page")
        val request = JsonObject().put("page", requestedPage)
        val options = DeliveryOptions().addHeader("action", "get-page")
        vertx.eventBus().send<Any>(dbQueue, request, options) { reply ->
            if (reply.succeeded()) {
                val body = reply.result().body() as JsonObject
                val found = body.getBoolean("found")
                val rawContent = body.getString("rawContent", emptyPageMarkDown)
                context.put("title", requestedPage)
                context.put("id", body.getInteger("id", -1))
                context.put("newPage", if (found) "no" else "yes")
                context.put("rawContent", rawContent)
                context.put("content", Processor.process(rawContent))
                context.put("timestamp", Date().toString())
                templateEngine.render(context, "templates", "/page.ftl") { ar ->
                    if (ar.succeeded()) {
                        context.response().putHeader("Content-Type", "text/html")
                        context.response().end(ar.result())
                    } else {
                        context.fail(ar.cause())
                    }
                }
            } else {
                context.fail(reply.cause())
            }
        }
    }

    private fun pageCreateHandler(context: RoutingContext) {
        val pageName = context.request().getParam("name")
        var location = "/wiki/$pageName"
        if (pageName.isEmpty()) {
            location = "/"
        }
        context.response().statusCode = 303
        context.response().putHeader("Location", location)
        context.response().end()
    }

    private fun pageUpdateHandler(context: RoutingContext) {
        val title = context.request().getParam("title")
        val request = JsonObject()
                .put("id", context.request().getParam("id"))
                .put("title", title)
                .put("markdown", context.request().getParam("markdown"))
        val options = DeliveryOptions()
        if ("yes" == context.request().getParam("newPage")) {
            options.addHeader("action", "create-page")
        } else {
            options.addHeader("action", "save-page")
        }
        vertx.eventBus().send<Any>(dbQueue, request, options) { reply ->
            if (reply.succeeded()) {
                context.response().statusCode = 303
                context.response().putHeader("Location", "/wiki/$title")
                context.response().end()
            } else {
                context.fail(reply.cause())
            }
        }
    }

    private fun pageDeletionHandler(context: RoutingContext) {
        val id = context.request().getParam("id")
        val request = JsonObject().put("id", id)
        val options = DeliveryOptions().addHeader("action", "delete-page")
        vertx.eventBus().send<Any>(dbQueue, request, options) { reply ->
            if (reply.succeeded()) {
                context.response().statusCode = 303
                context.response().putHeader("Location", "/")
                context.response().end()
            } else {
                context.fail(reply.cause())
            }
        }
    }
}