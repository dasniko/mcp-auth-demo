package dasniko.mcp.tools;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.jwt.JsonWebToken;
import org.jboss.logging.Logger;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolCallException;

/**
 * Didactic tool: shows the audience what the server actually sees after
 * validating the token. No specific scope is required beyond a valid token —
 * authentication is enforced at the HTTP layer by quarkus.http.auth.permission.
 */
@ApplicationScoped
public class WhoamiTool {

	private static final Logger LOG = Logger.getLogger(WhoamiTool.class);

	@Inject
	JsonWebToken jwt;

	@Inject
	ObjectMapper mapper;

	@Tool(name = "whoami", description = "Returns the validated JWT claims visible to this server: sub, preferred_username, scope, aud, iss, exp. No specific scope required beyond a valid token.")
	String whoami() {
		String sub = jwt.getSubject();
		String username = jwt.getClaim("preferred_username");
		String scope = jwt.getClaim("scope");
		Set<String> aud = jwt.getAudience();

		LOG.infof("tool=whoami sub=%s user=%s scope=[%s] aud=%s", sub, username, scope, aud);

		// LinkedHashMap preserves insertion order for a consistent demo output
		Map<String, Object> claims = new LinkedHashMap<>();
		claims.put("sub", sub);
		claims.put("preferred_username", username);
		claims.put("scope", scope);
		claims.put("aud", aud);
		claims.put("iss", jwt.getIssuer());
		claims.put("exp", jwt.getExpirationTime());

		try {
			return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(claims);
		} catch (JsonProcessingException e) {
			throw new ToolCallException("Serialisation error: " + e.getMessage(), e);
		}
	}
}
