# MyBank OBP Adapter

A bank-specific OBP adapter built on top of the [OBP-Rabbit-Cats-Adapter](https://github.com/OpenBankProject/OBP-Rabbit-Cats-Adapter) framework.

## Overview

This adapter implements the `LocalAdapter` interface to connect OBP (Open Bank Project) API to a Core Banking System (CBS) via RabbitMQ messaging. Currently configured with mock/stub data for development and testing.

## Prerequisites

- Java 11+
- Maven 3.6+
- The OBP-Rabbit-Cats-Adapter library installed locally

## Installation

### Step 1: Build the Base Library

First, you need to build and install the OBP-Rabbit-Cats-Adapter library:

```bash
cd /path/to/OBP-Rabbit-Cats-Adapter
mvn clean install
```

### Step 2: Build This Adapter

```bash
cd /path/to/my-obp-bank-rabbit-cats-compatible-adapter
mvn clean package
```

This produces a fat JAR at `target/mybank-obp-adapter-1.0.0-SNAPSHOT.jar`.

## Running the Adapter

### Environment Variables

Configure RabbitMQ connection via environment variables:

| Variable | Default | Description |
|----------|---------|-------------|
| `RABBITMQ_HOST` | `localhost` | RabbitMQ server hostname |
| `RABBITMQ_PORT` | `5672` | RabbitMQ AMQP port |
| `RABBITMQ_USERNAME` | `guest` | RabbitMQ username |
| `RABBITMQ_PASSWORD` | `guest` | RabbitMQ password |
| `RABBITMQ_VIRTUAL_HOST` | `/` | RabbitMQ virtual host |
| `RABBITMQ_REQUEST_QUEUE` | `obp.request` | Queue for incoming OBP messages |
| `RABBITMQ_RESPONSE_QUEUE` | `obp.response` | Queue for outgoing responses |
| `HTTP_SERVER_ENABLED` | `true` | Enable discovery HTTP server |
| `HTTP_SERVER_PORT` | `52345` | Discovery server port |
| `REDIS_ENABLED` | `false` | Enable Redis for message counting |

### Start the Adapter

```bash
# With default settings (localhost RabbitMQ)
java -jar target/mybank-obp-adapter-1.0.0-SNAPSHOT.jar

# With custom RabbitMQ settings
RABBITMQ_HOST=rabbitmq.example.com \
RABBITMQ_USERNAME=myuser \
RABBITMQ_PASSWORD=mypassword \
java -jar target/mybank-obp-adapter-1.0.0-SNAPSHOT.jar
```

## Supported Operations

The adapter currently supports the following OBP message types:

| Operation | Description |
|-----------|-------------|
| `obp.getAdapterInfo` | Get adapter metadata and supported operations |
| `obp.getBank` | Get bank information |
| `obp.getBanks` | Get list of all banks |
| `obp.getBankAccount` | Get account details |
| `obp.getBankAccounts` | Get list of accounts |
| `obp.getTransaction` | Get transaction details |
| `obp.getTransactions` | Get transaction history |
| `obp.checkFundsAvailable` | Check if funds are available |
| `obp.makePayment` | Process a payment |

## Project Structure

```
my-obp-bank-rabbit-cats-compatible-adapter/
├── pom.xml                           # Maven configuration
├── README.md                         # This file
├── LICENSE                           # License file
└── src/
    └── main/
        ├── scala/
        │   └── com/
        │       └── mybank/
        │           └── adapter/
        │               ├── MyBankLocalAdapter.scala  # Local adapter implementation
        │               └── MyBankMain.scala          # Application entry point
        └── resources/
            └── logback.xml           # Logging configuration
```

## Customizing for Real CBS

To connect to a real Core Banking System, modify the `MyBankLocalAdapter.scala`:

1. Add HTTP client dependencies to `pom.xml` (http4s is already included via the base library)
2. Replace mock method implementations with actual CBS API calls
3. Update error handling for CBS-specific error codes
4. Add authentication/authorization for CBS connections

Example of calling a real CBS API:

```scala
import org.http4s.client.Client
import org.http4s.ember.client.EmberClientBuilder

private def getBank(data: JsonObject, callContext: CallContext): IO[LocalAdapterResult] = {
  val bankId = data("bankId").flatMap(_.asString).getOrElse("unknown")

  EmberClientBuilder.default[IO].build.use { client =>
    client.expect[Json](s"https://cbs.mybank.com/api/banks/$bankId")
      .map { response =>
        LocalAdapterResult.success(response.asObject.getOrElse(JsonObject.empty))
      }
      .handleErrorWith { error =>
        IO.pure(LocalAdapterResult.error("CBS_ERROR", error.getMessage))
      }
  }
}
```

## Testing

### With Docker (RabbitMQ)

```bash
# Start RabbitMQ
docker run -d --name rabbitmq -p 5672:5672 -p 15672:15672 rabbitmq:3-management

# Run the adapter
java -jar target/mybank-obp-adapter-1.0.0-SNAPSHOT.jar
```

### Sending Test Messages

You can send test messages to the `obp.request` queue using the RabbitMQ Management UI or a client tool:

```json
{
  "process": "obp.getBank",
  "outboundAdapterCallContext": {
    "correlationId": "test-123",
    "sessionId": "session-456"
  },
  "bankId": "mybank-01"
}
```

## Architecture

```
┌─────────────────┐     ┌──────────────────┐     ┌─────────────────┐
│   OBP API       │────▶│   RabbitMQ       │────▶│  MyBank         │
│                 │     │   obp.request    │     │  Local Adapter  │
└─────────────────┘     └──────────────────┘     └────────┬────────┘
                                                          │
                                                          ▼
┌─────────────────┐     ┌──────────────────┐     ┌─────────────────┐
│   OBP API       │◀────│   RabbitMQ       │◀────│  CBS Connection │
│                 │     │   obp.response   │     │  (Mock/Real)    │
└─────────────────┘     └──────────────────┘     └─────────────────┘
```

## License

See the [LICENSE](LICENSE) file for details.
