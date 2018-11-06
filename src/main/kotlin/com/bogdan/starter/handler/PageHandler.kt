package com.bogdan.starter.handler

import com.bogdan.starter.db.DbQueries.SQL_ALL_PAGES
import com.bogdan.starter.db.DbQueries.SQL_CREATE_PAGE
import com.bogdan.starter.db.DbQueries.SQL_DELETE_PAGE
import com.bogdan.starter.db.DbQueries.SQL_GET_PAGE
import com.bogdan.starter.db.DbQueries.SQL_SAVE_PAGE
import com.github.rjeschke.txtmark.Processor
import io.vertx.core.json.JsonArray
import io.vertx.ext.jdbc.JDBCClient
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.templ.FreeMarkerTemplateEngine
import java.util.*
import java.util.stream.Collectors


class PageHandler(private val dbClient: JDBCClient) {

    private val templateEngine = FreeMarkerTemplateEngine.create()

    fun indexPageHandler(context: RoutingContext) {
        dbClient.getConnection { connectionAR ->
            if (connectionAR.succeeded()) {
                val connection = connectionAR.result()
                connection.query(SQL_ALL_PAGES) { allPagesQueryAR ->
                    connection.close()
                    if (allPagesQueryAR.succeeded()) {
                        val pages: List<String> = allPagesQueryAR.result()
                                .results
                                .stream()
                                .map { json -> json.getString(0) }
                                .sorted()
                                .collect(Collectors.toList())
                        context.put("title", "Wiki home")
                        context.put("pages", pages)
                        templateEngine.render(context, "templates", "/index.ftl") { renderingAR ->
                            if (renderingAR.succeeded()) {
                                context.response().putHeader("Content-Type", "text/html")
                                context.response().end(renderingAR.result())
                            } else {
                                context.fail(renderingAR.cause())
                            }
                        }
                    } else {
                        context.fail(allPagesQueryAR.cause())
                    }
                }
            } else {
                context.fail(connectionAR.cause())
            }
        }
    }

    private val emptyPageMarkDown = "# A new page\n" + "\n" + "Feel-free to write in Markdown!\n"

    fun pageRenderingHandler(context: RoutingContext) {
        val page = context.request().getParam("page")
        dbClient.getConnection { car ->
            if (car.succeeded()) {
                val connection = car.result()
                connection.queryWithParams(SQL_GET_PAGE, JsonArray().add(page)) { fetch ->
                    connection.close()
                    if (fetch.succeeded()) {
                        val row = fetch.result().results
                                .stream()
                                .findFirst()
                                .orElseGet { JsonArray().add(-1).add(emptyPageMarkDown) }
                        val id = row.getInteger(0)
                        val rawContent = row.getString(1)
                        context.put("title", page)
                        context.put("id", id)
                        context.put("newPage", if (fetch.result().results.size == 0) "yes" else "no")
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
                        context.fail(fetch.cause())
                    }
                }
            } else {
                context.fail(car.cause())
            }
        }
    }

    fun pageCreateHandler(context: RoutingContext) {
        val pageName = context.request().getParam("name")
        var location = "/wiki/$pageName"
        if (pageName.isEmpty()) {
            location = "/"
        }
        context.response().statusCode = 303
        context.response().putHeader("Location", location)
        context.response().end()
    }

    fun pageUpdateHandler(context: RoutingContext) {
        val id = context.request().getParam("id")
        val title = context.request().getParam("title")
        val markdown = context.request().getParam("markdown")
        val newPage = "yes" == context.request().getParam("newPage")
        dbClient.getConnection { car ->
            if (car.succeeded()) {
                val connection = car.result()
                val sql = if (newPage) SQL_CREATE_PAGE else SQL_SAVE_PAGE
                val params = JsonArray()
                if (newPage) {
                    params.add(title).add(markdown)
                } else {
                    params.add(markdown).add(id)
                }
                connection.updateWithParams(sql, params) { res ->
                    connection.close()
                    if (res.succeeded()) {
                        context.response().statusCode = 303
                        context.response().putHeader("Location", "/wiki/$title")
                        context.response().end()
                    } else {
                        context.fail(res.cause())
                    }
                }
            } else {
                context.fail(car.cause())
            }
        }
    }

    fun pageDeletionHandler(context: RoutingContext) {
        val id = context.request().getParam("id")
        dbClient.getConnection { car ->
            if (car.succeeded()) {
                val connection = car.result()
                connection.updateWithParams(SQL_DELETE_PAGE, JsonArray().add(id)) { res ->
                    connection.close()
                    if (res.succeeded()) {
                        context.response().statusCode = 303
                        context.response().putHeader("Location", "/")
                        context.response().end()
                    } else {
                        context.fail(res.cause())
                    }
                }
            } else {
                context.fail(car.cause())
            }
        }
    }
}