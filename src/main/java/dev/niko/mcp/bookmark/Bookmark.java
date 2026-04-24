package dev.niko.mcp.bookmark;

import java.util.List;

public record Bookmark(String id, String url, String title, List<String> tags) {}
