package consulting.atra.wissen.search;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

public final class CountingModel implements EmbeddingModel {

    private final int dimension;
    private final AtomicInteger calls = new AtomicInteger();

    public CountingModel(int dimension) {
        this.dimension = dimension;
    }

    public int calls() {
        return calls.get();
    }

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        List<Embedding> embeddings = new ArrayList<>();
        List<String> texts = request.getInstructions();
        for (int position = 0; position < texts.size(); position++) {
            embeddings.add(new Embedding(vector(texts.get(position)), position));
        }
        return new EmbeddingResponse(embeddings);
    }

    @Override
    public float[] embed(Document dokument) {
        return vector(dokument.getText());
    }

    @Override
    public int dimensions() {
        return dimension;
    }

    private float[] vector(String text) {
        calls.incrementAndGet();
        float[] values = new float[dimension];
        for (String wort : text.toLowerCase(Locale.GERMAN).split("[^\\p{L}\\p{N}]+")) {
            if (wort.length() < 3) {
                continue;
            }
            values[Math.floorMod(wort.hashCode(), dimension)] += 1.0f;
        }
        double laenge = 0.0;
        for (float value : values) {
            laenge += value * value;
        }
        if (laenge == 0.0) {
            values[0] = 1.0f;
            return values;
        }
        float norm = (float) Math.sqrt(laenge);
        for (int kuebel = 0; kuebel < values.length; kuebel++) {
            values[kuebel] /= norm;
        }
        return values;
    }
}
