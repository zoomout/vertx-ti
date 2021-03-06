package com.bogdan.starter

import com.bogdan.starter.verticles.MainVerticle
import io.vertx.core.Vertx
import io.vertx.junit5.Timeout
import io.vertx.junit5.VertxExtension
import io.vertx.junit5.VertxTestContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.util.concurrent.TimeUnit

@ExtendWith(VertxExtension::class)
class MainVerticleTest {

    @BeforeEach
    fun deployVerticle(vertx: Vertx, testContext: VertxTestContext) {
        vertx.deployVerticle(MainVerticle(), testContext.succeeding<String> { testContext.completeNow() })
    }

    @Test
    @DisplayName("Web server should start")
    @Timeout(value = 10, timeUnit = TimeUnit.SECONDS)
    @Throws(Throwable::class)
    fun startHttpServer(vertx: Vertx, testContext: VertxTestContext) {
        vertx.createHttpClient().getNow(8080, "localhost", "/") { response ->
            testContext.verify {
                assertEquals(response.statusCode(), 200)
                response.handler { body ->
                    assertTrue(body.toString().contains("Wiki home"))
                    testContext.completeNow()
                }
            }
        }
    }

}
