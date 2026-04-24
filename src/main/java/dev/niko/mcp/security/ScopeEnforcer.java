package dev.niko.mcp.security;

import java.util.Set;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.jwt.JsonWebToken;

import io.quarkiverse.mcp.server.ToolCallException;

/**
 * Checks that the presented token contains the required OAuth scope.
 *
 * Authentication has already been enforced at the HTTP layer (quarkus.http.auth.permission),
 * so by the time this is called the token is valid. A missing scope means "authenticated but
 * not authorised" — the ToolCallException propagates as a failed MCP ToolResponse, which is
 * intentionally different from the HTTP 401 the client sees for missing/invalid tokens.
 */
@ApplicationScoped
public class ScopeEnforcer {

    @Inject
    JsonWebToken jwt;

    public void require(String requiredScope) {
        String scopeClaim = jwt.getClaim("scope");
        Set<String> scopes = scopeClaim != null
                ? Set.of(scopeClaim.split("\\s+"))
                : Set.of();

        if (!scopes.contains(requiredScope)) {
            throw new ToolCallException(
                    "Insufficient scope: '" + requiredScope + "' required, token has [" + scopeClaim + "]");
        }
    }
}
