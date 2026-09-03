package consulting.atra.wissen.api;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@TestConfiguration(proxyBeanMethods = false)
public class TestEmbedding {

    @Bean
    EmbeddingModel embeddingModel() {
        return new WortsackModell();
    }

    static final class WortsackModell implements EmbeddingModel {

        private static final int KUEBEL = 512;

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
            return KUEBEL;
        }

        private static float[] vector(String text) {
            float[] values = new float[KUEBEL];
            for (String wort : text.toLowerCase(Locale.GERMAN).split("[^\\p{L}\\p{N}]+")) {
                if (wort.length() < 3) {
                    continue;
                }
                values[Math.floorMod(wort.hashCode(), KUEBEL)] += 1.0f;
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
}
