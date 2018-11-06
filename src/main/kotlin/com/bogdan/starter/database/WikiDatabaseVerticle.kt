package com.bogdan.starter.database

import com.bogdan.starter.Constants
import com.bogdan.starter.Constants.dbQueue
import com.bogdan.starter.Constants.jdbcDriverClass
import com.bogdan.starter.Constants.jdbcUrl
import com.bogdan.starter.Constants.sqlQueriesFile
import io.vertx.core.AbstractVerticle
import io.vertx.core.Future
import io.vertx.core.eventbus.Message
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.ext.jdbc.JDBCClient
import org.slf4j.LoggerFactory
import java.io.IOException
import java.util.*
import java.util.stream.Collectors


internal enum class SqlQuery {
    CREATE_PAGES_TABLE, ALL_PAGES, GET_PAGE, CREATE_PAGE, SAVE_PAGE, DELETE_PAGE
}

class WikiDatabaseVerticle : AbstractVerticle() {

    private val log = LoggerFactory.getLogger(WikiDatabaseVerticle::class.java)
    private val sqlQueries = HashMap<SqlQuery, String>()
    private lateinit var dbClient: JDBCClient

    @Throws(Exception::class)
    override fun start(startFuture: Future<Void>) {
        /*
         * Note: this uses blocking APIs, but data is small...
         */
        log.info("Starting WikiDatabaseVerticle...")
        loadSqlQueries()
        dbClient = JDBCClient.createShared(vertx, JsonObject()
                .put("url", config().getString(jdbcUrl))
                .put("driver_class", config().getString(jdbcDriverClass))
                .put("max_pool_size", config().getInteger(Constants.jdbcMaxPoolSize)))
        dbClient.getConnection { ar ->
            if (ar.failed()) {
                log.error("Could not open a database connection", ar.cause())
                startFuture.fail(ar.cause())
            } else {
                val connection = ar.result()
                connection.execute(sqlQueries[SqlQuery.CREATE_PAGES_TABLE]) { create ->
                    connection.close()
                    if (create.failed()) {
                        log.error("Database preparation error", create.cause())
                        startFuture.fail(create.cause())
                    } else {
                        vertx.eventBus().consumer<JsonObject>(config().getString(dbQueue)) { m -> onMessage(m) }
                    }
                    startFuture.complete()
                }
            }
        }
    }

    private fun onMessage(message: Message<JsonObject>) {
        if (!message.headers().contains("action")) {
            log.error("No action header specified for message with headers ${message.headers()} " +
                    "and body ${message.body().encodePrettily()}")
            message.fail(ErrorCodes.NO_ACTION_SPECIFIED.ordinal, "No action header specified")
            return
        }
        val action = message.headers().get("action")
        when (action) {
            "all-pages" -> fetchAllPages(message)
            "get-page" -> fetchPage(message)
            "create-page" -> createPage(message)
            "save-page" -> savePage(message)
            "delete-page" -> deletePage(message)
            else -> message.fail(ErrorCodes.BAD_ACTION.ordinal, "Bad action: $action")
        }
    }

    private fun fetchAllPages(message: Message<JsonObject>) {
        dbClient.query(sqlQueries[SqlQuery.ALL_PAGES]) { res ->
            if (res.succeeded()) {
                val pages = res.result()
                        .results
                        .stream()
                        .map { json -> json.getString(0) }.sorted().collect(Collectors.toList())
                message.reply(JsonObject().put("pages", JsonArray(pages)))
            } else {
                reportQueryError(message, res.cause())
            }
        }
    }

    private fun fetchPage(message: Message<JsonObject>) {
        val requestedPage = message.body().getString("page")
        val params = JsonArray().add(requestedPage)
        dbClient.queryWithParams(sqlQueries[SqlQuery.GET_PAGE], params) { fetch ->
            if (fetch.succeeded()) {
                val response = JsonObject()
                val resultSet = fetch.result()
                if (resultSet.numRows == 0) {
                    response.put("found", false)
                } else {
                    response.put("found", true)
                    val row = resultSet.results[0]
                    response.put("id", row.getInteger(0))
                    response.put("rawContent", row.getString(1))
                }
                message.reply(response)
            } else {
                reportQueryError(message, fetch.cause())
            }
        }
    }

    private fun createPage(message: Message<JsonObject>) {
        val request = message.body()
        val data = JsonArray()
                .add(request.getString("title"))
                .add(request.getString("markdown"))
        dbClient.updateWithParams(sqlQueries[SqlQuery.CREATE_PAGE], data) { res ->
            if (res.succeeded()) {
                message.reply("ok")

            }
        }
    }

    private fun savePage(message: Message<JsonObject>) {
        val request = message.body()
        val data = JsonArray()
                .add(request.getString("markdown"))
                .add(request.getString("id"))
        dbClient.updateWithParams(sqlQueries[SqlQuery.SAVE_PAGE], data) { res ->
            if (res.succeeded()) {
                message.reply("ok")
            } else {
                reportQueryError(message, res.cause())
            }
        }
    }

    private fun deletePage(message: Message<JsonObject>) {
        val data = JsonArray().add(message.body().getString("id"))
        dbClient.updateWithParams(sqlQueries[SqlQuery.DELETE_PAGE], data) { res ->
            if (res.succeeded()) {
                message.reply("ok")
            } else {
                reportQueryError(message, res.cause())
            }
        }
    }

    private fun reportQueryError(message: Message<JsonObject>, cause: Throwable) {
        log.error("Database query error", cause)
        message.fail(ErrorCodes.DB_ERROR.ordinal, cause.message)
    }

    enum class ErrorCodes {
        NO_ACTION_SPECIFIED, BAD_ACTION, DB_ERROR
    }

    @Throws(IOException::class)
    private fun loadSqlQueries() {
        val queriesInputStream = javaClass.getResourceAsStream(config().getString(sqlQueriesFile))
        val queriesProps = Properties()
        queriesProps.load(queriesInputStream)
        queriesInputStream.close()
        sqlQueries[SqlQuery.CREATE_PAGES_TABLE] = queriesProps.getProperty("create-pages-table")
        sqlQueries[SqlQuery.ALL_PAGES] = queriesProps.getProperty("all-pages")
        sqlQueries[SqlQuery.GET_PAGE] = queriesProps.getProperty("get-page")
        sqlQueries[SqlQuery.CREATE_PAGE] = queriesProps.getProperty("create-page")
        sqlQueries[SqlQuery.SAVE_PAGE] = queriesProps.getProperty("save-page")
        sqlQueries[SqlQuery.DELETE_PAGE] = queriesProps.getProperty("delete-page")
    }
}