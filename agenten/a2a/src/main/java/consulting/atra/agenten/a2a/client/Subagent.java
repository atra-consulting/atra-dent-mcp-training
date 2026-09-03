package consulting.atra.agenten.a2a.client;

public record Subagent(String url, String apiKey) {

    public Subagent {
        url = url == null ? "" : url.strip();
        apiKey = apiKey == null ? "" : apiKey.strip();
    }

    public static Subagent at(String url) {
        return new Subagent(url, "");
    }

    public boolean wired() {
        return !url.isBlank();
    }
}
