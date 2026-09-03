package consulting.atra.wissen.mcp;

import consulting.atra.wissen.beratung.BeratungHit;

import java.util.List;

public record LeitfadenResult(
        String frage,
        String vertraulichkeit,
        String vertraulichkeitshinweis,
        List<BeratungHit> abschnitte) {
}
