package com.bogdan.starter.verticles

import com.bogdan.starter.Constants.httpServerPort
import com.bogdan.starter.Constants.dbQueue
import com.bogdan.starter.Constants.jdbcDriverClass
import com.bogdan.starter.Constants.jdbcMaxPoolSize
import com.bogdan.starter.Constants.jdbcUrl
import com.bogdan.starter.Constants.sqlQueriesFile
import com.bogdan.starter.database.WikiDatabaseVerticle
import io.vertx.core.AbstractVerticle
import io.vertx.core.DeploymentOptions
import io.vertx.core.Future
import io.vertx.core.json.JsonObject
import org.slf4j.LoggerFactory

class MainVerticle : AbstractVerticle() {

    private val log = LoggerFactory.getLogger(MainVerticle::class.java)

    @Throws(Exception::class)
    override fun start(startFuture: Future<Void>) {
        val config = getConfiguration()

        val dbVerticleDeployment = Future.future<String>()
        vertx.deployVerticle(
                WikiDatabaseVerticle(),
                DeploymentOptions().setConfig(config),
                dbVerticleDeployment.completer()
        )
        dbVerticleDeployment.compose {
            val httpVerticleDeployment = Future.future<String>()
            vertx.deployVerticle(
                    "com.bogdan.starter.verticles.HttpServerVerticle",
                    DeploymentOptions().setInstances(2).setConfig(config),
                    httpVerticleDeployment.completer()
            )
            httpVerticleDeployment
        }.setHandler { ar ->
            if (ar.succeeded()) {
                startFuture.complete()
            } else {
                log.error("Error while starting main verticle ${ar.cause()}")
                startFuture.fail(ar.cause())
            }
        }
    }

    private fun getConfiguration(): JsonObject {
        val config = config()
        config.put(dbQueue, dbQueue)
        config.put(httpServerPort, 8080)
        config.put(jdbcUrl, "jdbc:hsqldb:file:db/wiki")
        config.put(jdbcDriverClass, "org.hsqldb.jdbcDriver")
        config.put(jdbcMaxPoolSize, 30)
        config.put(sqlQueriesFile, "/db-queries.properties")
        return config
    }

}
