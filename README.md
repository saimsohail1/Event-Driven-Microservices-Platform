# Event-Driven Microservices with Kafka

This project implements an event-driven microservices architecture using Spring Boot and Apache Kafka.

## Architecture

- **Order Service** (Port 8081): Creates orders and publishes `OrderCreatedEvent` to Kafka
- **Inventory Service** (Port 8082): Consumes order events and reserves stock
- **Payment Service** (Port 8083): Consumes order events and records payments
- **Frontend** (Port 8080): Static dashboard; nginx reverse-proxies `/api/*` to the services
- **platform-commons-starter**: shared Spring Boot library (correlation IDs, error body, RestClient retry + metrics, Kafka DLT + health). See [platform-commons-starter/README.md](platform-commons-starter/README.md).

The browser only ever talks to the frontend origin, so the services need no
public endpoints and no CORS configuration.

### Event flow

1. `POST /api/orders` writes the order **and** an `outbox_events` row in one transaction.
2. A scheduled publisher in Order Service drains the outbox to the `order.created` topic, keyed by order id.
3. Inventory Service reserves stock; Payment Service records a payment.
4. Both consumers are idempotent, so a redelivered event does not double-apply.

Because the event is committed with the order, an order can never be saved
without its event eventually being published — even if Kafka is down at the
time of the request. Messages that cannot be processed end up on
`order.created.DLT` instead of blocking their partition.

### Schema management

Each service owns its database and its schema is managed by **Flyway**
(`src/main/resources/db/migration`). Hibernate runs with `ddl-auto: none` and
never alters tables. To change the schema, add a new `V<n>__*.sql` migration.

## Prerequisites

1. **Docker + Docker Compose** — for Kafka, ZooKeeper and the databases
2. **Java 21+** — JDK 21 or higher
3. **Maven** — build tool

## Running locally

Everything, including the databases and the dashboard, comes up with Compose:

```bash
docker compose up --build -d
```

Then open the dashboard at <http://localhost:8080>.

Compose waits for Kafka and each database to report healthy before starting the
services, and Flyway creates the schemas on first boot. No manual database or
topic setup is needed.

To follow logs or shut down:

```bash
docker compose logs -f order-service
docker compose down          # add -v to also drop the database volumes
```

### Running a service from your IDE

Start only the infrastructure, then run the service on the host:

```bash
docker compose up -d zookeeper kafka order-db inventory-db payment-db

cd Order-service && mvn spring-boot:run
```

Kafka advertises a second listener on `localhost:9092` for exactly this case,
which is the default in each `application.yml`.

### Building and testing

The three services depend on `platform-commons-starter`. From the repo root
the reactor builds the starter first:

```bash
mvn -B verify
```

To build one service on its own, install the starter first:

```bash
mvn -B -f platform-commons-starter/pom.xml install -DskipTests
cd Order-service && mvn -B verify
```

Compose images copy each service's `target/*.jar`, so package before
`docker compose up --build`:

```bash
mvn -B package -DskipTests
docker compose up --build -d
```

## Trying the flow

1. Stock a product (inventory is never invented from thin air, so this comes first):

```bash
curl -X PUT http://localhost:8080/api/inventory \
  -H "Content-Type: application/json" \
  -d '{"productId": "PROD-001", "availableQuantity": 100}'
```

2. Create an order:

```bash
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{"productId": "PROD-001", "quantity": 5, "price": 99.99}'
```

3. Watch the consumers react (allow a second for the outbox poll):

```bash
curl http://localhost:8080/api/inventory/PROD-001   # 100 -> 95
curl http://localhost:8080/api/payments             # one payment of 499.95
curl http://localhost:8080/api/orders
```

## Service Endpoints

All endpoints are reachable through the frontend proxy on port 8080, or
directly on each service's own port.

### Order Service (Port 8081)
- `POST /api/orders` — create an order (`productId`, `quantity`, `price`)
- `GET /api/orders` — list orders, newest first

### Inventory Service (Port 8082)
- `GET /api/inventory` — list all stock levels
- `GET /api/inventory/{productId}` — stock for one product (404 if unknown)
- `PUT /api/inventory` — set stock for a product, creating it if needed

### Payment Service (Port 8083)
- `GET /api/payments` — list payments, newest first
- `POST /api/payments?orderId={id}&amount={amount}` — record a payment (409 if the order already has one)

### Health (all services)
- `GET /actuator/health/liveness` — process is up; does not touch the database
- `GET /actuator/health/readiness` — ready to serve; includes the database
- `GET /actuator/prometheus` — scrape endpoint (Micrometer Prometheus registry)

## Deploying to Kubernetes

See [k8s/README.md](k8s/README.md).
