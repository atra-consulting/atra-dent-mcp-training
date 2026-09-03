
FROM maven:3.9-eclipse-temurin-25 AS bau

WORKDIR /bau

ARG MODELL_REVISION=614241f622f53c4eeff9890bdc4f31cfecc418b3
RUN mkdir -p /bau/modell \
 && curl -fsSL --retry 3 -o /bau/modell/model.onnx \
      "https://huggingface.co/intfloat/multilingual-e5-small/resolve/${MODELL_REVISION}/onnx/model.onnx" \
 && curl -fsSL --retry 3 -o /bau/modell/tokenizer.json \
      "https://huggingface.co/intfloat/multilingual-e5-small/resolve/${MODELL_REVISION}/tokenizer.json"

COPY oas/ oas/
COPY wissen/ wissen/

RUN anzahl_pdf="$(find wissen/generated/pdf -name '*.pdf' 2>/dev/null | wc -l)" \
 && anzahl_html="$(find wissen/generated/html -name '*.html' 2>/dev/null | wc -l)" \
 && if [ "$anzahl_pdf" -eq 0 ] || [ "$anzahl_html" -eq 0 ]; then \
      echo "FEHLER: Die erzeugten Dokumente fehlen (gefunden unter wissen/generated/: ${anzahl_pdf} PDF, ${anzahl_html} HTML)." >&2; \
      echo "Zuerst ./wissen/build.sh ausfuehren, dann das Image erneut bauen." >&2; \
      exit 1; \
    fi

RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -f wissen/dienst/pom.xml package -DskipTests

RUN WISSEN_DATEN_VERZEICHNIS=/bau/wissen/daten \
    WISSEN_DOKUMENTE_VERZEICHNIS=/bau/wissen/generated \
    SPRING_AI_EMBEDDING_TRANSFORMER_ONNX_MODEL_URI=file:/bau/modell/model.onnx \
    SPRING_AI_EMBEDDING_TRANSFORMER_TOKENIZER_URI=file:/bau/modell/tokenizer.json \
    SPRING_AI_EMBEDDING_TRANSFORMER_CACHE_ENABLED=false \
    java -jar wissen/dienst/target/wissensdienst-*.jar > /tmp/probelauf.log 2>&1 & \
    pid=$!; \
    versuche=0; \
    until grep -q "Started WissenAnwendung" /tmp/probelauf.log 2>/dev/null; do \
      if ! kill -0 "$pid" 2>/dev/null || [ "$versuche" -ge 150 ]; then \
        echo "FEHLER: Der Probelauf des Dienstes ist gescheitert:" >&2; \
        cat /tmp/probelauf.log >&2; \
        exit 1; \
      fi; \
      versuche=$((versuche + 1)); \
      sleep 2; \
    done; \
    kill "$pid"; \
    chmod -R a+rX /root/.djl.ai

FROM eclipse-temurin:25-jre

RUN useradd --system --home-dir /anwendung wissensdienst

WORKDIR /anwendung

COPY --from=bau /bau/wissen/dienst/target/wissensdienst-*.jar wissensdienst.jar
COPY --from=bau /bau/wissen/daten/ daten/
COPY --from=bau /bau/wissen/generated/ generated/
COPY --from=bau /bau/modell/ modell/
COPY --from=bau /root/.djl.ai/ djl-cache/

ENV WISSEN_DATEN_VERZEICHNIS=/anwendung/daten \
    WISSEN_DOKUMENTE_VERZEICHNIS=/anwendung/generated \
    SPRING_AI_EMBEDDING_TRANSFORMER_ONNX_MODEL_URI=file:/anwendung/modell/model.onnx \
    SPRING_AI_EMBEDDING_TRANSFORMER_TOKENIZER_URI=file:/anwendung/modell/tokenizer.json \
    SPRING_AI_EMBEDDING_TRANSFORMER_CACHE_ENABLED=false \
    DJL_CACHE_DIR=/anwendung/djl-cache

USER wissensdienst

EXPOSE 8082

ENTRYPOINT ["java", "-jar", "/anwendung/wissensdienst.jar"]
