# mcp-auth-demo

A teaching/demo project that shows how to protect an MCP server with Keycloak.
Built with Quarkus 3.34.6, the `quarkus-mcp-server-http` Quarkiverse extension, and
`quarkus-oidc`.

**This is a demo — all bookmark data is stored in memory and lost on restart.**

---

## What it does

The server exposes five MCP tools over HTTP transport (MCP spec 2025-11-25):

| Tool | Required scope |
|------|---------------|
| `whoami` | any valid token |
| `list_bookmarks` | `bookmarks:read` |
| `search_bookmarks` | `bookmarks:read` |
| `add_bookmark` | `bookmarks:write` |
| `delete_bookmark` | `bookmarks:write` |

On every unauthenticated request to `/mcp/*` the server returns:

```
HTTP 401 Unauthorized
WWW-Authenticate: Bearer resource_metadata="http://localhost:8180/.well-known/oauth-protected-resource"
```

The `/.well-known/oauth-protected-resource` endpoint (RFC 9728) is served
automatically by `quarkus-oidc` and advertises the Keycloak issuer URL and the
two supported scopes. A scope-check failure returns a failed MCP `ToolResponse`
(not an HTTP 401), so the demo can show the difference between "not authenticated"
and "authenticated but not authorised".

Every tool invocation is logged at INFO level (tool name, sub, username, scopes,
audience, arguments) so a live-demo audience can follow along on screen.

---

## Build & run

### Prerequisites

- Java 25
- Maven 3.9+
- Docker (for the container image)
- A running Keycloak 26.x instance — see [Keycloak setup](#keycloak-setup) below

### Run locally with Maven (dev mode)

```bash
export ISSUER_URL=http://localhost:8080/realms/mcp-demo
export EXPECTED_AUDIENCE=http://localhost:8180/mcp
mvn quarkus:dev
```

### Build and run as a container

```bash
# 1. Build the fat jar
mvn package -DskipTests

# 2. Build the Docker image
docker build -f src/main/docker/Dockerfile.jvm -t mcp-auth-demo:1.0.0-SNAPSHOT .

# 3. Run with docker compose
ISSUER_URL=http://host.docker.internal:8080/realms/mcp-demo \
EXPECTED_AUDIENCE=http://localhost:8180/mcp \
docker compose up
```

### Environment variables

| Variable | Default                                 | Description |
|----------|-----------------------------------------|-------------|
| `ISSUER_URL` | `http://localhost:8080/realms/mcp-demo` | Keycloak realm URL (used as OIDC issuer) |
| `EXPECTED_AUDIENCE` | `http://localhost:8180/mcp`             | Expected `aud` claim — must match the audience mapper value in Keycloak |
| `QUARKUS_HTTP_PORT` | `8180`                                  | HTTP port |

---

## Keycloak setup

Tested with Keycloak **26.6.1**.

### 1 — Create the realm

1. Open the Keycloak admin console.
2. Create a new realm named **`mcp-demo`**.

### 2 — Create client scopes

Go to **Client scopes → Create client scope** twice:

| Name | Type | Description |
|------|------|-------------|
| `bookmarks:read` | Optional | Read access to bookmarks |
| `bookmarks:write` | Optional | Write/delete access to bookmarks |

For **each** scope, add an **Audience mapper**:

1. Open the scope → **Mappers** tab → **Add mapper → By configuration → Audience**.
2. Set **Name**: `mcp-server-audience`.
3. **Included Custom Audience**: type your `EXPECTED_AUDIENCE` value directly, e.g. `http://localhost:8180/mcp`.
4. **Add to access token**: ON.

> Use **Included Custom Audience** (not *Included Client Audience*). The latter adds a
> client ID string to `aud`, not a URL. With a custom audience you don't need a dedicated
> `mcp-server` client at all — the URL is injected directly.

### 3 — Create the end-user client (used by the MCP client / Claude Code)

This is the client that your MCP client uses to obtain tokens.

1. **Clients → Create client**, Client ID: `mcp-client`.
2. **Client authentication**: OFF (public client — no secret needed).
3. Enable **Standard flow** and **Device authorization grant** (useful for CLI tools).
4. Set **Valid redirect URIs** to `http://localhost/*` (adjust as needed).
5. Save.

### 4 — Assign scopes to the end-user client

1. Open `mcp-client` → **Client scopes** tab.
2. **Add client scope**: add `bookmarks:read` and `bookmarks:write` as **Optional** scopes.

Now a token requested for `mcp-client` will include the bookmarks scopes only when
explicitly requested (e.g. `scope=bookmarks:read bookmarks:write`).

Quarkus OIDC validates that the `aud` claim contains the value of `EXPECTED_AUDIENCE`.
The mapper added in step 2 ensures Keycloak injects this value into the token whenever
`bookmarks:read` or `bookmarks:write` is included.

### Quick token acquisition (curl)

```bash
# Token with both scopes
TOKEN=$(curl -s -X POST \
  http://localhost:8080/realms/mcp-demo/protocol/openid-connect/token \
  -d "client_id=mcp-client" \
  -d "grant_type=password" \
  -d "username=<your-test-user>" \
  -d "password=<password>" \
  -d "scope=openid bookmarks:read bookmarks:write" \
  | jq -r .access_token)

# Call whoami
curl -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -X POST http://localhost:8180/mcp \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"whoami","arguments":{}}}'
```

---

## Connecting Claude Code

Add to your project's `.mcp.json`:

```json
{
  "mcpServers": {
    "bookmarks": {
      "type": "http",
      "url": "http://localhost:8180/mcp"
    }
  }
}
```

Claude Code will discover the authorization server via the RFC 9728 metadata endpoint,
perform the OAuth flow against Keycloak, and attach the bearer token automatically.

For manual testing with a pre-obtained token you can pass it as a header:

```json
{
  "mcpServers": {
    "bookmarks": {
      "type": "http",
      "url": "http://localhost:8180/mcp",
      "headers": {
        "Authorization": "Bearer <your-token>"
      }
    }
  }
}
```

---

## Known limitations

- **In-memory storage only.** All bookmarks are lost when the server restarts. This
  is intentional — the demo focuses on auth, not persistence.
- **No RFC 8707 resource indicator on the client side.** The MCP 2025-11-25 spec
  requires clients to send the `resource` parameter so the authorization server can
  bind the token to a specific audience. Keycloak does not yet support the `resource`
  parameter. Audience binding is achieved instead via Keycloak client scopes with
  Audience mappers, as described above.
- **No multi-tenant support.** A single OIDC configuration is used; all users share
  the same Keycloak realm.
- **No rate limiting, CORS customisation, or production hardening** — this is
  intentionally a teaching project.
