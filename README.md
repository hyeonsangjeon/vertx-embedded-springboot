# Vert.x Embedded Spring Boot

> A demonstration of asynchronous multi-threaded worker pattern using Vert.x embedded in Spring Boot for distributed processing in Java.

## Overview

This project showcases how to integrate Vert.x's reactive, event-driven architecture with Spring Boot's dependency injection and data access capabilities. It implements a **Facade + Worker** pattern where HTTP requests are handled by a single Vert.x facade verticle and processed asynchronously by multiple worker verticles.

## Features

- **Vert.x Event Bus Communication**: Service Proxy pattern for type-safe async messaging
- **Multi-threaded Worker Pattern**: Configurable worker verticle instances for parallel processing
- **Spring Boot Integration**: Full dependency injection support for verticles
- **Dual Persistence Layer**: Both JPA/Hibernate and MyBatis examples
- **Database Migration**: Liquibase-based schema management
- **Health Monitoring**: Spring Boot Actuator endpoints
- **SQL Debugging**: log4jdbc for detailed query logging

## Architecture

```
HTTP Request → VertxFacade → RouteHandler → RequestHandler
    → Event Bus → VertxWorker → BookAsyncService → BookService → Database
```

**Key Components:**
- **VertxFacade**: Single instance handling HTTP requests on event loop threads
- **VertxWorker**: Multiple instances (default: 4) processing requests on worker thread pool
- **Event Bus**: Routes async messages between facade and worker verticles
- **Spring Integration**: SpringVerticleFactory enables verticles as Spring beans

## Quick Start

### Prerequisites
- Java 8+
- Maven 3+

### Run with H2 Database (In-Memory) 

```bash
mvn clean spring-boot:run -P h2local
```

### Run with MariaDB

Update `src/main/resources/profiles/mariadb/application.properties` with your database configuration, then:

```bash
mvn clean spring-boot:run -P mariadb
```

### Verify Startup

The application will:
1. Start Spring Boot
2. Run Liquibase database migrations
3. Deploy Vert.x verticles (1 Facade + 4 Workers)
4. Start listening on configured ports

Watch the console for `SpringWorker started` messages showing worker threads initialized.

## Application Ports

| Port | Service | Description |
|------|---------|-------------|
| **8989** | Vert.x HTTP Server | REST API endpoints |
| **9000** | Spring Boot | Internal (JDBC connection pool) |
| **7979** | Spring Actuator | Health monitoring and metrics |

## Monitoring

### Check Database Migration Status

Access Spring Boot Actuator (credentials: `bookexample` / `1234`):

```bash
# Actuator root
curl -u bookexample:1234 http://localhost:7979/actuator

# Liquibase migration details
curl -u bookexample:1234 http://localhost:7979/actuator/liquibase
```

### Observe Worker Thread Processing

Check console logs to see which worker thread handles each request:
```
[worker-thread-0] Processing request...
[worker-thread-1] Processing request...
[worker-thread-2] Processing request...
```

## API Usage

All endpoints are served on port **8989** under `/book`:

### List All Books
```bash
curl http://localhost:8989/book/list
```

### Get Single Book
```bash
curl http://localhost:8989/book/id/1
```

### Create Book
```bash
curl -X POST http://localhost:8989/book/add \
  -H "Content-Type: application/json" \
  -d '{
    "name": "marble comics",
    "author": "marble",
    "pages": 987
  }'
```

### Update Book
```bash
curl -X PUT http://localhost:8989/book/update \
  -H "Content-Type: application/json" \
  -d '{
    "id": 1,
    "name": "updated title",
    "author": "new author",
    "pages": 500
  }'
```

### Delete Book
```bash
curl -X DELETE http://localhost:8989/book/delete/2
```

### Response Format

All responses follow this structure:
```json
{
  "statusCode": 200,
  "data": { ... },
  "message": "operation success"
}
```

## Configuration

Configuration files are located in `src/main/resources/profiles/{profile}/`:
- **h2local**: In-memory H2 database (default)
- **mariadb**: MariaDB configuration

### Key Vert.x Settings

Edit `application.properties` to configure:

```properties
# Vert.x HTTP server port
vertx.port=8989

# Worker thread pool size (must be >= worker instances)
vertx.worker.pool.size=6

# Number of worker verticle instances
vertx.springWorker.instances=4

# Event loop execution time limit (ms)
vertx.max.eventloop.execute.time=10000

# Blocked thread check interval (ms)
vertx.blocked.threa.check.interval=1000
```

## How It Works

1. **Startup**: Spring Boot initializes and runs Liquibase migrations
2. **Verticle Deployment**: When `ApplicationReadyEvent` fires, both VertxFacade and VertxWorker verticles are deployed
3. **Request Handling**: HTTP requests arrive at VertxFacade (event loop thread)
4. **Event Bus**: VertxFacade sends messages via event bus using Service Proxy
5. **Worker Processing**: One of the VertxWorker instances picks up the message (worker thread)
6. **Business Logic**: Worker executes transactional service methods (JPA or MyBatis)
7. **Response**: Result flows back through event bus to VertxFacade, then to HTTP client

## Testing

Run tests with:
```bash
mvn test
```

Build JAR package:
```bash
mvn clean package
```

## Technology Stack

- **Vert.x 3.9.4**: Reactive toolkit for the JVM
- **Spring Boot 1.5.8**: Application framework
- **Spring Data JPA**: Repository abstraction
- **MyBatis 3.5.6**: SQL mapping framework
- **Liquibase**: Database migration
- **H2 / MariaDB**: Database options
- **log4jdbc**: SQL logging wrapper

## References

- [Vert.x Core Documentation](https://vertx.io/docs/vertx-core/java/)
- [Vert.x GitHub Repository](https://github.com/vert-x3/)
- [Vert.x Service Proxies](http://vertx.io/docs/vertx-service-proxy/java/)
- [Spring Boot Documentation](https://docs.spring.io/spring-boot/docs/1.5.x/reference/html/)

## License

This is an example project for educational purposes.



