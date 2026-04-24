package dasniko.mcp.bookmark;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * In-memory bookmark store keyed by the user's "sub" claim.
 * All data is lost on restart — intentional for a demo.
 */
@ApplicationScoped
public class BookmarkStore {

	private final ConcurrentHashMap<String, CopyOnWriteArrayList<Bookmark>> store =
			new ConcurrentHashMap<>();

	public List<Bookmark> list(String sub) {
		return List.copyOf(store.getOrDefault(sub, new CopyOnWriteArrayList<>()));
	}

	public Bookmark add(String sub, String url, String title, List<String> tags) {
		var bookmark = new Bookmark(
				UUID.randomUUID().toString(),
				url,
				title,
				tags != null ? List.copyOf(tags) : List.of());
		store.computeIfAbsent(sub, k -> new CopyOnWriteArrayList<>()).add(bookmark);
		return bookmark;
	}

	public List<Bookmark> search(String sub, String query) {
		String q = query.toLowerCase();
		return list(sub).stream()
				.filter(b -> b.url().toLowerCase().contains(q)
						|| b.title().toLowerCase().contains(q)
						|| b.tags().stream().anyMatch(t -> t.toLowerCase().contains(q)))
				.toList();
	}

	/** Returns true if the bookmark existed and was removed. */
	public boolean delete(String sub, String id) {
		CopyOnWriteArrayList<Bookmark> bookmarks = store.get(sub);
		return bookmarks != null && bookmarks.removeIf(b -> b.id().equals(id));
	}
}
