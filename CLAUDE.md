# CLAUDE.md — mcp-auth-demo

Teaching/demo project: a Quarkus MCP server protected by Keycloak.
The goal is to demonstrate OAuth 2.0 / OIDC authentication and scope-based
authorization in a live-demo setting — favour clarity and logging over
production hardening.

---

## Working in this project

### Co-authorship
Every commit in this project should include Claude as co-author. This is explicitly
desired by the project owner — always add the trailer:

```
Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>
```

### Java 25 on this machine
Java 25 is installed via SDKMAN as `25.0.2-tem`. It is **not** the shell default
(default is `21.0.3-tem`). Because `sdk use` doesn't persist across Bash
invocations, prefix every `mvn` call with:

```bash
JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-tem" \
PATH="$HOME/.sdkman/candidates/java/25.0.2-tem/bin:$PATH" \
mvn <goals>
```

Or make it the permanent default once:

```bash
sdk default java 25.0.2-tem
```

---

## Pinned versions

| Component | Version |
|-----------|---------|
| Java | 25 |
| Quarkus | 3.34.6 |
| quarkus-mcp-server (Quarkiverse) | 1.12.0 |
| Keycloak (in README only) | 26.6.1 |

Do not upgrade these without being asked. The Quarkus BOM is
`io.quarkus.platform:quarkus-bom`; the MCP BOM is
`io.quarkiverse.mcp:quarkus-mcp-server-bom`.

---

## Package layout

```
dev.niko.mcp
  bookmark/
    Bookmark.java          record — id (UUID), url, title, tags (List<String>)
    BookmarkStore.java     @ApplicationScoped — ConcurrentHashMap<sub, CopyOnWriteArrayList<Bookmark>>
  security/
    ScopeEnforcer.java     @ApplicationScoped — checks "scope" claim, throws ToolCallException
  tools/
    BookmarkTools.java     @ApplicationScoped — tools: list_bookmarks, add_bookmark, search_bookmarks, delete_bookmark
    WhoamiTool.java        @ApplicationScoped — tool: whoami (no scope required)
```

---

## MCP tools

All tool methods are on `@ApplicationScoped` CDI beans, annotated with
`@Tool(name = "...")`. Return type is `String` (becomes a text MCP content
item). `ToolCallException` → failed `ToolResponse` (MCP-level error).

| Tool | Method | Required scope |
|------|--------|---------------|
| `list_bookmarks` | `BookmarkTools.listBookmarks()` | `bookmarks:read` |
| `search_bookmarks` | `BookmarkTools.searchBookmarks(query)` | `bookmarks:read` |
| `add_bookmark` | `BookmarkTools.addBookmark(url, title, tags?)` | `bookmarks:write` |
| `delete_bookmark` | `BookmarkTools.deleteBookmark(id)` | `bookmarks:write` |
| `whoami` | `WhoamiTool.whoami()` | — (valid token only) |

`tags` in `add_bookmark` is `@ToolArg(required = false) List<String> tags` —
the store handles `null` gracefully.

---

## Authentication and authorisation flow

```
Client request to /mcp/*
  │
  ├─ No / invalid token ──► HTTP 401 + WWW-Authenticate header   (quarkus-oidc)
  │
  └─ Valid token
       │
       ├─ Wrong audience ──► HTTP 401   (quarkus.oidc.token.audience strict check)
       │
       └─ Correct token
            │
            ├─ Missing scope ──► failed ToolResponse (ToolCallException in ScopeEnforcer)
            │
            └─ All good ──► tool executes
```

The two failure modes are intentionally different so the demo audience can see
"not authenticated" vs "authenticated but not authorised".

---

## Key design decisions and what was rejected

### RFC 9728 (Protected Resource Metadata) — built-in, no custom code

`quarkus-oidc` serves `/.well-known/oauth-protected-resource` and injects
`WWW-Authenticate: Bearer resource_metadata="<url>"` on 401 responses
automatically when `quarkus.oidc.resource-metadata.enabled=true`.
**Do not add a custom JAX-RS resource or response filter for this.**

Relevant properties:
```properties
quarkus.oidc.resource-metadata.enabled=true
quarkus.oidc.resource-metadata.resource=${EXPECTED_AUDIENCE:http://localhost:8080/mcp}
quarkus.oidc.resource-metadata.scopes=bookmarks:read,bookmarks:write
quarkus.oidc.resource-metadata.force-https-scheme=false   # needed for localhost
```

### Audience binding — Keycloak Audience mappers, not RFC 8707

Keycloak 26 does not support the RFC 8707 `resource` parameter. Audience
binding is achieved by adding an Audience mapper to each client scope
(`bookmarks:read`, `bookmarks:write`) that injects the MCP server's URL into
the `aud` claim. The server validates this with `quarkus.oidc.token.audience`.

**Do not attempt to implement RFC 8707 client-side resource indicators.**

### Scope enforcement — manual check, not @RolesAllowed

`ScopeEnforcer.require(scope)` reads the `scope` claim (space-separated string)
from `JsonWebToken`, checks membership, and throws `ToolCallException` on
failure. This keeps the authorisation logic visible for demo purposes.
`@RolesAllowed` was rejected because it would require configuring scope→role
mapping, which adds invisible indirection.

### HTTP transport, not SSE

`quarkus-mcp-server-http` (streamable HTTP, MCP 2025-11-25) is used.
The SSE-only transport (`quarkus-mcp-server-sse`) was rejected because it is
the older spec. Do not switch to SSE unless explicitly asked.

### Storage — intentionally in-memory

`BookmarkStore` uses `ConcurrentHashMap<String, CopyOnWriteArrayList<Bookmark>>`
keyed by the user's `sub` claim. Data is lost on restart. This is a deliberate
demo constraint — do not add a persistence layer unless asked.

---

## Injection patterns

`JsonWebToken` and `SecurityIdentity` are request-scoped CDI beans. They are
injected into `@ApplicationScoped` tool beans via CDI proxy — this works
because every MCP tool call arrives on an HTTP request, keeping the request
context active.

```java
@Inject JsonWebToken jwt;                    // org.eclipse.microprofile.jwt.JsonWebToken
@Inject SecurityIdentity identity;           // io.quarkus.security.identity.SecurityIdentity
@Inject ObjectMapper mapper;                 // from quarkus-rest-jackson
```

---

## Logging convention

Every tool invocation logs one INFO line **before** the scope check:

```
tool=<name> sub=<sub> user=<preferred_username> scope=[<scope>] aud=<aud> args={<k=v, ...>}
```

Keep it single-line, no raw tokens or signatures.
Format is in `BookmarkTools.log()` and inline in `WhoamiTool.whoami()`.

---

## Build and run

```bash
# Dev mode (requires ISSUER_URL and EXPECTED_AUDIENCE env vars or defaults work if Keycloak is on :8180)
mvn quarkus:dev

# Package
mvn package -DskipTests

# Docker image
docker build -f src/main/docker/Dockerfile.jvm -t mcp-auth-demo:1.0.0-SNAPSHOT .

# Docker Compose (MCP server only — Keycloak is started separately)
ISSUER_URL=http://host.docker.internal:8180/realms/mcp-demo \
EXPECTED_AUDIENCE=http://localhost:8080/mcp \
docker compose up
```

Health: `GET /q/health`
Resource metadata: `GET /.well-known/oauth-protected-resource`
MCP endpoint: `POST /mcp`

---

## Environment variables

| Variable | Default | Purpose |
|----------|---------|---------|
| `ISSUER_URL` | `http://localhost:8180/realms/mcp-demo` | Keycloak realm URL / OIDC issuer |
| `EXPECTED_AUDIENCE` | `http://localhost:8080/mcp` | Expected `aud` claim |
| `QUARKUS_HTTP_PORT` | `8080` | HTTP listen port |

---

## What this project does NOT have (and why)

- No unit or integration tests — demo project, not a product
- No native image build — JVM mode only; no `Dockerfile.native`
- No rate limiting, CORS customisation, or production security hardening
- No persistence layer beyond the in-memory map
- No SSE transport (uses streamable HTTP instead)
