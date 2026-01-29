/*
 * MyBank OBP Adapter - Main Entry Point
 *
 * This is the main entry point for the MyBank OBP adapter.
 * It initializes the adapter with the MyBankLocalAdapter and starts
 * the RabbitMQ consumer to process OBP messages.
 */

package com.mybank.adapter

import cats.effect.{ExitCode, IO, IOApp}
import com.tesobe.obp.adapter.config.Config
import com.tesobe.obp.adapter.http.DiscoveryServer
import com.tesobe.obp.adapter.messaging.{
  RabbitMQClient,
  RabbitMQConsumer,
  RedisCounter
}
import com.tesobe.obp.adapter.telemetry.ConsoleTelemetry

object MyBankMain extends IOApp {

  def run(args: List[String]): IO[ExitCode] = {
    val banner =
      """
        |===============================================================
        |     MyBank OBP Adapter
        |     Version 1.0.0-SNAPSHOT
        |     Compatible with OBP-Rabbit-Cats-Adapter
        |===============================================================
        |""".stripMargin

    for {
      _ <- IO.println(banner)

      // Load configuration
      _ <- IO.println("[CONFIG] Loading configuration...")
      config <- Config.load
      _ <- IO.println(s"[OK] Configuration loaded")
      _ <- IO.println(
        s"   HTTP Server: ${config.http.host}:${config.http.port}"
      )
      _ <- IO.println(
        s"   RabbitMQ: ${config.rabbitmq.host}:${config.rabbitmq.port}"
      )
      _ <- IO.println(s"   Request Queue: ${config.queue.requestQueue}")
      _ <- IO.println(s"   Response Queue: ${config.queue.responseQueue}")
      _ <- IO.println("")

      // Validate configuration
      _ <- IO.println("[CONFIG] Validating configuration...")
      _ <- Config.validate(config)
      _ <- IO.println("[OK] Configuration valid")
      _ <- IO.println("")

      // Create telemetry
      telemetry = new ConsoleTelemetry()
      _ <- IO.println("[TELEMETRY] Initialized (Console mode)")
      _ <- IO.println("")

      // Create MyBank local adapter
      _ <- IO.println("[ADAPTER] Initializing MyBank local adapter...")
      localAdapter = new MyBankLocalAdapter(telemetry)
      _ <- IO.println(
        s"[OK] Local Adapter: ${localAdapter.name} v${localAdapter.version}"
      )
      _ <- IO.println("")

      // Test adapter health
      _ <- IO.println("[HEALTH] Checking adapter health...")
      healthResult <- localAdapter.checkHealth(
        com.tesobe.obp.adapter.models.CallContext(
          correlationId = "startup-health-check",
          sessionId = Some("startup"),
          userId = None,
          username = None,
          consumerId = None,
          generalContext = Map.empty
        )
      )
      _ <- healthResult match {
        case com.tesobe.obp.adapter.interfaces.LocalAdapterResult.Success(data, _) =>
          IO.println("[OK] Adapter is healthy")
        case com.tesobe.obp.adapter.interfaces.LocalAdapterResult
              .Error(code, msg, _) =>
          IO.println(s"[WARNING] Adapter health check failed: $code - $msg")
      }
      _ <- IO.println("")

      // Create RabbitMQ client for test messages
      rabbitClient = RabbitMQClient(config)
      _ <- IO(DiscoveryServer.setRabbitClient(rabbitClient))

      // Initialize Redis if enabled
      _ <-
        if (config.redis.enabled) {
          IO.println(
            s"[REDIS] Connecting to ${config.redis.host}:${config.redis.port}..."
          )
        } else {
          IO.println("[REDIS] Redis disabled")
        }

      // Start HTTP discovery server and RabbitMQ consumer concurrently
      _ <- IO.println("[STARTUP] Starting services...")
      _ <- IO.println("")

      exitCode <- (
        if (config.redis.enabled) {
          RedisCounter.create(config.redis.host, config.redis.port).use {
            redis =>
              IO(DiscoveryServer.setRedisCommands(redis)) *>
                IO.println("[OK] Redis connected") *>
                IO.println("") *>
                (if (config.http.enabled) {
                   DiscoveryServer.start(config).use { server =>
                     val displayHost =
                       if (config.http.host == "0.0.0.0") "localhost"
                       else config.http.host
                     IO.println(
                       s"[HTTP] Discovery server started at http://$displayHost:${config.http.port}"
                     ) *>
                       IO.println(
                         s"[INFO] Visit http://localhost:${config.http.port} to see service info"
                       ) *>
                       IO.println("") *>
                       RabbitMQConsumer.run(
                         config,
                         localAdapter,
                         telemetry,
                         Some(redis)
                       )
                   }
                 } else {
                   IO.println("[INFO] HTTP server disabled") *>
                     IO.println("") *>
                     RabbitMQConsumer.run(
                       config,
                       localAdapter,
                       telemetry,
                       Some(redis)
                     )
                 })
          }
        } else {
          if (config.http.enabled) {
            DiscoveryServer.start(config).use { server =>
              val displayHost =
                if (config.http.host == "0.0.0.0") "localhost"
                else config.http.host
              IO.println(
                s"[HTTP] Discovery server started at http://$displayHost:${config.http.port}"
              ) *>
                IO.println(
                  s"[INFO] Visit http://localhost:${config.http.port} to see service info"
                ) *>
                IO.println("") *>
                RabbitMQConsumer.run(config, localAdapter, telemetry, None)
            }
          } else {
            IO.println("[INFO] HTTP server disabled") *>
              IO.println("") *>
              RabbitMQConsumer.run(config, localAdapter, telemetry, None)
          }
        }
      ).as(ExitCode.Success).handleErrorWith { error =>
        IO.println(s"[FATAL] Fatal error: ${error.getMessage}") *>
          IO(error.printStackTrace()) *>
          IO.pure(ExitCode.Error)
      }

    } yield exitCode
  }
}
