# catalog-api: JSON REST API on plain Java Servlets

A small, complete example of a REST API **without a framework**: Jakarta Servlets on Tomcat,
MySQL over JDBC, Maven, packaged as a WAR. It exists to show working style: structure, security
defaults, tests and documentation. It is not a product.

## Stack
| Part | Choice |
|---|---|
| Runtime | Java 21, Jakarta Servlet 6 (Tomcat 10.1) |
| Data | MySQL 8, plain JDBC with prepared statements, HikariCP pool |
| JSON | Jackson, snake_case |
| Build | Maven, WAR packaging |
| Tests | JUnit 5, Testcontainers (real MySQL), embedded Tomcat (real HTTP) |

## Design
- **Front controller** (`ApiServlet`) on `/api/v1/*` with a small router (`Router`): method +
  path template, `405` with an `Allow` header for known paths, `404` otherwise.
- **Bearer authentication** (`BearerAuthFilter`): opaque random tokens, only the **SHA-256 hash**
  is stored (`api_client` table), tokens can be revoked. `/health` is the only public path.
- **Uniform envelope**: `{"data": ...}` on success, `{"error": {"code", "message"}}` on failure,
  always with a matching HTTP status (400, 401, 404, 405, 409, 413, 415, 500).
- **Input validation** collects every problem in one message (unknown fields, types, ranges,
  ISBN-13 check digit).
- **No client input in SQL text**: filters are bound parameters, sort fields go through a whitelist.
- Body size limit (64 KB) and a strict `Content-Type: application/json` check on writes.

## Endpoints
| Method | Path | Notes |
|---|---|---|
| GET | `/api/v1/health` | public |
| GET | `/api/v1/books` | `author`, `year_from`, `year_to`, `sort` (`title`, `author`, `year`, `id`, prefix `-` for desc), `offset`, `limit` (1–100) |
| POST | `/api/v1/books` | `isbn` (13 digits), `title`, `author`, `year` |
| GET | `/api/v1/books/{id}` | |
| PUT | `/api/v1/books/{id}` | full replacement |
| DELETE | `/api/v1/books/{id}` | `204` |

```bash
curl -s -H "Authorization: Bearer $TOKEN" "http://localhost:8080/catalog-api/api/v1/books?sort=-year&limit=5"
```

## Build and test
Requires JDK 21, Maven 3.9 and a running Docker daemon (for Testcontainers).

```bash
mvn verify        # 23 tests: unit + end-to-end against real MySQL, builds target/catalog-api.war
```

## Deploy
1. Create a MySQL database and user.
2. Set `CATALOG_DB_URL`, `CATALOG_DB_USER`, `CATALOG_DB_PASSWORD` for Tomcat (or context
   params `catalog.db.url` / `.user` / `.password`).
3. Drop `target/catalog-api.war` into `webapps/`. The schema is created on first start
   (`schema.sql`, idempotent).
4. Issue a token: `new ApiClients(dataSource).create("client-name")` returns it once; only the
   hash is stored. (A CLI for this is deliberately left out of the example.)

## Project layout
```
src/main/java/dev/anix/catalog/
  App.java, AppListener.java   wiring and bootstrap
  api/ApiServlet.java          front controller, error envelope
  auth/                        tokens, client store, bearer filter
  books/                       resource, validation, query options, JDBC repository
  db/Database.java             pool + schema bootstrap
  http/                        router, request/response types, JSON helpers
src/test/java/...              RouterTest, TokensTest, BookInputTest, CatalogApiIntegrationTest
```

## License
MIT
