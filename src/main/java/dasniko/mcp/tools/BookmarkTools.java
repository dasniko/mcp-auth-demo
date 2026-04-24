package dasniko.mcp.tools;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.jwt.JsonWebToken;
import org.jboss.logging.Logger;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import dasniko.mcp.bookmark.Bookmark;
import dasniko.mcp.bookmark.BookmarkStore;
import dasniko.mcp.security.ScopeEnforcer;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.ToolCallException;

@ApplicationScoped
public class BookmarkTools {

	private static final Logger LOG = Logger.getLogger(BookmarkTools.class);

	@Inject
	JsonWebToken jwt;

	@Inject
	BookmarkStore store;

	@Inject
	ScopeEnforcer scopeEnforcer;

	@Inject
	ObjectMapper mapper;

	// ── list_bookmarks ─────────────────────────────────────────────────────────

	@Tool(name = "list_bookmarks", description = "List all bookmarks belonging to the authenticated user.")
	String listBookmarks() {
		log("list_bookmarks");
		scopeEnforcer.require("bookmarks:read");
		return toJson(store.list(sub()));
	}

	// ── add_bookmark ───────────────────────────────────────────────────────────

	@Tool(name = "add_bookmark", description = "Create a new bookmark. Returns the stored bookmark including its generated UUID.")
	String addBookmark(
			@ToolArg(description = "URL to bookmark") String url,
			@ToolArg(description = "Human-readable title") String title,
			@ToolArg(description = "Optional list of tags", required = false) List<String> tags) {
		log("add_bookmark", "url=" + url, "title=" + title, "tags=" + tags);
		scopeEnforcer.require("bookmarks:write");
		Bookmark created = store.add(sub(), url, title, tags);
		return toJson(created);
	}

	// ── search_bookmarks ───────────────────────────────────────────────────────

	@Tool(name = "search_bookmarks", description = "Case-insensitive substring search across url, title, and tags of the current user's bookmarks.")
	String searchBookmarks(
			@ToolArg(description = "Search query string") String query) {
		log("search_bookmarks", "query=" + query);
		scopeEnforcer.require("bookmarks:read");
		return toJson(store.search(sub(), query));
	}

	// ── delete_bookmark ────────────────────────────────────────────────────────

	@Tool(name = "delete_bookmark", description = "Delete a bookmark by its UUID. Returns an error if the bookmark does not exist or belongs to a different user.")
	String deleteBookmark(
			@ToolArg(description = "UUID of the bookmark to delete") String id) {
		log("delete_bookmark", "id=" + id);
		scopeEnforcer.require("bookmarks:write");
		if (!store.delete(sub(), id)) {
			throw new ToolCallException("Bookmark not found: " + id);
		}
		return "Deleted bookmark " + id;
	}

	// ── helpers ────────────────────────────────────────────────────────────────

	private String sub() {
		return jwt.getSubject();
	}

	private void log(String toolName, String... args) {
		LOG.infof("tool=%s sub=%s user=%s scope=[%s] aud=%s args={%s}",
				toolName,
				jwt.getSubject(),
				jwt.<String>getClaim("preferred_username"),
				jwt.<String>getClaim("scope"),
				jwt.getAudience(),
				String.join(", ", args));
	}

	private String toJson(Object value) {
		try {
			return mapper.writeValueAsString(value);
		} catch (JsonProcessingException e) {
			throw new ToolCallException("Serialisation error: " + e.getMessage(), e);
		}
	}
}
