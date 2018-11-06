package com.bogdan.starter.verticles

import com.bogdan.starter.db.DbQueries
import com.bogdan.starter.handler.PageHandler
import io.vertx.core.AbstractVerticle
import io.vertx.core.Future
import io.vertx.core.http.HttpServer
import io.vertx.core.json.JsonObject
import io.vertx.ext.jdbc.JDBCClient
import io.vertx.ext.sql.SQLConnection
import io.vertx.ext.web.Router
import io.vertx.ext.web.handler.BodyHandler
import org.slf4j.LoggerFactory

class MainVerticle : AbstractVerticle() {

    private val log = LoggerFactory.getLogger(MainVerticle::class.java)
    private lateinit var dbClient: JDBCClient
    private val pageHandler: PageHandler by lazy { PageHandler(dbClient) }

    @Throws(Exception::class)
    override fun start(startFuture: Future<Void>) {
        val steps = prepareDatabase().compose { startHttpServer() }
        steps.setHandler { asyncResult ->
            if (asyncResult.succeeded()) {
                startFuture.complete()
            } else {
                startFuture.fail(asyncResult.cause())
            }
        }
    }

    private fun prepareDatabase(): Future<Void> {
        val future: Future<Void> = Future.future()
        dbClient = JDBCClient.createShared(vertx, JsonObject()
                .put("url", "jdbc:hsqldb:file:db/wiki")
                .put("driver_class", "org.hsqldb.jdbcDriver")
                .put("max_pool_size", 30))
        dbClient.getConnection { ar ->
            if (ar.failed()) {
                log.error("Could not open a database connection", ar.cause())
                future.fail(ar.cause())
            } else {
                val connection: SQLConnection = ar.result()
                connection.execute(
                        DbQueries.SQL_CREATE_PAGES_TABLE,
                        { createAsyncResult ->
                            connection.close()
                            if (createAsyncResult.failed()) {
                                log.error("Database preparation error", createAsyncResult.cause())
                                future.fail(createAsyncResult.cause())
                            } else {
                                future.complete()
                            }
                        }
                )
            }
        }
        return future
    }

    private fun startHttpServer(): Future<Void> {
        val future: Future<Void> = Future.future()
        val server: HttpServer = vertx.createHttpServer()
        val router: Router = Router.router(vertx)
        router.get("/").handler { rc -> pageHandler.indexPageHandler(rc) }
        router.get("/wiki/:page").handler { rc -> pageHandler.pageRenderingHandler(rc) }
        router.post().handler(BodyHandler.create())
        router.post("/save").handler { rc -> pageHandler.pageUpdateHandler(rc) }
        router.post("/create").handler { rc -> pageHandler.pageCreateHandler(rc) }
        router.post("/delete").handler { rc -> pageHandler.pageDeletionHandler(rc) }

        server.requestHandler(router::accept)
                .listen(8080, { ar ->
                    if (ar.succeeded()) {
                        log.info("HTTP server running on port 8080")
                        future.complete()
                    } else {
                        log.error("Could not start a HTTP server", ar.cause())
                        future.fail(ar.cause())
                    }
                })
        return future
    }

}
